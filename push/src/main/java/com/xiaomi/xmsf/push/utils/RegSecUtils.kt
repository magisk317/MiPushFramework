package com.xiaomi.xmsf.push.utils

import android.text.TextUtils
import com.nihility.XMPushUtils
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import top.trumeet.mipush.provider.entities.Event

object RegSecUtils {
    const val RegSecField = "__reg_sec__"

    @JvmStatic
    fun getRegSec(container: XmPushActionContainer?): String? {
        if (container == null) {
            return null
        }
        val metaInfo = container.metaInfo
        if (metaInfo != null && metaInfo.extra != null) {
            val regSec = metaInfo.extra[RegSecField]
            if (!TextUtils.isEmpty(regSec)) {
                return regSec
            }
        }
        return top.trumeet.common.utils.Utils.getRegSec(container.packageName)
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
