package io.github.magisk317.mipush.utils

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import io.github.aakira.napier.Napier
import com.xiaomi.mipush.sdk.DecryptException
import com.xiaomi.xmpush.thrift.XmPushActionContainer

class ConfigValueConverter {
    fun <T> convert(root: Any?, path: Array<String>, value: T): Any? {
        if (path.size == 1 && "pushAction" == path[0]) {
            return try {
                val container = root as XmPushActionContainer
                ConvertUtils.getResponseMessageBodyFromContainer(container, RegSecUtils.getRegSec(container))
            } catch (e: DecryptException) {
                logW("parse pushAction skipped: ${e.message ?: "decrypt_failed"}")
                null
            } catch (e: Throwable) {
                logE("parse pushAction failed", e)
                null
            }
        }
        return value
    }

    companion object {
        private val TAG = ConfigValueConverter::class.java.simpleName
    }
}
