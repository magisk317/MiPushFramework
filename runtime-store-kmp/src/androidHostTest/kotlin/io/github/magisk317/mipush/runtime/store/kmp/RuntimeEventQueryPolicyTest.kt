package io.github.magisk317.mipush.runtime.store.kmp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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
                "AND type IN (?,?) AND (search_text LIKE ? OR (search_text IS NULL AND dev_info LIKE ?)) " +
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
    fun `page query keeps current search behavior and paging order`() {
        val query = RuntimeEventQueryPolicy.page(
            offset = 40,
            limit = 20,
            types = null,
            packageName = " ",
            text = "body",
            userId = -1,
        )

        assertEquals(
            "SELECT * FROM EVENT WHERE user_id = ? " +
                "AND (search_text LIKE ? OR dev_info LIKE ?) " +
                "ORDER BY date DESC LIMIT ? OFFSET ?",
            query.sql,
        )
        assertEquals(
            listOf(
                RuntimeQueryArgument.IntValue(0),
                RuntimeQueryArgument.TextValue("%body%"),
                RuntimeQueryArgument.TextValue("%body%"),
                RuntimeQueryArgument.IntValue(20),
                RuntimeQueryArgument.IntValue(40),
            ),
            query.arguments,
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
