package io.github.magisk317.mipush.app

import io.github.magisk317.mipush.common.R as CommonR
import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import android.app.ActivityManager
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
import co.touchlab.kermit.Severity
import io.github.magisk317.xposed.logging.LogSanitizerConfig
import io.github.magisk317.mipush.utils.LogUtils
import io.github.magisk317.mipush.push.hook.HookTrace
import io.github.magisk317.mipush.bridge.MiPushRuntimeObserverBridge
import io.github.magisk317.mipush.service.runtime.MIPushNotificationPublishHelper
import io.github.magisk317.mipush.service.runtime.RegSecRecoveryHealer
import io.github.magisk317.mipush.notification.NotificationManagerEx
import io.github.magisk317.mipush.notification.NotificationHookBridge
import io.github.magisk317.mipush.notification.IslandOptionsSnapshotReader
import io.github.magisk317.mipush.notification.NotificationAvailabilityShellBridge
import io.github.magisk317.mipush.notification.NotificationPostOwner
import io.github.magisk317.mipush.notification.NotificationPostResult
import io.github.magisk317.mipush.notification.NotificationShellBridge
import io.github.magisk317.mipush.notification.SweetNotificationCoordinator
import io.github.magisk317.mipush.notification.LegacyNotificationIdentityMigration
import io.github.magisk317.mipush.utils.Hooker
import io.github.magisk317.mipush.control.PushControllerUtils
import io.github.magisk317.mipush.control.PushControllerUtils.isAppMainProc
import io.github.magisk317.mipush.notification.NotificationController
import io.github.magisk317.mipush.notification.NotificationChannelManager
import io.github.magisk317.mipush.notification.NotificationController.CHANNEL_WARN
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
import io.github.magisk317.mipush.runtime.store.db.EventDb
import io.github.magisk317.mipush.runtime.store.db.EventRetentionManager
import com.xiaomi.xmsf.stock.StockSurfaceBootstrap
import io.github.magisk317.mipush.app.di.AppDependencies
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.push.bridge.DefaultPushShellBridge
import io.github.magisk317.mipush.push.bridge.PushShellBridgeHolder
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
        PushShellBridgeHolder.install(DefaultPushShellBridge)
        Utils.regSecRecoveryListener = RegSecRecoveryHealer
        if (!isAppMainProc(this)) {
            LogUtils.init(this)
            CrashHandler.installCrashLogger()
            logI("Initialized minimal app shell outside main process")
            return
        }

        AppDependencies.start(this)
        Global.iconConfigurations().initFromAssets(this)
        val analyticsPrefEnabled = runCatching {
            runBlocking { preferenceRepository.isAnalyticsEnabled.first() }
        }.getOrDefault(true)
        configureAnalytics(analyticsPrefEnabled)
        applicationScope.launch {
            preferenceRepository.isAnalyticsEnabled.collect(::configureAnalytics)
        }
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
        ProactiveMiPushRegistrar.schedule(this)

        Hooker.setLogger(PushControllerUtils.wrapContext(this))
        // HookPushNC probes system_server readiness immediately after installing the hooks.
        // Initialize the XMSF-owned backend first so its HiddenApiBypass exemption is active
        // before that probe runs in the app process.
        NotificationHookBridge.init(applicationContext)
        NotificationManagerEx.init(applicationContext)
        Hooker.hook(this)
        NotificationShellBridge.installStatusBarRefresh(NotificationManagerEx::triggerStatusBarRefresh)
        NotificationShellBridge.installNotificationOperations(
            getNotificationTag = MIPushNotificationPublishHelper::getNotificationTag,
            getActiveNotifications = NotificationManagerEx::getActiveNotifications,
            cancel = NotificationManagerEx::cancel,
        )
        NotificationShellBridge.installPublishOperations(
            postDetailed = { packageName, tag, id, notification, userId ->
                val result = NotificationManagerEx.notifyDetailed(packageName, tag, id, notification, userId)
                NotificationPostResult(
                    posted = result.posted,
                    owner = when (result.owner) {
                        NotificationManagerEx.NotifyOwner.TARGET -> NotificationPostOwner.TARGET
                        NotificationManagerEx.NotifyOwner.LOCAL_XMSF -> NotificationPostOwner.LOCAL_XMSF
                        NotificationManagerEx.NotifyOwner.NONE -> NotificationPostOwner.NONE
                    },
                    reason = result.reason,
                )
            },
            notify = { packageName, tag, id, notification, userId ->
                NotificationManagerEx.notify(packageName, tag, id, notification, userId)
            },
        )
        NotificationAvailabilityShellBridge.install(
            findExistingChannelId = NotificationController::findExistingChannelId,
            notificationChannelEnabled = NotificationChannelManager::isNotificationChannelEnabled,
            resolveChannelId = NotificationController::getExistsChannelId,
        )
        IslandOptionsSnapshotReader.initialize(applicationContext, applicationScope)
        // Stock XMSF 7.4.67-C installs a process-lifetime screen receiver for style-5 reminder
        // cleanup. The older 3.7.9 runtime has no equivalent, so initialize the product coordinator
        // only from the main app shell after notification identity is ready.
        SweetNotificationCoordinator.initialize(applicationContext)
        applicationScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { LegacyNotificationIdentityMigration.runOnce(this@MiPushFrameworkApp) }
                .onFailure { logW("legacy notification identity migration failed: ${it.message}") }
        }
        // IslandOptionsSnapshotReader.initialize() now observes DataStore Flow directly;
        // the ACTION_PREF_CHANGED broadcast receiver was removed in favor of Flow.
        IslandOptionsSnapshotReader.initialize(applicationContext, applicationScope)
        // Initialize the runtime observer bridge before any service start.
        // BootReceiver normally does this, but it may not exist in the manifest
        // or may not have fired yet.
        MiPushRuntimeObserverBridge.ensureInstalled(this)
        PushRuntimeExecutionBridge.attach(this)
        PushRuntimeChannelTracker.attach(this)
        PushControllerUtils.startServiceFromPrefs(this)
        StockSurfaceBootstrap.bootstrap(this)
        requestDozeWhiteList()
        // Android 17: Check for memory limit warnings
        checkMemoryLimit()
        initEventRetention()
        PushHealthSnapshotLogger.log(this, "MiPushFrameworkApp.onCreate")
    }

    private fun configureAnalytics(enabled: Boolean) {
        val systemOtelEnabled =
            System.getProperty("magisk.otel.enabled")?.equals("true", ignoreCase = true) == true
        MagiskOtel.configureForInstallation(
            this,
            MagiskOtel.Config(
                enabled = BuildConfig.DEBUG || enabled || systemOtelEnabled,
                serviceName = "mipushframework",
                serviceVersion = VERSION_NAME,
                projectId = "83955143",
                projectName = "MiPushFramework",
                environment = if (BuildConfig.DEBUG) "debug" else "release",
            ),
        )
    }

    /**
     * 接线事件记录的保留期限清理:
     * - 向 [EventRetentionManager] 注入保留天数 provider(从 [PreferenceRepository] 缓存回传);
     * - 启动时触发一次清理,把长期无上界增长的事件表拉回保留窗口内。
     * 仅在主进程执行,避免多进程重复清理。
     */

    private fun initEventRetention() {
        val cachedRetentionDays = java.util.concurrent.atomic.AtomicInteger(7)
        EventRetentionManager.install { cachedRetentionDays.get() }
        EventRetentionManager.installDeleteHistory { days -> EventDb.deleteHistoryAsync(days) }
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

    @Suppress("unused")
    private fun registerPrefChangeReceiver() {
        // No-op: island settings cache refresh is now handled by DataStore Flow observation
        // in IslandOptionsSnapshotReader.initialize(). Status bar refresh is triggered
        // from the same Flow collector.
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
            logRecentProcessExit()
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory()
            val totalMemory = runtime.totalMemory()
            val freeMemory = runtime.freeMemory()
            val usedMemory = totalMemory - freeMemory

            logI("Memory usage: ${usedMemory / 1024 / 1024}MB / ${maxMemory / 1024 / 1024}MB")

            // Warn if using more than 80% of available memory
            if (MemoryLimitDiagnostics.isHighMemoryUsage(usedMemory, maxMemory)) {
                logW("High memory usage detected: ${usedMemory * 100 / maxMemory}%")
            }
        } catch (e: Exception) {
            logD("Memory check failed: ${e.message}")
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        trimCaches(level)
    }

    @Suppress("DEPRECATION")
    override fun onLowMemory() {
        super.onLowMemory()
        trimCaches(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
    }

    private fun trimCaches(level: Int) {
        val action = MemoryTrimPolicy.actionFor(level)
        when (action) {
            MemoryTrimPolicy.Action.NONE -> return
            MemoryTrimPolicy.Action.CLEAR_BITMAPS -> {
                Global.iconCache().clearBitmapCaches()
                NotificationController.clearIconPackCache()
            }
            MemoryTrimPolicy.Action.CLEAR_ALL -> {
                Global.iconCache().clearAll()
                Global.applicationNameCache().clear()
                NotificationController.clearIconPackCache()
            }
        }
        logD("Trimmed memory accelerators level=$level action=${action.name}")
    }

    private fun logRecentProcessExit() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val activityManager = getSystemService(ActivityManager::class.java) ?: return
        val latestExit = activityManager
            .getHistoricalProcessExitReasons(packageName, 4, 0)
            .firstOrNull { MemoryLimitDiagnostics.isMemoryRelatedExit(it) }
            ?: return
        logW("Recent memory-related process exit: ${MemoryLimitDiagnostics.describeExit(latestExit)}")
    }

    private fun initBasicLogger() {
        LogUtils.init(this)
        val initialDebugMode = runCatching {
            runBlocking { preferenceRepository.isDebugMode.first() }
        }.getOrDefault(false)
        LogUtils.setMinLogLevel(if (initialDebugMode) Severity.Verbose else Severity.Info)
        HookTrace.enabled = initialDebugMode
        LogSanitizerConfig.syncFromVerboseMode(initialDebugMode)
        // 收集后续变更，确保设置页开关拨动后实时生效
        applicationScope.launch {
            preferenceRepository.isDebugMode.collect { enabled ->
                LogUtils.setMinLogLevel(if (enabled) Severity.Verbose else Severity.Info)
                HookTrace.enabled = enabled
                LogSanitizerConfig.syncFromVerboseMode(enabled)
            }
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
