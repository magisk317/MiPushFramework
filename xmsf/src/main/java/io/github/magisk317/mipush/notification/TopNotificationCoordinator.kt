package io.github.magisk317.mipush.notification

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.channel.commonutils.misc.ScheduledJobConstants
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager
import com.xiaomi.channel.commonutils.reflect.JavaCalls
import com.xiaomi.push.service.NotificationUtils
import com.xiaomi.xmpush.thrift.PushMetaInfo
import io.github.aakira.napier.Napier
import io.github.magisk317.mipush.common.utils.Utils
import java.util.concurrent.atomic.AtomicLong

/** Restores stock XMSF 7.4.67-C's bounded top-notification lifecycle on the product publish path. */
@Suppress("DEPRECATION")
internal object TopNotificationCoordinator {
    private const val TAG = "TopNotificationCoordinator"
    internal const val EXTRA_REPEAT = "notification_top_repeat"
    internal const val EXTRA_PERIOD = "notification_top_period"
    internal const val EXTRA_FREQUENCY = "notification_top_frequency"
    internal const val LOCAL_ORIGINAL_WHEN = "mipush_org_when"
    internal const val LOCAL_FLAG = "mipush_n_top_flag"
    internal const val LOCAL_FREQUENCY = "mipush_n_top_fre"
    internal const val LOCAL_PERIOD = "mipush_n_top_prd"
    private const val EXTRA_MESSAGE_ID = "message_id"

    internal data class Spec(
        val periodSeconds: Int,
        val frequencySeconds: Int,
    )

    internal enum class UpdateAction {
        NONE,
        REPOST,
        DOWNGRADE,
        STOP,
    }

    internal data class UpdatePlan(
        val action: UpdateAction,
        val nextDelaySeconds: Int,
    )

    private data class NotificationSlot(
        val packageName: String,
        val tag: String?,
        val notificationId: Int,
        val userId: Int,
    )

    private data class ActiveJob(
        val slot: NotificationSlot,
        val generation: Long,
        val originalWhenMs: Long,
    )

    private val stateLock = Any()
    private val generationCounter = AtomicLong()
    private val activeJobs = mutableMapOf<String, ActiveJob>()
    private val jobsBySlot = mutableMapOf<NotificationSlot, String>()
    private val localStateKeys = listOf(
        LOCAL_FLAG,
        LOCAL_ORIGINAL_WHEN,
        LOCAL_FREQUENCY,
        LOCAL_PERIOD,
    )

    fun applyIfEligible(
        context: Context,
        metaInfo: PushMetaInfo,
        builder: NotificationCompat.Builder,
        originalWhenMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!MIUIUtils.isMIUI() || !MIUIUtils.isXMSF(context)) {
            return false
        }
        val spec = resolveSpec(metaInfo.extra) ?: return false
        applyInitialState(builder, spec, originalWhenMs)
        return true
    }

    fun postNotification(
        context: Context,
        packageName: String,
        tag: String?,
        notificationId: Int,
        messageId: String?,
        notification: Notification,
        userId: Int = Utils.myUserId(),
    ): Boolean = postNotificationDetailed(
        context = context,
        packageName = packageName,
        tag = tag,
        notificationId = notificationId,
        messageId = messageId,
        notification = notification,
        userId = userId,
    ).posted

    fun postNotificationDetailed(
        context: Context,
        packageName: String,
        tag: String?,
        notificationId: Int,
        messageId: String?,
        notification: Notification,
        userId: Int = Utils.myUserId(),
    ): NotificationManagerEx.NotifyResult {
        val slot = NotificationSlot(packageName, tag, notificationId, userId)
        val postedMessageId = resolveLifecycleMessageId(
            extras = notification.extras,
            expectedMessageId = messageId,
            eligibleEnvironment = MIUIUtils.isMIUI() && MIUIUtils.isXMSF(context),
        )
        val topJobId = postedMessageId?.let {
            scopedJobId(packageName, notificationId, it, userId)
        }
        val originalWhenMs = notification.extras?.getLong(LOCAL_ORIGINAL_WHEN, 0L) ?: 0L

        var generation: Long? = null
        val replacedJobs = linkedSetOf<String>()
        val result = synchronized(stateLock) {
            // The first product draft registered the lifecycle only after notify(). A running old
            // job could therefore overwrite a newer notification with the same slot. Serialize
            // the stock-derived reposts and the initial post, then transfer ownership only after
            // NotificationManager accepted the replacement.
            val accepted = NotificationManagerEx.notifyDetailed(packageName, tag, notificationId, notification, userId)
            if (accepted.posted) {
                removeSlotLocked(slot)?.let(replacedJobs::add)
                if (topJobId != null) {
                    if (removeJobLocked(topJobId) != null) {
                        replacedJobs.add(topJobId)
                    }
                    val nextGeneration = generationCounter.incrementAndGet()
                    generation = nextGeneration
                    activeJobs[topJobId] = ActiveJob(slot, nextGeneration, originalWhenMs)
                    jobsBySlot[slot] = topJobId
                }
            }
            accepted
        }
        if (!result.posted) return result

        val manager = ScheduledJobManager.getInstance(context.applicationContext)
        replacedJobs.forEach { manager.cancelJob(it) }
        val currentGeneration = generation ?: return result
        updateTopNotification(
            context = context.applicationContext,
            slot = slot,
            messageId = postedMessageId!!,
            sourceNotification = notification,
            generation = currentGeneration,
        )
        return result
    }

    fun onNotificationRemoved(context: Context, statusBarNotification: StatusBarNotification) {
        if (!NotificationUtils.isNotificationFromXmsf(context, statusBarNotification)) {
            return
        }
        val notification = statusBarNotification.notification ?: return
        val extras = notification.extras ?: return
        val messageId = extras
            .takeIf { it.getBoolean(LOCAL_FLAG, false) }
            ?.getString(EXTRA_MESSAGE_ID)
            ?.takeIf(String::isNotEmpty)
            ?: return
        val packageName = targetPackage(notification) ?: return
        val jobId = scopedJobId(
            packageName,
            statusBarNotification.id,
            messageId,
            statusBarNotification.userId,
        )
        val originalWhenMs = extras.getLong(LOCAL_ORIGINAL_WHEN, 0L)
        val shouldCancel = synchronized(stateLock) {
            // Stock 7.4.67-C m2 cancels by job ID alone. Product generations additionally compare
            // the stock original-when marker so a delayed removal cannot cancel its replacement.
            val active = activeJobs[jobId]
            when {
                active == null -> true
                active.originalWhenMs == originalWhenMs -> {
                    removeJobLocked(jobId)
                    true
                }
                else -> false
            }
        }
        if (shouldCancel) {
            ScheduledJobManager.getInstance(context.applicationContext).cancelJob(jobId)
        }
        Napier.d("cancel removed top notification job=$jobId accepted=$shouldCancel", tag = TAG)
    }

    /** Cancels lifecycle state immediately when MiPush receives an explicit server cancel. */
    fun cancelNotification(
        context: Context,
        packageName: String,
        tag: String?,
        notificationId: Int,
        userId: Int = Utils.myUserId(),
    ) {
        val slot = NotificationSlot(packageName, tag, notificationId, userId)
        val jobId = synchronized(stateLock) { removeSlotLocked(slot) }
        if (jobId != null) {
            ScheduledJobManager.getInstance(context.applicationContext).cancelJob(jobId)
        }
    }

    internal fun resolveSpec(extras: Map<String, String>?): Spec? {
        val source = extras ?: return null
        // Stock 7.4.67-C t0.l enters this feature only for an explicit true repeat value. The old
        // vendor compatibility helper defaulted a missing key to true; keep that dormant helper
        // untouched and enforce the actual publish contract in product-owned code.
        val repeat = source[EXTRA_REPEAT]?.takeIf(String::isNotEmpty) ?: return null
        if (!repeat.toBoolean()) {
            return null
        }
        val period = (source[EXTRA_PERIOD]?.toIntOrNull() ?: 0).coerceAtLeast(0)
        val frequency = (source[EXTRA_FREQUENCY]?.toIntOrNull() ?: 0).coerceAtLeast(0)
        return if (period > 0 && frequency <= period) {
            Spec(periodSeconds = period, frequencySeconds = frequency)
        } else {
            null
        }
    }

    internal fun resolveLifecycleMessageId(
        extras: Bundle?,
        expectedMessageId: String?,
        eligibleEnvironment: Boolean,
    ): String? {
        // Stock 7.4.67-C m2.f checks the MIUI/XMSF environment and local marker again when
        // scheduling. The build-time gate alone is insufficient for restored or foreign extras.
        if (!eligibleEnvironment || expectedMessageId.isNullOrEmpty()) return null
        return extras
            ?.takeIf { it.getBoolean(LOCAL_FLAG, false) }
            ?.getString(EXTRA_MESSAGE_ID)
            ?.takeIf { it.isNotEmpty() && it == expectedMessageId }
    }

    internal fun applyInitialState(
        builder: NotificationCompat.Builder,
        spec: Spec,
        originalWhenMs: Long,
    ) {
        // Stock 7.4.67-C t0.l uses priority 2 and group-alert behavior 1. The old product
        // publish path never copied these fields, so notification_top_* was inert.
        builder.priority = NotificationCompat.PRIORITY_MAX
        builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
        builder.addExtras(
            Bundle().apply {
                putLong(LOCAL_ORIGINAL_WHEN, originalWhenMs)
                putBoolean(LOCAL_FLAG, true)
                if (spec.frequencySeconds > 0) {
                    putInt(LOCAL_FREQUENCY, spec.frequencySeconds)
                }
                putInt(LOCAL_PERIOD, spec.periodSeconds)
            },
        )
    }

    internal fun calculateUpdatePlan(
        originalWhenMs: Long,
        periodSeconds: Int,
        frequencySeconds: Int,
        nowMs: Long,
        justPosted: Boolean,
    ): UpdatePlan {
        if (periodSeconds <= 0 || periodSeconds < frequencySeconds) {
            return UpdatePlan(UpdateAction.STOP, 0)
        }
        val periodMs = periodSeconds.toLong() * 1_000L
        val expiresAt = if (originalWhenMs > Long.MAX_VALUE - periodMs) {
            Long.MAX_VALUE
        } else {
            originalWhenMs + periodMs
        }
        val nextDelay = when {
            originalWhenMs >= nowMs || nowMs >= expiresAt -> 0
            frequencySeconds > 0 -> minOf(
                (expiresAt - nowMs) / 1_000L,
                frequencySeconds.toLong(),
            ).toInt()
            else -> periodSeconds
        }
        val action = when {
            justPosted -> UpdateAction.NONE
            nextDelay > 0 -> UpdateAction.REPOST
            else -> UpdateAction.DOWNGRADE
        }
        return UpdatePlan(action, nextDelay)
    }

    internal fun clearLocalState(extras: Bundle) {
        localStateKeys.forEach(extras::remove)
    }

    fun clearPackageState(
        context: Context,
        packageName: String,
        userId: Int = Utils.myUserId(),
    ) {
        val jobIds = synchronized(stateLock) {
            activeJobs.entries
                .filter { it.value.slot.packageName == packageName && it.value.slot.userId == userId }
                .map { it.key }
                .also { ids -> ids.forEach(::removeJobLocked) }
        }
        val manager = ScheduledJobManager.getInstance(context.applicationContext)
        jobIds.forEach(manager::cancelJob)
    }

    internal fun jobId(
        notificationId: Int,
        messageId: String,
        userId: Int = Utils.myUserId(),
    ): String {
        val userPrefix = if (userId == 0) "" else "${userId}_"
        return ScheduledJobConstants.TOP_NOTIFICATION_UPDATE_JOB_ID + userPrefix + notificationId + "_" + messageId
    }

    /**
     * Keep the stock job identity available for compatibility, but scope actual product jobs by
     * target package so two apps reusing a notification and message ID cannot replace each other.
     */
    internal fun scopedJobId(
        packageName: String,
        notificationId: Int,
        messageId: String,
        userId: Int,
    ): String = jobId(notificationId, messageId, userId) + "_" + packageName

    private fun updateTopNotification(
        context: Context,
        slot: NotificationSlot,
        messageId: String,
        sourceNotification: Notification?,
        generation: Long,
    ) {
        val jobId = scopedJobId(slot.packageName, slot.notificationId, messageId, slot.userId)
        if (!isCurrent(jobId, slot, generation)) {
            return
        }
        val notification = sourceNotification
            ?.takeIf { it.extras?.getString(EXTRA_MESSAGE_ID) == messageId }
            ?: findActiveNotification(slot, messageId)
        val extras = notification
            ?.extras
            ?.takeIf { it.getBoolean(LOCAL_FLAG, false) }
        if (notification == null || extras == null) {
            deactivate(context, jobId, slot, generation)
            return
        }

        val nowMs = System.currentTimeMillis()
        val plan = calculateUpdatePlan(
            originalWhenMs = extras.getLong(LOCAL_ORIGINAL_WHEN, 0L),
            periodSeconds = extras.getInt(LOCAL_PERIOD, 0),
            frequencySeconds = extras.getInt(LOCAL_FREQUENCY, 0),
            nowMs = nowMs,
            justPosted = sourceNotification != null,
        )
        when (plan.action) {
            UpdateAction.NONE -> Unit
            UpdateAction.REPOST -> {
                val updated = prepareTopRepost(notification, nowMs)
                when (postIfCurrent(jobId, slot, generation, updated)) {
                    null -> return
                    false -> {
                        deactivate(context, jobId, slot, generation)
                        return
                    }
                    true -> Unit
                }
                Napier.d("repost top notification job=$jobId delay=${plan.nextDelaySeconds}", tag = TAG)
            }
            UpdateAction.DOWNGRADE -> {
                val updated = rebuildCommonNotification(context, notification, nowMs)
                if (updated == null) {
                    deactivate(context, jobId, slot, generation)
                    return
                }
                val posted = postIfCurrent(jobId, slot, generation, updated) ?: return
                deactivate(context, jobId, slot, generation)
                Napier.d("downgrade top notification job=$jobId posted=$posted", tag = TAG)
                return
            }
            UpdateAction.STOP -> {
                deactivate(context, jobId, slot, generation)
                return
            }
        }

        if (plan.nextDelaySeconds > 0 && isCurrent(jobId, slot, generation)) {
            scheduleNext(
                context = context,
                slot = slot,
                messageId = messageId,
                delaySeconds = plan.nextDelaySeconds,
                generation = generation,
            )
        } else {
            deactivate(context, jobId, slot, generation)
        }
    }

    private fun scheduleNext(
        context: Context,
        slot: NotificationSlot,
        messageId: String,
        delaySeconds: Int,
        generation: Long,
    ) {
        val manager = ScheduledJobManager.getInstance(context)
        val jobId = scopedJobId(slot.packageName, slot.notificationId, messageId, slot.userId)
        val register = Runnable {
            val added: Boolean? = synchronized(stateLock) {
                if (!isCurrentLocked(jobId, slot, generation)) {
                    return@synchronized null
                }
                manager.cancelJob(jobId)
                manager.addOneShootJob(
                    job = buildUpdateJob(
                        context,
                        slot,
                        messageId,
                        generation,
                    ),
                    delaySeconds = delaySeconds,
                )
            }
            if (added == false) {
                Napier.w("failed to schedule top notification job=$jobId delay=$delaySeconds", tag = TAG)
                deactivate(context, jobId, slot, generation)
            }
        }
        // Stock 7.4.67-C m2 cancels and re-adds its keyed job from run(). This project's current
        // ScheduledJobManager removes that key after run() returns, so queue registration on its
        // single executor; this also lets user removal invalidate ownership before registration.
        manager.addOneShootJob(register)
    }

    private fun buildUpdateJob(
        context: Context,
        slot: NotificationSlot,
        messageId: String,
        generation: Long,
    ): ScheduledJobManager.Job {
        return object : ScheduledJobManager.Job() {
            override fun getJobId(): String =
                scopedJobId(slot.packageName, slot.notificationId, messageId, slot.userId)

            override fun run() {
                updateTopNotification(
                    context = context,
                    slot = slot,
                    messageId = messageId,
                    sourceNotification = null,
                    generation = generation,
                )
            }
        }
    }

    private fun findActiveNotification(
        slot: NotificationSlot,
        messageId: String,
    ): Notification? {
        return runCatching {
            NotificationManagerEx.getActiveNotifications(slot.packageName)
                ?.asSequence()
                ?.filterNotNull()
                ?.firstOrNull { active ->
                    active.userId == slot.userId &&
                        active.id == slot.notificationId &&
                        active.notification?.extras?.getString(EXTRA_MESSAGE_ID) == messageId
                }
                ?.notification
        }.onFailure {
            Napier.w(
                "query active top notification failed pkg=${slot.packageName} id=${slot.notificationId}",
                it,
                tag = TAG,
            )
        }.getOrNull()
    }

    internal fun prepareTopRepost(
        notification: Notification,
        nowMs: Long,
    ): Notification {
        // Stock 7.4.67-C m2 mutates the active Notification directly before reposting it. The old
        // product draft rebuilt every update and could drop hidden MIUI/delegation fields.
        if (notification.groupAlertBehavior != Notification.GROUP_ALERT_SUMMARY) {
            JavaCalls.setField(notification, "mGroupAlertBehavior", Notification.GROUP_ALERT_SUMMARY)
        }
        notification.`when` = nowMs
        return notification
    }

    @Suppress("DEPRECATION")
    private fun rebuildCommonNotification(
        context: Context,
        notification: Notification,
        nowMs: Long,
    ): Notification? {
        // Stock 7.4.67-C m2 keeps the existing channel, resets priority/when, and removes exactly
        // these four local markers. An older vendor helper switched to the default channel, which
        // could discard the user's channel policy; this product path intentionally does not.
        return runCatching {
            val extras = Bundle(notification.extras)
            clearLocalState(extras)
            Notification.Builder.recoverBuilder(context, notification)
                .setPriority(Notification.PRIORITY_DEFAULT)
                .setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY)
                .setWhen(nowMs)
                .setExtras(extras)
                .build()
        }.onFailure {
            Napier.w("downgrade top notification failed", it, tag = TAG)
        }.getOrNull()
    }

    private fun postIfCurrent(
        jobId: String,
        slot: NotificationSlot,
        generation: Long,
        notification: Notification,
    ): Boolean? {
        return synchronized(stateLock) {
            if (!isCurrentLocked(jobId, slot, generation)) {
                null
            } else {
                NotificationManagerEx.notify(
                    slot.packageName,
                    slot.tag,
                    slot.notificationId,
                    notification,
                    slot.userId,
                )
            }
        }
    }

    private fun isCurrent(jobId: String, slot: NotificationSlot, generation: Long): Boolean {
        return synchronized(stateLock) {
            isCurrentLocked(jobId, slot, generation)
        }
    }

    private fun isCurrentLocked(jobId: String, slot: NotificationSlot, generation: Long): Boolean {
        val active = activeJobs[jobId]
        return active?.slot == slot && active.generation == generation
    }

    private fun removeSlotLocked(slot: NotificationSlot): String? {
        val jobId = jobsBySlot.remove(slot) ?: return null
        if (activeJobs[jobId]?.slot == slot) {
            activeJobs.remove(jobId)
        }
        return jobId
    }

    private fun removeJobLocked(jobId: String): ActiveJob? {
        val active = activeJobs.remove(jobId) ?: return null
        if (jobsBySlot[active.slot] == jobId) {
            jobsBySlot.remove(active.slot)
        }
        return active
    }

    private fun deactivate(
        context: Context,
        jobId: String,
        slot: NotificationSlot,
        generation: Long,
    ): Boolean {
        val removed = synchronized(stateLock) {
            if (isCurrentLocked(jobId, slot, generation)) {
                removeJobLocked(jobId)
                true
            } else {
                false
            }
        }
        if (removed) {
            ScheduledJobManager.getInstance(context).cancelJob(jobId)
        }
        return removed
    }

    private fun targetPackage(notification: Notification): String? {
        val extras = notification.extras ?: return null
        return sequenceOf("target_package", "miui.targetPkg", "xmsf_target_package")
            .mapNotNull { extras.getString(it)?.takeIf(String::isNotBlank) }
            .firstOrNull()
    }
}
