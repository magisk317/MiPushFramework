package io.github.magisk317.mipush.service.runtime

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RegistrationRecordDeduperTest {
    @AfterEach
    fun tearDown() {
        RegistrationRecordDeduper.reset()
    }

    @Test
    fun `skips only repeated local records inside the window`() {
        assertFalse(RegistrationRecordDeduper.shouldSkip("com.example.app", nowMs = 1_000L))
        assertTrue(RegistrationRecordDeduper.shouldSkip("com.example.app", nowMs = 1_500L))
        assertFalse(RegistrationRecordDeduper.shouldSkip("com.example.other", nowMs = 1_500L))
    }

    @Test
    fun `allows a new record at the window boundary`() {
        assertFalse(RegistrationRecordDeduper.shouldSkip("com.example.app", nowMs = 1_000L))
        assertFalse(
            RegistrationRecordDeduper.shouldSkip(
                "com.example.app",
                nowMs = 1_000L + RegistrationRecordDeduper.DEDUP_WINDOW_MS,
            ),
        )
    }

    @Test
    fun `force register marker coalesces only the matching package record`() {
        RegistrationRecordDeduper.markRecorded("com.example.app", nowMs = 1_000L)

        assertTrue(RegistrationRecordDeduper.shouldSkip("com.example.app", nowMs = 1_500L))
        assertFalse(RegistrationRecordDeduper.shouldSkip("com.example.other", nowMs = 1_500L))
    }

    @Test
    fun `package reset removes only that record marker`() {
        RegistrationRecordDeduper.markRecorded("com.example.app", nowMs = 1_000L)
        RegistrationRecordDeduper.markRecorded("com.example.other", nowMs = 1_000L)

        RegistrationRecordDeduper.reset("com.example.app")

        assertFalse(RegistrationRecordDeduper.shouldSkip("com.example.app", nowMs = 1_500L))
        assertTrue(RegistrationRecordDeduper.shouldSkip("com.example.other", nowMs = 1_500L))
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
}
