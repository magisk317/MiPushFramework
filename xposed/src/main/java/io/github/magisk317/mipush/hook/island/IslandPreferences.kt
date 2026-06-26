package io.github.magisk317.mipush.hook.island

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import io.github.magisk317.mipush.common.ACTION_PREF_CHANGED
import io.github.magisk317.mipush.common.ISLAND_PREF_AUTHORITY
import io.github.magisk317.mipush.common.ISLAND_PREF_COLUMN_KEY
import io.github.magisk317.mipush.common.ISLAND_PREF_COLUMN_PACKAGE
import io.github.magisk317.mipush.common.ISLAND_PREF_COLUMN_VALUE
import io.github.magisk317.mipush.common.ISLAND_PREF_ENABLE_FLOAT
import io.github.magisk317.mipush.common.ISLAND_PREF_ENABLED
import io.github.magisk317.mipush.common.ISLAND_PREF_FIRST_FLOAT
import io.github.magisk317.mipush.common.ISLAND_PREF_FOCUS_NOTIF
import io.github.magisk317.mipush.common.ISLAND_PREF_PATH_FLAGS
import io.github.magisk317.mipush.common.ISLAND_PREF_SHOW_NOTIFICATION
import io.github.magisk317.mipush.common.ISLAND_PREF_SHOW_ORIGINAL_NOTIFICATION
import io.github.magisk317.mipush.common.ISLAND_PREF_TIMEOUT
import io.github.magisk317.mipush.common.COLOR_STATUS_BAR_ICON_KEY
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.xposed.currentApplication

object IslandPreferences {
    private const val TAG = "IslandPreferences"
    private const val PREF_REFRESH_INTERVAL_MS = 60_000L
    private val PREF_KEYS = arrayOf(
        ISLAND_PREF_ENABLED,
        ISLAND_PREF_TIMEOUT,
        ISLAND_PREF_FIRST_FLOAT,
        ISLAND_PREF_ENABLE_FLOAT,
        ISLAND_PREF_SHOW_NOTIFICATION,
        ISLAND_PREF_SHOW_ORIGINAL_NOTIFICATION,
        ISLAND_PREF_FOCUS_NOTIF,
        COLOR_STATUS_BAR_ICON_KEY,
    )

    @Volatile
    private var options = IslandOptions()

    @Volatile
    private var refreshLoopStarted = false

    fun current(): IslandOptions = options

    fun current(packageName: String?): IslandOptions {
        val pkg = packageName?.takeIf { it.isNotBlank() } ?: return options
        return readOptions(pkg).getOrNull() ?: options
    }

    fun refreshNow() {
        readOptions(packageName = null).onSuccess {
            options = it
            XLog.i(
                TAG,
                "refreshed options: showNotification=${it.showNotification} " +
                    "enableFloat=${it.enableFloat} enabled=${it.enabled} " +
                    "focusNotification=${it.focusNotification} colorStatusBarIcon=${it.colorStatusBarIcon}",
            )
        }.onFailure {
            XLog.w(TAG, "failed to refresh island prefs: ${it.message}")
        }
    }

    fun startRefreshLoop() {
        if (refreshLoopStarted) return
        synchronized(IslandPreferences::class.java) {
            if (refreshLoopStarted) return
            refreshLoopStarted = true
            refreshNow()
            // Listen for immediate preference change broadcasts
            // Delay registration until Application is available
            Thread({
                var registered = false
                while (!registered) {
                    runCatching {
                        val app = currentApplication() ?: return@runCatching
                        app.registerReceiver(
                            object : BroadcastReceiver() {
                                override fun onReceive(context: Context?, intent: Intent?) {
                                    refreshNow()
                                }
                            },
                            IntentFilter(ACTION_PREF_CHANGED),
                            Context.RECEIVER_EXPORTED,
                        )
                        registered = true
                    }
                    if (!registered) {
                        try {
                            Thread.sleep(1000)
                        } catch (_: InterruptedException) {
                            return@Thread
                        }
                    }
                }
            }, "MiPushPrefReceiver").apply {
                isDaemon = true
                start()
            }
            Thread({
                while (true) {
                    try {
                        Thread.sleep(PREF_REFRESH_INTERVAL_MS)
                    } catch (_: InterruptedException) {
                        return@Thread
                    }
                    refreshNow()
                }
            }, "MiPushIslandPrefs").apply {
                isDaemon = true
                start()
            }
        }
    }

    internal fun resetForTest(options: IslandOptions = IslandOptions()) {
        this.options = options
        refreshLoopStarted = false
    }

    private fun readOptions(packageName: String?): Result<IslandOptions> = runCatching {
        val app = currentApplication() ?: return@runCatching options
        val prefUri = Uri.Builder()
            .scheme("content")
            .authority(ISLAND_PREF_AUTHORITY)
            .appendPath(ISLAND_PREF_PATH_FLAGS)
            .also { builder ->
                if (!packageName.isNullOrBlank()) {
                    builder.appendQueryParameter(ISLAND_PREF_COLUMN_PACKAGE, packageName)
                }
            }
            .build()
        val values = app.contentResolver.query(prefUri, null, null, PREF_KEYS, null)?.use { cursor ->
            val keyIndex = cursor.getColumnIndex(ISLAND_PREF_COLUMN_KEY)
            val valueIndex = cursor.getColumnIndex(ISLAND_PREF_COLUMN_VALUE)
            if (keyIndex < 0 || valueIndex < 0) {
                emptyMap()
            } else {
                buildMap<String, String> {
                    while (cursor.moveToNext()) {
                        put(cursor.getString(keyIndex), cursor.getString(valueIndex))
                    }
                }
            }
        }.orEmpty()

        IslandOptions(
            enabled = values.booleanValue(ISLAND_PREF_ENABLED, true),
            timeoutSecs = values.intValue(ISLAND_PREF_TIMEOUT, 5).coerceAtLeast(1),
            firstFloat = values.booleanValue(ISLAND_PREF_FIRST_FLOAT, true),
            enableFloat = values.booleanValue(ISLAND_PREF_ENABLE_FLOAT, true),
            showNotification = values.booleanValue(ISLAND_PREF_SHOW_NOTIFICATION, true),
            showOriginalNotification = values.booleanValue(ISLAND_PREF_SHOW_ORIGINAL_NOTIFICATION, true),
            focusNotification = values.booleanValue(ISLAND_PREF_FOCUS_NOTIF, true),
            colorStatusBarIcon = values.booleanValue(COLOR_STATUS_BAR_ICON_KEY, false),
        )
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
