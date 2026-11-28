package io.github.magisk317.mipush.hook.system

import android.app.Notification
import android.content.Context
import android.os.Build
import android.os.Bundle
import io.github.magisk317.mipush.notification.FocusSemanticCore
import io.github.magisk317.xposed.logging.XLog
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NativeFocusNotificationRewriterTest {
    @AfterEach
    fun resetLogState() {
        XLog.resetForTest()
    }

    @Test
    fun `converted marker skips rebuild and promotion`() {
        val extras = mockk<Bundle>()
        every {
            extras.getBoolean(NativeFocusNotificationRewriter.EXTRA_CONVERTED, false)
        } returns true
        // Notification.extras is a public field on the API surface (no getter), so the stub is
        // placed by field assignment on the mock instance instead of call recording.
        val notification = mockk<Notification>()
        notification.extras = extras

        val result = NativeFocusNotificationRewriter.rewrite(
            NativeFocusNotificationRewriter.RewriteInput(
                packageName = "com.example.target",
                opPackageName = "com.example.target",
                userId = 0,
                callingUid = 10_000,
                notification = notification,
                enabled = true,
                isMiui = false,
                supportsLiveUpdates = true,
                context = mockk<Context>(),
            ),
        )

        assertNull(result.notification)
        assertEquals("already_converted", result.reason)
        assertFalse(result.promotionTokenIssued)
    }

    @Test
    fun `miui detection failure logs a diagnostic and stays fail safe`() {
        // android.os.SystemProperties is hidden from the unit-test classpath, so the reflection
        // lookup below always takes the failure path in this environment.
        val messages = mutableListOf<String>()
        XLog.setTestSink { _, message -> messages.add(message) }

        val isMiui = NativeFocusNotificationRewriter.isMiuiBuild()

        assertTrue(isMiui)
        assertTrue(
            messages.any { it.contains("miui build detection failed") },
            "expected a miui detection diagnostic, got $messages",
        )
    }

    @Test
    fun `live update support follows the android 16 qpr1 boundary`() {
        assertTrue(
            NativeFocusNotificationRewriter.supportsLiveUpdatesAt(
                sdkInt = Build.VERSION_CODES.BAKLAVA,
                sdkIntFull = Build.VERSION_CODES_FULL.BAKLAVA_1,
            ),
        )
        assertFalse(
            NativeFocusNotificationRewriter.supportsLiveUpdatesAt(
                sdkInt = Build.VERSION_CODES.BAKLAVA,
                sdkIntFull = Build.VERSION_CODES_FULL.BAKLAVA_1 - 1,
            ),
        )
        assertFalse(
            NativeFocusNotificationRewriter.supportsLiveUpdatesAt(
                sdkInt = Build.VERSION_CODES.BAKLAVA - 1,
                sdkIntFull = Build.VERSION_CODES_FULL.BAKLAVA_1,
            ),
        )
        assertTrue(
            NativeFocusNotificationRewriter.supportsLiveUpdatesAt(
                sdkInt = Build.VERSION_CODES.BAKLAVA + 1,
                sdkIntFull = Build.VERSION_CODES_FULL.BAKLAVA_1 + 1,
            ),
        )
    }

    @Test
    fun `unsupported platform degrades progress plan to big text without promotion`() {
        val execution = NativeFocusNotificationRewriter.resolveExecutionPlan(
            progressPlan(),
            supportsLiveUpdates = false,
        )

        assertEquals(FocusSemanticCore.NativeStyle.BIG_TEXT, execution.plan.style)
        assertFalse(execution.promote)
        assertTrue(execution.plan.forceOngoing)
        assertEquals(50, execution.plan.progressPercent)
    }

    @Test
    fun `unsupported platform keeps big text plan without promotion`() {
        val execution = NativeFocusNotificationRewriter.resolveExecutionPlan(
            bigTextPlan(),
            supportsLiveUpdates = false,
        )

        assertEquals(FocusSemanticCore.NativeStyle.BIG_TEXT, execution.plan.style)
        assertFalse(execution.promote)
    }

    @Test
    fun `supported platform keeps progress plan with promotion`() {
        val execution = NativeFocusNotificationRewriter.resolveExecutionPlan(
            progressPlan(),
            supportsLiveUpdates = true,
        )

        assertEquals(FocusSemanticCore.NativeStyle.PROGRESS, execution.plan.style)
        assertTrue(execution.promote)
    }

    @Test
    fun `supported platform keeps big text plan with promotion`() {
        val execution = NativeFocusNotificationRewriter.resolveExecutionPlan(
            bigTextPlan(),
            supportsLiveUpdates = true,
        )

        assertEquals(FocusSemanticCore.NativeStyle.BIG_TEXT, execution.plan.style)
        assertTrue(execution.promote)
    }

    private fun progressPlan() = FocusSemanticCore.NativeLiveUpdatePlan(
        shouldRewrite = true,
        style = FocusSemanticCore.NativeStyle.PROGRESS,
        title = "title",
        text = "text",
        shortText = null,
        progressPercent = 50,
        forceOngoing = true,
        reason = "progress_payload",
    )

    private fun bigTextPlan() = FocusSemanticCore.NativeLiveUpdatePlan(
        shouldRewrite = true,
        style = FocusSemanticCore.NativeStyle.BIG_TEXT,
        title = "title",
        text = "text",
        shortText = null,
        progressPercent = null,
        forceOngoing = true,
        reason = "focus_content",
    )
}
