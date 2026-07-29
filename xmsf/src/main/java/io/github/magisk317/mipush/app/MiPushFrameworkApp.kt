package io.github.magisk317.mipush.app

import io.github.magisk317.mipush.common.R as CommonR
import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

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
import io.github.magisk317.xposed.logging.LogSanitizerConfig
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.LogLevel
import io.github.magisk317.mipush.utils.LogUtils
import io.github.magisk317.mipush.push.hook.HookTrace
import io.github.magisk317.mipush.bridge.LegacyLoggerBridge
import io.github.magisk317.mipush.bridge.MiPushRuntimeObserverBridge
import io.github.magisk317.mipush.notification.NotificationManagerEx
import io.github.magisk317.mipush.notification.SweetNotificationCoordinator
import io.github.magisk317.mipush.notification.LegacyNotificationIdentityMigration
import io.github.magisk317.mipush.utils.Hooker
import io.github.magisk317.mipush.control.PushControllerUtils
import io.github.magisk317.mipush.control.PushControllerUtils.isAppMainProc
import io.github.magisk317.mipush.notification.NotificationController.CHANNEL_WARN
import io.github.magisk317.mipush.platform.support.PermissionUtils
import io.github.magisk317.mipush.platform.support.CrashHandler
import io.github.magisk317.mipush.runtime.PushRuntimeChannelTracker
import io.github.magisk317.mipush.runtime.PushRuntimeExecutionBridge
import com.xiaomi.xmsf.R
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.common.ISLAND_PREF_READ_PERMISSION
import io.github.magisk317.mipush.common.VERSION_NAME
import io.github.magisk317.mipush.platform.service.PushServiceAccessibility
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.runtime.store.DatabaseUtils
import io.github.magisk317.mipush.runtime.store.db.EventRetentionManager
import com.xiaomi.xmsf.stock.StockSurfaceBootstrap
import io.github.magisk317.mipush.app.di.AppDependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import io.github.magisk317.mipush.common.BuildConfig
import io.github.magisk317.xposed.logging.MagiskOtel

open class MiPushFrameworkApp : Application() {
    private val preferenceRepository: PreferenceRepository by lazy { AppDependencies.get(this) }


    override fun onCreate() {
        applicationScope = MainScope()
        super.onCreate()
        TelemetryDisabler.disableAll(this)
        Utils.setApplicationContext(this)
        if (!isAppMainProc(this)) {
            LogUtils.init(this)
            CrashHandler.installCrashLogger()
            logI("Initialized minimal app shell outside main process")
            return
        }

        AppDependencies.start(this)
        val analyticsPrefEnabled = runCatching {
            runBlocking { preferenceRepository.isAnalyticsEnabled.first() }
        }.getOrDefault(true)
        val systemOtelEnabled =
            System.getProperty("magisk.otel.enabled")?.equals("true", ignoreCase = true) == true
        MagiskOtel.configureForInstallation(
            this,
            MagiskOtel.Config(
                enabled = BuildConfig.DEBUG || analyticsPrefEnabled || systemOtelEnabled,
                serviceName = "mipushframework",
                serviceVersion = VERSION_NAME,
                projectId = "83955143",
                projectName = "MiPushFramework",
                environment = if (BuildConfig.DEBUG) "debug" else "release",
            ),
        )
        MagiskOtel.event(
            name = "app.boot",
            attributes = mapOf(
                "result" to "ok",
                "process" to "main",
            ),
        )
        initBasicLogger()
        CrashHandler.installCrashLogger()
        DatabaseUtils.init(this)
        onAppDependenciesStarted()
        XSpaceXmsfInstallKeeper.scheduleForced(this, "MiPushFrameworkApp.onCreate")
        scheduleSilentPermissionGrants()
        ProactiveMiPushRegistrar.schedule(this)

        Hooker.setLogger(PushControllerUtils.wrapContext(this))
        Hooker.hook(this)
        NotificationManagerEx.init(applicationContext)
        // Stock XMSF 7.4.67-C installs a process-lifetime screen receiver for style-5 reminder
        // cleanup. The older 3.7.9 runtime has no equivalent, so initialize the product coordinator
        // only from the main app shell after notification identity is ready.
        SweetNotificationCoordinator.initialize(applicationContext)
        applicationScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { LegacyNotificationIdentityMigration.runOnce(this@MiPushFrameworkApp) }
                .onFailure { logW("legacy notification identity migration failed: ${it.message}") }
        }
        registerPrefChangeReceiver()
        // Initialize the runtime observer bridge before any service start.
        // BootReceiver normally does this, but it may not exist in the manifest
        // or may not have fired yet.
        MiPushRuntimeObserverBridge.ensureInstalled(this)
        PushRuntimeExecutionBridge.attach(this)
        PushRuntimeChannelTracker.attach(this)
        PushControllerUtils.setAllEnable(true, this)
        StockSurfaceBootstrap.bootstrap(this)
        requestDozeWhiteList()
        // Android 17: Check for memory limit warnings
        checkMemoryLimit()
        initEventRetention()
        PushHealthSnapshotLogger.log(this, "MiPushFrameworkApp.onCreate")
    }

    /**
     * 接线事件记录的保留期限清理:
     * - 向 [EventRetentionManager] 注入保留天数 provider(从 [PreferenceRepository] 缓存回传);
     * - 启动时触发一次清理,把长期无上界增长的事件表拉回保留窗口内。
     * 仅在主进程执行,避免多进程重复清理。
     */

    /**
     * Best-effort root grant of silent permissions for xmsf + manager (primary and dual-space).
     * Settings special-access lists often omit dual-space clones; root appops is the reliable path.
     */
    private fun scheduleSilentPermissionGrants() {
        applicationScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                PermissionUtils.grantSilentPermissionsForFramework(
                    userId = PermissionUtils.USER_AUTO,
                )
            }.onFailure {
                logW("silent permission grant failed: ${it.message}")
            }
        }
    }

    private fun initEventRetention() {
        val cachedRetentionDays = java.util.concurrent.atomic.AtomicInteger(7)
        EventRetentionManager.install { cachedRetentionDays.get() }
        applicationScope.launch {
            preferenceRepository.eventRetentionDays.collect { days ->
                cachedRetentionDays.set(days.coerceAtLeast(1))
            }
        }
        applicationScope.launch {
            val days = runCatching {
                preferenceRepository.eventRetentionDays.first()
            }.getOrDefault(7).coerceAtLeast(1)
            cachedRetentionDays.set(days)
            EventRetentionManager.pruneNow()
        }
    }

    protected open fun onAppDependenciesStarted() = Unit

    private fun registerPrefChangeReceiver() {
        runCatching {
            ContextCompat.registerReceiver(
                this,
                object : android.content.BroadcastReceiver() {
                    override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                        io.github.magisk317.mipush.notification.NotificationManagerEx.triggerStatusBarRefresh()
                    }
                },
                android.content.IntentFilter(io.github.magisk317.mipush.common.ACTION_PREF_CHANGED),
                ISLAND_PREF_READ_PERMISSION,
                null,
                ContextCompat.RECEIVER_EXPORTED,
            )
        }.onFailure {
            logE("failed to register pref change receiver", it)
        }
    }

    private fun requestDozeWhiteList() {
        try {
            if (!PushServiceAccessibility.isInDozeWhiteList(this)) {
                notifyDozeWhiteListRequest(NotificationManagerCompat.from(this))
            }
        } catch (e: RuntimeException) {
            logE(e.message ?: "error", e)
        }
    }

    /**
     * Android 17 (API 37) memory limit check.
     * Detect if the app is affected by the new memory limit feature.
     */
    private fun checkMemoryLimit() {
        if (Build.VERSION.SDK_INT < 35) return // Android 15+
        try {
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory()
            val totalMemory = runtime.totalMemory()
            val freeMemory = runtime.freeMemory()
            val usedMemory = totalMemory - freeMemory

            logI("Memory usage: ${usedMemory / 1024 / 1024}MB / ${maxMemory / 1024 / 1024}MB")

            // Warn if using more than 80% of available memory
            if (usedMemory > maxMemory * 0.8) {
                logW("High memory usage detected: ${usedMemory * 100 / maxMemory}%")
            }
        } catch (e: Exception) {
            logD("Memory check failed: ${e.message}")
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
        HookTrace.enabled = initialDebugMode
        val initialLogSanitization = runCatching {
            runBlocking { preferenceRepository.isLogSanitizationEnabled.first() }
        }.getOrNull()
        LogSanitizerConfig.syncSanitizationEnabled(initialLogSanitization)
        // 收集后续变更，确保设置页开关拨动后实时生效
        applicationScope.launch {
            preferenceRepository.isDebugMode.collect { enabled ->
                LegacyLoggerBridge.setDebugLoggingEnabled(enabled)
                LogUtils.setMinLogLevel(if (enabled) LogLevel.VERBOSE else LogLevel.INFO)
                HookTrace.enabled = enabled
            }
        }
        applicationScope.launch {
            preferenceRepository.isLogSanitizationEnabled
                .catch { LogSanitizerConfig.syncSanitizationEnabled(null) }
                .collect { LogSanitizerConfig.syncSanitizationEnabled(it) }
        }
        logI("App starts: $VERSION_NAME, debugMode=$initialDebugMode")
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
            .setSmallIcon(CommonR.drawable.ic_notifications_black_24dp)
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

    companion object {
        private const val MIPUSH_EXTRA = "mipush_extra"
        lateinit var applicationScope: CoroutineScope
            private set
    }
}
