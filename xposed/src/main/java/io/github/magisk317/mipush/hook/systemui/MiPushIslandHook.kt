package io.github.magisk317.mipush.hook.systemui
import io.github.magisk317.xposed.BaseHook
import io.github.magisk317.xposed.LoadParam

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.app.NotificationManager
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import io.github.magisk317.mipush.common.notification.NotificationContentSupport
import io.github.magisk317.mipush.notification.policy.NotificationClickFallbackContract
import io.github.magisk317.mipush.common.XMSF_PACKAGE_NAME
import io.github.magisk317.mipush.common.utils.ImgUtils
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.hook.island.IslandDispatchContract
import io.github.magisk317.mipush.hook.island.IslandDispatcher
import io.github.magisk317.mipush.hook.island.IslandPreferences
import io.github.magisk317.mipush.hook.island.IslandRequest
import io.github.magisk317.xposed.currentApplication
import io.github.magisk317.xposed.findClass
import io.github.magisk317.xposed.hook
import io.github.magisk317.xposed.hookMethod
import java.lang.reflect.Method

class MiPushIslandHook : BaseHook() {
    override fun onLoadPackage(param: LoadParam) {
        val classLoader = param.classLoader
        XLog.i(TAG, "install systemui island hook implementation=focus-extras-first")
        IslandPreferences.startRefreshLoop()
        hookGenerateInnerNotifBean(classLoader)
        hookNotificationRemoved(classLoader)
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

    @Suppress("DEPRECATION")
    private fun handleStatusBarNotification(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val extras = notification.extras ?: return
        sbn.let(MiPushIslandVisualState::record)
        if (extras.getBoolean(IslandDispatchContract.PROCESSED, false)) return
        if (IslandDispatchContract.hasNativeFocusPayload(extras)) {
            XLog.d(TAG, "preserve native focus payload pkg=${sbn.packageName} key=${sbn.key}")
            return
        }

        val sourcePackage = resolveSourcePackage(sbn, extras) ?: return
        if (!extras.getBoolean(EXTRA_ALLOW_PROXY, false)) {
            return
        }
        val options = IslandPreferences.current(sourcePackage, sbn.userId)
        if (!options.canInjectFocusPayload) return
        XLog.i(
            TAG,
            "handleNotification pkg=$sourcePackage showNotification=${options.showNotification} " +
                "enableFloat=${options.enableFloat} canInject=${options.canInjectFocusPayload}",
        )
        val title = NotificationContentSupport.firstText(
            extras,
            Notification.EXTRA_TITLE,
            Notification.EXTRA_TITLE_BIG,
        ) ?: notification.tickerText?.toString() ?: return
        val content = NotificationContentSupport.firstText(
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
        val channelId = notification.channelId
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
                userId = sbn.userId,
                sourceChannelId = channelId,
                contentIntent = resolveClickIntent(
                    context = context,
                    sourcePackage = sourcePackage,
                    notification = notification,
                    notificationId = sbn.id,
                    userId = sbn.userId,
                ),
                isOngoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
                actions = notification.actions?.toList().orEmpty(),
                clearBeforePost = true,
            ),
        )
        // Track source → proxy mapping so we can cancel proxy when source is removed.
        val sourceKey = sourceKeyFor(sbn)
        trackedForCancel.record(sourceKey, proxyId)
        XLog.d(TAG, "posted island proxy pkg=$sourcePackage id=${sbn.id} proxyId=$proxyId sourceKey=$sourceKey")
    }

    private fun resolveClickIntent(
        context: Context,
        sourcePackage: String,
        notification: Notification,
        notificationId: Int,
        userId: Int,
    ): android.app.PendingIntent? {
        val original = notification.contentIntent
        if (
            original == null ||
            original.creatorPackage != XMSF_PACKAGE_NAME ||
            !notification.extras.getBoolean(NotificationClickFallbackContract.USE_LAUNCHER_FALLBACK, false)
        ) {
            return original
        }

        val targetContext = IslandClickRouting.contextForUser(context, userId) ?: return original
        val launchIntent = runCatching {
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                `package` = sourcePackage
            }.let { launcherQuery ->
                val resolved = targetContext.packageManager.resolveActivity(launcherQuery, 0)
                    ?.activityInfo
                    ?.let { android.content.ComponentName(it.packageName, it.name) }
                    ?: return@runCatching null
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    component = resolved
                }
            }
        }.getOrNull() ?: return original
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return runCatching {
            android.app.PendingIntent.getActivity(
                targetContext,
                IslandClickRouting.requestCode(sourcePackage, notificationId, userId),
                launchIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE,
            )
        }.onFailure {
            XLog.w(TAG, "failed to create target launch PendingIntent pkg=$sourcePackage: ${it.message}")
        }.getOrNull() ?: original
    }

    private fun resolveSourcePackage(sbn: StatusBarNotification, extras: Bundle): String? {
        val explicit = extras.getString("target_package")
            ?: extras.getString("miui.targetPkg")
            ?: extras.getString("xmsf_target_package")
            ?: extras.getString(IslandDispatchContract.SOURCE_PACKAGE)
        if (!explicit.isNullOrBlank()) return explicit
        return null
    }

    private fun resolveIcon(
        context: Context,
        packageName: String,
        notification: Notification,
        extras: Bundle,
    ): Icon {
        // Monochrome mode must not feed multi-color TYPE_BITMAP logos into the island proxy
        // smallIcon. Prefer an already-white silhouette BITMAP or convert the app logo to white
        // alpha so SystemUI monochrome SRC_IN tint stays single-color.
        if (!IslandPreferences.current().colorStatusBarIcon) {
            monochromeStatusBarIcon(context, packageName, notification)?.let { return it }
        }
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
            notification.smallIcon ?: Icon.createWithResource("android", android.R.drawable.sym_def_app_icon)
        }
    }

    private fun monochromeStatusBarIcon(
        context: Context,
        packageName: String,
        notification: Notification,
    ): Icon? {
        val smallIcon = notification.smallIcon
        // MiPush monochrome posts already convert app logos to white-alpha BITMAP silhouettes.
        if (smallIcon != null && smallIcon.type == Icon.TYPE_BITMAP) {
            return smallIcon
        }
        return runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val raw = ImgUtils.drawableToBitmap(drawable)
            val white = ImgUtils.convertToTransparentAndWhite(raw)
            Icon.createWithBitmap(white)
        }.getOrNull() ?: smallIcon
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

    @Suppress("DEPRECATION")
    private fun proxyNotificationId(sbn: StatusBarNotification): Int {
        return IslandProxyNotificationIds.fromPackage(sbn.packageName, sbn.userId)
    }

    @Suppress("DEPRECATION")
    private fun dedupKeyFor(sbn: StatusBarNotification): Int {
        return IslandProxyDedupKeys.fromStatusBarKey(
            key = sbn.key,
            packageName = sbn.packageName,
            notificationId = sbn.id,
            tag = sbn.tag,
            userId = sbn.userId,
        )
    }

    @Suppress("DEPRECATION")
    private fun sourceKeyFor(sbn: StatusBarNotification): String {
        return IslandProxySourceKeys.fromStatusBarKey(
            key = sbn.key,
            packageName = sbn.packageName,
            notificationId = sbn.id,
            tag = sbn.tag,
            userId = sbn.userId,
        )
    }

    internal fun isNotificationRemovedCallback(method: Method): Boolean {
        return method.name == "onNotificationRemoved" &&
            method.parameterTypes.firstOrNull() == StatusBarNotification::class.java
    }

    private companion object {
        private const val TAG = "MiPushIslandHook"
        private const val EXTRA_ALLOW_PROXY = "mipush_island_allow_proxy"
        private const val EXTRA_LARGE_ICON_KEY = "android.largeIcon"
        private const val PROXY_POST_DEDUPE_MS = 2_000L
        private const val MAX_TRACKED_SIZE = 500
        private val SYSTEM_UI_NOTIFICATION_LISTENER_CLASSES = listOf(
            "com.android.systemui.statusbar.notification.MiuiNotificationListener",
            "com.android.systemui.statusbar.NotificationListener",
        )
        private val recentProxyPosts = IslandProxyPostTracker(PROXY_POST_DEDUPE_MS)
        private val trackedForCancel = IslandProxyOwnershipTracker(MAX_TRACKED_SIZE)
    }

    // -- Phase 5: cancel proxy notification when source notification is removed --

    private fun hookNotificationRemoved(classLoader: ClassLoader) {
        var hookedCount = 0
        for (className in SYSTEM_UI_NOTIFICATION_LISTENER_CLASSES) {
            runCatching {
                classLoader.findClass(className).declaredMethods
                    .filter(::isNotificationRemovedCallback)
                    .forEach { method ->
                        method.hook {
                            doAfter {
                                handleNotificationRemoved(
                                    thisObject as? NotificationListenerService,
                                    args.firstOrNull() as? StatusBarNotification,
                                )
                            }
                        }
                        hookedCount++
                    }
            }.onFailure {
                XLog.d(TAG, "skip notification removal target $className: ${it.message}")
            }
        }
        if (hookedCount > 0) {
            XLog.i(TAG, "hooked SystemUI notification removal callbacks count=$hookedCount")
            return
        }

        // Last-resort fallback for variants whose listener does not override the framework API.
        runCatching {
            classLoader.findClass("android.service.notification.NotificationListenerService")
                .declaredMethods
                .filter(::isNotificationRemovedCallback)
                .forEach { method ->
                    method.hook {
                        doAfter {
                            handleNotificationRemoved(thisObject as? NotificationListenerService, args.firstOrNull() as? StatusBarNotification)
                        }
                    }
                    hookedCount++
                }
            check(hookedCount > 0) { "no notification removal callback found" }
            XLog.i(TAG, "hooked framework notification removal fallback count=$hookedCount")
        }.onFailure {
            XLog.e(TAG, "notification removal hook failed: ${it.message}", it)
        }
    }

    @Suppress("DEPRECATION")
    private fun handleNotificationRemoved(
        listener: NotificationListenerService?,
        sbn: StatusBarNotification?,
    ) {
        sbn ?: return
        MiPushIslandVisualState.remove(sbn)
        val context = currentApplication()?.applicationContext ?: return
        val sourceKey = sourceKeyFor(sbn)
        val proxyId = trackedForCancel.removeAndResolveCancellation(sourceKey)
        if (proxyId != null) {
            IslandDispatcher.cancel(context, proxyId, sbn.userId)
            XLog.d(TAG, "cancelled proxy proxyId=$proxyId for removed source key=$sourceKey")
        }
        // Native Live Update path never enters trackedForCancel (allowIslandProxy=false). When the
        // shade row is dismissed, force-cancel the same notification so the promoted island dies.
        cancelNativeLiveUpdateIfNeeded(context, listener, sbn)
    }

    private fun cancelNativeLiveUpdateIfNeeded(
        context: Context,
        listener: NotificationListenerService?,
        sbn: StatusBarNotification,
    ) {
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return
        val promotedFlag = runCatching {
            Notification::class.java.getField("FLAG_PROMOTED_ONGOING").getInt(null)
        }.getOrDefault(0)
        val isLiveUpdate = extras.getBoolean("xmsf.live_update", false) ||
            extras.getBoolean("android.requestPromotedOngoing", false) ||
            (promotedFlag != 0 && (notification.flags and promotedFlag) != 0)
        if (!isLiveUpdate) return
        val relatedToMipush = sbn.packageName == "com.xiaomi.xmsf" ||
            !extras.getString("target_package").isNullOrBlank() ||
            !extras.getString("miui.targetPkg").isNullOrBlank() ||
            !extras.getString("xmsf_target_package").isNullOrBlank()
        if (!relatedToMipush) return

        // Prefer NLS cancelNotification(key) — it asks NMS to remove the real posting package entry
        // (works for both target-package identity and local xmsf fallback). SystemUI's own
        // NotificationManager.cancel(tag,id) would only cancel SystemUI-owned posts.
        val cancelledViaListener = runCatching {
            if (listener == null) return@runCatching false
            listener.cancelNotification(sbn.key)
            true
        }.getOrDefault(false)
        val targetPackage = extras.getString("target_package")
            ?: extras.getString("miui.targetPkg")
            ?: extras.getString("xmsf_target_package")
            ?: sbn.packageName
        XLog.d(
            TAG,
            "native live-update remove sync pkg=${sbn.packageName} target=$targetPackage " +
                "tag=${sbn.tag} id=${sbn.id} key=${sbn.key} listenerCancel=$cancelledViaListener",
        )
    }
}
