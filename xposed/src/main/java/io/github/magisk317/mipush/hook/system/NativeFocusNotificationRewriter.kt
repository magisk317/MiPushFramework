package io.github.magisk317.mipush.hook.system

import android.app.Notification
import android.app.NotificationChannel
import android.content.Context
import android.os.Bundle
import android.os.Build
import android.os.Parcel
import android.os.SystemClock
import android.os.UserHandle
import io.github.magisk317.mipush.common.XMSF_PACKAGE_NAME
import io.github.magisk317.mipush.hook.island.IslandDispatchContract
import io.github.magisk317.mipush.hook.island.IslandPreferences
import io.github.magisk317.mipush.hook.util.hasCustomVisualContent
import io.github.magisk317.mipush.hook.util.newBuilder
import io.github.magisk317.mipush.common.notification.NotificationContentSupport
import io.github.magisk317.mipush.notification.FocusSemanticCore
import io.github.magisk317.xposed.findClass
import io.github.magisk317.xposed.callMethod
import io.github.magisk317.xposed.hookMethod
import io.github.magisk317.xposed.logging.FixedWindowLogLimiter
import io.github.magisk317.xposed.logging.XLog

/** System-server-only adapter for third-party MIUI focus notifications. */
internal object NativeFocusNotificationRewriter {
    const val EXTRA_CONVERTED = "mipush.native_focus_live_update"
    private const val EXTRA_PROMOTION_TOKEN = "mipush.native_focus_live_update_token"
    private const val EXTRA_PROMOTION_REQUEST = "android.requestPromotedOngoing"
    private const val EXTRA_COLORIZED = "android.colorized"
    private const val PER_USER_RANGE = 100_000
    private const val TAG = "NativeFocusLiveUpdate"
    private const val MIUI_DETECTION_LOG_KEY = "miui_build_detection"
    private const val MAX_SHORT_CRITICAL_TEXT = 15

    private val promotionRegistry = NativeFocusPromotionRegistry()
    private val logLimiter = FixedWindowLogLimiter(maxEvents = 8, windowMs = 30_000L)

    internal data class RewriteInput(
        val packageName: String,
        val opPackageName: String,
        val userId: Int,
        val callingUid: Int,
        val notification: Notification,
        val enabled: Boolean,
        val isMiui: Boolean,
        val supportsLiveUpdates: Boolean,
        val context: Context,
    )

    internal data class RewriteResult(
        val notification: Notification?,
        val reason: String,
        val promotionTokenIssued: Boolean = false,
    )

    internal data class ExecutionPlan(
        val plan: FocusSemanticCore.NativeLiveUpdatePlan,
        val promote: Boolean,
    )

    fun rewrite(input: RewriteInput): RewriteResult {
        val notification = input.notification
        val extras = runCatching { notification.extras }.getOrNull()
            ?: return RewriteResult(null, "missing_extras")
        // A notification this object rebuilt carries the marker below. Skip the rebuild so a
        // re-enqueued update for the same id is not rebuilt a second time. A forged marker only
        // skips the conversion: promotion is granted solely from a token the registry here issues.
        if (extras.getBoolean(EXTRA_CONVERTED, false)) {
            return RewriteResult(null, "already_converted")
        }
        val hasFocusPayload = IslandDispatchContract.hasNativeFocusPayload(extras)
        val initialDecision = NativeFocusRewritePolicy.decide(
            NativeFocusRewritePolicy.Input(
                enabled = input.enabled,
                hasFocusPayload = hasFocusPayload,
                isMiui = input.isMiui,
                hasCustomViews = notification.hasCustomVisualContent(),
                isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
                hasTitle = false,
                hasText = false,
                hasChannel = !notification.channelId.isNullOrBlank(),
                colorized = extras.getBoolean(EXTRA_COLORIZED, false),
            ),
        )
        if (initialDecision != NativeFocusRewritePolicy.Decision.MISSING_TITLE &&
            initialDecision != NativeFocusRewritePolicy.Decision.MISSING_TEXT
        ) {
            return RewriteResult(null, initialDecision.name.lowercase())
        }

        val workingNotification = copyNotification(notification)
            ?: return RewriteResult(null, "copy_failed")
        val workingExtras = runCatching { workingNotification.extras }.getOrNull()
            ?: return RewriteResult(null, "missing_extras")
        // Module-private extras received from an app are untrusted. Remove any spoofed token before
        // considering the payload; only this rewriter can issue a token in the system_server map.
        // Work on a Parcelable copy because Notification.Builder(context, notification) adopts and
        // mutates the passed Notification instance. Any failed rewrite must leave the caller's
        // original object untouched.
        workingExtras.remove(EXTRA_PROMOTION_TOKEN)
        val focusParam = workingExtras.getString(IslandDispatchContract.FOCUS_PARAM)
        val title = NotificationContentSupport.firstText(
            workingExtras,
            Notification.EXTRA_TITLE,
            Notification.EXTRA_TITLE_BIG,
        )
        val text = NotificationContentSupport.firstText(
            workingExtras,
            Notification.EXTRA_TEXT,
            Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_SUB_TEXT,
            Notification.EXTRA_INFO_TEXT,
        ) ?: workingNotification.tickerText?.toString()
        val plan = FocusSemanticCore.planNativeLiveUpdate(
            focusParam = focusParam,
            notificationTitle = title,
            notificationText = text,
        )
        val execution = resolveExecutionPlan(plan, input.supportsLiveUpdates)
        val decision = NativeFocusRewritePolicy.decide(
            NativeFocusRewritePolicy.Input(
                enabled = input.enabled,
                hasFocusPayload = hasFocusPayload,
                isMiui = input.isMiui,
                hasCustomViews = workingNotification.hasCustomVisualContent(),
                isGroupSummary = workingNotification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
                hasTitle = !execution.plan.title.isNullOrBlank(),
                hasText = !execution.plan.text.isNullOrBlank(),
                hasChannel = !workingNotification.channelId.isNullOrBlank(),
                colorized = workingExtras.getBoolean(EXTRA_COLORIZED, false),
            ),
        )
        if (decision != NativeFocusRewritePolicy.Decision.REWRITE || !execution.plan.shouldRewrite) {
            return RewriteResult(null, execution.plan.reason.ifBlank { decision.name.lowercase() })
        }
        if (!isDirectPackagePost(input)) {
            return RewriteResult(null, "not_direct_package_post")
        }

        return runCatching {
            val userContext = input.context.callMethod(
                "createContextAsUser",
                createUserHandle(input.userId),
                Context.CONTEXT_RESTRICTED,
            ) as Context
            val packageContext = userContext.createPackageContext(
                input.packageName,
                Context.CONTEXT_RESTRICTED,
            )
            val rewritten = buildLiveUpdateNotification(
                packageContext,
                workingNotification,
                execution.plan,
                execution.promote,
            )
            val rewrittenExtras = rewritten.extras
            rewrittenExtras.putBoolean(EXTRA_CONVERTED, true)
            val token = if (execution.promote) {
                promotionRegistry.issue(input.packageName, input.callingUid)
            } else {
                null
            }
            if (token != null) rewrittenExtras.putString(EXTRA_PROMOTION_TOKEN, token)
            RewriteResult(
                notification = rewritten,
                reason = if (token == null) "rewritten_without_promotion_token" else execution.plan.reason,
                promotionTokenIssued = token != null,
            )
        }.getOrElse { error ->
            logLimited(input.packageName, "rewrite failed: ${error.javaClass.simpleName}")
            RewriteResult(null, "rewrite_failed")
        }
    }

    /**
     * Platforms without live-update rendering keep the rebuild as an ordinary ongoing
     * notification: progress payloads fall back to BigText and no promotion token is issued.
     */
    internal fun resolveExecutionPlan(
        plan: FocusSemanticCore.NativeLiveUpdatePlan,
        supportsLiveUpdates: Boolean,
    ): ExecutionPlan {
        if (supportsLiveUpdates) return ExecutionPlan(plan = plan, promote = true)
        val degraded = if (plan.style == FocusSemanticCore.NativeStyle.PROGRESS) {
            plan.copy(style = FocusSemanticCore.NativeStyle.BIG_TEXT)
        } else {
            plan
        }
        return ExecutionPlan(plan = degraded, promote = false)
    }

    private fun copyNotification(notification: Notification): Notification? = runCatching {
        val parcel = Parcel.obtain()
        try {
            notification.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            Notification.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }.getOrNull()

    private fun buildLiveUpdateNotification(
        context: Context,
        notification: Notification,
        plan: FocusSemanticCore.NativeLiveUpdatePlan,
        promote: Boolean,
    ): Notification {
        val builder = notification.newBuilder(context)
            .setContentTitle(plan.title)
            .setContentText(plan.text)
            .setOngoing(plan.forceOngoing)
            .setAutoCancel(false)
        when (plan.style) {
            FocusSemanticCore.NativeStyle.PROGRESS -> applyPlatformProgressStyle(builder, plan.progressPercent)
            FocusSemanticCore.NativeStyle.BIG_TEXT -> {
                // The adopted builder carries any platform progress the original notification
                // set; a text rebuild must not render it alongside BigText.
                builder.setProgress(0, 0, false)
                builder.setStyle(Notification.BigTextStyle().bigText(plan.text))
            }
            null -> error("native focus plan had no style")
        }
        val shortText = plan.shortText
        if (!shortText.isNullOrBlank()) {
            runCatching {
                builder.javaClass.getMethod("setShortCriticalText", CharSequence::class.java)
                    .invoke(builder, shortText.take(MAX_SHORT_CRITICAL_TEXT))
            }
        }
        if (promote) requestPromotion(builder)
        return builder.build()
    }

    private fun requestPromotion(builder: Notification.Builder) {
        builder.addExtras(Bundle().apply { putBoolean(EXTRA_PROMOTION_REQUEST, true) })
    }

    fun installPromotionHook(classLoader: ClassLoader?): Boolean {
        if (!supportsLiveUpdates()) return false
        return runCatching {
            val nms = findClass("com.android.server.notification.NotificationManagerService", classLoader)
            nms.hookMethod(
                "fixNotificationWithChannel",
                Notification::class.java,
                NotificationChannel::class.java,
                Int::class.javaPrimitiveType!!,
                String::class.java,
            ) {
                doAfter {
                    promoteIfEligible(
                        notification = args.getOrNull(0) as? Notification,
                        channel = args.getOrNull(1) as? NotificationChannel,
                        notificationUid = args.getOrNull(2) as? Int,
                        packageName = args.getOrNull(3) as? String,
                    )
                }
            }
            XLog.i(TAG, "installed scoped fixNotificationWithChannel promotion hook")
            true
        }.getOrElse { error ->
            XLog.w(TAG, "native focus promotion hook unavailable: ${error.javaClass.simpleName}")
            false
        }
    }

    fun promoteIfEligible(
        notification: Notification?,
        channel: NotificationChannel?,
        notificationUid: Int?,
        packageName: String?,
    ): Boolean {
        notification ?: return false
        channel ?: return false
        val uid = notificationUid ?: return false
        val pkg = packageName?.takeIf(String::isNotBlank) ?: return false
        val extras = runCatching { notification.extras }.getOrNull() ?: return false
        val token = extras.getString(EXTRA_PROMOTION_TOKEN) ?: return false
        extras.remove(EXTRA_PROMOTION_TOKEN)
        val trusted = promotionRegistry.consume(token, pkg, uid)
        val eligibility = runCatching {
            notification.javaClass.getMethod("hasPromotableCharacteristics")
                .invoke(notification) as Boolean
        }.getOrDefault(false)
        val flag = runCatching {
            Notification::class.java.getField("FLAG_PROMOTED_ONGOING").getInt(null)
        }.getOrDefault(0)
        val shouldPromote = flag != 0 && NativeFocusRewritePolicy.shouldSetPromotedFlag(
            hasTrustedRewriteGrant = trusted,
            requestPromoted = extras.getBoolean(EXTRA_PROMOTION_REQUEST, false),
            ongoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
            hasTitle = !NotificationContentSupport.firstText(
                extras,
                Notification.EXTRA_TITLE,
                Notification.EXTRA_TITLE_BIG,
            ).isNullOrBlank(),
            hasPromotableStyle = eligibility,
            isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            hasCustomViews = notification.hasCustomVisualContent(),
            colorized = extras.getBoolean(EXTRA_COLORIZED, false),
            channelImportance = channel.importance,
        )
        if (!shouldPromote) return false
        notification.flags = notification.flags or flag
        logLimited(pkg, "promoted eligible native focus notification")
        return true
    }

    internal fun isDirectPackagePost(input: RewriteInput): Boolean {
        if (input.packageName.isBlank() || input.packageName == XMSF_PACKAGE_NAME) return false
        if (input.opPackageName != input.packageName || input.userId < 0 || input.callingUid < 0) return false
        if (input.callingUid / PER_USER_RANGE != input.userId) return false
        return runCatching {
            input.context.packageManager.getPackagesForUid(input.callingUid)
                ?.contains(input.packageName) == true
        }.getOrDefault(false)
    }

    private fun createUserHandle(userId: Int): UserHandle {
        runCatching {
            return UserHandle::class.java
                .getMethod("of", Int::class.javaPrimitiveType)
                .invoke(null, userId) as UserHandle
        }
        return UserHandle::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType).apply {
            isAccessible = true
        }.newInstance(userId)
    }

    internal fun supportsLiveUpdates(): Boolean {
        // SDK_INT_FULL was added in Android 16; the level guard keeps the field read
        // short-circuited on older releases.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return false
        return supportsLiveUpdatesAt(
            sdkInt = Build.VERSION.SDK_INT,
            sdkIntFull = Build.VERSION.SDK_INT_FULL,
        )
    }

    internal fun supportsLiveUpdatesAt(sdkInt: Int, sdkIntFull: Int): Boolean {
        if (sdkInt < Build.VERSION_CODES.BAKLAVA) return false
        return sdkIntFull >= Build.VERSION_CODES_FULL.BAKLAVA_1
    }

    internal fun isMiuiBuild(): Boolean = runCatching {
        val systemProperties = Class.forName("android.os.SystemProperties")
        val get = systemProperties.getDeclaredMethod(
            "get",
            String::class.java,
            String::class.java,
        )
        listOf("ro.miui.ui.version.name", "ro.miui.ui.version.code").any { key ->
            (get.invoke(null, key, "") as? String).orEmpty().isNotBlank()
        }
    }.getOrElse { error ->
        // Failure keeps the fail-safe default: assuming a MIUI build preserves its native focus
        // rendering path instead of rewriting notifications MIUI renders itself. The diagnostic
        // carries only the reflection failure class.
        logLimitedDiagnostic("miui build detection failed; assuming miui: ${error.javaClass.simpleName}")
        true
    }

    private fun applyPlatformProgressStyle(builder: Notification.Builder, percent: Int?) {
        val styleClass = Class.forName("android.app.Notification\$ProgressStyle")
        val style = styleClass.getDeclaredConstructor().newInstance()
        styleClass.getMethod("setStyledByProgress", Boolean::class.javaPrimitiveType).invoke(style, true)
        if (percent == null) {
            styleClass.getMethod("setProgressIndeterminate", Boolean::class.javaPrimitiveType).invoke(style, true)
        } else {
            val value = percent.coerceIn(0, 100)
            styleClass.getMethod("setProgress", Int::class.javaPrimitiveType).invoke(style, value)
            val segmentClass = Class.forName("android.app.Notification\$ProgressStyle\$Segment")
            val pointClass = Class.forName("android.app.Notification\$ProgressStyle\$Point")
            val segment = segmentClass.getDeclaredConstructor(Int::class.javaPrimitiveType).newInstance(100)
            val point = pointClass.getDeclaredConstructor(Int::class.javaPrimitiveType).newInstance(value)
            styleClass.getMethod("addProgressSegment", segmentClass).invoke(style, segment)
            styleClass.getMethod("addProgressPoint", pointClass).invoke(style, point)
        }
        builder.javaClass.getMethod("setStyle", Notification.Style::class.java).invoke(builder, style)
        builder.setCategory(Notification.CATEGORY_PROGRESS)
    }

    private fun logLimited(packageName: String, message: String) {
        if (logLimiter.tryAcquire(packageName, SystemClock.elapsedRealtime()).allowed) {
            XLog.d(TAG, "pkg=$packageName $message")
        }
    }

    private fun logLimitedDiagnostic(message: String) {
        if (logLimiter.tryAcquire(MIUI_DETECTION_LOG_KEY, System.nanoTime()).allowed) {
            XLog.w(TAG, message)
        }
    }
}
