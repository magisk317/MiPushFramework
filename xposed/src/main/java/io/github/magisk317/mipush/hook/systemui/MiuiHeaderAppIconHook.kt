package io.github.magisk317.mipush.hook.systemui

import android.app.Notification
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.widget.ImageView
import io.github.magisk317.mipush.common.XMSF_PACKAGE_NAME
import io.github.magisk317.mipush.common.island.IslandOptions
import io.github.magisk317.mipush.common.notification.StatusBarMonochromeIconPolicy
import io.github.magisk317.mipush.common.notification.iconpack.digestIdentity
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.mipush.hook.island.IslandDispatchContract
import io.github.magisk317.mipush.hook.island.IslandPreferences
import io.github.magisk317.xposed.callMethod
import io.github.magisk317.xposed.findClass
import io.github.magisk317.xposed.hookAllMethods
import io.github.magisk317.xposed.hookMethod
import java.util.Collections
import java.util.WeakHashMap

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
                    doBefore {
                        val imageView = args.getOrNull(2) as? ImageView
                        // A new bind is starting, so whatever this module claimed for the view is
                        // stale: a recycled row must never be pinned to another notification.
                        imageView?.let { ShadeHeaderIconClaims.invalidate(it) }
                    }
                    doAfter {
                        val context = args.getOrNull(0) as? Context ?: return@doAfter
                        val expandedNotification = args.getOrNull(1) ?: return@doAfter
                        val imageView = args.getOrNull(2) as? ImageView ?: return@doAfter
                        replaceHeaderIcon(context, expandedNotification, imageView)
                    }
                }
            XLog.i(TAG, "hooked NotifImageUtil.applyAppIconAllowCustom for MiPush XSpace header icons")
            hookShadeHeaderIconDefense(classLoader)
        }.onFailure {
            XLog.e(TAG, "install MiPush XSpace header icon hook failed: ${it.message}", it)
        }
    }

    private fun replaceHeaderIcon(
        context: Context,
        expandedNotification: Any,
        imageView: ImageView,
    ) {
        clearShadeIconTint(imageView)
        val notification = runCatching {
            expandedNotification.callMethod("getNotification") as? Notification
        }.getOrNull() ?: return
        val extras = notification.extras ?: return
        val postingPackage = resolvePostingPackage(expandedNotification)
        val explicitTargetPackage = resolveTargetPackage(extras)
        val ownerPackage = explicitTargetPackage ?: postingPackage ?: return
        val userId = resolveUserId(expandedNotification)
        val isMockReplayReceipt = extras.getBoolean(EXTRA_MOCK_REPLAY_RECEIPT, false)
        val options = IslandPreferences.current()
        if (
            SystemUiNotificationPolicy.isMonochromeTakeoverEnabled(options.colorStatusBarIcon) &&
            options.monochromeShadeIcon &&
            applyMonochromeShadeIcon(
                context = context,
                imageView = imageView,
                expandedNotification = expandedNotification,
                extras = extras,
                postingPackage = postingPackage,
                ownerPackage = ownerPackage,
                postedSmallIcon = notification.smallIcon,
                notificationFlags = notification.flags,
                userId = userId,
                options = options,
            )
        ) {
            return
        }
        if (explicitTargetPackage == null) return
        // Evaluate the existing identity/scope gate before touching any replacement source. This
        // keeps non-MiPush, unresolved, non-XSpace, and XMSF notifications query-free.
        if (
            !MiuiHeaderAppIconPolicy.shouldReplace(
                userId = userId,
                targetPackage = explicitTargetPackage,
                postingPackage = postingPackage,
                hasReplacementIcon = true,
                isMockReplayReceipt = isMockReplayReceipt,
            )
        ) {
            return
        }

        val replacement = resolveReplacementDrawable(
            context = context,
            targetPackage = explicitTargetPackage,
            userId = userId,
        )
        if (
            !MiuiHeaderAppIconPolicy.shouldReplace(
                userId = userId,
                targetPackage = explicitTargetPackage,
                postingPackage = postingPackage,
                hasReplacementIcon = replacement.drawable != null,
                isMockReplayReceipt = isMockReplayReceipt,
            )
        ) {
            return
        }

        imageView.setImageDrawable(replacement.drawable)
        imageView.invalidate()
        logReplacement(
            targetPackage = explicitTargetPackage,
            userId = userId,
            isMockReplayReceipt = isMockReplayReceipt,
            colorStatusBarIcon = options.colorStatusBarIcon,
            source = replacement.source,
        )
    }

    /**
     * Replaces the notification-center header app icon with a theme-tinted single-color
     * silhouette when the monochrome shade option is on. The shade option is a scope of the
     * status-bar monochrome master switch: with the master switch off the header is left to
     * MIUI's own rendering, which shows the colored app icon.
     *
     * Source chain mirrors the status bar, because a shade glyph that disagrees with the
     * status-bar glyph next to it reads as a bug: icon-pack hit, then a silhouette for
     * MiPush-managed posts (whose launcher bitmap would tint into a white block) and for
     * synthetic auto-group summaries (the same record the status bar answers with a
     * silhouette), then the notification's own already-grayscale smallIcon tinted, and
     * otherwise no replacement at all. Synthesizing a silhouette for an ordinary unmanaged
     * post is what produced blank Alipay headers and a white blob for the framework icon.
     */
    private fun applyMonochromeShadeIcon(
        context: Context,
        imageView: ImageView,
        expandedNotification: Any,
        extras: Bundle,
        postingPackage: String?,
        ownerPackage: String,
        postedSmallIcon: Icon?,
        notificationFlags: Int,
        userId: Int?,
        options: IslandOptions,
    ): Boolean {
        val isMiPushManaged = SystemUiNotificationPolicy.isMiPushManagedNotification(extras)
        val uid = runCatching {
            expandedNotification.callMethod("getUid") as? Int
        }.getOrNull() ?: -1
        val attemptsIconPack = SystemUiNotificationPolicy.shouldAttemptIconPackSubstitution(
            thirdPartyIconPack = options.thirdPartyIconPack,
            systemAppIconPack = options.systemAppIconPack,
            isMiPushManaged = isMiPushManaged,
            uid = uid,
            isSystemApp = isSystemApplication(context, postingPackage ?: ownerPackage),
        )
        fun iconPackIcon(packageName: String): Icon? {
            val resolvedUserId = userId ?: return null
            if (!attemptsIconPack) return null
            return StatusBarMonochromeIconPolicy.iconPackIconForPackageOrNull(
                context = context,
                packageName = packageName,
            )
        }
        val packIcon = iconPackIcon(ownerPackage) ?: postingPackage
            ?.takeIf { it != ownerPackage }
            ?.let(::iconPackIcon)
        val isSyntheticAutogroupSummary =
            SystemUiNotificationPolicy.isSyntheticAutogroupSummaryNotification(notificationFlags)
        // Only ask the shared status-bar probe when nothing better is available: it loads the
        // posted drawable to judge it, so it stays behind both the icon-pack lookup and the
        // summary branch, whose answer never depends on the posted glyph.
        val needsPostedGlyphProbe =
            !isMiPushManaged && packIcon == null && !isSyntheticAutogroupSummary
        val postedIsMonochrome = needsPostedGlyphProbe &&
            PostedGlyphMonochromeProbe.isPositivelyGrayscale(context, postedSmallIcon)
        val postedIsUnusable = needsPostedGlyphProbe &&
            PostedGlyphMonochromeProbe.isUnusableGlyph(postedSmallIcon, notificationFlags)
        val source = MiuiHeaderAppIconPolicy.selectMonochromeShadeSource(
            isMiPushManaged = isMiPushManaged,
            attemptsIconPack = attemptsIconPack,
            iconPackHit = packIcon != null,
            isSyntheticAutogroupSummary = isSyntheticAutogroupSummary,
            postedGlyphIsMonochrome = postedIsMonochrome,
            postedGlyphIsUnusable = postedIsUnusable,
        )
        // A settled decision is pinned: re-probing an animated vector small icon on every bind
        // flips its verdict, which is what made FlClash alternate between colored and monochrome.
        val key = shadeDecisionKey(ownerPackage, postingPackage, postedSmallIcon)
        val cached = key?.let { shadeDecisions[it] }
        val icon = cached?.icon ?: if (cached == null) {
            runCatching {
                resolveShadeIcon(context, source, ownerPackage, postingPackage, postedSmallIcon, packIcon)
            }.getOrNull()
        } else {
            null
        }
        if (icon == null) {
            // Cache leave-native, but never a glyph that is merely not generated yet: that has to
            // be retried on the next bind or the row would stay colored forever.
            if (cached == null && key != null &&
                MiuiHeaderAppIconPolicy.cachesShadeDecision(source, null)
            ) {
                shadeDecisions[key] = ShadeDecision(null)
            }
            logShadeSkipped(ownerPackage, source)
            return false
        }
        if (cached == null && key != null) shadeDecisions[key] = ShadeDecision(icon)
        val drawable = runCatching { icon.loadDrawable(context) }.getOrNull() ?: return false
        val tint = resolveShadeIconTint(imageView.context)
        imageView.setImageDrawable(drawable)
        imageView.imageTintList = tint.colors
        shadeTintedViews.add(imageView)
        // Claim the view so the defence below can put this exact glyph back when the posting app
        // re-applies its own RemoteViews content on top of it.
        ShadeHeaderIconClaims.claim(imageView, icon, tint.colors)
        logShadeMonochrome(ownerPackage, source, isMiPushManaged, tint, drawable)
        return true
    }

    /**
     * Defends a claimed header icon against the app's own asynchronous re-apply.
     *
     * `ImageView.updateDrawable` is where every drawable write into a view funnels -- this
     * module's replacement, MIUI's re-resolution inside `applyAppIconAllowCustom`, and the posting
     * app's `RemoteViews$AsyncApplyTask` re-apply alike -- and it runs after the new drawable is
     * already in the view, so a restore here is not overwritten by the write that triggered it.
     * Only views this module has claimed are touched, and a claim is dropped at the start of every
     * bind, so a recycled row is never pinned to another notification's glyph.
     */
    private fun hookShadeHeaderIconDefense(classLoader: ClassLoader) {
        runCatching {
            val iconViewClass = classLoader.findClass("com.android.systemui.statusbar.StatusBarIconView")
            iconViewClass.hookAllMethods("updateDrawable") {
                doAfter {
                    val view = thisObject as? ImageView ?: return@doAfter
                    val claim = ShadeHeaderIconClaims.claimFor(view) ?: return@doAfter
                    if (ShadeHeaderIconClaims.isRestoring) return@doAfter
                    restoreClaimedHeaderIcon(view, claim)
                }
            }
        }.onFailure {
            XLog.d(TAG, "shade header icon defense unavailable: ${it.message}")
        }
    }

    private fun restoreClaimedHeaderIcon(view: ImageView, claim: ShadeHeaderIconClaims.Claim) {
        val drawable = runCatching { claim.icon.loadDrawable(view.context) }.getOrNull() ?: return
        ShadeHeaderIconClaims.restoring {
            view.setImageDrawable(drawable)
            view.imageTintList = claim.tint
        }
        val digest = digestIdentity(view.javaClass.name)
        if (!loggedPackages.add("restore#$digest")) return
        XLog.d(TAG, "restored claimed header icon pkgView=${view.javaClass.simpleName}")
    }

    /** Records the rows deliberately left native, so "why is this one still colored" is answerable. */
    private fun logShadeSkipped(ownerPackage: String, source: MiuiHeaderShadeSource) {
        val digest = digestIdentity(ownerPackage)
        val reason = if (source == MiuiHeaderShadeSource.NONE) {
            "outside-scope"
        } else {
            "glyph-not-ready:$source"
        }
        if (!loggedPackages.add("shade-skip:$reason#$digest")) return
        XLog.i(TAG, "kept native notification center header icon pkgDigest=$digest reason=$reason")
    }

    private fun resolveShadeIcon(
        context: Context,
        source: MiuiHeaderShadeSource,
        ownerPackage: String,
        postingPackage: String?,
        postedSmallIcon: Icon?,
        packIcon: Icon?,
    ): Icon? = when (source) {
        MiuiHeaderShadeSource.ICON_PACK -> packIcon
        MiuiHeaderShadeSource.SILHOUETTE ->
            shadeGlyphOf(context, ownerPackage) ?: postingPackage
                ?.takeIf { it != ownerPackage }
                ?.let { shadeGlyphOf(context, it) }
        MiuiHeaderShadeSource.POSTED_GLYPH -> postedSmallIcon
        MiuiHeaderShadeSource.NONE -> null
    }

    /**
     * Shade glyph for a package: its icon-pack entry, else its themed layer, else the manufactured
     * silhouette. Cache-only by contract, so an unready glyph yields null and the row keeps
     * MIUI's native header until the background worker finishes.
     *
     * Product rule (2026-10-26): an app whose launcher icon is a legacy flattened bitmap cannot be
     * given a silhouette -- `convertToTransparentAndWhite` on an opaque-backed asset is exactly the
     * white blob we removed once already, and there is no shaped layer to work from. Those apps keep
     * MIUI's native header even with the monochrome shade switch on; only the icon pack can change
     * that, which is why the pack tier is tried first.
     */
    private fun shadeGlyphOf(context: Context, packageName: String): Icon? =
        StatusBarMonochromeIconPolicy.shadeIconForPackageOrNull(context, packageName)
            ?: StatusBarMonochromeIconPolicy.whiteIconForPackageOrNull(context, packageName)

    /**
     * Binds a decision to the exact glyph it was made about: package pair plus the posted small
     * icon identity. Bitmap icons carry no comparable id, so the live object's identity is used --
     * valid for as long as that record is what SystemUI keeps re-binding.
     */
    private fun shadeDecisionKey(
        ownerPackage: String,
        postingPackage: String?,
        postedSmallIcon: Icon?,
    ): String? {
        val (type, resId, resPackage) = PostedGlyphMonochromeProbe.resourceFields(postedSmallIcon)
        val glyphIdentity = if (type == SystemUiNotificationPolicy.ICON_TYPE_RESOURCE && resId != 0) {
            "$type:$resId:$resPackage"
        } else {
            postedSmallIcon?.let { "$type:${System.identityHashCode(it)}" } ?: return null
        }
        return "$ownerPackage|$postingPackage|$glyphIdentity"
    }

    private fun clearShadeIconTint(imageView: ImageView) {
        if (shadeTintedViews.remove(imageView)) {
            imageView.imageTintList = null
        }
    }

    /**
     * Header icons sit on MIUI light or dark notification cards, so the silhouette follows the
     * shade appearance: white on dark, black on light.
     *
     * Two per-row signals were measured on HyperOS and both are unusable. The row text views are
     * still empty while the icon binds, so their colors cannot be read, and textColorPrimary
     * resolves inconsistently across row wrappers (ffe4e5f0 and ff30323a in the same pass).
     */
    private fun resolveShadeIconTint(context: Context): ShadeTint {
        val nightMode = (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return ShadeTint(ColorStateList.valueOf(if (nightMode) Color.WHITE else Color.BLACK), "uiMode")
    }

    private fun isSystemApplication(context: Context, packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return runCatching {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0),
                ).flags
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(packageName, 0).flags
            }
            flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        }.getOrDefault(false)
    }

    private fun logShadeMonochrome(
        ownerPackage: String,
        source: MiuiHeaderShadeSource,
        isMiPushManaged: Boolean,
        tint: ShadeTint,
        drawable: Drawable,
    ) {
        val digest = digestIdentity(ownerPackage)
        if (!loggedPackages.add("shade:${source.name}:$isMiPushManaged#$digest")) return
        val ink = inkCoveragePercent(drawable)
        XLog.i(
            TAG,
            "replaced notification center header icon mode=monochrome source=${source.name} " +
                "managed=$isMiPushManaged ink=${ink ?: -1}% tint=${"%08x".format(tint.color)} " +
                "tintOrigin=${tint.origin} pkgDigest=$digest",
        )
    }

    /**
     * Share of opaque pixels in the glyph being painted, so a blank silhouette (near zero) and a
     * solid white block (near full) are both detectable from logcat instead of the screen.
     */
    private fun inkCoveragePercent(drawable: Drawable): Int? {
        val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: return null
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        var opaque = 0
        for (pixel in pixels) {
            if (Color.alpha(pixel) > OPAQUE_ALPHA_THRESHOLD) opaque++
        }
        return opaque * PERCENT_SCALE / pixels.size
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
        targetPackage: String,
        userId: Int?,
    ): HeaderReplacement {
        val targetAppIcon = resolveTargetAppIconDrawable(context, targetPackage, userId)
        val source = MiuiHeaderAppIconPolicy.selectReplacementSource(hasTargetAppIcon = targetAppIcon != null)
        return HeaderReplacement(source, targetAppIcon)
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
        private const val OPAQUE_ALPHA_THRESHOLD = 128
        private const val PERCENT_SCALE = 100
        private val loggedPackages: MutableSet<String> = Collections.synchronizedSet(HashSet())
        private const val SHADE_DECISION_CACHE_MAX = 128

        private val shadeDecisions: MutableMap<String, ShadeDecision> =
            Collections.synchronizedMap(
                object : LinkedHashMap<String, ShadeDecision>(32, 0.75f, true) {
                    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ShadeDecision>?): Boolean {
                        return size > SHADE_DECISION_CACHE_MAX
                    }
                },
            )
        private val shadeTintedViews: MutableSet<ImageView> =
            Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<ImageView, Boolean>()))
    }
}

private data class HeaderReplacement(
    val source: MiuiHeaderAppIconSource,
    val drawable: Drawable?,
)

internal enum class MiuiHeaderAppIconSource {
    APP,
    UNAVAILABLE,
}

/** A pinned shade outcome: an icon to paint, or null meaning leave the native header alone. */
internal data class ShadeDecision(val icon: Icon?)

/** Header-icon tint plus where it came from, so an unreadable color is diagnosable from logcat. */
internal data class ShadeTint(val colors: ColorStateList, val origin: String) {
    val color: Int get() = colors.defaultColor
}

internal enum class MiuiHeaderShadeSource {
    ICON_PACK,
    SILHOUETTE,
    POSTED_GLYPH,
    NONE,
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

    fun selectReplacementSource(hasTargetAppIcon: Boolean): MiuiHeaderAppIconSource =
        if (hasTargetAppIcon) MiuiHeaderAppIconSource.APP else MiuiHeaderAppIconSource.UNAVAILABLE

    /**
     * Source for the notification-center monochrome header icon.
     *
     * Mirrors the status bar chain including its synthetic auto-group branch: a summary record
     * copies the child's smallIcon but none of its extras, so it never looks MiPush-managed and
     * its glyph is whatever the app ships for the status bar (a bell, for apps that have one).
     * The status bar answers that with icon-pack then silhouette, so the header must too, or the
     * two surfaces disagree about one and the same record. Unlike the status bar this stays
     * inside the icon-pack switches: outside their scope nothing is forced into the header.
     */
    fun selectMonochromeShadeSource(
        isMiPushManaged: Boolean,
        attemptsIconPack: Boolean,
        iconPackHit: Boolean,
        isSyntheticAutogroupSummary: Boolean,
        postedGlyphIsMonochrome: Boolean,
        postedGlyphIsUnusable: Boolean,
    ): MiuiHeaderShadeSource = when {
        attemptsIconPack && iconPackHit -> MiuiHeaderShadeSource.ICON_PACK
        isMiPushManaged -> MiuiHeaderShadeSource.SILHOUETTE
        attemptsIconPack && isSyntheticAutogroupSummary -> MiuiHeaderShadeSource.SILHOUETTE
        attemptsIconPack && postedGlyphIsUnusable -> MiuiHeaderShadeSource.SILHOUETTE
        attemptsIconPack && postedGlyphIsMonochrome -> MiuiHeaderShadeSource.POSTED_GLYPH
        else -> MiuiHeaderShadeSource.NONE
    }

    /**
     * Whether a shade outcome may be pinned. A null outcome is only worth caching when the policy
     * said leave-native; a glyph that is not generated yet must be retried on a later bind.
     */
    fun cachesShadeDecision(source: MiuiHeaderShadeSource, outcome: Icon?): Boolean =
        outcome != null || source == MiuiHeaderShadeSource.NONE
}
