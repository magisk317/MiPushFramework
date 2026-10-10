package io.github.magisk317.mipush.common.notification

import android.content.ContentResolver
import android.content.Context
import android.graphics.drawable.Icon
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StatusBarMonochromeIconPolicyTest {
    private val capturedTasks = mutableListOf<Runnable>()
    private val resolver = mockk<ContentResolver>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    @BeforeEach
    fun setUp() {
        StatusBarMonochromeIconPolicy.clearCacheForTest()
        capturedTasks.clear()
        // Capture prefetch tasks instead of running them so tests can assert that the calling
        // thread never touches the content resolver.
        StatusBarMonochromeIconPolicy.setIconPackPrefetchExecutorForTest { capturedTasks.add(it) }
        every { context.applicationContext } returns context
        every { context.contentResolver } returns resolver
    }

    @AfterEach
    fun tearDown() {
        StatusBarMonochromeIconPolicy.setIconPackPrefetchExecutorForTest(null)
        StatusBarMonochromeIconPolicy.clearCacheForTest()
    }

    @Test
    fun `status bar fallback helper returns null when package icon is unavailable`() {
        assertNull(
            StatusBarMonochromeIconPolicy.whiteIconForPackageOrNull(
                context,
                "com.example.app",
            ),
        )
    }

    @Test
    fun `icon pack lookup never queries the provider on the calling thread`() {
        val icon = StatusBarMonochromeIconPolicy.iconPackIconForPackageOrNull(context, "com.example.app", 0)
        assertNull(icon)
        assertEquals(1, capturedTasks.size)
        verify { resolver wasNot Called }
    }

    @Test
    fun `icon pack cache hit returns the cached icon without scheduling work`() {
        val key = StatusBarMonochromeIconPolicy.iconPackCacheKey(0, "com.example.app")
        val cached = mockk<Icon>()
        StatusBarMonochromeIconPolicy.putIconPackEntryForTest(
            key,
            cached,
            System.currentTimeMillis(),
        )
        val icon = StatusBarMonochromeIconPolicy.iconPackIconForPackageOrNull(context, "com.example.app", 0)
        assertEquals(cached, icon)
        assertEquals(0, capturedTasks.size)
        verify { resolver wasNot Called }
    }

    @Test
    fun `failed prefetch is negatively cached and suppresses immediate retries`() {
        val key = StatusBarMonochromeIconPolicy.iconPackCacheKey(0, "com.example.app")
        // Drain the captured task on the test thread. Uri building on the mockable android.jar
        // throws "not mocked", which the worker treats as a provider failure.
        capturedTasks.forEach { it.run() }

        val icon = StatusBarMonochromeIconPolicy.iconPackIconForPackageOrNull(context, "com.example.app", 0)
        assertNull(icon)
        // The first lookup scheduled one task; running it stored a fresh negative entry, so the
        // second lookup must not schedule another within the negative TTL.
        assertEquals(1, capturedTasks.size)
        StatusBarMonochromeIconPolicy.putIconPackEntryForTest(key, null, System.currentTimeMillis())
        assertNull(
            StatusBarMonochromeIconPolicy.iconPackIconForPackageOrNull(context, "com.example.app", 0),
        )
        assertEquals(1, capturedTasks.size)
    }

    @Test
    fun `cache decision covers miss stale negative and fresh states`() {
        val icon = mockk<Icon>()
        val now = 1_000_000L
        val fresh = StatusBarMonochromeIconPolicy.IconPackEntry(icon, now - 1_000)
        val stale = StatusBarMonochromeIconPolicy.IconPackEntry(icon, now - 11 * 60 * 1000)
        val negativeFresh = StatusBarMonochromeIconPolicy.IconPackEntry(null, now - 1_000)
        val negativeExpired = StatusBarMonochromeIconPolicy.IconPackEntry(null, now - 60_000)

        assertEquals(
            StatusBarMonochromeIconPolicy.IconPackCacheDecision.HIT_FRESH,
            StatusBarMonochromeIconPolicy.decideIconPackCache(fresh, now),
        )
        assertEquals(
            StatusBarMonochromeIconPolicy.IconPackCacheDecision.HIT_STALE,
            StatusBarMonochromeIconPolicy.decideIconPackCache(stale, now),
        )
        assertEquals(
            StatusBarMonochromeIconPolicy.IconPackCacheDecision.HIT_NEGATIVE_FRESH,
            StatusBarMonochromeIconPolicy.decideIconPackCache(negativeFresh, now),
        )
        assertEquals(
            StatusBarMonochromeIconPolicy.IconPackCacheDecision.MISS,
            StatusBarMonochromeIconPolicy.decideIconPackCache(negativeExpired, now),
        )
        assertEquals(
            StatusBarMonochromeIconPolicy.IconPackCacheDecision.MISS,
            StatusBarMonochromeIconPolicy.decideIconPackCache(null, now),
        )
    }
}
