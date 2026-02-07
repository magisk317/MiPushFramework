package com.nihility.service

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import com.nihility.Global
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmsf.R
import top.trumeet.common.Constants
import top.trumeet.common.utils.Utils
import top.trumeet.mipush.provider.db.EventDb
import top.trumeet.mipush.provider.db.RegisteredApplicationDb
import top.trumeet.mipush.provider.entities.Event
import top.trumeet.mipush.provider.entities.RegisteredApplication
import top.trumeet.mipush.provider.event.type.RegistrationType

class RegisterRecorder(private val context: Context) {
    private val logger: Logger = XLog.tag(TAG).build()

    fun recordRegisterRequest(intent: Intent?) {
        try {
            if (!isRegisterAppRequest(intent)) {
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
        EventDb.insertEvent(Event.ResultType.OK, RegistrationType(null, pkg, null))
    }

    fun isRegisterAppRequest(intent: Intent?): Boolean {
        return intent != null && PushConstants.MIPUSH_ACTION_REGISTER_APP == intent.action
    }

    fun showRegisterToastIfUserAllow(application: RegisteredApplication) {
        if (canShowRegisterNotification(application)) {
            showRegisterNotification(application)
        } else {
            Log.e("XMPushService Bridge", "Notification disabled")
        }
    }

    fun showRegisterNotification(application: RegisteredApplication) {
        val appName = Global.ApplicationNameCache().getAppName(context, application.packageName)
        val usedString = context.getString(R.string.notification_registerAllowed, appName)
        Utils.makeText(context, usedString, Toast.LENGTH_SHORT)
    }

    fun canShowRegisterNotification(application: RegisteredApplication): Boolean {
        var notificationOnRegister = Global.ConfigCenter().isNotificationOnRegister(context)
        notificationOnRegister = notificationOnRegister && application.notificationOnRegister
        return notificationOnRegister
    }

    companion object {
        private val TAG = RegisterRecorder::class.java.simpleName
    }
}
