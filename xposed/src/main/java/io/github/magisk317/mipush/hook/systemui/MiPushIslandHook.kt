package io.github.magisk317.mipush.hook.systemui

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.app.NotificationManager
import android.service.notification.StatusBarNotification
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.hook.island.IslandDispatchContract
import io.github.magisk317.mipush.hook.island.IslandDispatcher
import io.github.magisk317.mipush.hook.island.IslandDispatcherHook
import io.github.magisk317.mipush.hook.island.IslandPreferences
import io.github.magisk317.mipush.hook.island.IslandRequest
import io.github.magisk317.mipush.xposed.currentApplication
import io.github.magisk317.mipush.xposed.findClass
import io.github.magisk317.mipush.xposed.hookMethod

class MiPushIslandHook {
    fun hook(classLoader: ClassLoader) {
        XLog.i(TAG, "install systemui island hook implementation=focus-extras-first")
        IslandPreferences.startRefreshLoop()
        IslandDispatcherHook().hook()
        hookGenerateInnerNotifBean(classLoader)
    }

    private fun hookGenerateInnerNotifBean(classLoader: ClassLoader) {
        runCatching {
            classLoader.findClass("com.miui.systemui.notification.MiuiBaseNotifUtil")
                .hookMethod("generateInnerNotifBean", StatusBarNotification::class.java) {
                    doBefore {
                        handleStatusBarNotification(args.firstOrNull() as? StatusBarNotification)
                    }
                }
            XLog.i(TAG, "hooked MiuiBaseNotifUtil.generateInnerNotifBean")
        }.onFailure {
            XLog.e(TAG, "hook generateInnerNotifBean failed: ${it.message}", it)
        }
    }

    private fun handleStatusBarNotification(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val extras = notification.extras ?: return
        if (extras.getBoolean(IslandDispatchContract.PROCESSED, false)) return
        if (extras.containsKey(IslandDispatchContract.FOCUS_PARAM)) return

        val sourcePackage = resolveSourcePackage(sbn, extras) ?: return
        if (!extras.getBoolean(EXTRA_ALLOW_PROXY, false)) {
            return
        }
        val options = IslandPreferences.current(sourcePackage)
        if (!options.canInjectFocusPayload) return
        val title = firstText(
            extras,
            Notification.EXTRA_TITLE,
            Notification.EXTRA_TITLE_BIG,
        ) ?: notification.tickerText?.toString() ?: return
        val content = firstText(
            extras,
            Notification.EXTRA_TEXT,
            Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_SUB_TEXT,
            Notification.EXTRA_INFO_TEXT,
        ) ?: title

        val context = currentApplication()?.applicationContext ?: return
        if (!options.showOriginalNotification) {
            runCatching {
                val manager = context.getSystemService(NotificationManager::class.java) ?: return@runCatching
                manager.cancel(sbn.tag, sbn.id)
                XLog.d(TAG, "dropped original notification pkg=$sourcePackage key=${sbn.key}")
            }.onFailure {
                XLog.w(TAG, "failed to cancel original notification: ${it.message}")
            }
        }
        val icon = resolveIcon(context, sourcePackage, notification, extras)
        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification.channelId
        } else {
            null
        }
        val proxyId = proxyNotificationId(sbn)
        if (recentProxyPosts.shouldSkip(dedupKeyFor(sbn))) return
        IslandDispatcher.post(
            context,
            IslandRequest(
                title = title,
                content = content,
                icon = icon,
                notificationId = proxyId,
                timeoutSecs = options.timeoutSecs,
                firstFloat = options.firstFloat,
                enableFloat = options.enableFloat,
                showNotification = options.showNotification,
                sourcePackage = sourcePackage,
                sourceChannelId = channelId,
                contentIntent = notification.contentIntent,
                isOngoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
                actions = notification.actions?.toList().orEmpty(),
                clearBeforePost = true,
            ),
        )
        XLog.d(TAG, "posted island proxy pkg=$sourcePackage id=${sbn.id}")
    }

    private fun resolveSourcePackage(sbn: StatusBarNotification, extras: Bundle): String? {
        val explicit = extras.getString("target_package")
            ?: extras.getString("miui.targetPkg")
            ?: extras.getString("xmsf_target_package")
            ?: extras.getString(IslandDispatchContract.SOURCE_PACKAGE)
        if (!explicit.isNullOrBlank()) return explicit
        return null
    }

    private fun firstText(extras: Bundle, vararg keys: String): String? {
        return keys.firstNotNullOfOrNull { key ->
            extras.getCharSequence(key)?.toString()?.takeIf { it.isNotBlank() }
        }
    }

    private fun resolveIcon(
        context: Context,
        packageName: String,
        notification: Notification,
        extras: Bundle,
    ): Icon {
        extractLargeIcon(notification, extras)?.let { return it }
        return runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            Icon.createWithBitmap(
                Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(1),
                    drawable.intrinsicHeight.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888,
                ).also { bitmap ->
                    val canvas = android.graphics.Canvas(bitmap)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                },
            )
        }.getOrElse {
            notification.smallIcon ?: Icon.createWithResource(context, android.R.drawable.sym_def_app_icon)
        }
    }

    private fun extractLargeIcon(notification: Notification, extras: Bundle): Icon? {
        runCatching { notification.getLargeIcon() }.getOrNull()?.let { return it }
        val value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(EXTRA_LARGE_ICON_KEY, Any::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable(EXTRA_LARGE_ICON_KEY)
        }
        return when (value) {
            is Icon -> value
            is Bitmap -> Icon.createWithBitmap(value)
            else -> null
        }
    }

    private fun proxyNotificationId(sbn: StatusBarNotification): Int {
        return IslandProxyNotificationIds.fromPackage(sbn.packageName)
    }

    private fun dedupKeyFor(sbn: StatusBarNotification): Int {
        val key = sbn.key
        if (!key.isNullOrBlank()) return key.hashCode()
        return (sbn.packageName.hashCode() xor sbn.id)
    }

    private companion object {
        private const val TAG = "MiPushIslandHook"
        private const val EXTRA_ALLOW_PROXY = "mipush_island_allow_proxy"
        private const val EXTRA_LARGE_ICON_KEY = "android.largeIcon"
        private const val PROXY_POST_DEDUPE_MS = 2_000L
        private val recentProxyPosts = IslandProxyPostTracker(PROXY_POST_DEDUPE_MS)
    }
}
