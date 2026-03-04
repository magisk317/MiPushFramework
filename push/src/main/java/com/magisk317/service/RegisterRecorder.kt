package com.magisk317.service

import android.content.Context
import android.content.Intent
import android.widget.Toast
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import com.magisk317.Global
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmsf.R
import top.trumeet.common.Constants
import top.trumeet.common.utils.Utils
import top.trumeet.mipush.provider.db.EventDb
import top.trumeet.mipush.provider.db.RegisteredApplicationDb
import top.trumeet.mipush.provider.entities.Event
import top.trumeet.mipush.provider.entities.RegisteredApplication
import top.trumeet.mipush.provider.event.type.RegistrationType
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
        val appName = Global.ApplicationNameCache().getAppName(context, application.packageName)
        val usedString = context.getString(R.string.notification_registerAllowed, appName)
        Utils.makeText(context, usedString, Toast.LENGTH_SHORT)
    }

    fun canShowRegisterNotification(application: RegisteredApplication): Boolean {
        var notificationOnRegister = runBlocking { Global.ConfigCenter().isNotificationOnRegisterAsync() }
        notificationOnRegister = notificationOnRegister && application.notificationOnRegister
        return notificationOnRegister
    }

    companion object {
        private val TAG = RegisterRecorder::class.java.simpleName
    }
}
