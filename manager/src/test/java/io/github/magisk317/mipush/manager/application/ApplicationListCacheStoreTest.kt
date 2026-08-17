package io.github.magisk317.mipush.manager.application

import io.github.magisk317.mipush.common.manager.ManagerApplication
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ApplicationListCacheStoreTest {
    @Test
    fun `cache bucket includes user query filter and system app scope`() {
        val queryBucket = ApplicationListCacheStore.bucketKey(10, "mail", 0, false)
        val filterBucket = ApplicationListCacheStore.bucketKey(10, "mail", 1, false)
        val userBucket = ApplicationListCacheStore.bucketKey(11, "mail", 0, false)
        val systemBucket = ApplicationListCacheStore.bucketKey(10, "mail", 0, true)

        assertNotEquals(queryBucket, filterBucket)
        assertNotEquals(queryBucket, userBucket)
        assertNotEquals(queryBucket, systemBucket)
    }

    @Test
    fun `invalid snapshot cannot become a successful empty cache`() {
        val snapshot = CachedApplicationSnapshot(
            userId = 7,
            query = "",
            filterMode = 0,
            includeSystemApps = false,
            applications = listOf(ManagerApplication(userId = 8, packageName = "wrong-user")),
            totalPkg = 0,
            total = 0,
            usingMiPush = 0,
            notUsingMiPush = 0,
            registered = 0,
            notRegistered = 0,
        )

        assertFalse(snapshot.isValid())
    }

    @Test
    fun `valid empty snapshot is a distinct scoped result`() {
        val snapshot = CachedApplicationSnapshot(
            userId = 7,
            query = "",
            filterMode = 0,
            includeSystemApps = false,
            applications = emptyList(),
            totalPkg = 0,
            total = 0,
            usingMiPush = 0,
            notUsingMiPush = 0,
            registered = 0,
            notRegistered = 0,
        )

        assertTrue(snapshot.isValid())
    }
}
