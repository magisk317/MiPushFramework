package io.github.magisk317.mipush.hook.island

import android.net.Uri
import io.github.magisk317.mipush.common.ISLAND_PREF_AUTHORITY
import io.github.magisk317.mipush.common.ISLAND_PREF_COLUMN_KEY
import io.github.magisk317.mipush.common.ISLAND_PREF_COLUMN_VALUE
import io.github.magisk317.mipush.common.ISLAND_PREF_ENABLE_FLOAT
import io.github.magisk317.mipush.common.ISLAND_PREF_ENABLED
import io.github.magisk317.mipush.common.ISLAND_PREF_FIRST_FLOAT
import io.github.magisk317.mipush.common.ISLAND_PREF_FOCUS_NOTIF
import io.github.magisk317.mipush.common.ISLAND_PREF_PATH_FLAGS
import io.github.magisk317.mipush.common.ISLAND_PREF_SHOW_NOTIFICATION
import io.github.magisk317.mipush.common.ISLAND_PREF_TIMEOUT
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.xposed.currentApplication

object IslandPreferences {
    private const val TAG = "IslandPreferences"
    private const val PREF_REFRESH_INTERVAL_MS = 60_000L
    private val PREF_KEYS = arrayOf(
        ISLAND_PREF_ENABLED,
        ISLAND_PREF_TIMEOUT,
        ISLAND_PREF_FIRST_FLOAT,
        ISLAND_PREF_ENABLE_FLOAT,
        ISLAND_PREF_SHOW_NOTIFICATION,
        ISLAND_PREF_FOCUS_NOTIF,
    )

    @Volatile
    private var options = IslandOptions()

    @Volatile
    private var refreshLoopStarted = false

    fun current(): IslandOptions = options

    fun refreshNow() {
        runCatching {
            val app = currentApplication() ?: return
            val prefUri = Uri.parse("content://$ISLAND_PREF_AUTHORITY/$ISLAND_PREF_PATH_FLAGS")
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

            options = IslandOptions(
                enabled = values.booleanValue(ISLAND_PREF_ENABLED, true),
                timeoutSecs = values.intValue(ISLAND_PREF_TIMEOUT, 5).coerceAtLeast(1),
                firstFloat = values.booleanValue(ISLAND_PREF_FIRST_FLOAT, true),
                enableFloat = values.booleanValue(ISLAND_PREF_ENABLE_FLOAT, true),
                showNotification = values.booleanValue(ISLAND_PREF_SHOW_NOTIFICATION, true),
                focusNotification = values.booleanValue(ISLAND_PREF_FOCUS_NOTIF, true),
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

    private fun Map<String, String>.booleanValue(key: String, default: Boolean): Boolean {
        return this[key]?.let { value ->
            value == "1" || value.equals("true", ignoreCase = true)
        } ?: default
    }

    private fun Map<String, String>.intValue(key: String, default: Int): Int {
        return this[key]?.toIntOrNull() ?: default
    }
}
