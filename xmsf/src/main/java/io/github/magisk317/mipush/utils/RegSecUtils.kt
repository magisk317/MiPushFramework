package io.github.magisk317.mipush.utils

import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.magisk317.mipush.runtime.store.entities.Event
import io.github.magisk317.mipush.common.configurations.RegSecUtils as CoreRegSecUtils

object RegSecUtils {
    const val RegSecField = CoreRegSecUtils.RegSecField

    @JvmStatic
    fun getRegSec(container: XmPushActionContainer?): String? {
        return CoreRegSecUtils.getRegSec(container)
    }

    @JvmStatic
    fun getCandidateRegSecs(container: XmPushActionContainer?, preferredRegSec: String? = null): List<String> {
        return CoreRegSecUtils.getCandidateRegSecs(container, preferredRegSec)
    }

    @JvmStatic
    fun getContainerWithRegSec(event: Event?): XmPushActionContainer? {
        return CoreRegSecUtils.getContainerWithRegSec(event?.payload, event?.regSec)
    }
}
