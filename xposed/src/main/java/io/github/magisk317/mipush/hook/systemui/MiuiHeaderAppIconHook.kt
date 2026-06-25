package io.github.magisk317.mipush.hook.systemui

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.widget.ImageView
import io.github.magisk317.mipush.common.XMSF_PACKAGE_NAME
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.hook.island.IslandDispatchContract
import io.github.magisk317.mipush.hook.island.IslandPreferences
import io.github.magisk317.xposed.callMethod
import io.github.magisk317.xposed.findClass
import io.github.magisk317.xposed.hookMethod
import java.util.Collections

class MiuiHeaderAppIconHook {
    fun hook(classLoader: ClassLoader) {
        runCatching {
            val expandedNotificationClass =
                classLoader.findClass("com.android.systemui.statusbar.notification.ExpandedNotification")
            classLoader.findClass("com.android.systemui.statusbar.notification.utils.NotifImageUtil")
                .hookMethod(
                    "applyAppIconAllowCustom",
                    Context::class.java,
                    expandedNotificationClass,
                    ImageView::class.java,
                    Boolean::class.javaPrimitiveType!!,
                ) {
                    doAfter {
                        val context = args.getOrNull(0) as? Context ?: return@doAfter
                        val expandedNotification = args.getOrNull(1) ?: return@doAfter
                        val imageView = args.getOrNull(2) as? ImageView ?: return@doAfter
                        replaceHeaderIcon(context, expandedNotification, imageView)
                    }
                }
            XLog.i(TAG, "hooked NotifImageUtil.applyAppIconAllowCustom for MiPush XSpace header icons")
        }.onFailure {
            XLog.e(TAG, "install MiPush XSpace header icon hook failed: ${it.message}", it)
        }
    }

    private fun replaceHeaderIcon(
        context: Context,
        expandedNotification: Any,
        imageView: ImageView,
    ) {
        val notification = runCatching {
            expandedNotification.callMethod("getNotification") as? Notification
        }.getOrNull() ?: return
        val extras = notification.extras ?: return
        val targetPackage = resolveTargetPackage(extras) ?: return
        val userId = resolveUserId(expandedNotification)
        val isMockReplayReceipt = extras.getBoolean(EXTRA_MOCK_REPLAY_RECEIPT, false)
        val colorStatusBarIcon = IslandPreferences.current().colorStatusBarIcon
        val drawable = resolveReplacementDrawable(
            context = context,
            notification = notification,
            extras = extras,
            targetPackage = targetPackage,
            isMockReplayReceipt = isMockReplayReceipt,
            colorStatusBarIcon = colorStatusBarIcon,
        )
        if (
            !MiuiHeaderAppIconPolicy.shouldReplace(
                userId = userId,
                targetPackage = targetPackage,
                hasReplacementIcon = drawable != null,
                isMockReplayReceipt = isMockReplayReceipt,
            )
        ) {
            return
        }

        imageView.setImageDrawable(drawable)
        imageView.invalidate()
        logReplacement(targetPackage, userId, isMockReplayReceipt, colorStatusBarIcon)
    }

    private fun resolveTargetPackage(extras: Bundle): String? {
        return sequenceOf(
            extras.getString("target_package"),
            extras.getString("miui.targetPkg"),
            extras.getString("xmsf_target_package"),
            extras.getString(EXTRA_MOCK_REPLAY_SOURCE_PACKAGE),
            extras.getString(IslandDispatchContract.SOURCE_PACKAGE),
        ).firstOrNull { !it.isNullOrBlank() }
    }

    private fun resolveUserId(expandedNotification: Any): Int? {
        val user = runCatching {
            expandedNotification.callMethod("getUser") as? UserHandle
        }.getOrNull() ?: return null
        return runCatching {
            user.callMethod("getIdentifier") as? Int
        }.getOrNull()
    }

    private fun resolveReplacementDrawable(
        context: Context,
        notification: Notification,
        extras: Bundle,
        targetPackage: String,
        isMockReplayReceipt: Boolean,
        colorStatusBarIcon: Boolean,
    ): Drawable? {
        if (isMockReplayReceipt) {
            if (!colorStatusBarIcon) {
                return runCatching { notification.smallIcon?.loadDrawable(context) }.getOrNull()
            }
            resolveTargetAppIconDrawable(context, targetPackage)?.let { return it }
        }
        return resolveLargeIconDrawable(context, notification, extras)
    }

    private fun resolveTargetAppIconDrawable(context: Context, packageName: String): Drawable? {
        return runCatching {
            context.packageManager.getApplicationIcon(packageName)
        }.onFailure {
            XLog.e(TAG, "failed to resolve target app icon pkg=$packageName", it)
        }.getOrNull()
    }

    private fun resolveLargeIconDrawable(
        context: Context,
        notification: Notification,
        extras: Bundle,
    ): Drawable? {
        runCatching { notification.getLargeIcon()?.loadDrawable(context) }
            .getOrNull()
            ?.let { return it }
        @Suppress("DEPRECATION")
        notification.largeIcon?.let { return BitmapDrawable(context.resources, it) }
        val extraValue = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(EXTRA_LARGE_ICON, Any::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable(EXTRA_LARGE_ICON)
        }
        return when (extraValue) {
            is Icon -> runCatching { extraValue.loadDrawable(context) }.getOrNull()
            is Bitmap -> BitmapDrawable(context.resources, extraValue)
            else -> null
        }
    }

    private fun logReplacement(
        targetPackage: String,
        userId: Int?,
        isMockReplayReceipt: Boolean,
        colorStatusBarIcon: Boolean,
    ) {
        val mode = if (isMockReplayReceipt) {
            "mock-replay:${if (colorStatusBarIcon) "color" else "monochrome"}"
        } else {
            "xspace"
        }
        if (!loggedPackages.add("$mode#$targetPackage#${userId ?: -1}")) return
        XLog.i(TAG, "replaced MIUI header app icon mode=$mode pkg=$targetPackage userId=${userId ?: -1}")
    }

    private companion object {
        private const val TAG = "MiuiHeaderAppIconHook"
        private const val EXTRA_LARGE_ICON = "android.largeIcon"
        private const val EXTRA_MOCK_REPLAY_RECEIPT = "mipush_mock_replay_receipt"
        private const val EXTRA_MOCK_REPLAY_SOURCE_PACKAGE = "mipush_mock_replay_source_package"
        private val loggedPackages: MutableSet<String> = Collections.synchronizedSet(HashSet())
    }
}

internal object MiuiHeaderAppIconPolicy {
    private const val XSPACE_USER_ID = 999

    fun shouldReplace(
        userId: Int?,
        targetPackage: String?,
        hasReplacementIcon: Boolean,
        isMockReplayReceipt: Boolean = false,
    ): Boolean {
        if (targetPackage.isNullOrBlank()) return false
        if (targetPackage == XMSF_PACKAGE_NAME) return false
        if (!hasReplacementIcon) return false
        if (isMockReplayReceipt) return true
        return userId == XSPACE_USER_ID
    }
}
