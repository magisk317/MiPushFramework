package com.xiaomi.xmsf.push.utils

import android.text.TextUtils
import io.github.magisk317.mipush.XMPushUtils
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.magisk317.mipush.runtime.store.entities.Event

object RegSecUtils {
    const val RegSecField = "__reg_sec__"

    @JvmStatic
    fun getRegSec(container: XmPushActionContainer?): String? {
        return getCandidateRegSecs(container).firstOrNull()
    }

    @JvmStatic
    fun getCandidateRegSecs(container: XmPushActionContainer?, preferredRegSec: String? = null): List<String> {
        if (container == null) {
            return emptyList()
        }
        val candidates = linkedSetOf<String>()
        if (!preferredRegSec.isNullOrEmpty()) {
            candidates.add(preferredRegSec)
        }
        val metaInfo = container.metaInfo
        if (metaInfo != null && metaInfo.extra != null) {
            val regSec = metaInfo.extra[RegSecField]
            if (!TextUtils.isEmpty(regSec)) {
                candidates.add(regSec!!)
            }
        }
        val packageName = container.packageName
        if (!packageName.isNullOrEmpty()) {
            candidates += io.github.magisk317.mipush.common.utils.Utils.getRegSecs(packageName)
        }
        return candidates.toList()
    }

    @JvmStatic
    fun getContainerWithRegSec(event: Event?): XmPushActionContainer? {
        if (event?.payload == null) {
            return null
        }
        val container = XMPushUtils.packToContainer(event.payload) ?: return null
        val metaInfo = container.metaInfo
        if (metaInfo == null || metaInfo.extra == null) {
            return container
        }
        val regSec = event.regSec
        if (!TextUtils.isEmpty(regSec)) {
            metaInfo.extra[RegSecField] = regSec
        }
        return container
    }
}
