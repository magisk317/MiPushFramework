package io.github.magisk317.mipush.manager.runtime.read

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ManagerApplicationPageTokenTest {
    @Test
    fun `token round trips only for the issuing query`() {
        val query = ManagerApplicationReadQuery(
            query = "push",
            filterMode = ManagerApplicationReadQuery.FILTER_REGISTERED,
            includeSystemApps = true,
            pageSize = 25,
            userId = 0,
        )

        val token = ManagerApplicationPageToken.encode(query, "com.example.client", 0)

        assertEquals("com.example.client", ManagerApplicationPageToken.decode(query, token, 0))
        assertThrows(IllegalArgumentException::class.java) {
            ManagerApplicationPageToken.decode(query.copy(pageSize = 10), token, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ManagerApplicationPageToken.decode(query.copy(schemaVersion = 2), token, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ManagerApplicationPageToken.decode(query, token, 999)
        }
    }

    @Test
    fun `malformed and unsupported tokens are rejected`() {
        val query = ManagerApplicationReadQuery(userId = 0)

        assertThrows(IllegalArgumentException::class.java) {
            ManagerApplicationPageToken.decode(query, "not-base64!", 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ManagerApplicationPageToken.decode(query, "AQ", 0)
        }
    }
}
