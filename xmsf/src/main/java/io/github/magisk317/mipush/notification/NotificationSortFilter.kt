package io.github.magisk317.mipush.notification

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.xiaomi.xmpush.thrift.PushMetaInfo
import io.github.aakira.napier.Napier
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Filters notifications based on MIUI focus parameters.
 *
 * Based on stock xmsf 7.4.67 `com.xiaomi.push.sort` package.
 * Handles `miui.focus.param` JSON in notification extras:
 * - legacy root `updatable` / `reopen`
 * - HyperIsland ToolKit `param_v2.updatable` / `param_v2.reopen`
 * - Deleted focus notifications are cached and filtered for 24h.
 */
object NotificationSortFilter {

    private const val TAG = "NotificationSortFilter"
    private const val MAX_CACHE_SIZE = 50
    private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L
    private const val PREFS_NAME = "mipush_focus_deleted_notifications"
    private const val EXTRA_PACKAGE_NAME = "package_name"
    private const val EXTRA_NOTIFICATION_ID = "notification_id"
    private const val ACTION_FOCUS_DELETED = "io.github.magisk317.mipush.notification.FOCUS_DELETED"
    private val json = Json { ignoreUnknownKeys = true }

    private val deletedFocusCache = LinkedHashMap<String, Long>(MAX_CACHE_SIZE, 0.75f, true)
    private var persistentStore: DeletedFocusStore? = null
    private var loadedFromStore = false

    internal interface DeletedFocusStore {
        fun readAll(): Map<String, Long>
        fun put(key: String, expiresAtMs: Long)
        fun remove(key: String)
        fun clear()
    }

    /**
     * Check if this notification should be filtered out based on focus parameters.
     * Returns true if the notification should be dropped.
     */
    @JvmStatic
    fun shouldFilter(
        metaInfo: PushMetaInfo?,
        packageName: String,
        notificationId: Int,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean = shouldFilter(null, metaInfo, packageName, notificationId, nowMs)

    @JvmStatic
    fun shouldFilter(
        context: Context?,
        metaInfo: PushMetaInfo?,
        packageName: String,
        notificationId: Int,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val focusParam = metaInfo?.extra?.get("miui.focus.param")
        return shouldFilter(context, focusParam, packageName, notificationId, nowMs)
    }

    @JvmStatic
    fun shouldFilter(
        context: Context?,
        focusParam: String?,
        packageName: String,
        notificationId: Int,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val focus = parseFocusParam(focusParam) ?: return false
        if (!focus.updatable) return false

        val key = cacheKey(packageName, notificationId)
        synchronized(deletedFocusCache) {
            ensureLoadedLocked(context)
            pruneExpiredLocked(nowMs)
            if (focus.reopen != "close") {
                deletedFocusCache.remove(key)
                persistentStore?.remove(key)
                return false
            }
            val cached = deletedFocusCache.containsKey(key)
            if (cached) {
                Napier.d("filtering deleted focus notification key=$key", tag = TAG)
            }
            return cached
        }
    }

    /**
     * Record that a focus notification has been deleted/removed.
     * Should be called when a notification with focus parameters is cancelled.
     */
    @JvmStatic
    fun onFocusDeleted(packageName: String, notificationId: Int, nowMs: Long = System.currentTimeMillis()) {
        onFocusDeleted(null, packageName, notificationId, nowMs)
    }

    @JvmStatic
    fun onFocusDeleted(
        context: Context?,
        packageName: String,
        notificationId: Int,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val key = cacheKey(packageName, notificationId)
        synchronized(deletedFocusCache) {
            ensureLoadedLocked(context)
            pruneExpiredLocked(nowMs)
            val expiresAtMs = nowMs + CACHE_TTL_MS
            deletedFocusCache[key] = expiresAtMs
            persistentStore?.put(key, expiresAtMs)
            enforceMaxSizeLocked()
            Napier.d("recorded focus deletion key=$key cacheSize=${deletedFocusCache.size}", tag = TAG)
        }
    }

    @JvmStatic
    fun attachDeleteIntentIfNeeded(
        context: Context,
        builder: NotificationCompat.Builder,
        packageName: String,
        focusParam: String?,
        notificationId: Int
    ) {
        val focus = parseFocusParam(focusParam) ?: return
        if (!focus.updatable) return

        val key = cacheKey(packageName, notificationId)
        val intent = Intent(context, NotificationFocusDeleteReceiver::class.java).apply {
            action = ACTION_FOCUS_DELETED
            data = Uri.parse("mipush-focus-delete://$key")
            putExtra(EXTRA_PACKAGE_NAME, packageName)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        builder.setDeleteIntent(
            PendingIntent.getBroadcast(
                context,
                key.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    internal fun handleDeleteIntent(context: Context?, intent: Intent?, nowMs: Long = System.currentTimeMillis()) {
        if (intent?.action != ACTION_FOCUS_DELETED) return
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, Int.MIN_VALUE)
        if (notificationId == Int.MIN_VALUE) return
        onFocusDeleted(context, packageName, notificationId, nowMs)
    }

    internal fun resetForTest() {
        synchronized(deletedFocusCache) {
            deletedFocusCache.clear()
            persistentStore?.clear()
            persistentStore = null
            loadedFromStore = false
        }
    }

    internal fun clearMemoryCacheForTest() {
        synchronized(deletedFocusCache) {
            deletedFocusCache.clear()
            loadedFromStore = false
        }
    }

    internal fun installPersistentStoreForTest(store: DeletedFocusStore) {
        synchronized(deletedFocusCache) {
            deletedFocusCache.clear()
            persistentStore = store
            loadedFromStore = false
        }
    }

    internal fun parseFocusParamForTest(focusParam: String?): Pair<Boolean, String>? {
        return parseFocusParam(focusParam)?.let { it.updatable to it.reopen }
    }

    private fun parseFocusParam(focusParam: String?): FocusParam? {
        focusParam ?: return null
        return try {
            val root = json.parseToJsonElement(focusParam).jsonObject
            val payload = root["param_v2"]?.jsonObject ?: root
            val reopenPrimitive = payload["reopen"]?.jsonPrimitive
            FocusParam(
                updatable = payload["updatable"]?.jsonPrimitive?.booleanOrNull ?: false,
                reopen = when (reopenPrimitive?.booleanOrNull) {
                    true -> "reopen"
                    false -> "close"
                    null -> reopenPrimitive?.contentOrNull ?: "close"
                }
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun cacheKey(packageName: String, notificationId: Int): String = "$packageName:$notificationId"

    private fun ensureLoadedLocked(context: Context?) {
        if (persistentStore == null && context != null) {
            persistentStore = SharedPreferencesDeletedFocusStore(
                context.applicationContext ?: context
            )
        }
        if (loadedFromStore) return
        persistentStore?.let { store ->
            deletedFocusCache.putAll(store.readAll())
            enforceMaxSizeLocked()
            loadedFromStore = true
        }
    }

    private fun pruneExpiredLocked(nowMs: Long) {
        val iterator = deletedFocusCache.entries.iterator()
        val expiredKeys = mutableListOf<String>()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value <= nowMs) {
                expiredKeys.add(entry.key)
                iterator.remove()
            }
        }
        expiredKeys.forEach { persistentStore?.remove(it) }
    }

    private fun enforceMaxSizeLocked() {
        val iterator = deletedFocusCache.entries.iterator()
        while (deletedFocusCache.size > MAX_CACHE_SIZE && iterator.hasNext()) {
            val key = iterator.next().key
            iterator.remove()
            persistentStore?.remove(key)
        }
    }

    private data class FocusParam(val updatable: Boolean, val reopen: String)

    private class SharedPreferencesDeletedFocusStore(context: Context) : DeletedFocusStore {
        private val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        override fun readAll(): Map<String, Long> {
            return prefs.all.mapNotNull { (key, value) ->
                val expiresAtMs = when (value) {
                    is Long -> value
                    is Number -> value.toLong()
                    is String -> value.toLongOrNull()
                    else -> null
                } ?: return@mapNotNull null
                key to expiresAtMs
            }.toMap()
        }

        override fun put(key: String, expiresAtMs: Long) {
            prefs.edit().putLong(key, expiresAtMs).commit()
        }

        override fun remove(key: String) {
            prefs.edit().remove(key).commit()
        }

        override fun clear() {
            prefs.edit().clear().commit()
        }
    }
}

class NotificationFocusDeleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationSortFilter.handleDeleteIntent(context, intent)
    }
}
