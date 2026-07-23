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
        )

        val token = ManagerApplicationPageToken.encode(query, "com.example.client")

        assertEquals("com.example.client", ManagerApplicationPageToken.decode(query, token))
        assertThrows(IllegalArgumentException::class.java) {
            ManagerApplicationPageToken.decode(query.copy(pageSize = 10), token)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ManagerApplicationPageToken.decode(query.copy(schemaVersion = 2), token)
        }
    }

    @Test
    fun `malformed and unsupported tokens are rejected`() {
        val query = ManagerApplicationReadQuery()

        assertThrows(IllegalArgumentException::class.java) {
            ManagerApplicationPageToken.decode(query, "not-base64!")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ManagerApplicationPageToken.decode(query, "AQ")
        }
    }
}
