package io.github.magisk317.mipush.manager.events

import android.content.Context
import android.net.Uri
import android.os.Bundle
import io.github.magisk317.mipush.manager.application.ManagerEvent
import kotlinx.serialization.json.Json

/** Signed Binder handoff for the cache produced by the XMSF health cycle. */
object EventListCacheSync {
    const val AUTHORITY = "io.github.magisk317.mipush.event-cache"
    const val METHOD_PUT_DEFAULT = "put_default_event_cache"
    const val EXTRA_QUERY_KEY = "query_key"
    const val EXTRA_EVENTS_JSON = "events_json"
    const val RESULT_SUCCESS = "success"
    const val RESULT_EVENT_COUNT = "event_count"
    const val RESULT_ERROR = "error"
    const val DEFAULT_QUERY_KEY = "q=;p="
    const val MAX_CACHE_EVENTS = 200
    const val MAX_PAYLOAD_BYTES = 512 * 1024

    private val uri = Uri.parse("content://$AUTHORITY")

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(events: List<ManagerEvent>): String = json.encodeToString(events)

    fun decode(raw: String): List<ManagerEvent> =
        json.decodeFromString(raw)

    fun handoff(
        context: Context,
        queryKey: String,
        events: List<ManagerEvent>,
    ): EventListCacheHandoffResult {
        if (queryKey != DEFAULT_QUERY_KEY) {
            return EventListCacheHandoffResult(false, error = "unsupported_query")
        }
        val raw = encode(events)
        if (raw.toByteArray(Charsets.UTF_8).size > MAX_PAYLOAD_BYTES) {
            return EventListCacheHandoffResult(false, error = "payload_too_large")
        }
        val response = runCatching {
            context.contentResolver.call(
                uri,
                METHOD_PUT_DEFAULT,
                queryKey,
                Bundle().apply { putString(EXTRA_EVENTS_JSON, raw) },
            )
        }.getOrElse { error ->
            return EventListCacheHandoffResult(
                success = false,
                error = error.javaClass.simpleName,
            )
        } ?: return EventListCacheHandoffResult(false, error = "empty_response")

        return EventListCacheHandoffResult(
            success = response.getBoolean(RESULT_SUCCESS, false),
            eventCount = response.getInt(RESULT_EVENT_COUNT, 0),
            error = response.getString(RESULT_ERROR),
        )
    }
}

data class EventListCacheHandoffResult(
    val success: Boolean,
    val eventCount: Int = 0,
    val error: String? = null,
)
