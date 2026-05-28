package io.github.magisk317.mipush

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import android.content.Intent
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.magisk317.mipush.utils.ConvertUtils

class MiPushEventListener {
    fun receiveFromServer(container: XmPushActionContainer) {
        logD("From Server     : " + ConvertUtils.toJson(container))
    }

    fun transferToApplication(container: XmPushActionContainer) {
        logD("To   Application: " + ConvertUtils.toJson(container))
    }

    fun receiveFromApplication(intent: Intent) {
        logD("From Application: " + ConvertUtils.toJson(intent))
    }

    fun transferToServer(intent: Intent) {
        logD("To   Server     : " + ConvertUtils.toJson(intent))
    }

    companion object {
        private val TAG: String = MiPushEventListener::class.java.simpleName
    }
}
