package com.xiaomi.xmsf

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import com.magisk317.notification.NotificationManagerEx
import com.magisk317.utils.Hooker
import com.magisk317.utils.PrivilegeElevator
import com.oasisfeng.condom.CondomOptions
import com.oasisfeng.condom.CondomProcess
import com.xiaomi.xmsf.CrashHandler
import com.xiaomi.xmsf.push.control.PushControllerUtils
import com.xiaomi.xmsf.push.control.PushControllerUtils.isAppMainProc
import com.xiaomi.xmsf.push.control.XMOutbound
import com.xiaomi.xmsf.push.notification.NotificationController.CHANNEL_WARN
import com.xiaomi.xmsf.push.service.MiuiPushActivateService
import com.xiaomi.xmsf.utils.LogUtils
import top.trumeet.common.Constants
import top.trumeet.common.Constants.TAG_CONDOM
import top.trumeet.common.push.PushServiceAccessibility
import top.trumeet.common.utils.Utils
import top.trumeet.mipush.provider.DatabaseUtils
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.magisk317.data.DataStoreManager

class MiPushFrameworkApp : Application() {
    private lateinit var logger: Logger

    override fun onCreate() {
        applicationScope = MainScope()
        super.onCreate()
        DatabaseUtils.init(this)
        PrivilegeElevator.tryToElevate()
        Utils.setApplicationContext(this)
        initBasicLogger()
        CrashHandler.installCrashLogger()

        Hooker.setLogger(PushControllerUtils.wrapContext(this))
        Hooker.hook(this)
        NotificationManagerEx.init(applicationContext)
        installCondom()
        PushControllerUtils.setAllEnable(true, this)
        awakePushActivateServiceOnMainProc(PushControllerUtils.wrapContext(this))
        requestDozeWhiteList()
    }

    private fun requestDozeWhiteList() {
        try {
            if (!PushServiceAccessibility.isInDozeWhiteList(this)) {
                notifyDozeWhiteListRequest(NotificationManagerCompat.from(this))
            }
        } catch (e: RuntimeException) {
            logger.e(e.message, e)
        }
    }

    private fun awakePushActivateServiceOnMainProc(context: Context) {
        if (isAppMainProc(this)) {
            val currentTimeMillis = System.currentTimeMillis()
            val elapsedMs = currentTimeMillis - getLastStartupTime()
            val fiveMinutesMs = 300_000
            if (elapsedMs > fiveMinutesMs || elapsedMs < 0) {
                setStartupTime(currentTimeMillis)
                MiuiPushActivateService.awakePushActivateService(context, "com.xiaomi.xmsf.push.SCAN")
            }
        }
    }

    private fun installCondom() {
        val options: CondomOptions = XMOutbound.create(this, "${TAG_CONDOM}_PROCESS", false)
        CondomProcess.installExceptDefaultProcess(this, options)
    }

    private fun initBasicLogger() {
        LogUtils.init(this)
        logger = XLog.tag(MiPushFrameworkApp::class.java.simpleName).build()
        logger.i("App starts: ${BuildConfig.VERSION_NAME}")
    }

    private fun notifyDozeWhiteListRequest(manager: NotificationManagerCompat) {
        createWarnChannel(manager)
        val removeDozeActivityIntent = Intent().setComponent(
            ComponentName(Constants.SERVICE_APP_NAME, Constants.REMOVE_DOZE_COMPONENT_NAME)
        )
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            removeDozeActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_WARN)
            .setContentInfo(getString(R.string.wizard_title_doze_whitelist))
            .setContentTitle(getString(R.string.wizard_title_doze_whitelist))
            .setContentText(getString(R.string.wizard_descr_doze_whitelist))
            .setTicker(getString(R.string.wizard_descr_doze_whitelist))
            .setSmallIcon(R.drawable.ic_notifications_black_24dp)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pendingIntent)
            .setShowWhen(true)
            .setAutoCancel(true)
            .build()
        manager.notify(javaClass.simpleName, 100, notification)
    }

    private fun createWarnChannel(manager: NotificationManagerCompat) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannelCompat.Builder(CHANNEL_WARN, NotificationManager.IMPORTANCE_HIGH)
                .setName(getString(R.string.wizard_title_doze_whitelist))
            val notificationChannelGroup = NotificationChannelGroupCompat.Builder(CHANNEL_WARN)
                .setName(CHANNEL_WARN)
                .build()
            manager.createNotificationChannelGroup(notificationChannelGroup)
            channel.setGroup(notificationChannelGroup.id)
            manager.createNotificationChannel(channel.build())
        }
    }

    private fun getLastStartupTime(): Long = runBlocking { DataStoreManager.lastStartupTime.first() }

    private fun setStartupTime(value: Long) {
        applicationScope.launch {
            DataStoreManager.setLastStartupTime(value)
        }
    }

    companion object {
        private const val MIPUSH_EXTRA = "mipush_extra"
        lateinit var applicationScope: CoroutineScope
            private set
    }
}
