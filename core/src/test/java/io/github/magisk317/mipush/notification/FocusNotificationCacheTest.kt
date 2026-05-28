package io.github.magisk317.mipush.notification

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class FocusNotificationCacheTest {

    private val store = InMemoryDeletedFocusStore()

    @BeforeEach
    fun setUp() {
        FocusNotificationCache.resetForTest()
        FocusNotificationCache.installPersistentStoreForTest(store)
    }

    @AfterEach
    fun tearDown() {
        FocusNotificationCache.resetForTest()
    }

    // --- shouldFilter() tests ---

    @Nested
    inner class ShouldFilterTests {

        @Test
        fun `returns false for null focusParam`() {
            assertFalse(
                FocusNotificationCache.shouldFilter(null, "pkg", 1, nowMs = 1_000L)
            )
        }

        @Test
        fun `returns false for invalid JSON focusParam`() {
            assertFalse(
                FocusNotificationCache.shouldFilter("not json", "pkg", 1, nowMs = 1_000L)
            )
        }

        @Test
        fun `returns false for non-updatable focus`() {
            val param = """{"updatable":false,"reopen":"close"}"""
            FocusNotificationCache.onFocusDeleted("pkg", 1, nowMs = 1_000L)
            assertFalse(
                FocusNotificationCache.shouldFilter(param, "pkg", 1, nowMs = 2_000L)
            )
        }

        @Test
        fun `returns false when notification not previously deleted`() {
            val param = """{"updatable":true,"reopen":"close"}"""
            assertFalse(
                FocusNotificationCache.shouldFilter(param, "pkg", 1, nowMs = 1_000L)
            )
        }

        @Test
        fun `returns true when notification was deleted and reopen is close`() {
            val param = """{"updatable":true,"reopen":"close"}"""
            FocusNotificationCache.onFocusDeleted("pkg", 1, nowMs = 1_000L)
            assertTrue(
                FocusNotificationCache.shouldFilter(param, "pkg", 1, nowMs = 2_000L)
            )
        }

        @Test
        fun `returns false when reopen is not close (clears cache)`() {
            val closeParam = """{"updatable":true,"reopen":"close"}"""
            val reopenParam = """{"updatable":true,"reopen":"reopen"}"""

            FocusNotificationCache.onFocusDeleted("pkg", 1, nowMs = 1_000L)
            assertTrue(
                FocusNotificationCache.shouldFilter(closeParam, "pkg", 1, nowMs = 2_000L)
            )

            // Reopen clears the cache entry
            assertFalse(
                FocusNotificationCache.shouldFilter(reopenParam, "pkg", 1, nowMs = 3_000L)
            )

            // After reopen, close should no longer filter
            assertFalse(
                FocusNotificationCache.shouldFilter(closeParam, "pkg", 1, nowMs = 4_000L)
            )
        }

        @Test
        fun `different notification IDs are independent`() {
            val param = """{"updatable":true,"reopen":"close"}"""
            FocusNotificationCache.onFocusDeleted("pkg", 1, nowMs = 1_000L)

            assertTrue(
                FocusNotificationCache.shouldFilter(param, "pkg", 1, nowMs = 2_000L)
            )
            assertFalse(
                FocusNotificationCache.shouldFilter(param, "pkg", 2, nowMs = 2_000L)
            )
        }

        @Test
        fun `different packages are independent`() {
            val param = """{"updatable":true,"reopen":"close"}"""
            FocusNotificationCache.onFocusDeleted("pkg.a", 1, nowMs = 1_000L)

            assertTrue(
                FocusNotificationCache.shouldFilter(param, "pkg.a", 1, nowMs = 2_000L)
            )
            assertFalse(
                FocusNotificationCache.shouldFilter(param, "pkg.b", 1, nowMs = 2_000L)
            )
        }
    }

    // --- Expiry tests ---

    @Nested
    inner class ExpiryTests {

        @Test
        fun `deleted entry expires after 24 hours`() {
            val param = """{"updatable":true,"reopen":"close"}"""
            val deleteTime = 1_000L
            val ttl = 24 * 60 * 60 * 1000L

            FocusNotificationCache.onFocusDeleted("pkg", 1, nowMs = deleteTime)

            // Still within TTL
            assertTrue(
                FocusNotificationCache.shouldFilter(param, "pkg", 1, nowMs = deleteTime + ttl - 1)
            )

            // Exactly at TTL boundary (expired)
            assertFalse(
                FocusNotificationCache.shouldFilter(param, "pkg", 1, nowMs = deleteTime + ttl)
            )

            // After TTL
            assertFalse(
                FocusNotificationCache.shouldFilter(param, "pkg", 1, nowMs = deleteTime + ttl + 1)
            )
        }
    }

    // --- Prune / max size tests ---

    @Nested
    inner class PruneAndMaxSizeTests {

        @Test
        fun `cache enforces max size of 50`() {
            val param = """{"updatable":true,"reopen":"close"}"""

            // Add 55 entries
            for (i in 1..55) {
                FocusNotificationCache.onFocusDeleted("pkg", i, nowMs = 1_000L + i)
            }

            // The earliest entries should have been evicted
            assertFalse(
                FocusNotificationCache.shouldFilter(param, "pkg", 1, nowMs = 2_000L)
            )

            // The latest entries should still be present
            assertTrue(
                FocusNotificationCache.shouldFilter(param, "pkg", 55, nowMs = 2_000L)
            )
        }

        @Test
        fun `expired entries are pruned on access`() {
            val param = """{"updatable":true,"reopen":"close"}"""
            val ttl = 24 * 60 * 60 * 1000L

            FocusNotificationCache.onFocusDeleted("pkg", 1, nowMs = 1_000L)
            FocusNotificationCache.onFocusDeleted("pkg", 2, nowMs = 1_000L + ttl)

            // At time ttl + 2000, entry 1 is expired but entry 2 is still valid
            assertFalse(
                FocusNotificationCache.shouldFilter(param, "pkg", 1, nowMs = 1_000L + ttl + 1)
            )
            assertTrue(
                FocusNotificationCache.shouldFilter(param, "pkg", 2, nowMs = 1_000L + ttl + 1)
            )
        }
    }

    // --- Persistence tests ---

    @Nested
    inner class PersistenceTests {

        @Test
        fun `deleted entry is persisted to store`() {
            FocusNotificationCache.onFocusDeleted("pkg", 42, nowMs = 1_000L)
            assertTrue(store.contains("pkg:42"))
        }

        @Test
        fun `reopen removes entry from persistent store`() {
            val reopenParam = """{"updatable":true,"reopen":"reopen"}"""
            FocusNotificationCache.onFocusDeleted("pkg", 42, nowMs = 1_000L)
            assertTrue(store.contains("pkg:42"))

            FocusNotificationCache.shouldFilter(reopenParam, "pkg", 42, nowMs = 2_000L)
            assertFalse(store.contains("pkg:42"))
        }

        @Test
        fun `cache reloads from persistent store after memory clear`() {
            val param = """{"updatable":true,"reopen":"close"}"""
            FocusNotificationCache.onFocusDeleted("pkg", 42, nowMs = 1_000L)

            FocusNotificationCache.clearMemoryCacheForTest()

            assertTrue(
                FocusNotificationCache.shouldFilter(param, "pkg", 42, nowMs = 2_000L)
            )
        }
    }

    // --- parseFocusParam() tests ---

    @Nested
    inner class ParseFocusParamTests {

        @Test
        fun `returns null for null input`() {
            assertNull(FocusNotificationCache.parseFocusParam(null))
        }

        @Test
        fun `returns null for invalid JSON`() {
            assertNull(FocusNotificationCache.parseFocusParam("not json"))
        }

        @Test
        fun `returns null for empty string`() {
            assertNull(FocusNotificationCache.parseFocusParam(""))
        }

        @Test
        fun `parses legacy root format`() {
            val param = """{"updatable":true,"reopen":"close"}"""
            val result = FocusNotificationCache.parseFocusParam(param)
            assertNotNull(result)
            assertEquals(true, result!!.updatable)
            assertEquals("close", result.reopen)
        }

        @Test
        fun `parses param_v2 format`() {
            val param = """{"param_v2":{"updatable":true,"reopen":"reopen"}}"""
            val result = FocusNotificationCache.parseFocusParam(param)
            assertNotNull(result)
            assertEquals(true, result!!.updatable)
            assertEquals("reopen", result.reopen)
        }

        @Test
        fun `param_v2 boolean false reopen is parsed as close`() {
            val param = """{"param_v2":{"updatable":true,"reopen":false}}"""
            val result = FocusNotificationCache.parseFocusParam(param)
            assertNotNull(result)
            assertEquals(true, result!!.updatable)
            assertEquals("close", result.reopen)
        }

        @Test
        fun `param_v2 boolean true reopen is parsed as reopen`() {
            val param = """{"param_v2":{"updatable":true,"reopen":true}}"""
            val result = FocusNotificationCache.parseFocusParam(param)
            assertNotNull(result)
            assertEquals(true, result!!.updatable)
            assertEquals("reopen", result.reopen)
        }

        @Test
        fun `updatable defaults to false when missing`() {
            val param = """{"reopen":"close"}"""
            val result = FocusNotificationCache.parseFocusParam(param)
            assertNotNull(result)
            assertEquals(false, result!!.updatable)
        }

        @Test
        fun `reopen defaults to close when missing`() {
            val param = """{"updatable":true}"""
            val result = FocusNotificationCache.parseFocusParam(param)
            assertNotNull(result)
            assertEquals("close", result!!.reopen)
        }
    }

    // --- cacheKey() tests ---

    @Nested
    inner class CacheKeyTests {

        @Test
        fun `generates correct key format`() {
            assertEquals("com.example:42", FocusNotificationCache.cacheKey("com.example", 42))
        }

        @Test
        fun `different packages produce different keys`() {
            val key1 = FocusNotificationCache.cacheKey("pkg.a", 1)
            val key2 = FocusNotificationCache.cacheKey("pkg.b", 1)
            assertTrue(key1 != key2)
        }

        @Test
        fun `different IDs produce different keys`() {
            val key1 = FocusNotificationCache.cacheKey("pkg", 1)
            val key2 = FocusNotificationCache.cacheKey("pkg", 2)
            assertTrue(key1 != key2)
        }
    }

    // --- Test helper ---

    private class InMemoryDeletedFocusStore : FocusNotificationCache.DeletedFocusStore {
        private val values = mutableMapOf<String, Long>()

        override fun readAll(): Map<String, Long> = values.toMap()

        override fun put(key: String, expiresAtMs: Long) {
            values[key] = expiresAtMs
        }

        override fun remove(key: String) {
            values.remove(key)
        }

        override fun clear() {
            values.clear()
        }

        fun contains(key: String): Boolean = values.containsKey(key)
    }
}
