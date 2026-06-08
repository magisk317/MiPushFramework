package io.github.magisk317.mipush.service

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import android.content.Context
import android.content.Intent
import android.widget.Toast
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmsf.R
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.runtime.store.db.EventDb
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import io.github.magisk317.mipush.runtime.store.entities.Event
import io.github.magisk317.mipush.runtime.store.event.type.RegistrationType
import io.github.magisk317.mipush.service.runtime.RegistrationIntentDeduper
import kotlinx.coroutines.runBlocking

class RegisterRecorder(private val context: Context) {

    fun recordRegisterRequest(intent: Intent?) {
        logD("recordRegisterRequest() called with intent: $intent")
        try {
            if (!isRegisterAppRequest(intent)) {
                logD("Not a register app request")
                return
            }

            val pkg = intent?.getStringExtra(Constants.EXTRA_MI_PUSH_PACKAGE)
            if (pkg == null) {
                logE("Package name is NULL!")
                return
            }

            if (!Utils.isUserApplication(context.applicationContext, pkg)) {
                logD("skip system application registration pkg=$pkg")
                return
            }

            if (RegistrationIntentDeduper.shouldDrop("register_recorder", intent)) {
                logD("skip duplicate register record pkg=$pkg")
                return
            }

            if (RegisteredApplicationDb.isBlocked(pkg)) {
                logD("skip blocked application registration pkg=$pkg")
                return
            }

            logD("onHandleIntent -> A application want to register push")
            RegisteredApplicationDb.registerApplication(pkg)
            saveRegisterAppRecord(pkg)
        } catch (e: RuntimeException) {
            logE("XMPushService::onHandleIntent: ", e)
            toastErrorMessage(e)
        }
    }

    fun toastErrorMessage(e: RuntimeException) {
        Utils.makeText(context, context.getString(R.string.common_err, e.message), Toast.LENGTH_LONG)
    }

    fun saveRegisterAppRecord(pkg: String) {
        runBlocking { EventDb.insertEventAsync(Event.ResultType.OK, RegistrationType(null, pkg, null)) }
    }

    fun isRegisterAppRequest(intent: Intent?): Boolean {
        return intent != null && PushConstants.MIPUSH_ACTION_REGISTER_APP == intent.action
    }

    companion object {
        private val TAG = RegisterRecorder::class.java.simpleName
    }
}
