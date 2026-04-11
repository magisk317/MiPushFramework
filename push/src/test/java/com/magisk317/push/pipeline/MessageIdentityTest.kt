package com.magisk317.push.pipeline

import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MessageIdentityTest {

    @Test
    fun fromContainer_prefersJobKeyOverMetaId() {
        val container = XmPushActionContainer()
        container.metaInfo = PushMetaInfo().apply {
            id = "meta-id"
            extra = hashMapOf(PushConstants.EXTRA_JOB_KEY to "job-id")
        }

        assertEquals("job-id", MessageIdentity.fromContainer(container))
    }

    @Test
    fun fromContainer_fallsBackToMetaId() {
        val container = XmPushActionContainer()
        container.metaInfo = PushMetaInfo().apply {
            id = "meta-id-only"
        }

        assertEquals("meta-id-only", MessageIdentity.fromContainer(container))
    }

    @Test
    fun fromMeta_returnsNullWhenBlank() {
        assertNull(MessageIdentity.fromMeta(""))
        assertNull(MessageIdentity.fromMeta(null))
    }
}
