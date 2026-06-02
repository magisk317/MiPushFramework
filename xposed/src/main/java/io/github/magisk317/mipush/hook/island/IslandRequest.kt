package io.github.magisk317.mipush.hook.island

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import io.github.magisk317.mipush.common.NotificationStyle

data class IslandRequest(
    val title: String,
    val content: String,
    val icon: Icon? = null,
    val notificationId: Int = IslandDispatchContract.DEFAULT_NOTIFICATION_ID,
    val timeoutSecs: Int = 5,
    val firstFloat: Boolean = true,
    val enableFloat: Boolean = true,
    val showNotification: Boolean = true,
    val sourcePackage: String? = null,
    val sourceChannelId: String? = null,
    val contentIntent: PendingIntent? = null,
    val isOngoing: Boolean = false,
    val actions: List<Notification.Action> = emptyList(),
    val showIslandIcon: Boolean = true,
    val clearBeforePost: Boolean = false,
    val highlightColor: String? = null,
    val islandOuterGlow: Boolean = false,
    val style: NotificationStyle? = null,
    val smallOnly: Boolean = false,
) {
    fun toBundle(): Bundle = Bundle().apply {
        putString(KEY_TITLE, title)
        putString(KEY_CONTENT, content)
        putParcelable(KEY_ICON, icon)
        putInt(KEY_NOTIFICATION_ID, notificationId)
        putInt(KEY_TIMEOUT_SECS, timeoutSecs)
        putBoolean(KEY_FIRST_FLOAT, firstFloat)
        putBoolean(KEY_ENABLE_FLOAT, enableFloat)
        putBoolean(KEY_SHOW_NOTIFICATION, showNotification)
        putString(KEY_SOURCE_PACKAGE, sourcePackage)
        putString(KEY_SOURCE_CHANNEL_ID, sourceChannelId)
        putParcelable(KEY_CONTENT_INTENT, contentIntent)
        putBoolean(KEY_ONGOING, isOngoing)
        putBoolean(KEY_SHOW_ISLAND_ICON, showIslandIcon)
        putBoolean(KEY_CLEAR_BEFORE_POST, clearBeforePost)
        putString(KEY_HIGHLIGHT_COLOR, highlightColor)
        putBoolean(KEY_ISLAND_OUTER_GLOW, islandOuterGlow)
        putString(KEY_STYLE, style?.name)
        putBoolean(KEY_SMALL_ONLY, smallOnly)
        if (actions.isNotEmpty()) {
            putParcelableArray(KEY_ACTIONS, actions.toTypedArray())
        }
    }

    companion object {
        private const val KEY_TITLE = "title"
        private const val KEY_CONTENT = "content"
        private const val KEY_ICON = "icon"
        private const val KEY_NOTIFICATION_ID = "notificationId"
        private const val KEY_TIMEOUT_SECS = "timeoutSecs"
        private const val KEY_FIRST_FLOAT = "firstFloat"
        private const val KEY_ENABLE_FLOAT = "enableFloat"
        private const val KEY_SHOW_NOTIFICATION = "showNotification"
        private const val KEY_SOURCE_PACKAGE = "sourcePackage"
        private const val KEY_SOURCE_CHANNEL_ID = "sourceChannelId"
        private const val KEY_CONTENT_INTENT = "contentIntent"
        private const val KEY_ONGOING = "isOngoing"
        private const val KEY_ACTIONS = "actions"
        private const val KEY_SHOW_ISLAND_ICON = "showIslandIcon"
        private const val KEY_CLEAR_BEFORE_POST = "clearBeforePost"
        private const val KEY_HIGHLIGHT_COLOR = "highlightColor"
        private const val KEY_ISLAND_OUTER_GLOW = "islandOuterGlow"
        private const val KEY_STYLE = "style"
        private const val KEY_SMALL_ONLY = "smallOnly"

        fun fromIntent(intent: Intent): IslandRequest {
            return fromBundle(intent.extras ?: Bundle())
        }

        fun fromBundle(bundle: Bundle): IslandRequest {
            return IslandRequest(
                title = bundle.getString(KEY_TITLE).orEmpty(),
                content = bundle.getString(KEY_CONTENT).orEmpty(),
                icon = bundle.parcelable(KEY_ICON, Icon::class.java),
                notificationId = bundle.getInt(
                    KEY_NOTIFICATION_ID,
                    IslandDispatchContract.DEFAULT_NOTIFICATION_ID,
                ),
                timeoutSecs = bundle.getInt(KEY_TIMEOUT_SECS, 5),
                firstFloat = bundle.getBoolean(KEY_FIRST_FLOAT, true),
                enableFloat = bundle.getBoolean(KEY_ENABLE_FLOAT, true),
                showNotification = bundle.getBoolean(KEY_SHOW_NOTIFICATION, true),
                sourcePackage = bundle.getString(KEY_SOURCE_PACKAGE),
                sourceChannelId = bundle.getString(KEY_SOURCE_CHANNEL_ID),
                contentIntent = bundle.parcelable(KEY_CONTENT_INTENT, PendingIntent::class.java),
                isOngoing = bundle.getBoolean(KEY_ONGOING, false),
                actions = bundle.actions(),
                showIslandIcon = bundle.getBoolean(KEY_SHOW_ISLAND_ICON, true),
                clearBeforePost = bundle.getBoolean(KEY_CLEAR_BEFORE_POST, false),
                highlightColor = bundle.getString(KEY_HIGHLIGHT_COLOR),
                islandOuterGlow = bundle.getBoolean(KEY_ISLAND_OUTER_GLOW, false),
                style = bundle.getString(KEY_STYLE)?.let { styleName ->
                    runCatching { NotificationStyle.valueOf(styleName) }.getOrNull()
                },
                smallOnly = bundle.getBoolean(KEY_SMALL_ONLY, false),
            )
        }

        private fun Bundle.actions(): List<Notification.Action> {
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    getParcelableArray(KEY_ACTIONS, Notification.Action::class.java)?.toList()
                        ?: emptyList()
                } else {
                    @Suppress("DEPRECATION")
                    getParcelableArray(KEY_ACTIONS)?.filterIsInstance<Notification.Action>()
                        ?: emptyList()
                }
            } catch (_: Throwable) {
                emptyList()
            }
        }

        private fun <T> Bundle.parcelable(key: String, clazz: Class<T>): T? {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getParcelable(key, clazz)
            } else {
                @Suppress("DEPRECATION")
                getParcelable(key)
            }
        }
    }
}
