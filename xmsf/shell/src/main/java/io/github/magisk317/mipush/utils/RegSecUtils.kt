package io.github.magisk317.mipush.utils

import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.magisk317.mipush.compat.RegistrationStateCompat
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeEventRow
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.platform.support.XMPushUtils

object RegSecUtils {
    const val RegSecField = "__reg_sec__"

    @JvmStatic
    fun getRegSec(container: XmPushActionContainer?, userId: Int = Utils.myUserId()): String? {
        return getCandidateRegSecs(container, userId = userId).firstOrNull()
    }

    @JvmStatic
    fun getCandidateRegSecs(
        container: XmPushActionContainer?,
        preferredRegSec: String? = null,
        userId: Int = Utils.myUserId(),
    ): List<String> {
        val candidates = directCandidates(container, preferredRegSec, userId)
        if (candidates.isNotEmpty()) return candidates
        if (container?.isEncryptAction != true) return emptyList()

        val packageName = container.packageName?.takeIf { it.isNotBlank() } ?: return emptyList()
        if (RegistrationStateCompat.recoverLocalRegSec(packageName, userId) == null) return emptyList()
        return directCandidates(container, preferredRegSec, userId)
    }

    @JvmStatic
    fun getContainerWithRegSec(event: RuntimeEventRow?): XmPushActionContainer? {
        return getContainerWithRegSec(event?.payload, event?.regSec)
    }

    @JvmStatic
    fun getContainerWithRegSec(payload: ByteArray?, regSec: String?): XmPushActionContainer? {
        val container = payload?.let(XMPushUtils::packToContainer) ?: return null
        val extra = container.metaInfo?.extra ?: return container
        if (!regSec.isNullOrEmpty()) {
            extra[RegSecField] = regSec
        }
        return container
    }

    private fun directCandidates(
        container: XmPushActionContainer?,
        preferredRegSec: String?,
        userId: Int,
    ): List<String> {
        if (container == null) return emptyList()
        val candidates = linkedSetOf<String>()
        if (!preferredRegSec.isNullOrEmpty()) candidates += preferredRegSec
        container.metaInfo?.extra?.get(RegSecField)
            ?.takeIf { it.isNotEmpty() }
            ?.let(candidates::add)
        container.packageName
            ?.takeIf { it.isNotEmpty() }
            ?.let { candidates += Utils.getRegSecs(it, userId) }
        return candidates.toList()
    }
}
