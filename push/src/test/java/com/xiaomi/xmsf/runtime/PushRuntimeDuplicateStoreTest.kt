package com.xiaomi.xmsf.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushRuntimeDuplicateStoreTest {

    @Test
    fun `parseStoredEntries upgrades legacy comma payloads`() {
        val parsed = PushRuntimeDuplicateStore.parseStoredEntries("msg-1,msg-2", nowMs = 1_000L)

        assertEquals(linkedMapOf("msg-1" to 1_000L, "msg-2" to 1_000L), parsed)
    }

    @Test
    fun `checkAndMark expires stale ids before duplicate check`() {
        val entries = linkedMapOf("msg-1" to 1L)

        assertFalse(
            PushRuntimeDuplicateStore.checkAndMark(
                entries = entries,
                messageId = "msg-1",
                nowMs = PushRuntimeDuplicateStore.MESSAGE_ID_TTL_MS + 5L,
            ),
        )
        assertEquals(PushRuntimeDuplicateStore.MESSAGE_ID_TTL_MS + 5L, entries["msg-1"])
    }

    @Test
    fun `checkAndMark keeps duplicate within ttl window`() {
        val entries = linkedMapOf<String, Long>()

        assertFalse(PushRuntimeDuplicateStore.checkAndMark(entries, "msg-1", nowMs = 1_000L))
        assertTrue(PushRuntimeDuplicateStore.checkAndMark(entries, "msg-1", nowMs = 2_000L))
    }
}
