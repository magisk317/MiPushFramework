package io.github.magisk317.mipush.common.configurations

import io.github.aakira.napier.Napier
import com.xiaomi.xmpush.thrift.XmPushActionContainer

class ConfigValueConverter {
    fun <T> convert(root: Any?, path: Array<String>, value: T): Any? {
        if (path.size == 1 && "pushAction" == path[0]) {
            return try {
                val container = root as XmPushActionContainer
                ConvertUtils.getResponseMessageBodyFromContainer(container, RegSecUtils.getRegSec(container))
            } catch (e: Throwable) {
                logger.e("parse pushAction failed", e)
                null
            }
        }
        return value
    }

    companion object {
        private val TAG = ConfigValueConverter::class.java.simpleName
        private val logger = object {
            fun e(msg: String, t: Throwable? = null) = Napier.e(msg, t, tag = TAG)
        }
    }
}
