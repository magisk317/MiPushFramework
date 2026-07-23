package io.github.magisk317.mipush.hook.amap

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

internal data class AmapDriveNotificationContent(
    val title: String,
    val content: String,
)

/**
 * Keeps the latest useful driving instruction emitted by AMap's drive live-view bridge.
 */
internal class AmapDriveLiveViewState {
    private var currentContent: AmapDriveNotificationContent? = null

    @Synchronized
    fun accept(dataType: Int, rawData: String?): AmapDriveNotificationContent? {
        when (dataType) {
            NAVIGATION_DATA_TYPE -> {
                parseNavigation(rawData)?.let { currentContent = it }
            }
            CLEAR_DATA_TYPE -> currentContent = null
        }
        return currentContent
    }

    @Synchronized
    fun current(): AmapDriveNotificationContent? = currentContent

    @Synchronized
    fun clear() {
        currentContent = null
    }

    private fun parseNavigation(rawData: String?): AmapDriveNotificationContent? {
        if (rawData.isNullOrBlank()) return null
        val payload = runCatching { Json.parseToJsonElement(rawData).jsonObject }.getOrNull()
            ?: return null
        val distance = payload.string("distance")
        if (distance.isEmpty()) return null

        val unit = payload.string("unit")
        val nextAction = payload.string("nextAction")
        val nextTarget = payload.string("nextTarget")
        val distanceText = distance + unit
        val title = listOf(distanceText, nextAction)
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        val content = nextTarget.ifEmpty { nextAction.ifEmpty { distanceText } }
        return AmapDriveNotificationContent(title = title, content = content)
    }

    private fun Map<String, JsonElement>.string(key: String): String {
        return (this[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    }

    private companion object {
        private const val NAVIGATION_DATA_TYPE = 3
        private const val CLEAR_DATA_TYPE = 6
    }
}
