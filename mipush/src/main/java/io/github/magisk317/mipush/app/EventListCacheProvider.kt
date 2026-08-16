package io.github.magisk317.mipush.app

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import io.github.magisk317.mipush.common.manager.ManagerEvent
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.feature.main.subpage.EventInfoForDisplay
import io.github.magisk317.mipush.manager.events.EventListCacheStoreRegistry
import io.github.magisk317.mipush.manager.events.EventListCacheSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.Date

/** Receives the XMSF health-cycle cache through a signature-protected Binder call. */
class EventListCacheProvider : ContentProvider() {
    override fun onCreate(): Boolean = context != null

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        if (method != EventListCacheSync.METHOD_PUT_DEFAULT) return failure("unsupported_method")
        if (arg != EventListCacheSync.DEFAULT_QUERY_KEY) return failure("unsupported_query")
        val raw = extras?.getString(EventListCacheSync.EXTRA_EVENTS_JSON)
            ?: return failure("missing_payload")
        if (raw.toByteArray(Charsets.UTF_8).size > EventListCacheSync.MAX_PAYLOAD_BYTES) {
            return failure("payload_too_large")
        }
        val events = runCatching { EventListCacheSync.decode(raw) }
            .getOrElse { return failure("invalid_payload") }
        if (events.size > EventListCacheSync.MAX_CACHE_EVENTS) return failure("too_many_events")

        val appContext = context ?: return failure("context_unavailable")
        return runCatching {
            val merged = runBlocking(Dispatchers.IO) {
                EventListCacheStoreRegistry.get(appContext).mergeAndPutCached(
                    queryKey = arg,
                    incoming = events.map { it.toDisplay() },
                )
            }
            logI("event cache handoff applied query=$arg events=${merged.size}")
            Bundle().apply {
                putBoolean(EventListCacheSync.RESULT_SUCCESS, true)
                putInt(EventListCacheSync.RESULT_EVENT_COUNT, merged.size)
            }
        }.getOrElse { error ->
            logW("event cache handoff apply failed error=${error.javaClass.simpleName}")
            failure(error.javaClass.simpleName)
        }
    }

    override fun getType(uri: Uri): String? = null
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private fun failure(reason: String) = Bundle().apply {
        putBoolean(EventListCacheSync.RESULT_SUCCESS, false)
        putString(EventListCacheSync.RESULT_ERROR, reason)
    }

    private fun ManagerEvent.toDisplay() = EventInfoForDisplay(
        id = id,
        packageName = packageName,
        configOptions = configOptions,
        channel = channel,
        receiveDate = Date(receiveDateMs),
        title = title,
        content = content,
        appName = appName,
        event = this,
    )
}
