package io.github.magisk317.mipush.app

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.magisk317.mipush.diagnostics.PushHealthSnapshotLogger
import io.github.magisk317.mipush.telemetry.TelemetryDisabler
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.LogLevel
import io.github.magisk317.mipush.utils.LogUtils
import io.github.magisk317.mipush.bridge.LegacyLoggerBridge
import io.github.magisk317.mipush.notification.NotificationManagerEx
import io.github.magisk317.mipush.utils.Hooker
import io.github.magisk317.mipush.utils.PrivilegeElevator
import io.github.magisk317.mipush.control.PushControllerUtils
import io.github.magisk317.mipush.control.PushControllerUtils.isAppMainProc
import io.github.magisk317.mipush.notification.NotificationController.CHANNEL_WARN
import io.github.magisk317.mipush.platform.support.CrashHandler
import com.xiaomi.xmsf.push.service.MiuiPushActivateService
import io.github.magisk317.mipush.runtime.PushRuntimeChannelTracker
import io.github.magisk317.mipush.runtime.PushRuntimeExecutionBridge
import com.xiaomi.xmsf.BuildConfig
import com.xiaomi.xmsf.R
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.platform.service.PushServiceAccessibility
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.runtime.store.DatabaseUtils
import com.xiaomi.xmsf.stock.StockSurfaceBootstrap
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MiPushFrameworkApp : Application() {
    @Inject lateinit var preferenceRepository: PreferenceRepository

    private val logger = object {
        fun i(msg: String) = Napier.i(msg, tag = "MiPushFrameworkApp")
        fun e(msg: String?, t: Throwable? = null) = Napier.e(msg ?: "", t, tag = "MiPushFrameworkApp")
    }

    override fun onCreate() {
        applicationScope = MainScope()
        super.onCreate()
        DatabaseUtils.init(this)
        TelemetryDisabler.disableAll(this)
        PrivilegeElevator.tryToElevate()
        Utils.setApplicationContext(this)
        initBasicLogger()
        CrashHandler.installCrashLogger()

        Hooker.setLogger(PushControllerUtils.wrapContext(this))
        Hooker.hook(this)
        NotificationManagerEx.init(applicationContext)
        // Initialize the runtime observer early so XMPushService.observer is set
        // before any service start. BootReceiver normally does this, but it may
        // not exist in the manifest or may not have fired yet.
        if (com.xiaomi.push.service.XMPushService.observer == null) {
            io.github.magisk317.mipush.bridge.MiPushRuntimeObserverBridge(this)
        }
        PushRuntimeExecutionBridge.attach(this)
        PushRuntimeChannelTracker.attach(this)
        PushControllerUtils.setAllEnable(true, this)
        awakePushActivateServiceOnMainProc(PushControllerUtils.wrapContext(this))
        StockSurfaceBootstrap.bootstrap(this)
        requestDozeWhiteList()
        PushHealthSnapshotLogger.log(this, "MiPushFrameworkApp.onCreate")
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

    private fun initBasicLogger() {
        LogUtils.init(this)
        // 读取初始 debugMode 并立即同步到 MyLog，避免启动阶段 DEBUG 日志被错误过滤
        val initialDebugMode = runCatching {
            runBlocking { preferenceRepository.isDebugMode.first() }
        }.getOrDefault(false)
        LegacyLoggerBridge.setDebugLoggingEnabled(initialDebugMode)
        LogUtils.setMinLogLevel(if (initialDebugMode) LogLevel.VERBOSE else LogLevel.INFO)
        // 收集后续变更，确保设置页开关拨动后实时生效
        applicationScope.launch {
            preferenceRepository.isDebugMode.collect { enabled ->
                LegacyLoggerBridge.setDebugLoggingEnabled(enabled)
                LogUtils.setMinLogLevel(if (enabled) LogLevel.VERBOSE else LogLevel.INFO)
            }
        }
        logger.i("App starts: ${BuildConfig.VERSION_NAME}, debugMode=$initialDebugMode")
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            manager.notify(javaClass.simpleName, 100, notification)
        }
    }

    private fun createWarnChannel(manager: NotificationManagerCompat) {
        val channel = NotificationChannelCompat.Builder(CHANNEL_WARN, NotificationManager.IMPORTANCE_HIGH)
            .setName(getString(R.string.wizard_title_doze_whitelist))
        val notificationChannelGroup = NotificationChannelGroupCompat.Builder(CHANNEL_WARN)
            .setName(CHANNEL_WARN)
            .build()
        manager.createNotificationChannelGroup(notificationChannelGroup)
        channel.setGroup(notificationChannelGroup.id)
        manager.createNotificationChannel(channel.build())
    }

    private fun getLastStartupTime(): Long {
        // Use cached value from SharedPreferences to avoid blocking main thread.
        // DataStore-backed preferences are eventually consistent; SharedPreferences
        // provides a synchronous fallback that is safe on Application.onCreate.
        return try {
            val prefs = getSharedPreferences("mipush_startup", Context.MODE_PRIVATE)
            prefs.getLong("last_startup_time", 0L)
        } catch (_: Throwable) {
            0L
        }
    }

    private fun setStartupTime(value: Long) {
        try {
            getSharedPreferences("mipush_startup", Context.MODE_PRIVATE)
                .edit().putLong("last_startup_time", value).apply()
        } catch (_: Throwable) {}
        applicationScope.launch {
            preferenceRepository.setLastStartupTime(value)
        }
    }

    companion object {
        private const val MIPUSH_EXTRA = "mipush_extra"
        lateinit var applicationScope: CoroutineScope
            private set
    }
}
