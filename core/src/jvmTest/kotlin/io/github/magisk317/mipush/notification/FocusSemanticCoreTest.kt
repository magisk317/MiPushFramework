package io.github.magisk317.mipush.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FocusSemanticCoreTest {
    @Test fun `configured focus is authoritative`() {
        val plan = FocusSemanticCore.plan(null, "{\"param_v2\":{\"progressBar\":{\"progress\":42},\"ticker\":\"下载中 42%\"}}", null, true, FocusSemanticCore.Capabilities(true, true))
        assertTrue(plan.attachMiuiFocusExtras)
        assertEquals("miui_focus_extras", plan.reason)
        assertEquals(42, plan.semantic?.progressPercent)
    }
    @Test fun `non miui generated progress uses native plan`() {
        val plan = FocusSemanticCore.plan(null, null, "{\"progressBar\":{\"progress\":75},\"ticker\":\"下载中\"}", true, FocusSemanticCore.Capabilities(false, true))
        assertEquals("native_live_update", plan.reason)
        assertEquals(FocusSemanticCore.Style.PROGRESS, plan.semantic?.style)
    }
}
