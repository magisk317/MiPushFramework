package io.github.magisk317.mipush.data

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL

/**
 * Emergency kill switch for technical analytics ([io.github.magisk317.xposed.logging.MagiskOtel]).
 *
 * The local analytics preference is opt-out and therefore on for most installations. That is fine
 * until a release starts emitting something it should not; without this gate the only remedy is
 * shipping another build. The gate reads a tiny JSON document from the project repository:
 *
 * ```json
 * { "analytics_enabled": false }
 * ```
 *
 * Design rules:
 * - **Fail open to the local decision.** When the document is unreachable, malformed, or missing
 *   the key, the cached decision is kept and the local preference still decides. A dead endpoint
 *   must never silently disable or enable analytics on its own.
 * - **Only an explicit `false` disables.** `true` restores the local preference.
 * - **Cached across restarts** in private SharedPreferences so the first event of a cold start is
 *   already gated; the network refresh only updates the cache for later runs.
 */
object TelemetryRemoteGate {
    private const val PREFS_NAME = "telemetry_remote_gate"
    private const val KEY_DISABLED = "analytics_disabled"
    private const val KEY_LAST_FETCH = "last_fetch_millis"
    private const val KEY_ENABLED = "analytics_enabled"
    private const val FETCH_INTERVAL_MILLIS = 6 * 60 * 60 * 1000L
    private const val CONNECT_TIMEOUT_MILLIS = 3_000
    private const val READ_TIMEOUT_MILLIS = 3_000

    /** Project-controlled document; edit on `beta` to flip analytics fleet-wide. */
    const val ENDPOINT: String =
        "https://gitlab.com/magisk3171/MiPushFramework/-/raw/beta/telemetry.json"

    @Volatile
    private var cachedDisabled: Boolean? = null

    /**
     * Synchronous cached read. Safe to call from the main thread: it touches SharedPreferences once
     * per process and never performs I/O afterwards.
     */
    @Synchronized
    @JvmStatic
    fun isForceDisabled(context: Context): Boolean {
        cachedDisabled?.let {
            return it
        }
        val value = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DISABLED, false)
        cachedDisabled = value
        return value
    }

    /**
     * Best-effort background refresh. Call from a worker thread; never throws and never overrides
     * the cached decision with an unverified response.
     */
    @JvmStatic
    fun refresh(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_FETCH, 0L) < FETCH_INTERVAL_MILLIS) {
            return
        }
        val payload = runCatching { fetch(ENDPOINT) }.getOrNull().orEmpty()
        val disabled = parseDisabled(payload) ?: return
        prefs.edit()
            .putBoolean(KEY_DISABLED, disabled)
            .putLong(KEY_LAST_FETCH, now)
            .apply()
        cachedDisabled = disabled
    }

    private fun fetch(endpoint: String): String? {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) {
                null
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Returns `true` when the document explicitly disables analytics, `false` when it explicitly
     * allows it, and `null` when the document cannot be trusted (unparseable or key absent).
     *
     * Deliberately dependency-free: `org.json` ships inside `android.jar`, whose unit-test stub
     * throws "not mocked", and pulling a JSON library into this module just to read one boolean
     * is not worth it. The scan only accepts a boolean literal for `analytics_enabled`, so an HTML
     * error page or an unrelated payload yields `null` and leaves the local decision in charge.
     */
    internal fun parseDisabled(payload: String): Boolean? {
        val match = Regex("\"analytics_enabled\"\\s*:\\s*(true|false)").find(payload) ?: return null
        return match.groupValues[1] == "false"
    }
}
