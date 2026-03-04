package com.magisk317

import android.content.Intent
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmsf.utils.ConvertUtils

class MiPushEventListener {
    fun receiveFromServer(container: XmPushActionContainer) {
        logger.i("From Server     : " + ConvertUtils.toJson(container))
    }

    fun transferToApplication(container: XmPushActionContainer) {
        logger.i("To   Application: " + ConvertUtils.toJson(container))
    }

    fun receiveFromApplication(intent: Intent) {
        logger.i("From Application: " + ConvertUtils.toJson(intent))
    }

    fun transferToServer(intent: Intent) {
        logger.i("To   Server     : " + ConvertUtils.toJson(intent))
    }

    companion object {
        private val TAG: String = MiPushEventListener::class.java.simpleName
        private val logger = object {
            fun i(msg: String) = Napier.i(msg, tag = TAG)
        }
    }
}
