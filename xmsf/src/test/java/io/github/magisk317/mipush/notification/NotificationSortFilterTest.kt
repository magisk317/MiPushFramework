package io.github.magisk317.mipush.notification

import com.xiaomi.xmpush.thrift.PushMetaInfo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationSortFilterTest {

    @AfterEach
    fun tearDown() {
        NotificationSortFilter.resetForTest()
    }

    @Test
    fun `deleted focus notification is filtered until timeout`() {
        val metaInfo = focusMeta("close")

        assertFalse(NotificationSortFilter.shouldFilter(metaInfo, "pkg", 42, nowMs = 1_000L))

        NotificationSortFilter.onFocusDeleted("pkg", 42, nowMs = 1_000L)

        assertTrue(NotificationSortFilter.shouldFilter(metaInfo, "pkg", 42, nowMs = 1_000L))
        assertFalse(NotificationSortFilter.shouldFilter(metaInfo, "pkg", 42, nowMs = 86_401_001L))
    }

    @Test
    fun `deleted focus cache reloads from persistent store`() {
        val store = InMemoryDeletedFocusStore()
        NotificationSortFilter.installPersistentStoreForTest(store)
        NotificationSortFilter.onFocusDeleted("pkg", 42, nowMs = 1_000L)
        NotificationSortFilter.clearMemoryCacheForTest()

        assertTrue(NotificationSortFilter.shouldFilter(focusMeta("close"), "pkg", 42, nowMs = 2_000L))
    }

    @Test
    fun `reopen focus notification clears deleted cache`() {
        val closeMeta = focusMeta("close")
        val reopenMeta = focusMeta("reopen")
        val store = InMemoryDeletedFocusStore()
        NotificationSortFilter.installPersistentStoreForTest(store)

        NotificationSortFilter.onFocusDeleted("pkg", 42, nowMs = 1_000L)

        assertFalse(NotificationSortFilter.shouldFilter(reopenMeta, "pkg", 42, nowMs = 2_000L))
        assertFalse(store.contains("pkg:42"))
        assertFalse(NotificationSortFilter.shouldFilter(closeMeta, "pkg", 42, nowMs = 3_000L))
    }

    @Test
    fun `non updatable focus notifications are not filtered`() {
        val metaInfo = PushMetaInfo().apply {
            extra = mutableMapOf("miui.focus.param" to """{"updatable":false,"reopen":"close"}""")
        }

        NotificationSortFilter.onFocusDeleted("pkg", 42, nowMs = 1_000L)

        assertFalse(NotificationSortFilter.shouldFilter(metaInfo, "pkg", 42, nowMs = 2_000L))
    }

    private fun focusMeta(reopen: String): PushMetaInfo {
        return PushMetaInfo().apply {
            extra = mutableMapOf("miui.focus.param" to """{"updatable":true,"reopen":"$reopen"}""")
        }
    }

    private class InMemoryDeletedFocusStore : NotificationSortFilter.DeletedFocusStore {
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
