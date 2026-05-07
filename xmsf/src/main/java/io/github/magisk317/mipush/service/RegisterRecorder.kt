package io.github.magisk317.mipush.service

import android.content.Context
import android.content.Intent
import android.widget.Toast
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import io.github.magisk317.mipush.platform.support.Global
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmsf.R
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.runtime.store.db.EventDb
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import io.github.magisk317.mipush.runtime.store.entities.Event
import io.github.magisk317.mipush.runtime.store.entities.RegisteredApplication
import io.github.magisk317.mipush.runtime.store.event.type.RegistrationType
import kotlinx.coroutines.runBlocking

class RegisterRecorder(private val context: Context) {
    private val logger = object {
        fun d(msg: String) = Napier.d(msg, tag = TAG)
        fun e(msg: String) = Napier.e(msg, tag = TAG)
        fun e(msg: String, t: Throwable) = Napier.e(msg, t, tag = TAG)
    }

    fun recordRegisterRequest(intent: Intent?) {
        logger.d("recordRegisterRequest() called with intent: $intent")
        try {
            if (!isRegisterAppRequest(intent)) {
                logger.d("Not a register app request")
                return
            }

            val pkg = intent?.getStringExtra(Constants.EXTRA_MI_PUSH_PACKAGE)
            if (pkg == null) {
                logger.e("Package name is NULL!")
                return
            }

            if (RegisteredApplicationDb.isBlocked(pkg)) {
                logger.d("skip blocked application registration pkg=$pkg")
                return
            }

            logger.d("onHandleIntent -> A application want to register push")
            showRegisterToastIfUserAllow(RegisteredApplicationDb.registerApplication(pkg))
            saveRegisterAppRecord(pkg)
        } catch (e: RuntimeException) {
            logger.e("XMPushService::onHandleIntent: ", e)
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

    fun showRegisterToastIfUserAllow(application: RegisteredApplication) {
        if (canShowRegisterNotification(application)) {
            showRegisterNotification(application)
        } else {
            logger.e("Notification disabled")
        }
    }

    fun showRegisterNotification(application: RegisteredApplication) {
        val appName = Global.applicationNameCache().getAppName(context, application.packageName)
        val usedString = context.getString(R.string.notification_registerAllowed, appName)
        Utils.makeText(context, usedString, Toast.LENGTH_SHORT)
    }

    fun canShowRegisterNotification(application: RegisteredApplication): Boolean {
        var notificationOnRegister = runBlocking { Global.configCenter().isNotificationOnRegisterAsync() }
        notificationOnRegister = notificationOnRegister && application.notificationOnRegister
        return notificationOnRegister
    }

    companion object {
        private val TAG = RegisterRecorder::class.java.simpleName
    }
}
