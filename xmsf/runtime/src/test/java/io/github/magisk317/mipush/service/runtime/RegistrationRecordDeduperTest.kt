package io.github.magisk317.mipush.service.runtime

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RegistrationRecordDeduperTest {
    @AfterEach
    fun tearDown() {
        RegistrationRecordDeduper.reset()
    }

    @Test
    fun `skips only repeated local records inside the window`() {
        assertFalse(RegistrationRecordDeduper.shouldSkip("com.example.app", nowMs = 1_000L, userId = 0))
        assertTrue(RegistrationRecordDeduper.shouldSkip("com.example.app", nowMs = 1_500L, userId = 0))
        assertFalse(RegistrationRecordDeduper.shouldSkip("com.example.other", nowMs = 1_500L, userId = 0))
    }

    @Test
    fun `allows a new record at the window boundary`() {
        assertFalse(RegistrationRecordDeduper.shouldSkip("com.example.app", nowMs = 1_000L, userId = 0))
        assertFalse(
            RegistrationRecordDeduper.shouldSkip(
                "com.example.app",
                nowMs = 1_000L + RegistrationRecordDeduper.DEDUP_WINDOW_MS,
                userId = 0,
            ),
        )
    }

    @Test
    fun `force register marker coalesces only the matching package record`() {
        RegistrationRecordDeduper.markRecorded("com.example.app", nowMs = 1_000L, userId = 0)

        assertTrue(RegistrationRecordDeduper.shouldSkip("com.example.app", nowMs = 1_500L, userId = 0))
        assertFalse(RegistrationRecordDeduper.shouldSkip("com.example.other", nowMs = 1_500L, userId = 0))
    }

    @Test
    fun `package reset removes only that record marker`() {
        RegistrationRecordDeduper.markRecorded("com.example.app", nowMs = 1_000L, userId = 0)
        RegistrationRecordDeduper.markRecorded("com.example.other", nowMs = 1_000L, userId = 0)

        RegistrationRecordDeduper.reset("com.example.app", userId = 0)

        assertFalse(RegistrationRecordDeduper.shouldSkip("com.example.app", nowMs = 1_500L, userId = 0))
        assertTrue(RegistrationRecordDeduper.shouldSkip("com.example.other", nowMs = 1_500L, userId = 0))
    }

    @Test
    fun `same package is independent across users`() {
        assertFalse(
            RegistrationRecordDeduper.shouldSkip(
                "com.example.app",
                nowMs = 1_000L,
                userId = 0,
            ),
        )
        assertFalse(
            RegistrationRecordDeduper.shouldSkip(
                "com.example.app",
                nowMs = 1_500L,
                userId = 999,
            ),
        )
        assertTrue(
            RegistrationRecordDeduper.shouldSkip(
                "com.example.app",
                nowMs = 2_000L,
                userId = 0,
            ),
        )
    }

    @Test
    fun `rejects invalid user ids instead of falling back to primary`() {
        assertThrows<IllegalArgumentException> {
            RegistrationRecordDeduper.shouldSkip(
                "com.example.app",
                nowMs = 1_000L,
                userId = -1,
            )
        }
        assertThrows<IllegalArgumentException> {
            RegistrationRecordDeduper.markRecorded(
                "com.example.app",
                nowMs = 1_000L,
                userId = -1,
            )
        }
        assertThrows<IllegalArgumentException> {
            RegistrationRecordDeduper.reset("com.example.app", userId = -1)
        }
    }
}
