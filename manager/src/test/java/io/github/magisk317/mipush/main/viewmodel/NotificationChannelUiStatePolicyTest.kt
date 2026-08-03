package io.github.magisk317.mipush.main.viewmodel

import io.github.magisk317.mipush.manager.notification.NotificationChannelReadResult
import io.github.magisk317.mipush.manager.notification.NotificationChannelReadStatus
import io.github.magisk317.mipush.manager.notification.NotificationChannelSnapshot
import io.github.magisk317.mipush.manager.notification.NotificationChannelSummary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationChannelUiStatePolicyTest {
    @Test
    fun `first load distinguishes successful empty and unavailable states`() {
        val loading = NotificationChannelUiStatePolicy.start(NotificationChannelUiState(), PACKAGE_A)
        assertTrue(loading.isLoading)
        assertNull(loading.snapshot)

        val empty = NotificationChannelUiStatePolicy.complete(
            loading,
            PACKAGE_A,
            NotificationChannelReadResult.Available(snapshot(PACKAGE_A)),
        )
        assertFalse(empty.isLoading)
        assertEquals(emptyList<NotificationChannelSummary>(), empty.snapshot?.channels)
        assertNull(empty.unavailableStatus)

        val unavailable = NotificationChannelUiStatePolicy.complete(
            loading,
            PACKAGE_A,
            NotificationChannelReadResult.Unavailable(NotificationChannelReadStatus.TIMED_OUT),
        )
        assertFalse(unavailable.isLoading)
        assertNull(unavailable.snapshot)
        assertEquals(NotificationChannelReadStatus.TIMED_OUT, unavailable.unavailableStatus)
    }

    @Test
    fun `same package refresh keeps the last snapshot through loading and failure`() {
        val available = NotificationChannelUiStatePolicy.complete(
            NotificationChannelUiStatePolicy.start(NotificationChannelUiState(), PACKAGE_A),
            PACKAGE_A,
            NotificationChannelReadResult.Available(snapshot(PACKAGE_A, withChannel = true)),
        )

        val refreshing = NotificationChannelUiStatePolicy.start(available, PACKAGE_A)
        assertTrue(refreshing.isLoading)
        assertEquals(listOf("channel"), refreshing.snapshot?.channels?.map { it.id })

        val failed = NotificationChannelUiStatePolicy.complete(
            refreshing,
            PACKAGE_A,
            NotificationChannelReadResult.Unavailable(NotificationChannelReadStatus.FAILED),
        )
        assertFalse(failed.isLoading)
        assertEquals(listOf("channel"), failed.snapshot?.channels?.map { it.id })
        assertEquals(NotificationChannelReadStatus.FAILED, failed.unavailableStatus)
    }

    @Test
    fun `successful retry replaces the snapshot and clears the error`() {
        val failed = NotificationChannelUiState(
            packageName = PACKAGE_A,
            snapshot = snapshot(PACKAGE_A),
            unavailableStatus = NotificationChannelReadStatus.DISCONNECTED,
        )
        val refreshing = NotificationChannelUiStatePolicy.start(failed, PACKAGE_A)
        assertNull(refreshing.unavailableStatus)
        val recovered = NotificationChannelUiStatePolicy.complete(
            refreshing,
            PACKAGE_A,
            NotificationChannelReadResult.Available(snapshot(PACKAGE_A, withChannel = true)),
        )

        assertFalse(recovered.isLoading)
        assertNull(recovered.unavailableStatus)
        assertEquals(listOf("channel"), recovered.snapshot?.channels?.map { it.id })
    }

    @Test
    fun `package switch clears stale content and ignores the previous response`() {
        val availableA = NotificationChannelUiState(
            packageName = PACKAGE_A,
            snapshot = snapshot(PACKAGE_A, withChannel = true),
        )
        val loadingB = NotificationChannelUiStatePolicy.start(availableA, PACKAGE_B)

        assertEquals(PACKAGE_B, loadingB.packageName)
        assertTrue(loadingB.isLoading)
        assertNull(loadingB.snapshot)

        val afterStaleResponse = NotificationChannelUiStatePolicy.complete(
            loadingB,
            PACKAGE_A,
            NotificationChannelReadResult.Available(snapshot(PACKAGE_A)),
        )
        assertEquals(loadingB, afterStaleResponse)
    }

    @Test
    fun `response package mismatch keeps prior data and becomes failed`() {
        val availableA = NotificationChannelUiState(
            packageName = PACKAGE_A,
            snapshot = snapshot(PACKAGE_A, withChannel = true),
        )
        val loadingA = NotificationChannelUiStatePolicy.start(availableA, PACKAGE_A)

        val mismatched = NotificationChannelUiStatePolicy.complete(
            loadingA,
            PACKAGE_A,
            NotificationChannelReadResult.Available(snapshot(PACKAGE_B)),
        )

        assertFalse(mismatched.isLoading)
        assertEquals(PACKAGE_A, mismatched.snapshot?.packageName)
        assertEquals(listOf("channel"), mismatched.snapshot?.channels?.map { it.id })
        assertEquals(NotificationChannelReadStatus.FAILED, mismatched.unavailableStatus)
    }

    private fun snapshot(
        packageName: String,
        withChannel: Boolean = false,
    ) = NotificationChannelSnapshot(
        packageName = packageName,
        isHooked = true,
        channels = if (withChannel) {
            listOf(
                NotificationChannelSummary(
                    id = "channel",
                    name = "Channel",
                    importance = 3,
                    groupId = null,
                    description = null,
                    enabled = true,
                    managedByMiPush = false,
                ),
            )
        } else {
            emptyList()
        },
        groups = emptyList(),
    )

    private companion object {
        const val PACKAGE_A = "com.example.a"
        const val PACKAGE_B = "com.example.b"
    }
}
