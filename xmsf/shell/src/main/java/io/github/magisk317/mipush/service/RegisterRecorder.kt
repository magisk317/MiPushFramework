package io.github.magisk317.mipush.service

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmsf.R
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.runtime.store.db.EventDb
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import io.github.magisk317.mipush.runtime.store.kmp.EventRowResultType
import io.github.magisk317.mipush.runtime.store.event.type.RegistrationType
import io.github.magisk317.mipush.service.runtime.RegistrationRecordDeduper
import kotlinx.coroutines.runBlocking
import io.github.magisk317.xposed.logging.MagiskOtel

class RegisterRecorder(private val context: Context) {

    fun recordRegisterRequest(intent: Intent?) {
        try {
            if (!isRegisterAppRequest(intent)) {
                emitRegister(result = "skip", reason = "not_register_request")
                return
            }

            val pkg = intent?.getStringExtra(Constants.EXTRA_MI_PUSH_PACKAGE)
            if (pkg == null) {
                logE("Package name is NULL!")
                emitRegister(result = "error", reason = "missing_package", statusOk = false)
                return
            }

            if (!Utils.isUserApplication(context.applicationContext, pkg)) {
                logD("skip system application registration pkg=$pkg")
                emitRegister(result = "skip", reason = "system_app", packageName = pkg)
                return
            }

            if (RegisteredApplicationDb.isBlocked(pkg)) {
                logD("skip blocked application registration pkg=$pkg")
                emitRegister(result = "skip", reason = "blocked", packageName = pkg)
                return
            }

            RegisteredApplicationDb.registerApplication(pkg)
            if (RegistrationRecordDeduper.shouldSkip(pkg)) {
                logD("skip duplicate register record pkg=$pkg")
                emitRegister(result = "skip", reason = "duplicate", packageName = pkg)
                return
            }

            logD("onHandleIntent -> A application want to register push")
            saveRegisterAppRecord(pkg)
            emitRegister(result = "ok", reason = "recorded", packageName = pkg)
        } catch (e: RuntimeException) {
            logE("XMPushService::onHandleIntent: ", e)
            toastErrorMessage(e)
            emitRegister(
                result = "error",
                reason = "exception",
                statusOk = false,
                errorClass = e.javaClass.simpleName,
            )
        }
    }

    private fun emitRegister(
        result: String,
        reason: String,
        statusOk: Boolean = true,
        packageName: String? = null,
        errorClass: String? = null,
    ) {
        val attrs = mutableMapOf(
            "result" to result,
            "duration_ms" to "0",
            "process" to "main",
            "stage" to "record",
            "reason" to reason,
        )
        if (!packageName.isNullOrBlank()) {
            attrs["target_package"] = packageName
        }
        if (!errorClass.isNullOrBlank()) {
            attrs["error_class"] = errorClass
        }
        MagiskOtel.event(name = "push.register", attributes = attrs, statusOk = statusOk)
    }

    fun toastErrorMessage(e: RuntimeException) {
        Utils.makeText(context, context.getString(R.string.common_err, e.message), Toast.LENGTH_LONG)
    }

    fun saveRegisterAppRecord(pkg: String) {
        runBlocking { EventDb.insertEventAsync(EventRowResultType.OK, RegistrationType(null, pkg, null)) }
    }

    fun isRegisterAppRequest(intent: Intent?): Boolean {
        return intent != null && PushConstants.MIPUSH_ACTION_REGISTER_APP == intent.action
    }

    companion object {
        private val TAG = RegisterRecorder::class.java.simpleName
    }
}
