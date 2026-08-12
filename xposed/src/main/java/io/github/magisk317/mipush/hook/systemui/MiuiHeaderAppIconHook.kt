package io.github.magisk317.mipush.hook.systemui

import android.app.Notification
import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.UserHandle
import android.widget.ImageView
import io.github.magisk317.mipush.common.XMSF_PACKAGE_NAME
import io.github.magisk317.mipush.common.notification.iconpack.ICON_PACK_SOURCE_IDENTITY_EXTRA
import io.github.magisk317.mipush.common.notification.iconpack.digestIdentity
import io.github.magisk317.mipush.common.notification.iconpack.thirdPartyPackSourceIdentity
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
        val postingPackage = resolvePostingPackage(expandedNotification)
        val userId = resolveUserId(expandedNotification)
        val isMockReplayReceipt = extras.getBoolean(EXTRA_MOCK_REPLAY_RECEIPT, false)
        val colorStatusBarIcon = IslandPreferences.current().colorStatusBarIcon
        // Evaluate the existing identity/scope gate before touching any replacement source. This
        // keeps non-MiPush, unresolved, non-XSpace, and XMSF notifications query-free.
        if (
            !MiuiHeaderAppIconPolicy.shouldReplace(
                userId = userId,
                targetPackage = targetPackage,
                postingPackage = postingPackage,
                hasReplacementIcon = true,
                isMockReplayReceipt = isMockReplayReceipt,
            )
        ) {
            return
        }

        val replacement = resolveReplacementDrawable(
            context = context,
            notification = notification,
            extras = extras,
            targetPackage = targetPackage,
            userId = userId,
        )
        if (
            !MiuiHeaderAppIconPolicy.shouldReplace(
                userId = userId,
                targetPackage = targetPackage,
                postingPackage = postingPackage,
                hasReplacementIcon = replacement.drawable != null,
                isMockReplayReceipt = isMockReplayReceipt,
            )
        ) {
            return
        }

        imageView.setImageDrawable(replacement.drawable)
        imageView.invalidate()
        logReplacement(targetPackage, userId, isMockReplayReceipt, colorStatusBarIcon, replacement.source)
    }

    private fun resolvePostingPackage(expandedNotification: Any): String? {
        return runCatching {
            expandedNotification.callMethod("getPackageName") as? String
        }.getOrNull()?.takeIf { it.isNotBlank() }
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
        userId: Int?,
    ): HeaderReplacement {
        // Notification.smallIcon is the only permitted header transport for a third-party pack.
        // The explicit identity marker prevents an ordinary/original smallIcon from being
        // mistaken for a successful third-party result.
        val passedSmallIcon = if (
            notification.smallIcon?.type == Icon.TYPE_BITMAP &&
            MiuiHeaderAppIconPolicy.isPassedThirdPartySmallIcon(
                sourceIdentity = extras.getString(ICON_PACK_SOURCE_IDENTITY_EXTRA),
                targetPackage = targetPackage,
            )
        ) {
            runCatching { notification.smallIcon?.loadDrawable(context) }.getOrNull()
        } else {
            null
        }
        if (passedSmallIcon != null) {
            return HeaderReplacement(MiuiHeaderAppIconSource.THIRD_PARTY_PACK, passedSmallIcon)
        }

        val targetAppIcon = resolveTargetAppIconDrawable(context, targetPackage, userId)
        val source = MiuiHeaderAppIconPolicy.selectReplacementSource(
            hasPassedThirdPartySmallIcon = false,
            hasTargetAppIcon = targetAppIcon != null,
        )
        return when (source) {
            MiuiHeaderAppIconSource.THIRD_PARTY_PACK ->
                HeaderReplacement(source, passedSmallIcon)
            MiuiHeaderAppIconSource.APP -> HeaderReplacement(source, targetAppIcon)
            MiuiHeaderAppIconSource.UNAVAILABLE -> HeaderReplacement(source, null)
        }
    }

    private fun resolveTargetAppIconDrawable(
        context: Context,
        packageName: String,
        userId: Int?,
    ): Drawable? {
        val lookupContext = if (userId == null) {
            context
        } else {
            resolveUserContext(context, userId) ?: return null
        }
        return runCatching {
            lookupContext.packageManager.getApplicationIcon(packageName)
        }.onFailure {
            XLog.e(
                TAG,
                "failed to resolve target app icon pkgDigest=${digestIdentity(packageName)}",
                it,
            )
        }.getOrNull()
    }

    private fun resolveUserContext(context: Context, userId: Int): Context? {
        if (userId == 0) return context
        return runCatching {
            val userHandleClass = UserHandle::class.java
            val userHandle = userHandleClass
                .getMethod("of", Int::class.javaPrimitiveType)
                .invoke(null, userId)
            Context::class.java
                .getMethod(
                    "createContextAsUser",
                    userHandleClass,
                    Int::class.javaPrimitiveType,
                )
                .invoke(context, userHandle, 0) as Context
        }.onFailure {
            XLog.e(
                TAG,
                "skip target app icon for unavailable notification user=" +
                    digestIdentity("user:$userId"),
                it,
            )
        }.getOrNull()
    }

    private fun logReplacement(
        targetPackage: String,
        userId: Int?,
        isMockReplayReceipt: Boolean,
        colorStatusBarIcon: Boolean,
        source: MiuiHeaderAppIconSource,
    ) {
        val mode = if (isMockReplayReceipt) {
            "mock-replay:${if (colorStatusBarIcon) "color" else "monochrome"}"
        } else {
            "xspace"
        }
        val packageDigest = digestIdentity(targetPackage)
        val userDigest = digestIdentity("user:${userId ?: -1}")
        val sourceDigest = digestIdentity(source.name)
        if (!loggedPackages.add("$mode#$packageDigest#$userDigest#$sourceDigest")) return
        XLog.i(
            TAG,
            "replaced MIUI header app icon mode=$mode sourceDigest=$sourceDigest " +
                "pkgDigest=$packageDigest userDigest=$userDigest",
        )
    }

    private companion object {
        private const val TAG = "MiuiHeaderAppIconHook"
        private const val EXTRA_MOCK_REPLAY_RECEIPT = "mipush_mock_replay_receipt"
        private const val EXTRA_MOCK_REPLAY_SOURCE_PACKAGE = "mipush_mock_replay_source_package"
        private val loggedPackages: MutableSet<String> = Collections.synchronizedSet(HashSet())
    }
}

private data class HeaderReplacement(
    val source: MiuiHeaderAppIconSource,
    val drawable: Drawable?,
)

internal enum class MiuiHeaderAppIconSource {
    THIRD_PARTY_PACK,
    APP,
    UNAVAILABLE,
}

internal object MiuiHeaderAppIconPolicy {
    private const val XSPACE_USER_ID = 999

    fun shouldReplace(
        userId: Int?,
        targetPackage: String?,
        postingPackage: String?,
        hasReplacementIcon: Boolean,
        isMockReplayReceipt: Boolean = false,
    ): Boolean {
        if (targetPackage.isNullOrBlank()) return false
        if (targetPackage == XMSF_PACKAGE_NAME) return false
        if (!hasReplacementIcon) return false
        if (isMockReplayReceipt) return true
        // A correctly delegated notification already has targetPackage as the SBN package, so
        // stock SystemUI can resolve and XSpace-badge its app icon. Override only the local-XMSF
        // fallback identity; otherwise a content largeIcon would replace a correct app header.
        return userId == XSPACE_USER_ID &&
            !postingPackage.isNullOrBlank() &&
            postingPackage != targetPackage
    }

    fun isPassedThirdPartySmallIcon(sourceIdentity: String?, targetPackage: String): Boolean =
        sourceIdentity == thirdPartyPackSourceIdentity(targetPackage)

    fun selectReplacementSource(
        hasPassedThirdPartySmallIcon: Boolean,
        hasTargetAppIcon: Boolean,
    ): MiuiHeaderAppIconSource = when {
        hasPassedThirdPartySmallIcon -> MiuiHeaderAppIconSource.THIRD_PARTY_PACK
        hasTargetAppIcon -> MiuiHeaderAppIconSource.APP
        else -> MiuiHeaderAppIconSource.UNAVAILABLE
    }
}
