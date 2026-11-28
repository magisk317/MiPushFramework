package io.github.magisk317.mipush.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NativeFocusLiveUpdatePlanTest {
    @Test
    fun `progress focus maps to progress style with payload percentage`() {
        val plan = FocusSemanticCore.planNativeLiveUpdate(
            focusParam = """{"param_v2":{"businessName":"Delivery","progressBar":{"progress":42},"ticker":"Delivery 42%"}}""",
            notificationTitle = "Order",
            notificationText = "Courier on the way",
        )

        assertTrue(plan.shouldRewrite)
        assertEquals(FocusSemanticCore.NativeStyle.PROGRESS, plan.style)
        assertEquals(42, plan.progressPercent)
        assertEquals("Order", plan.title)
        assertEquals("Courier on the way", plan.text)
        assertTrue(plan.forceOngoing)
    }

    @Test
    fun `message focus maps to big text without invented progress`() {
        val plan = FocusSemanticCore.planNativeLiveUpdate(
            focusParam = """{"param_v2":{"chatInfo":{"title":"Alice","content":"Hello"}}}""",
            notificationTitle = "New message",
            notificationText = "Hello",
        )

        assertTrue(plan.shouldRewrite)
        assertEquals(FocusSemanticCore.NativeStyle.BIG_TEXT, plan.style)
        assertEquals(null, plan.progressPercent)
    }

    @Test
    fun `malformed focus payload is left unchanged`() {
        val plan = FocusSemanticCore.planNativeLiveUpdate(
            focusParam = "not-json",
            notificationTitle = "Title",
            notificationText = "Text",
        )

        assertFalse(plan.shouldRewrite)
        assertEquals("invalid_focus_payload", plan.reason)
    }

    @Test
    fun `missing title leaves notification unchanged`() {
        val plan = FocusSemanticCore.planNativeLiveUpdate(
            focusParam = """{"param_v2":{"ticker":"Download in progress"}}""",
            notificationTitle = null,
            notificationText = "Downloading",
        )

        assertFalse(plan.shouldRewrite)
        assertEquals("missing_title", plan.reason)
    }

    @Test
    fun `payload labels can supply title and text when platform extras are empty`() {
        val plan = FocusSemanticCore.planNativeLiveUpdate(
            focusParam = """{"param_v2":{"businessName":"Delivery","hintInfo":{"title":"Arriving"},"progressBar":{"progress":75}}}""",
            notificationTitle = null,
            notificationText = null,
        )

        assertTrue(plan.shouldRewrite)
        assertEquals("Arriving", plan.title)
        assertEquals("Delivery Arriving", plan.text)
    }

    @Test
    fun `multi progress payload maps to progress style with payload percentage`() {
        val plan = FocusSemanticCore.planNativeLiveUpdate(
            focusParam = """{"param_v2":{"multiProgressInfo":{"progress":30,"points":3,"color":"#FF0000"},"ticker":"Installing 30%"}}""",
            notificationTitle = "Update",
            notificationText = "Installing package",
        )

        assertTrue(plan.shouldRewrite)
        assertEquals(FocusSemanticCore.NativeStyle.PROGRESS, plan.style)
        assertEquals(30, plan.progressPercent)
        assertTrue(plan.forceOngoing)
    }

    @Test
    fun `multi progress zero percent with zero points stays progress style`() {
        val plan = FocusSemanticCore.planNativeLiveUpdate(
            focusParam = """{"param_v2":{"multiProgressInfo":{"progress":0,"points":0}}}""",
            notificationTitle = "Update",
            notificationText = "Queued",
        )

        assertEquals(FocusSemanticCore.NativeStyle.PROGRESS, plan.style)
        assertEquals(0, plan.progressPercent)
    }

    @Test
    fun `multi progress full percent stays progress style`() {
        val plan = FocusSemanticCore.planNativeLiveUpdate(
            focusParam = """{"param_v2":{"multiProgressInfo":{"progress":100,"points":4}}}""",
            notificationTitle = "Update",
            notificationText = "Finishing",
        )

        assertEquals(FocusSemanticCore.NativeStyle.PROGRESS, plan.style)
        assertEquals(100, plan.progressPercent)
    }

    @Test
    fun `multi progress over 100 clamps to 100`() {
        val plan = FocusSemanticCore.planNativeLiveUpdate(
            focusParam = """{"param_v2":{"multiProgressInfo":{"progress":150}}}""",
            notificationTitle = "Update",
            notificationText = "Installing",
        )

        assertEquals(FocusSemanticCore.NativeStyle.PROGRESS, plan.style)
        assertEquals(100, plan.progressPercent)
    }

    @Test
    fun `multi progress negative sentinel falls back to big text`() {
        val plan = FocusSemanticCore.planNativeLiveUpdate(
            focusParam = """{"param_v2":{"multiProgressInfo":{"progress":-1,"points":3}}}""",
            notificationTitle = "Update",
            notificationText = "Installing",
        )

        assertTrue(plan.shouldRewrite)
        assertEquals(FocusSemanticCore.NativeStyle.BIG_TEXT, plan.style)
        assertEquals(null, plan.progressPercent)
        assertEquals("indeterminate_progress", plan.reason)
    }

    @Test
    fun `multi progress missing progress field falls back to big text`() {
        val plan = FocusSemanticCore.planNativeLiveUpdate(
            focusParam = """{"param_v2":{"multiProgressInfo":{"points":3,"color":"#FF0000"}}}""",
            notificationTitle = "Update",
            notificationText = "Installing",
        )

        assertTrue(plan.shouldRewrite)
        assertEquals(FocusSemanticCore.NativeStyle.BIG_TEXT, plan.style)
        assertEquals(null, plan.progressPercent)
        assertEquals("indeterminate_progress", plan.reason)
    }

    @Test
    fun `multi progress non numeric progress falls back to big text`() {
        val plan = FocusSemanticCore.planNativeLiveUpdate(
            focusParam = """{"param_v2":{"multiProgressInfo":{"progress":"abc"}}}""",
            notificationTitle = "Update",
            notificationText = "Installing",
        )

        assertEquals(FocusSemanticCore.NativeStyle.BIG_TEXT, plan.style)
        assertEquals(null, plan.progressPercent)
        assertEquals("indeterminate_progress", plan.reason)
    }

    @Test
    fun `multi progress negative points keeps deterministic percent`() {
        val plan = FocusSemanticCore.planNativeLiveUpdate(
            focusParam = """{"param_v2":{"multiProgressInfo":{"progress":40,"points":-1}}}""",
            notificationTitle = "Update",
            notificationText = "Installing",
        )

        assertEquals(FocusSemanticCore.NativeStyle.PROGRESS, plan.style)
        assertEquals(40, plan.progressPercent)
    }

    @Test
    fun `progress bar negative sentinel is not coerced to zero`() {
        val plan = FocusSemanticCore.planNativeLiveUpdate(
            focusParam = """{"param_v2":{"progressBar":{"progress":-1}}}""",
            notificationTitle = "Update",
            notificationText = "Installing",
        )

        assertEquals(FocusSemanticCore.NativeStyle.BIG_TEXT, plan.style)
        assertEquals(null, plan.progressPercent)
        assertEquals("indeterminate_progress", plan.reason)
    }

    @Test
    fun `multi progress supplies percent when progress bar lacks progress`() {
        val plan = FocusSemanticCore.planNativeLiveUpdate(
            focusParam = """{"param_v2":{"progressBar":{"colorProgress":"#FF0000"},"multiProgressInfo":{"progress":66}}}""",
            notificationTitle = "Update",
            notificationText = "Installing",
        )

        assertEquals(FocusSemanticCore.NativeStyle.PROGRESS, plan.style)
        assertEquals(66, plan.progressPercent)
    }
}
