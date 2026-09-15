package io.github.magisk317.mipush.subscribe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubscribeChannelSyncPolicyTest {

    @Test
    fun `request entries default to stock minus one for unknown packages`() {
        val entries = SubscribeChannelSyncPolicy.requestEntries(
            knownVersions = mapOf("com.foo" to 12L),
            packages = listOf("com.foo", "", "com.bar"),
        )

        assertEquals(2, entries.size)
        assertEquals("com.foo", entries[0].packageName)
        assertEquals(12L, entries[0].appConfigVersion)
        assertEquals("com.bar", entries[1].packageName)
        assertEquals(SubscribeChannelSyncPolicy.DEFAULT_APP_CONFIG_VERSION, entries[1].appConfigVersion)
    }

    @Test
    fun `batch composition matches stock 50 per batch chunking`() {
        val apps = (1..120).map { SubscribeChannelSyncPolicy.AppConfigEntry("com.app$it", it.toLong()) }

        val batches = SubscribeChannelSyncPolicy.buildRequestBatches("session-1", apps)

        assertEquals(3, batches.size)
        assertEquals(50, batches[0].apps.size)
        assertEquals(50, batches[1].apps.size)
        assertEquals(20, batches[2].apps.size)
        batches.forEachIndexed { index, batch ->
            assertEquals("session-1", batch.sessionId)
            assertEquals(index, batch.batchIndex)
            assertEquals(3, batch.totalBatch)
            assertEquals(120, batch.totalNum)
        }
        assertEquals("com.app1", batches[0].apps.first().packageName)
        assertEquals("com.app50", batches[0].apps.last().packageName)
        assertEquals("com.app120", batches[2].apps.last().packageName)
    }

    @Test
    fun `exact multiple of batch size does not add an empty batch`() {
        val apps = (1..100).map { SubscribeChannelSyncPolicy.AppConfigEntry("com.app$it", 0L) }

        assertEquals(2, SubscribeChannelSyncPolicy.buildRequestBatches("s", apps).size)
    }

    @Test
    fun `empty or unkeyed sessions produce no batches`() {
        assertTrue(SubscribeChannelSyncPolicy.buildRequestBatches("s", emptyList()).isEmpty())
        assertTrue(SubscribeChannelSyncPolicy.buildRequestBatches("", listOf(SubscribeChannelSyncPolicy.AppConfigEntry("com.a", 1L))).isEmpty())
    }

    @Test
    fun `custom batch size keeps session totals consistent`() {
        val apps = (1..7).map { SubscribeChannelSyncPolicy.AppConfigEntry("com.app$it", it.toLong()) }

        val batches = SubscribeChannelSyncPolicy.buildRequestBatches("s", apps, batchSize = 3)

        assertEquals(3, batches.size)
        assertEquals(3, batches[0].apps.size)
        assertEquals(3, batches[1].apps.size)
        assertEquals(1, batches[2].apps.size)
        batches.forEach { assertEquals(7, it.totalNum) }
        assertEquals(3, batches.maxOf { it.totalBatch })
    }

    @Test
    fun `result merge treats server as authoritative and filters garbage`() {
        val merged = SubscribeChannelSyncPolicy.applyResult(
            current = mapOf("com.foo" to 5L, "com.keep" to 9L),
            received = mapOf(
                "com.foo" to 3L,                       // lower version still overwrites (server copy wins)
                "" to 42L,                             // blank package ignored
                "com.bad" to -7L,                      // below sentinel ignored
                "com.new" to SubscribeChannelSyncPolicy.DEFAULT_APP_CONFIG_VERSION, // explicit -1 accepted
            ),
        )

        assertEquals(3L, merged["com.foo"])
        assertEquals(9L, merged["com.keep"])
        assertEquals(-1L, merged["com.new"])
        assertEquals(null, merged["com.bad"])
    }

    @Test
    fun `ack extras copy inbound map and append stock session keys`() {
        val extras = SubscribeChannelSyncPolicy.ackExtras(
            existingExtra = mapOf("trace" to "abc"),
            sessionId = "session-9",
            batchIndex = 2,
        )

        assertEquals("abc", extras["trace"])
        assertEquals("session-9", extras[SubscribeChannelSyncPolicy.EXTRA_SESSION_ID])
        assertEquals("2", extras[SubscribeChannelSyncPolicy.EXTRA_BATCH_INDEX])
    }

    @Test
    fun `ack extras keep stock String valueOf null semantics`() {
        val extras = SubscribeChannelSyncPolicy.ackExtras(null, null, 0)

        assertEquals("null", extras[SubscribeChannelSyncPolicy.EXTRA_SESSION_ID])
        assertEquals("0", extras[SubscribeChannelSyncPolicy.EXTRA_BATCH_INDEX])
    }
}
