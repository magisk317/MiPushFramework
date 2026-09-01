package io.github.magisk317.mipush.hook.island

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import io.github.magisk317.mipush.common.ACTION_PREF_CHANGED
import io.github.magisk317.mipush.common.ISLAND_PREF_AUTHORITY
import io.github.magisk317.mipush.common.ISLAND_PREF_COLUMN_KEY
import io.github.magisk317.mipush.common.ISLAND_PREF_COLUMN_PACKAGE
import io.github.magisk317.mipush.common.ISLAND_PREF_COLUMN_USER
import io.github.magisk317.mipush.common.ISLAND_PREF_COLUMN_VALUE
import io.github.magisk317.mipush.common.ISLAND_PREF_ENABLE_FLOAT
import io.github.magisk317.mipush.common.ISLAND_PREF_ENABLED
import io.github.magisk317.mipush.common.ISLAND_PREF_FIRST_FLOAT
import io.github.magisk317.mipush.common.ISLAND_PREF_FOCUS_NOTIF
import io.github.magisk317.mipush.common.ISLAND_PREF_PATH_FLAGS
import io.github.magisk317.mipush.common.ISLAND_PREF_READ_PERMISSION
import io.github.magisk317.mipush.common.ISLAND_PREF_SHOW_NOTIFICATION
import io.github.magisk317.mipush.common.ISLAND_PREF_SHOW_ORIGINAL_NOTIFICATION
import io.github.magisk317.mipush.common.ISLAND_PREF_TIMEOUT
import io.github.magisk317.mipush.common.COLOR_STATUS_BAR_ICON_KEY
import io.github.magisk317.mipush.common.COLOR_STATUS_BAR_ICON_GLOBAL_KEY
import io.github.magisk317.mipush.common.DUAL_APP_ENABLED_KEY
import io.github.magisk317.mipush.common.LOG_SANITIZATION_ENABLED_KEY
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.xposed.logging.LogSanitizerConfig
import io.github.magisk317.xposed.currentApplication
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

@SuppressLint("StaticFieldLeak")
object IslandPreferences {
    private const val TAG = "IslandPreferences"
    private const val PREF_REFRESH_INTERVAL_MS = 60_000L
    private const val FAILURE_LOG_INTERVAL_MS = 10L * 60L * 1000L
    private val PREF_KEYS = arrayOf(
        ISLAND_PREF_ENABLED,
        ISLAND_PREF_TIMEOUT,
        ISLAND_PREF_FIRST_FLOAT,
        ISLAND_PREF_ENABLE_FLOAT,
        ISLAND_PREF_SHOW_NOTIFICATION,
        ISLAND_PREF_SHOW_ORIGINAL_NOTIFICATION,
        ISLAND_PREF_FOCUS_NOTIF,
        COLOR_STATUS_BAR_ICON_KEY,
        COLOR_STATUS_BAR_ICON_GLOBAL_KEY,
        DUAL_APP_ENABLED_KEY,
        LOG_SANITIZATION_ENABLED_KEY,
    )

    @Volatile
    private var options = IslandOptions()

    @Volatile
    private var refreshLoopStarted = false

    @Volatile
    private var lastLoggedOptions: IslandOptions? = null

    @Volatile
    private var lastRefreshFailure: String? = null

    @Volatile
    private var lastRefreshFailureAtMs: Long = 0L
    internal data class PackageKey(val userId: Int, val packageName: String)

    private val packageOptions = ConcurrentHashMap<PackageKey, IslandOptions>()
    private val packageRefreshes = ConcurrentHashMap.newKeySet<PackageKey>()
    private val refreshGeneration = AtomicLong()
    private val loopGeneration = AtomicLong()
    private val refreshLock = Any()

    @Volatile
    private var refreshExecutor: ExecutorService? = null
    private var preferenceReceiverThread: Thread? = null
    private var preferencePollThread: Thread? = null
    private var preferenceReceiverContext: Context? = null
    private var preferenceReceiver: BroadcastReceiver? = null

    fun current(): IslandOptions = options

    fun current(packageName: String?, userId: Int? = null): IslandOptions {
        val pkg = packageName?.takeIf { it.isNotBlank() } ?: return options
        val normalizedUserId = userId?.takeIf { it >= 0 } ?: return options.copy(
            enabled = false,
            focusNotification = false,
        )
        val key = PackageKey(normalizedUserId, pkg)
        val cached = packageOptions[key]
        if (cached == null && packageRefreshes.add(key)) {
            val generation = refreshGeneration.get()
            val executor = refreshExecutor
            if (executor == null || executor.isShutdown) {
                packageRefreshes.remove(key)
            } else {
                runCatching {
                    executor.execute {
                        readOptions(pkg, key.userId).onSuccess {
                            if (refreshGeneration.get() == generation) {
                                packageOptions[key] = it
                            }
                        }
                        packageRefreshes.remove(key)
                    }
                }.onFailure {
                    packageRefreshes.remove(key)
                }
            }
        }
        // Package-specific opt-outs must not inherit globally enabled behavior while the first
        // asynchronous read is in flight. Focus and visual rendering are both user-visible
        // policies; fail closed until the package snapshot is available.
        return cached ?: options.copy(
            focusNotification = false,
        )
    }

    fun refreshNow() {
        val refresh = prepareRefresh()
        val executor = refreshExecutor ?: return
        if (executor.isShutdown) return
        runCatching {
            executor.execute {
                refreshBlocking(refresh.generation)
                refresh.packageNames.forEach { packageName ->
                    readOptions(packageName.packageName, packageName.userId).onSuccess {
                        if (refreshGeneration.get() == refresh.generation) {
                            packageOptions[packageName] = it
                        }
                    }
                }
            }
        }
    }

    private fun refreshBlocking(generation: Long) {
        readOptions(packageName = null).onSuccess {
            if (refreshGeneration.get() != generation) return@onSuccess
            options = it
            if (lastLoggedOptions != it) {
                lastLoggedOptions = it
                XLog.i(
                    TAG,
                    "refreshed options: showNotification=${it.showNotification} " +
                        "enableFloat=${it.enableFloat} enabled=${it.enabled} " +
                        "focusNotification=${it.focusNotification} colorStatusBarIcon=${it.colorStatusBarIcon} " +
                        "colorStatusBarIconGlobal=${it.colorStatusBarIconGlobal} " +
                        "dualAppEnabled=${it.dualAppEnabled}",
                )
            }
            lastRefreshFailure = null
            lastRefreshFailureAtMs = 0L
        }.onFailure {
            val message = it.message ?: it.javaClass.simpleName
            val nowMs = System.currentTimeMillis()
            if (message != lastRefreshFailure || nowMs - lastRefreshFailureAtMs >= FAILURE_LOG_INTERVAL_MS) {
                lastRefreshFailure = message
                lastRefreshFailureAtMs = nowMs
                XLog.w(TAG, "failed to refresh island prefs: $message")
            }
        }
    }

    internal fun prepareRefresh(): RefreshRequest = RefreshRequest(
        generation = refreshGeneration.incrementAndGet(),
        // Keep serving the last package-specific value while its replacement is loaded.
        packageNames = (packageOptions.keys + packageRefreshes).toSet(),
    )

    internal data class RefreshRequest(
        val generation: Long,
        val packageNames: Set<PackageKey>,
    )

    fun startRefreshLoop() {
        if (refreshLoopStarted) return
        synchronized(refreshLock) {
            if (refreshLoopStarted) return
            refreshExecutor = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "MiPushIslandPrefs").apply { isDaemon = true }
            }
            refreshLoopStarted = true
            val generation = loopGeneration.incrementAndGet()
            refreshNow()
            // Listen for immediate preference change broadcasts
            // Delay registration until Application is available
            preferenceReceiverThread = Thread({
                registerPreferenceReceiverWhenReady(generation)
            }, "MiPushPrefReceiver").apply {
                isDaemon = true
                start()
            }
            preferencePollThread = Thread({
                while (isRefreshActive(generation)) {
                    try {
                        Thread.sleep(PREF_REFRESH_INTERVAL_MS)
                    } catch (_: InterruptedException) {
                        return@Thread
                    }
                    if (isRefreshActive(generation)) {
                        refreshNow()
                    }
                }
            }, "MiPushIslandPrefs").apply {
                isDaemon = true
                start()
            }
        }
    }

    /** Stop resources owned by the current module ClassLoader before libxposed hot reload. */
    fun stopRefreshLoop() {
        val receiverContext: Context?
        val receiver: BroadcastReceiver?
        val receiverThread: Thread?
        val pollThread: Thread?
        val executor: ExecutorService?
        synchronized(refreshLock) {
            refreshGeneration.incrementAndGet()
            loopGeneration.incrementAndGet()
            refreshLoopStarted = false
            receiverContext = preferenceReceiverContext
            receiver = preferenceReceiver
            receiverThread = preferenceReceiverThread
            pollThread = preferencePollThread
            executor = refreshExecutor
            preferenceReceiverContext = null
            preferenceReceiver = null
            preferenceReceiverThread = null
            preferencePollThread = null
            refreshExecutor = null
            packageRefreshes.clear()
        }
        receiverThread?.interrupt()
        pollThread?.interrupt()
        executor?.shutdownNow()
        if (receiverContext != null && receiver != null) {
            runCatching { receiverContext.unregisterReceiver(receiver) }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerPreferenceReceiverWhenReady(generation: Long) {
        while (isRefreshActive(generation)) {
            val app = currentApplication()
            if (app != null) {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        refreshNow()
                    }
                }
                val registered = runCatching {
                    val filter = IntentFilter(ACTION_PREF_CHANGED)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        app.registerReceiver(
                            receiver,
                            filter,
                            ISLAND_PREF_READ_PERMISSION,
                            null,
                            Context.RECEIVER_EXPORTED,
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        app.registerReceiver(receiver, filter, ISLAND_PREF_READ_PERMISSION, null)
                    }
                }.isSuccess
                if (registered) {
                    val keepRegistration = synchronized(refreshLock) {
                        if (isRefreshActive(generation)) {
                            preferenceReceiverContext = app
                            preferenceReceiver = receiver
                            true
                        } else {
                            false
                        }
                    }
                    if (keepRegistration) {
                        // The initial refresh can run before ActivityThread exposes Application,
                        // which otherwise leaves SystemUI on defaults until the 60-second poll.
                        refreshNow()
                        return
                    }
                    runCatching { app.unregisterReceiver(receiver) }
                    return
                }
            }
            try {
                Thread.sleep(1000)
            } catch (_: InterruptedException) {
                return
            }
        }
    }

    private fun isRefreshActive(generation: Long): Boolean =
        refreshLoopStarted && loopGeneration.get() == generation


    internal fun resetForTest(options: IslandOptions = IslandOptions()) {
        stopRefreshLoop()
        this.options = options
        lastLoggedOptions = null
        lastRefreshFailure = null
        lastRefreshFailureAtMs = 0L
        packageOptions.clear()
        packageRefreshes.clear()
    }

    internal fun cachePackageOptionsForTest(
        packageName: String,
        options: IslandOptions,
        userId: Int,
    ) {
        packageOptions[PackageKey(
            io.github.magisk317.mipush.common.utils.Utils.requireValidUserId(userId),
            packageName,
        )] = options
    }

    private fun readOptions(packageName: String?, userId: Int? = null): Result<IslandOptions> = runCatching {
        val app = currentApplication() ?: return@runCatching options
        val prefUri = Uri.Builder()
            .scheme("content")
            .authority(ISLAND_PREF_AUTHORITY)
            .appendPath(ISLAND_PREF_PATH_FLAGS)
            .also { builder ->
                if (!packageName.isNullOrBlank()) {
                    builder.appendQueryParameter(ISLAND_PREF_COLUMN_PACKAGE, packageName)
                }
                if (packageName != null && userId != null) {
                    builder.appendQueryParameter(ISLAND_PREF_COLUMN_USER, userId.toString())
                }
            }
            .build()
        val values = app.contentResolver.query(prefUri, null, null, PREF_KEYS, null)?.use { cursor ->
            val keyIndex = cursor.getColumnIndex(ISLAND_PREF_COLUMN_KEY)
            val valueIndex = cursor.getColumnIndex(ISLAND_PREF_COLUMN_VALUE)
            check(keyIndex >= 0 && valueIndex >= 0) {
                "island preference provider returned an invalid cursor"
            }
            buildMap<String, String> {
                while (cursor.moveToNext()) {
                    put(cursor.getString(keyIndex), cursor.getString(valueIndex))
                }
            }
        } ?: error("island preference provider returned no cursor")
        check(values.isNotEmpty()) { "island preference provider returned no values" }

        // Reuse the single provider query above instead of a second readFlag round-trip.
        val logSanitizationEnabled = values.booleanValue(LOG_SANITIZATION_ENABLED_KEY, false)
        LogSanitizerConfig.syncSanitizationEnabled(logSanitizationEnabled)

        IslandOptions(
            enabled = values.booleanValue(ISLAND_PREF_ENABLED, true),
            timeoutSecs = values.intValue(ISLAND_PREF_TIMEOUT, 5).coerceAtLeast(1),
            firstFloat = values.booleanValue(ISLAND_PREF_FIRST_FLOAT, true),
            enableFloat = values.booleanValue(ISLAND_PREF_ENABLE_FLOAT, true),
            showNotification = values.booleanValue(ISLAND_PREF_SHOW_NOTIFICATION, true),
            showOriginalNotification = values.booleanValue(ISLAND_PREF_SHOW_ORIGINAL_NOTIFICATION, true),
            focusNotification = values.booleanValue(ISLAND_PREF_FOCUS_NOTIF, false),
            colorStatusBarIcon = values.booleanValue(COLOR_STATUS_BAR_ICON_KEY, false),
            colorStatusBarIconGlobal = values.booleanValue(COLOR_STATUS_BAR_ICON_GLOBAL_KEY, false),
            dualAppEnabled = values.booleanValue(DUAL_APP_ENABLED_KEY, false),
        )
    }.onFailure {
        LogSanitizerConfig.syncSanitizationEnabled(null)
    }

    private fun Map<String, String>.booleanValue(key: String, default: Boolean): Boolean {
        return this[key]?.let { value ->
            value == "1" || value.equals("true", ignoreCase = true)
        } ?: default
    }

    private fun Map<String, String>.intValue(key: String, default: Int): Int {
        return this[key]?.toIntOrNull() ?: default
    }

}
