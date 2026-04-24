package io.github.magisk317.mipush.common.configurations

import android.text.TextUtils
import com.xiaomi.xmpush.thrift.XmPushActionContainer

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
        if (metaInfo != null && metaInfo.getExtra() != null) {
            val regSec = metaInfo.getExtra()[RegSecField]
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
    fun getContainerWithRegSec(payload: ByteArray?, regSec: String?): XmPushActionContainer? {
        if (payload == null) {
            return null
        }
        val container = XMPushUtils.packToContainer(payload) ?: return null
        val metaInfo = container.metaInfo
        if (metaInfo == null || metaInfo.getExtra() == null) {
            return container
        }
        if (!TextUtils.isEmpty(regSec)) {
            metaInfo.putToExtra(RegSecField, regSec)
        }
        return container
    }
}
