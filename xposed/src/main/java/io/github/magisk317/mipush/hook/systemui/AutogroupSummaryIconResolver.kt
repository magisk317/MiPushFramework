package io.github.magisk317.mipush.hook.systemui

import android.content.Context
import android.graphics.drawable.Icon
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import android.view.View
import android.widget.ImageView
import java.util.LinkedHashMap
import io.github.magisk317.mipush.common.notification.NotificationOwnerResolver
import io.github.magisk317.mipush.common.notification.StatusBarMonochromeIconPolicy
import io.github.magisk317.xposed.callMethod
import io.github.magisk317.xposed.logging.XLog

internal data class AutogroupChildGlyphResolution(
    val childStateKnown: Boolean,
    val hasMonochromeChild: Boolean,
    val icon: Icon?,
    val source: String?,
)

/** Resolves and tracks the status-bar-only child glyph override for synthetic auto-group summaries. */
internal class AutogroupSummaryIconResolver(
    private val statusBarNotificationFromEntry: (Any?) -> StatusBarNotification?,
    private val notificationUserId: (StatusBarNotification) -> Int?,
    private val isSystemApplication: (Context?, String?) -> Boolean,
) {
    companion object {
        private const val TAG = "HookSystemUI"
        private const val OVERRIDE_TTL_MS = 15_000L
        private const val MAX_OVERRIDES = 128
    }

    private data class AppliedOverride(
        val icon: Icon,
        val expiresAtElapsedMs: Long,
    )

    private data class ChildNotification(
        val sbn: StatusBarNotification,
        val notification: android.app.Notification,
    )

    private data class SafeGlyphResolution(
        val inspectionKnown: Boolean,
        val icon: Icon?,
    )

    private class GroupChildrenReadFailure(val reason: String) : RuntimeException(reason)

    private val overrideLock = Any()
    private val overrides = LinkedHashMap<String, AppliedOverride>(32, 0.75f, true)

    /**
     * Group-key dimension: managed children record the silhouette actually rendered for their
     * group, so an unmanaged summary can borrow it without depending on HyperOS view-model
     * reflection (attach-state chains differ across ROM builds and may be unreadable).
     */
    private val groupGlyphs = LinkedHashMap<String, AppliedOverride>(32, 0.75f, true)

    fun clearAll() {
        synchronized(overrideLock) { overrides.clear() }
    }

    fun clear(sbn: StatusBarNotification) {
        val key = statusBarNotificationKey(sbn) ?: return
        synchronized(overrideLock) { overrides.remove(key) }
    }

    fun overrideIconOrNull(sbn: StatusBarNotification): Icon? {
        val key = statusBarNotificationKey(sbn) ?: return null
        val now = SystemClock.elapsedRealtime()
        return synchronized(overrideLock) {
            removeExpiredOverrides(now)
            overrides[key]?.icon
        }
    }

    fun rememberOverride(key: String, icon: Icon) {
        val now = SystemClock.elapsedRealtime()
        synchronized(overrideLock) {
            removeExpiredOverrides(now)
            overrides[key] = AppliedOverride(icon, now + OVERRIDE_TTL_MS)
            while (overrides.size > MAX_OVERRIDES) {
                val oldestKey = overrides.keys.firstOrNull() ?: break
                overrides.remove(oldestKey)
            }
        }
    }

    /** Records the single-color glyph rendered for a notification group's managed children. */
    fun rememberGroupGlyph(groupKey: String, icon: Icon) {
        val now = SystemClock.elapsedRealtime()
        synchronized(overrideLock) {
            removeExpiredOverrides(now)
            groupGlyphs[groupKey] = AppliedOverride(icon, now + OVERRIDE_TTL_MS)
            while (groupGlyphs.size > MAX_OVERRIDES) {
                val oldestKey = groupGlyphs.keys.firstOrNull() ?: break
                groupGlyphs.remove(oldestKey)
            }
        }
    }

    /** Cached group glyph recorded from a managed child of the same [groupKey], if fresh. */
    fun groupGlyphOrNull(groupKey: String): Icon? {
        val now = SystemClock.elapsedRealtime()
        return synchronized(overrideLock) {
            removeExpiredOverrides(now)
            groupGlyphs[groupKey]?.icon
        }
    }

    /** Positive grayscale plus safe alpha margins is required to preserve a summary's own glyph. */
    fun postedMonochromeGlyphSafety(context: Context, icon: Icon?): Boolean? {
        if (icon == null) return false
        val isGrayscale = PostedGlyphMonochromeProbe.detect(context, icon) ?: return null
        if (!isGrayscale) return false
        return StatusBarMonochromeIconPolicy.isStatusSafeGlyph(context, icon)
    }

    fun applyFrameworkAutogroupSummaryIcon(
        iconView: View,
        sbn: StatusBarNotification,
        thirdPartyIconPack: Boolean,
        systemAppIconPack: Boolean,
    ) {
        val notification = sbn.notification ?: return
        if (!SystemUiNotificationPolicy.isSyntheticAutogroupSummaryNotification(notification.flags)) {
            return
        }
        val imageView = iconView as? ImageView ?: return
        val context = imageView.context
        val childOverride = overrideIconOrNull(sbn)
        if (childOverride != null) {
            val drawable = runCatching { childOverride.loadDrawable(context) }.getOrNull()
            if (drawable != null) {
                imageView.setImageDrawable(drawable)
                imageView.invalidate()
                XLog.d(TAG, "setIcon applied autogroup child glyph")
                return
            }
            clear(sbn)
            XLog.d(TAG, "setIcon autogroup child glyph unavailable")
        }
        val owner = NotificationOwnerResolver.resolve(
            sbn.packageName,
            notification.extras,
        )
        val userId = notificationUserId(sbn) ?: return
        fun iconPackIcon(packageName: String): Icon? =
            StatusBarMonochromeIconPolicy.preferredMonochromeIconForPackageOrNull(
                context = context,
                packageName = packageName,
                userId = userId,
            )
        val isMiPushManaged = SystemUiNotificationPolicy.isMiPushManagedNotification(
            notification.extras,
        )
        var icon: Icon? = iconPackIcon(owner)
        if (icon == null && owner != sbn.packageName) {
            icon = iconPackIcon(sbn.packageName)
        }
        if (icon == null && !isMiPushManaged) {
            val shouldInspectPostedGlyph =
                SystemUiNotificationPolicy.shouldInspectPostedGlyphForMonochrome(
                    thirdPartyIconPack = thirdPartyIconPack,
                    systemAppIconPack = systemAppIconPack,
                    isMiPushManaged = false,
                    uid = sbn.uid,
                    isSystemApp = isSystemApplication(context, sbn.packageName),
                    postedPackageName = sbn.packageName,
                )
            if (shouldInspectPostedGlyph && postedMonochromeGlyphSafety(context, notification.smallIcon) == true) {
                XLog.d(TAG, "setIcon preserved autogroup posted glyph")
                return
            }
        }
        fun silhouetteIcon(packageName: String): Icon? =
            StatusBarMonochromeIconPolicy.whiteIconForPackageOrNull(
                context = context,
                packageName = packageName,
            )
        if (icon == null && isMiPushManaged) {
            icon = silhouetteIcon(owner)
                ?: if (owner != sbn.packageName) {
                    silhouetteIcon(sbn.packageName)
                } else {
                    null
                }
        }
        if (icon == null && isMiPushManaged) {
            icon = StatusBarMonochromeIconPolicy.frameworkAndroidLogoIconOrNull()
        }
        if (icon == null) {
            XLog.d(TAG, "setIcon preserved autogroup summary native managed=$isMiPushManaged")
            return
        }
        val drawable = runCatching { icon.loadDrawable(context) }.getOrNull() ?: return
        imageView.setImageDrawable(drawable)
        imageView.invalidate()
        XLog.d(TAG, "setIcon applied autogroup summary icon")
    }

    fun resolveChildGlyph(
        entry: Any,
        summarySbn: StatusBarNotification,
        context: Context,
        thirdPartyIconPack: Boolean,
        systemAppIconPack: Boolean,
    ): AutogroupChildGlyphResolution {
        val children = groupChildrenForSummary(entry, summarySbn)
        if (children == null) {
            XLog.d(TAG, "autogroup child lookup unknown")
            return unknownChildGlyphResolution()
        }
        if (children.isEmpty()) {
            return AutogroupChildGlyphResolution(
                childStateKnown = true,
                hasMonochromeChild = false,
                icon = null,
                source = null,
            )
        }

        var hasMonochromeChild = false
        var replacementIcon: Icon? = null
        var source: String? = null
        for (child in children) {
            val childSbn = child.sbn
            val childNotification = child.notification
            val childPackage = childSbn.packageName.takeIf { it.isNotBlank() }
                ?: return unknownChildGlyphResolution()
            val childOwner = NotificationOwnerResolver.resolve(
                childPackage,
                childNotification.extras,
            )
            val isMiPushManaged = SystemUiNotificationPolicy.isMiPushManagedNotification(
                childNotification.extras,
            )
            val isSystemApp = isSystemApplication(context, childPackage)
            val childUserId = notificationUserId(childSbn) ?: return unknownChildGlyphResolution()
            val attemptsIconPack = SystemUiNotificationPolicy.shouldAttemptIconPackSubstitution(
                thirdPartyIconPack = thirdPartyIconPack,
                systemAppIconPack = systemAppIconPack,
                isMiPushManaged = isMiPushManaged,
                uid = childSbn.uid,
                isSystemApp = isSystemApp,
            )
            val childPackIcon = if (attemptsIconPack) {
                StatusBarMonochromeIconPolicy.preferredMonochromeIconForPackageOrNull(
                    context, childOwner, childUserId,
                )
                    ?: if (childOwner != childPackage) {
                        StatusBarMonochromeIconPolicy.preferredMonochromeIconForPackageOrNull(
                            context, childPackage, childUserId,
                        )
                    } else {
                        null
                    }
            } else {
                null
            }
            if (childPackIcon != null) {
                hasMonochromeChild = true
                if (replacementIcon == null) {
                    val safePackIcon = resolveSafeStatusGlyph(context, childPackIcon)
                    if (!safePackIcon.inspectionKnown) {
                        XLog.d(TAG, "autogroup child glyph unknown source=iconpack")
                        return unknownChildGlyphResolution()
                    }
                    if (safePackIcon.icon != null) {
                        replacementIcon = safePackIcon.icon
                        source = "iconpack"
                    }
                }
            }

            if (isMiPushManaged) {
                hasMonochromeChild = true
                if (replacementIcon == null) {
                    val silhouette = StatusBarMonochromeIconPolicy.whiteIconForPackageOrNull(
                        context = context,
                        packageName = childOwner,
                    ) ?: if (childOwner != childPackage) {
                        StatusBarMonochromeIconPolicy.whiteIconForPackageOrNull(
                            context = context,
                            packageName = childPackage,
                        )
                    } else {
                        null
                    }
                    if (silhouette != null) {
                        val safeSilhouette = resolveSafeStatusGlyph(context, silhouette)
                        if (!safeSilhouette.inspectionKnown) {
                            XLog.d(TAG, "autogroup child glyph unknown source=silhouette")
                            return unknownChildGlyphResolution()
                        }
                        if (safeSilhouette.icon != null) {
                            replacementIcon = safeSilhouette.icon
                            source = "silhouette"
                        }
                    }
                }
                continue
            }

            val shouldInspectPostedGlyph =
                SystemUiNotificationPolicy.shouldInspectPostedGlyphForMonochrome(
                    thirdPartyIconPack = thirdPartyIconPack,
                    systemAppIconPack = systemAppIconPack,
                    isMiPushManaged = false,
                    uid = childSbn.uid,
                    isSystemApp = isSystemApp,
                    postedPackageName = childPackage,
                )
            if (!shouldInspectPostedGlyph) continue
            val postedGlyph = childNotification.smallIcon ?: continue
            when (PostedGlyphMonochromeProbe.detect(context, postedGlyph)) {
                null -> {
                    XLog.d(TAG, "autogroup child glyph unknown source=posted")
                    return unknownChildGlyphResolution()
                }
                false -> Unit
                true -> {
                    hasMonochromeChild = true
                    if (replacementIcon == null) {
                        val safePostedGlyph = resolveSafeStatusGlyph(context, postedGlyph)
                        if (!safePostedGlyph.inspectionKnown) {
                            XLog.d(TAG, "autogroup child glyph unknown source=posted")
                            return unknownChildGlyphResolution()
                        }
                        if (safePostedGlyph.icon != null) {
                            replacementIcon = safePostedGlyph.icon
                            source = "posted-monochrome"
                        }
                    }
                }
            }
        }
        return AutogroupChildGlyphResolution(
            childStateKnown = true,
            hasMonochromeChild = hasMonochromeChild,
            icon = replacementIcon,
            source = source,
        )
    }

    /**
     * Reads the summary's exact ListEntry parent and its direct GroupEntry children. Reflection
     * failure, a non-entry child, or a group/user mismatch is unknown and therefore fails closed.
     */
    private fun groupChildrenForSummary(
        entry: Any,
        summarySbn: StatusBarNotification,
    ): List<ChildNotification>? {
        val children = try {
            val summaryKey = runCatching { summarySbn.key }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: throw GroupChildrenReadFailure("summary-key")
            val summaryGroupKey = runCatching { summarySbn.groupKey }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: throw GroupChildrenReadFailure("group-key")
            val summaryUserId = notificationUserId(summarySbn)
                ?: throw GroupChildrenReadFailure("summary-user")
            val attachState = runCatching { entry.callMethod("getAttachState") }.getOrNull()
                ?: throw GroupChildrenReadFailure("attach-state")
            val parent = runCatching { attachState.callMethod("getParent") }.getOrNull()
                ?: throw GroupChildrenReadFailure("parent")
            val rawChildren = runCatching { parent.callMethod("getChildren") as? List<*> }.getOrNull()
                ?: throw GroupChildrenReadFailure("children")
            if (rawChildren.isEmpty()) {
                XLog.d(TAG, "autogroup child lookup empty")
                emptyList()
            } else {
                val result = ArrayList<ChildNotification>(rawChildren.size)
                for (childEntry in rawChildren) {
                    if (childEntry == null) throw GroupChildrenReadFailure("null-child")
                    val childSbn = statusBarNotificationFromEntry(childEntry)
                        ?: throw GroupChildrenReadFailure("child-entry")
                    val childNotification = childSbn.notification
                        ?: throw GroupChildrenReadFailure("child-notification")
                    val childKey = runCatching { childSbn.key }.getOrNull()
                        ?: throw GroupChildrenReadFailure("child-key")
                    val childGroupKey = runCatching { childSbn.groupKey }.getOrNull()
                        ?: throw GroupChildrenReadFailure("child-group-key")
                    if (childKey == summaryKey) throw GroupChildrenReadFailure("summary-in-children")
                    if (childGroupKey != summaryGroupKey) throw GroupChildrenReadFailure("group-mismatch")
                    if (notificationUserId(childSbn) != summaryUserId) {
                        throw GroupChildrenReadFailure("user-mismatch")
                    }
                    result += ChildNotification(childSbn, childNotification)
                }
                result
            }
        } catch (failure: GroupChildrenReadFailure) {
            XLog.d(TAG, "autogroup child lookup failed reason=${failure.reason}")
            null
        }
        return children
    }

    private fun resolveSafeStatusGlyph(context: Context, icon: Icon): SafeGlyphResolution {
        return when (StatusBarMonochromeIconPolicy.isStatusSafeGlyph(context, icon)) {
            true -> SafeGlyphResolution(inspectionKnown = true, icon = icon)
            false -> SafeGlyphResolution(
                inspectionKnown = true,
                icon = StatusBarMonochromeIconPolicy.verifiedStatusSafeGlyphOrNull(context, icon),
            )
            null -> SafeGlyphResolution(inspectionKnown = false, icon = null)
        }
    }

    private fun unknownChildGlyphResolution() =
        AutogroupChildGlyphResolution(
            childStateKnown = false,
            hasMonochromeChild = false,
            icon = null,
            source = null,
        )

    fun overrideKeyOrNull(sbn: StatusBarNotification): String? = statusBarNotificationKey(sbn)

    private fun statusBarNotificationKey(sbn: StatusBarNotification): String? =
        runCatching { sbn.key }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun removeExpiredOverrides(nowElapsedMs: Long) {
        val iterator = overrides.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value.expiresAtElapsedMs <= nowElapsedMs) iterator.remove()
        }
    }
}
