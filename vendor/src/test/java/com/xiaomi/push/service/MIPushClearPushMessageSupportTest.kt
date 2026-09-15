package com.xiaomi.push.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MIPushClearPushMessageSupportTest {
    @Test
    fun `parseNotifyId mirrors the stock s d minus-two default`() {
        assertEquals(-2, MIPushClearPushMessageSupport.parseNotifyId(null))
        assertEquals(-2, MIPushClearPushMessageSupport.parseNotifyId(""))
        assertEquals(-2, MIPushClearPushMessageSupport.parseNotifyId("not-a-number"))
        assertEquals(7, MIPushClearPushMessageSupport.parseNotifyId("7"))
        assertEquals(-1, MIPushClearPushMessageSupport.parseNotifyId("-1"))
    }

    @Test
    fun `resolveMatcher mirrors the stock wc d b c e selector order`() {
        val notifyAndMsg = MIPushClearPushMessageSupport.resolveMatcher(
            mapOf(PushConstants.PUSH_NOTIFY_ID to "5", MIPushClearPushMessageSupport.EXTRA_MSG_ID to "m1"),
        )
        assertEquals(MIPushClearPushMessageSupport.MatcherKind.NOTIFY_ID_AND_MSG_ID, notifyAndMsg?.kind)
        assertEquals(4, notifyAndMsg?.kind?.cancelType)
        assertEquals(5, notifyAndMsg?.notifyId)
        assertEquals("m1", notifyAndMsg?.msgId)

        val msgOnly = MIPushClearPushMessageSupport.resolveMatcher(
            mapOf(MIPushClearPushMessageSupport.EXTRA_MSG_ID to "m1"),
        )
        assertEquals(MIPushClearPushMessageSupport.MatcherKind.MSG_ID, msgOnly?.kind)
        assertEquals(2, msgOnly?.kind?.cancelType)

        // An empty msg_id value does not select wc.b; notifyId >= 0 falls to wc.c.
        val notifyOnly = MIPushClearPushMessageSupport.resolveMatcher(
            mapOf(PushConstants.PUSH_NOTIFY_ID to "9", MIPushClearPushMessageSupport.EXTRA_MSG_ID to ""),
        )
        assertEquals(MIPushClearPushMessageSupport.MatcherKind.NOTIFY_ID, notifyOnly?.kind)
        assertEquals(1, notifyOnly?.kind?.cancelType)

        val titleDesc = MIPushClearPushMessageSupport.resolveMatcher(
            mapOf(PushConstants.PUSH_TITLE to "t", PushConstants.PUSH_DESCRIPTION to "d"),
        )
        assertEquals(MIPushClearPushMessageSupport.MatcherKind.TITLE_DESCRIPTION, titleDesc?.kind)
        assertEquals(3, titleDesc?.kind?.cancelType)
    }

    @Test
    fun `resolveMatcher returns null when no stock matcher can be built`() {
        assertNull(MIPushClearPushMessageSupport.resolveMatcher(null))
        assertNull(MIPushClearPushMessageSupport.resolveMatcher(emptyMap()))
        // Stock gates wc.c/wc.d on notifyId >= 0: NOTIFY_ALL (-1) alone falls through
        // to the title+description check and ends in the no-matcher ack path.
        assertNull(
            MIPushClearPushMessageSupport.resolveMatcher(
                mapOf(
                    PushConstants.PUSH_NOTIFY_ID to "-1",
                    PushConstants.PUSH_TITLE to "only-title",
                ),
            ),
        )
        // Stock wc.e requires both texts.
        assertNull(
            MIPushClearPushMessageSupport.resolveMatcher(
                mapOf(PushConstants.PUSH_DESCRIPTION to "only-description"),
            ),
        )
    }
}
