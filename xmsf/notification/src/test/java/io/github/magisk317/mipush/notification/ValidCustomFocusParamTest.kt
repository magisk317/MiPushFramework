package io.github.magisk317.mipush.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ValidCustomFocusParamTest {
    @Test
    fun `accepts bounded json objects verbatim`() {
        val raw = "{\"business\":\"tsmclient\",\"param_island\":{}}"
        assertEquals(raw, NotificationFocusPayloadSupport.validCustomFocusParam(raw))
    }

    @Test
    fun `rejects scalars arrays and malformed input`() {
        assertNull(NotificationFocusPayloadSupport.validCustomFocusParam("\"not-an-object\""))
        assertNull(NotificationFocusPayloadSupport.validCustomFocusParam("[]"))
        assertNull(NotificationFocusPayloadSupport.validCustomFocusParam("{broken"))
        assertNull(NotificationFocusPayloadSupport.validCustomFocusParam("42"))
    }

    @Test
    fun `rejects blank and missing values`() {
        assertNull(NotificationFocusPayloadSupport.validCustomFocusParam(null))
        assertNull(NotificationFocusPayloadSupport.validCustomFocusParam(""))
        assertNull(NotificationFocusPayloadSupport.validCustomFocusParam("   "))
    }
}
