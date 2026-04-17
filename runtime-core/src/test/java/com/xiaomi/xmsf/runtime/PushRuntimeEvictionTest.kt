package io.github.magisk317.mipush.runtime.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PushRuntimeEvictionTest {

    @Test
    fun `registration records are capped and oldest evicted`() {
        PushRuntime.clearStateForTests()

        // Register more packages than MAX_REGISTRATION_RECORDS (512)
        val count = 600
        for (i in 1..count) {
            PushRuntime.observeRegistrationResult(
                packageName = "com.example.pkg$i",
                success = true,
                source = "test"
            )
        }

        val snapshot = PushRuntime.snapshot()
        assertTrue(
            snapshot.trackedRegistrationCount <= 512,
            "Expected at most 512 registration records, got ${snapshot.trackedRegistrationCount}"
        )

        // Oldest packages should have been evicted
        assertNull(
            PushRuntime.getRegistrationRecord("com.example.pkg1"),
            "Oldest registration record should have been evicted"
        )

        // Most recent packages should still be present
        assertNotNull(
            PushRuntime.getRegistrationRecord("com.example.pkg$count"),
            "Most recent registration record should still be present"
        )
    }

    @Test
    fun `channel records are capped and oldest evicted`() {
        PushRuntime.clearStateForTests()

        // Create more channel records than MAX_CHANNEL_RECORDS (256)
        val count = 300
        for (i in 1..count) {
            PushRuntime.observeChannelState(
                packageName = "com.example.ch$i",
                channelId = "$i",
                userId = "user$i",
                session = "s$i",
                state = PushChannelState.Bound,
                source = "test"
            )
        }

        val records = PushRuntime.getChannelRecords()
        assertTrue(
            records.size <= 256,
            "Expected at most 256 channel records, got ${records.size}"
        )

        // Oldest channel should have been evicted
        val hasOldest = records.any { it.packageName == "com.example.ch1" }
        assertTrue(!hasOldest, "Oldest channel record should have been evicted")

        // Most recent channel should still be present
        val hasNewest = records.any { it.packageName == "com.example.ch$count" }
        assertTrue(hasNewest, "Most recent channel record should still be present")
    }

    @Test
    fun `eviction preserves most recent entries`() {
        PushRuntime.clearStateForTests()

        for (i in 1..520) {
            PushRuntime.observeRegistrationResult(
                packageName = "com.example.pkg$i",
                success = true,
                source = "test"
            )
        }

        // Packages 9..520 should survive (512 entries), packages 1..8 evicted
        for (i in 9..520) {
            assertNotNull(
                PushRuntime.getRegistrationRecord("com.example.pkg$i"),
                "Package com.example.pkg$i should still be present"
            )
        }
        for (i in 1..8) {
            assertNull(
                PushRuntime.getRegistrationRecord("com.example.pkg$i"),
                "Package com.example.pkg$i should have been evicted"
            )
        }
    }
}
