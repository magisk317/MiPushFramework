package io.github.magisk317.mipush.hook.systemui

import android.graphics.Color
import android.os.Bundle
import android.service.notification.StatusBarNotification
import io.github.magisk317.mipush.common.island.IslandOptions
import io.github.magisk317.mipush.common.island.IslandVisualContract
import io.github.magisk317.mipush.hook.island.IslandDispatchContract
import io.github.magisk317.mipush.hook.island.IslandPreferences
import java.util.concurrent.ConcurrentHashMap

@Suppress("DEPRECATION")
internal object MiPushIslandVisualState {
    private const val DEFAULT_ACCENT = "#FF00C8FF"
    private val active = ConcurrentHashMap<String, IslandVisualSnapshot>()

    fun record(sbn: StatusBarNotification) {
        val extras = sbn.notification?.extras ?: return
        if (!isMiPushVisual(extras)) return
        val sourcePackage = extras.getString(IslandDispatchContract.SOURCE_PACKAGE)
            ?: sbn.packageName
        record(
            key = visualKey(sbn),
            sourcePackage = sourcePackage,
            extras = extras,
            options = IslandPreferences.current(sourcePackage, sbn.userId),
        )
    }

    internal fun recordForTest(
        key: String,
        sourcePackage: String,
        extras: Bundle,
        options: IslandOptions,
    ) {
        record(key, sourcePackage, extras, options)
    }

    private fun record(
        key: String,
        sourcePackage: String,
        extras: Bundle,
        options: IslandOptions,
    ) {
        if (!isMiPushVisual(extras)) return
        val accent = firstColor(extras) ?: DEFAULT_ACCENT
        active[key] = IslandVisualSnapshot(
            key = key,
            sourcePackage = sourcePackage,
            accentColor = accent,
            options = options,
            owner = extras.getString(IslandVisualContract.OWNER_KEY).orEmpty(),
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun remove(sbn: StatusBarNotification) {
        active.remove(visualKey(sbn))
    }

    internal fun visualKey(sbn: StatusBarNotification): String =
        sbn.key.takeIf { it.isNotBlank() }
            ?: fallbackVisualKey(sbn.userId, sbn.packageName, sbn.id, sbn.tag)

    internal fun fallbackVisualKey(userId: Int, packageName: String, notificationId: Int, tag: String?): String =
        "$userId|$packageName:$notificationId:${tag.orEmpty()}"

    /** Returns a snapshot only when the SystemUI view can be associated unambiguously. */
    fun current(): IslandVisualSnapshot? = active.values.singleOrNull()

    internal fun snapshotForKey(key: String): IslandVisualSnapshot? = active[key]

    fun clear() {
        active.clear()
    }

    internal fun isMiPushVisual(extras: Bundle): Boolean =
        extras.getInt(IslandVisualContract.VISUAL_VERSION_KEY, -1) == IslandVisualContract.VERSION &&
            extras.getString(IslandVisualContract.VISUAL_MARKER_KEY) == IslandVisualContract.VISUAL_MARKER &&
            extras.getString(IslandVisualContract.OWNER_KEY) == IslandVisualContract.MIPUSH_OWNER

    internal fun isRenderable(snapshot: IslandVisualSnapshot): Boolean =
        snapshot.owner == IslandVisualContract.MIPUSH_OWNER

    private fun firstColor(extras: Bundle): String? = sequenceOf(
        extras.getString(IslandVisualContract.HIGHLIGHT_COLOR_KEY),
        extras.getString(IslandVisualContract.GLOW_COLOR_KEY),
        extras.getString(IslandVisualContract.ISLAND_GLOW_COLOR_KEY),
    ).firstOrNull { value ->
        !value.isNullOrBlank() && runCatching { Color.parseColor(value) }.isSuccess
    }
}

internal data class IslandVisualSnapshot(
    val key: String,
    val sourcePackage: String,
    val accentColor: String,
    val options: IslandOptions,
    val owner: String,
    val updatedAt: Long,
)
