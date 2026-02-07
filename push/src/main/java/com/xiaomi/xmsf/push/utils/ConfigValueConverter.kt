package com.xiaomi.xmsf.push.utils

import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmsf.utils.ConvertUtils

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
        private val logger: Logger = XLog.tag(ConfigValueConverter::class.java.simpleName).build()
    }
}
