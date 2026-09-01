package io.github.magisk317.mipush.runtime.store.kmp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RuntimeEventQueryPolicyTest {
    @Test
    fun `cursor query preserves filters and uses legacy search fallback`() {
        val query = RuntimeEventQueryPolicy.byId(
            lastId = 42L,
            size = 20,
            types = setOf(21, 2),
            packageName = "com.example.app",
            text = "hello",
            userId = 999,
        )

        assertEquals(
            "SELECT * FROM EVENT WHERE user_id = ? AND id < ? AND pkg = ? " +
                "AND (type IN (?,?)) AND (search_text LIKE ? OR (search_text IS NULL AND dev_info LIKE ?)) " +
                "ORDER BY id DESC LIMIT ?",
            query.sql,
        )
        assertEquals(
            listOf(
                RuntimeQueryArgument.IntValue(999),
                RuntimeQueryArgument.LongValue(42L),
                RuntimeQueryArgument.TextValue("com.example.app"),
                RuntimeQueryArgument.IntValue(2),
                RuntimeQueryArgument.IntValue(21),
                RuntimeQueryArgument.TextValue("%hello%"),
                RuntimeQueryArgument.TextValue("%hello%"),
                RuntimeQueryArgument.IntValue(20),
            ),
            query.arguments,
        )
    }

    @Test
    fun `default event query keeps only push messages and excludes registration`() {
        val query = RuntimeEventQueryPolicy.byId(
            lastId = null,
            size = 20,
            types = setOf(EventRowType.SendMessage),
            packageName = "com.example.app",
            text = null,
            userId = 999,
            includeFirstSuccessfulRegistration = false,
            excludedResults = setOf(EventRowResultType.DENY_DISABLED),
        )

        assertEquals(
            "SELECT * FROM EVENT WHERE user_id = ? AND pkg = ? " +
                "AND (type IN (?) AND result NOT IN (?)) " +
                "ORDER BY id DESC LIMIT ?",
            query.sql,
        )
        assertEquals(
            listOf(
                RuntimeQueryArgument.IntValue(999),
                RuntimeQueryArgument.TextValue("com.example.app"),
                RuntimeQueryArgument.IntValue(EventRowType.SendMessage),
                RuntimeQueryArgument.IntValue(EventRowResultType.DENY_DISABLED),
                RuntimeQueryArgument.IntValue(20),
            ),
            query.arguments,
        )
    }

    @Test
    fun `default query can include only the first successful registration result`() {
        val query = RuntimeEventQueryPolicy.byId(
            lastId = null,
            size = 20,
            types = setOf(EventRowType.SendMessage),
            packageName = "com.example.app",
            text = null,
            userId = 999,
            includeFirstSuccessfulRegistration = true,
            excludedResults = setOf(EventRowResultType.DENY_DISABLED),
        )

        assertTrue(query.sql.contains("OR (type = ? AND result = ? AND NOT EXISTS"))
        assertEquals(
            listOf(
                RuntimeQueryArgument.IntValue(999),
                RuntimeQueryArgument.TextValue("com.example.app"),
                RuntimeQueryArgument.IntValue(EventRowType.SendMessage),
                RuntimeQueryArgument.IntValue(EventRowResultType.DENY_DISABLED),
                RuntimeQueryArgument.IntValue(EventRowType.RegistrationResult),
                RuntimeQueryArgument.IntValue(EventRowResultType.OK),
                RuntimeQueryArgument.IntValue(EventRowType.RegistrationResult),
                RuntimeQueryArgument.IntValue(EventRowResultType.OK),
                RuntimeQueryArgument.IntValue(20),
            ),
            query.arguments,
        )
    }

    @Test
    fun `page query rejects invalid user instead of falling back to primary`() {
        assertThrows<IllegalArgumentException> {
            RuntimeEventQueryPolicy.page(
                offset = 40,
                limit = 20,
                types = null,
                packageName = " ",
                text = "body",
                userId = -1,
            )
        }
    }


    @Test
    fun `disabled messages are hidden by default but activity policy is type scoped`() {
        assertTrue(
            RuntimeEventVisibilityPolicy.isHiddenByDefault(
                type = EventRowType.SendMessage,
                result = EventRowResultType.OK,
                currentlyDisabled = true,
            ),
        )
        assertTrue(
            RuntimeEventVisibilityPolicy.isHiddenByDefault(
                type = EventRowType.SendMessage,
                result = EventRowResultType.DENY_DISABLED,
                currentlyDisabled = false,
            ),
        )
        assertFalse(
            RuntimeEventVisibilityPolicy.isHiddenByDefault(
                type = EventRowType.SendMessage,
                result = EventRowResultType.OK,
                currentlyDisabled = false,
            ),
        )
        assertFalse(
            RuntimeEventVisibilityPolicy.isHiddenByDefault(
                type = EventRowType.RegistrationResult,
                result = EventRowResultType.OK,
                currentlyDisabled = true,
            ),
        )
    }
    @Test
    fun `blank filters are omitted`() {
        val query = RuntimeEventQueryPolicy.byId(
            lastId = null,
            size = 10,
            types = emptySet(),
            packageName = null,
            text = " ",
            userId = 0,
        )

        assertEquals(
            "SELECT * FROM EVENT WHERE user_id = ? ORDER BY id DESC LIMIT ?",
            query.sql,
        )
        assertEquals(
            listOf(RuntimeQueryArgument.IntValue(0), RuntimeQueryArgument.IntValue(10)),
            query.arguments,
        )
    }
}
