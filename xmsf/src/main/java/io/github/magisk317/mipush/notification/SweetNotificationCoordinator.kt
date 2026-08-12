package io.github.magisk317.mipush.notification

import android.app.ActivityManager
import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Process
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.Display
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager
import com.xiaomi.push.service.NotificationUtils
import com.xiaomi.xmpush.thrift.PushMetaInfo
import io.github.aakira.napier.Napier
import io.github.magisk317.mipush.common.utils.Utils
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Product-owned implementation of stock XMSF 7.4.67-C's sweet-notification lifecycle.
 *
 * MiPush SDK 3.7.9 only supplies the older generic notification styles. Before this coordinator,
 * MiPushFramework parsed the newer `<ft>` text tags but ignored `remind_status`, `sequence`, and
 * timeout state. That made a style-5 reminder behave like an ordinary repeatable notification.
 * The private Xiaomi RemoteViews remain a presentation detail; this class restores the observable
 * lifecycle without moving newer behavior into the pinned 3.7.9 compatibility sources.
 */
@Suppress("DEPRECATION")
internal object SweetNotificationCoordinator {
    private const val TAG = "SweetNotification"

    internal const val EXTRA_STYLE_TYPE = "notification_style_type"
    internal const val EXTRA_REMIND_STATUS = "remind_status"
    internal const val EXTRA_REMIND_DURATION = "remind_duration"
    internal const val EXTRA_REMIND_TIMEOUT = "remind_timeout"
    internal const val EXTRA_REMIND_END = "remind_end"
    internal const val EXTRA_SEQUENCE = "sequence"
    internal const val EXTRA_ENABLE_KEYGUARD = "miui.enableKeyguard"
    internal const val EXTRA_ENABLE_FLOAT = "miui.enableFloat"
    internal const val LOCAL_GENERATION = "mipush_sweet_generation"

    private const val STYLE_TYPE_SWEET = "5"
    private const val PREF_CLICKED_STATUS = "sweet_clicked_status"
    private const val PREF_SEQUENCE = "sweet_sequence"
    private const val PREF_MILEPOST_STATUS = "sweet_milepost_status"
    private const val MIN_DURATION_SECONDS = 180
    private const val MAX_DURATION_SECONDS = 7_200

    private val initialized = AtomicBoolean()
    private val generationCounter = AtomicLong()
    private val stateLock = Any()
    private val jobLock = Any()
    private val activeJobs = mutableMapOf<String, ActiveReminder>()

    internal data class CardContent(
        val title: String?,
        val text: String?,
        val backgroundUri: String?,
    )

    internal data class ReminderSpec(
        val status: String,
        val durationSeconds: Int,
        val sequence: String?,
    )

    internal data class PreflightDecision(
        val suppress: Boolean,
        val refreshMilepost: Boolean,
    )

    internal data class Milepost(
        val createdAtMs: Long,
        val status: String,
        val durationSeconds: Int,
    )

    private data class ActiveReminder(
        val packageName: String,
        val notificationId: Int,
        val userId: Int,
        val status: String,
        val sequence: String?,
        val generation: Long,
    )

    internal data class TrackedNotification(
        val packageName: String,
        val notificationId: Int,
        val userId: Int,
    )

    fun initialize(context: Context) {
        if (!isEligibleEnvironment(context)) return
        if (!initialized.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        runCatching {
            ContextCompat.registerReceiver(
                appContext,
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        if (intent.action == Intent.ACTION_SCREEN_ON) {
                            onScreenOn(context.applicationContext)
                        }
                    }
                },
                IntentFilter(Intent.ACTION_SCREEN_ON),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.onFailure {
            initialized.set(false)
            Napier.w("failed to register sweet-notification screen receiver", it, tag = TAG)
        }
    }

    fun shouldSuppress(
        context: Context,
        packageName: String,
        notificationId: Int,
        metaInfo: PushMetaInfo,
        nowMs: Long = System.currentTimeMillis(),
        userId: Int = Utils.myUserId(),
    ): Boolean {
        if (!isEligibleEnvironment(context)) return false
        val extras = metaInfo.extra ?: return false
        val status = extras[EXTRA_REMIND_STATUS]?.takeIf(String::isNotEmpty) ?: return false
        val key = stateKey(packageName, notificationId, userId)
        val active = hasActiveReminder(packageName, notificationId, userId)
        val decision = synchronized(stateLock) {
            pruneExpiredMilepostsLocked(context, nowMs)
            val previousSequence = readString(context, PREF_SEQUENCE, key)?.toLongOrNull() ?: 0L
            val milepostStatus = readMilepost(context, key)?.status
            val clickedStatus = readString(context, PREF_CLICKED_STATUS, key)
            decidePreflight(
                incomingStatus = status,
                incomingSequence = extras[EXTRA_SEQUENCE]?.toLongOrNull() ?: 0L,
                previousSequence = previousSequence,
                milepostStatus = milepostStatus,
                clickedStatus = clickedStatus,
                activeNotification = active,
            ).also { result ->
                if (result.refreshMilepost) {
                    writeMilepost(
                        context = context,
                        key = key,
                        status = status,
                        durationSeconds = clampDuration(extras[EXTRA_REMIND_DURATION]?.toIntOrNull()),
                        nowMs = nowMs,
                    )
                }
            }
        }
        if (decision.refreshMilepost) {
            cancelTimeout(context, packageName, notificationId, userId)
        }
        if (decision.suppress) {
            Napier.d(
                "suppress repeated sweet notification pkg=$packageName id=$notificationId status=$status",
                tag = TAG,
            )
        }
        return decision.suppress
    }

    fun applyIfEligible(
        context: Context,
        packageName: String,
        notificationId: Int,
        metaInfo: PushMetaInfo,
        builder: NotificationCompat.Builder,
        userId: Int = Utils.myUserId(),
    ): Boolean {
        if (!isEligibleEnvironment(context)) return false
        val extras = metaInfo.extra ?: return false
        if (extras[EXTRA_STYLE_TYPE] != STYLE_TYPE_SWEET) return false
        val spec = resolveReminderSpec(extras) ?: return false
        val key = stateKey(packageName, notificationId, userId)
        val statusChanged = synchronized(stateLock) {
            pruneExpiredMilepostsLocked(context, System.currentTimeMillis())
            readMilepost(context, key)?.status != spec.status
        }
        applyReminderMetadata(
            builder = builder,
            spec = spec,
            statusChanged = statusChanged,
            targetForeground = isTargetForeground(context, packageName),
            displayOn = isDisplayOn(context),
        )
        return true
    }

    /** Runs after the final Notification is built and before the publish attempt, matching t$b.e. */
    fun onNotificationBuilt(
        context: Context,
        packageName: String,
        notificationId: Int,
        metaInfo: PushMetaInfo,
        notification: Notification,
        userId: Int = Utils.myUserId(),
    ) {
        if (!isEligibleEnvironment(context)) return
        val source = metaInfo.extra ?: emptyMap()
        cancelTimeout(context, packageName, notificationId, userId)
        synchronized(stateLock) {
            val key = stateKey(packageName, notificationId, userId)
            if (source.containsKey(EXTRA_REMIND_END)) {
                clearStateLocked(context, key)
            }
        }
        // Stock 7.4.67-C e.e delegates to d.c when a normal notification replaces the same ID:
        // cancel the previous sweet record, but retain its milepost/click state until remind_end or
        // expiry. The first implementation removed the milepost here and broke repeat suppression.
        if (shouldCancelPriorReminder(
                remindStatus = source[EXTRA_REMIND_STATUS],
                activeReminder = hasActiveReminder(packageName, notificationId, userId),
            )
        ) {
            findActiveNotification(packageName, notificationId, userId)?.let { active ->
                NotificationManagerEx.cancel(packageName, active.tag, active.id, userId)
            }
        }
        if (!notification.extras?.getString(EXTRA_REMIND_STATUS).isNullOrEmpty()) {
            notification.extras.putLong(LOCAL_GENERATION, generationCounter.incrementAndGet())
        }
    }

    fun onNotificationPosted(
        context: Context,
        statusBarNotification: StatusBarNotification,
    ) {
        if (!isEligibleEnvironment(context) ||
            !NotificationUtils.isNotificationFromXmsf(context, statusBarNotification)
        ) {
            return
        }
        val notification = statusBarNotification.notification ?: return
        val packageName = targetPackage(notification) ?: return
        onNotificationPosted(
            context,
            packageName,
            statusBarNotification.id,
            notification,
            userId = statusBarNotification.userId,
        )
    }

    fun onNotificationPosted(
        context: Context,
        packageName: String,
        notificationId: Int,
        notification: Notification,
        nowMs: Long = System.currentTimeMillis(),
        userId: Int = Utils.myUserId(),
    ) {
        if (!isEligibleEnvironment(context)) return
        val extras = notification.extras ?: return
        val status = extras.getString(EXTRA_REMIND_STATUS)?.takeIf(String::isNotEmpty) ?: return
        val durationSeconds = clampDuration(extras.getInt(EXTRA_REMIND_TIMEOUT, MIN_DURATION_SECONDS))
        val sequence = extras.getString(EXTRA_SEQUENCE)
        val generation = extras.getLong(LOCAL_GENERATION, 0L).takeIf { it > 0L }
            ?: generationCounter.incrementAndGet().also { extras.putLong(LOCAL_GENERATION, it) }
        val key = stateKey(packageName, notificationId, userId)
        synchronized(stateLock) {
            sequence?.toLongOrNull()?.takeIf { it >= 0L }?.let {
                writeString(context, PREF_SEQUENCE, key, it.toString())
            }
            writeMilepost(context, key, status, durationSeconds, nowMs)
        }
        scheduleTimeout(
            context = context.applicationContext,
            reminder = ActiveReminder(
                packageName = packageName,
                notificationId = notificationId,
                userId = userId,
                status = status,
                sequence = sequence,
                generation = generation,
            ),
            durationSeconds = durationSeconds,
        )
    }

    fun onNotificationRemoved(
        context: Context,
        statusBarNotification: StatusBarNotification,
        reason: Int,
    ) {
        if (!shouldRecordUserRemoval(reason) ||
            !isEligibleEnvironment(context) ||
            !NotificationUtils.isNotificationFromXmsf(context, statusBarNotification)
        ) {
            return
        }
        val notification = statusBarNotification.notification ?: return
        val extras = notification.extras ?: return
        val status = extras.getString(EXTRA_REMIND_STATUS)?.takeIf(String::isNotEmpty) ?: return
        val packageName = targetPackage(notification) ?: return
        val userId = statusBarNotification.userId
        val jobId = jobId(packageName, statusBarNotification.id, userId)
        val generation = extras.getLong(LOCAL_GENERATION, 0L)
        val accepted = synchronized(jobLock) {
            val active = activeJobs[jobId]
            when {
                active == null -> true
                matchesReminderIdentity(
                    expectedGeneration = active.generation,
                    expectedStatus = active.status,
                    expectedSequence = active.sequence,
                    candidateGeneration = generation,
                    candidateStatus = status,
                    candidateSequence = extras.getString(EXTRA_SEQUENCE),
                ) -> {
                    activeJobs.remove(jobId)
                    true
                }
                else -> false
            }
        }
        if (!accepted) return
        ScheduledJobManager.getInstance(context.applicationContext).cancelJob(jobId)
        synchronized(stateLock) {
            writeString(context, PREF_CLICKED_STATUS, stateKey(packageName, statusBarNotification.id, userId), status)
        }
    }

    /** Cancels the timeout state immediately for an explicit server notification cancel. */
    fun cancelNotification(
        context: Context,
        packageName: String,
        notificationId: Int,
        userId: Int = Utils.myUserId(),
    ) {
        cancelTimeout(context, packageName, notificationId, userId)
    }

    fun clearPackageState(
        context: Context,
        packageName: String,
        userId: Int = Utils.myUserId(),
    ) {
        val jobIds = synchronized(jobLock) {
            activeJobs.entries
                .filter { it.value.packageName == packageName && it.value.userId == userId }
                .map { it.key }
                .also { ids -> ids.forEach(activeJobs::remove) }
        }
        val manager = ScheduledJobManager.getInstance(context.applicationContext)
        jobIds.forEach(manager::cancelJob)

        val userPrefix = if (userId == 0) "" else "$userId|"
        val statePrefix = "$userPrefix$packageName-"
        synchronized(stateLock) {
            listOf(PREF_CLICKED_STATUS, PREF_SEQUENCE, PREF_MILEPOST_STATUS).forEach { name ->
                val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
                val keys = preferences.all.keys.filter { it.startsWith(statePrefix) }
                if (keys.isNotEmpty()) {
                    preferences.edit().apply {
                        keys.forEach(::remove)
                    }.apply()
                }
            }
        }
    }

    internal fun resolveCardContent(extras: Map<String, String>?): CardContent? {
        val source = extras ?: return null
        if (source[EXTRA_STYLE_TYPE] != STYLE_TYPE_SWEET) return null
        val title = source["notify_style_5_alert"]?.takeIf(String::isNotEmpty)
            ?: source["notify_rich_title"]?.takeIf(String::isNotEmpty)
        val text = listOfNotNull(
            source["notify_style_5_left"]?.takeIf(String::isNotEmpty),
            source["notify_style_5_right"]?.takeIf(String::isNotEmpty),
        ).joinToString("  ").takeIf(String::isNotEmpty)
            ?: source["notify_rich_desc"]?.takeIf(String::isNotEmpty)
        return CardContent(
            title = title,
            text = text,
            backgroundUri = source["notify_style_5_bg_pic"]?.takeIf(String::isNotEmpty),
        )
    }

    internal fun resolveReminderSpec(extras: Map<String, String>?): ReminderSpec? {
        val source = extras ?: return null
        val status = source[EXTRA_REMIND_STATUS]?.takeIf(String::isNotEmpty) ?: return null
        return ReminderSpec(
            status = status,
            durationSeconds = clampDuration(source[EXTRA_REMIND_DURATION]?.toIntOrNull()),
            sequence = source[EXTRA_SEQUENCE]?.takeIf(String::isNotEmpty),
        )
    }

    internal fun decidePreflight(
        incomingStatus: String?,
        incomingSequence: Long,
        previousSequence: Long,
        milepostStatus: String?,
        clickedStatus: String?,
        activeNotification: Boolean,
    ): PreflightDecision {
        if (incomingStatus.isNullOrEmpty()) return PreflightDecision(false, false)
        if (previousSequence > 0L && incomingSequence < previousSequence) {
            return PreflightDecision(true, false)
        }
        val repeatedClickedStatus = incomingStatus == milepostStatus &&
            !activeNotification &&
            incomingStatus == clickedStatus
        return PreflightDecision(
            suppress = repeatedClickedStatus,
            refreshMilepost = repeatedClickedStatus,
        )
    }

    internal fun clampDuration(value: Int?): Int {
        return (value ?: 0).coerceIn(MIN_DURATION_SECONDS, MAX_DURATION_SECONDS)
    }

    internal fun applyReminderMetadata(
        builder: NotificationCompat.Builder,
        spec: ReminderSpec,
        statusChanged: Boolean,
        targetForeground: Boolean,
        displayOn: Boolean,
    ) {
        val hasExplicitKeyguardPolicy = builder.extras.containsKey(EXTRA_ENABLE_KEYGUARD)
        val hasExplicitFloatPolicy = builder.extras.containsKey(EXTRA_ENABLE_FLOAT)
        // Stock 7.4.67-C style 5 enables keyguard for a changed status and suppresses floating while
        // the target is foreground. Repeated states only remain keyguard-visible while the screen is
        // already on. The old product builder copied none of these typed extras. The first product
        // implementation then overwrote explicit enable_keyguard/enable_float payload controls;
        // stock t0 applies those generic controls after style 5, so preserve their final precedence.
        builder.addExtras(
            Bundle().apply {
                putInt(EXTRA_REMIND_TIMEOUT, spec.durationSeconds)
                putString(EXTRA_REMIND_STATUS, spec.status)
                spec.sequence?.let { putString(EXTRA_SEQUENCE, it) }
                if (!hasExplicitKeyguardPolicy) {
                    putBoolean(EXTRA_ENABLE_KEYGUARD, statusChanged || displayOn)
                }
                if (!hasExplicitFloatPolicy) {
                    putBoolean(EXTRA_ENABLE_FLOAT, statusChanged && !targetForeground)
                }
            },
        )
    }

    internal fun parseMilepost(value: String?): Milepost? {
        if (value.isNullOrEmpty()) return null
        val parts = value.split('-')
        if (parts.size < 3) return null
        return Milepost(
            createdAtMs = parts[0].toLongOrNull() ?: return null,
            status = parts[1],
            durationSeconds = clampDuration(parts[2].toIntOrNull()),
        )
    }

    internal fun shouldRecordUserRemoval(reason: Int): Boolean {
        return reason == NotificationListenerService.REASON_CLICK ||
            reason == NotificationListenerService.REASON_CANCEL ||
            reason == NotificationListenerService.REASON_CANCEL_ALL
    }

    internal fun shouldCancelPriorReminder(
        remindStatus: String?,
        activeReminder: Boolean,
    ): Boolean = remindStatus.isNullOrEmpty() && activeReminder

    internal fun shouldRestoreKeyguard(
        notificationId: Int,
        trackedIds: Set<Int>,
        remindStatus: String?,
        keyguardEnabled: Boolean,
    ): Boolean {
        return notificationId in trackedIds && !remindStatus.isNullOrEmpty() && !keyguardEnabled
    }

    internal fun matchesReminderIdentity(
        expectedGeneration: Long,
        expectedStatus: String,
        expectedSequence: String?,
        candidateGeneration: Long,
        candidateStatus: String?,
        candidateSequence: String?,
    ): Boolean {
        val stateMatches = expectedStatus == candidateStatus && expectedSequence == candidateSequence
        return stateMatches && (candidateGeneration <= 0L || expectedGeneration == candidateGeneration)
    }

    internal fun jobId(
        packageName: String,
        notificationId: Int,
        userId: Int = Utils.myUserId(),
    ): String {
        val userPrefix = if (userId == 0) "" else "${userId}_"
        return "n_sweet_timeout_${userPrefix}${notificationId}_$packageName"
    }

    private fun scheduleTimeout(
        context: Context,
        reminder: ActiveReminder,
        durationSeconds: Int,
    ) {
        val manager = ScheduledJobManager.getInstance(context)
        val id = jobId(reminder.packageName, reminder.notificationId, reminder.userId)
        synchronized(jobLock) {
            activeJobs[id] = reminder
            manager.cancelJob(id)
            val timeoutJob = object : ScheduledJobManager.Job() {
                override fun getJobId(): String = id

                override fun run() {
                    expireReminder(context, id, reminder)
                }
            }
            val added = manager.addOneShootJob(
                timeoutJob as ScheduledJobManager.Job?,
                durationSeconds,
            )
            if (!added && activeJobs[id]?.generation == reminder.generation) {
                activeJobs.remove(id)
                Napier.w("failed to schedule sweet timeout job=$id", tag = TAG)
            }
        }
    }

    private fun expireReminder(
        context: Context,
        id: String,
        reminder: ActiveReminder,
    ) {
        val current = synchronized(jobLock) {
            activeJobs[id]?.takeIf { it.generation == reminder.generation }
        } ?: return
        val active = findActiveNotification(current.packageName, current.notificationId, current.userId)
        if (active != null && matches(active, current)) {
            NotificationManagerEx.cancel(current.packageName, null, current.notificationId, current.userId)
        }
        val removed = synchronized(jobLock) {
            if (activeJobs[id]?.generation == current.generation) {
                activeJobs.remove(id)
                true
            } else {
                false
            }
        }
        if (removed) {
            synchronized(stateLock) {
                clearStateLocked(context, stateKey(current.packageName, current.notificationId, current.userId))
            }
        }
    }

    private fun cancelTimeout(context: Context, packageName: String, notificationId: Int, userId: Int) {
        val id = jobId(packageName, notificationId, userId)
        synchronized(jobLock) {
            activeJobs.remove(id)
        }
        ScheduledJobManager.getInstance(context.applicationContext).cancelJob(id)
    }

    private fun onScreenOn(context: Context) {
        if (!isEligibleEnvironment(context)) return
        ScheduledJobManager.getInstance(context).addOneShootJob(
            Runnable { restoreTrackedKeyguardVisibility(context) },
        )
    }

    private fun restoreTrackedKeyguardVisibility(context: Context) {
        val liveMileposts = synchronized(stateLock) {
            pruneExpiredMilepostsLocked(context, System.currentTimeMillis())
        }
        val trackedByPackage = liveMileposts.keys.mapNotNull(::parseStateKey).groupBy { it.packageName }
        trackedByPackage.forEach { (packageName, trackedNotifications) ->
            runCatching {
                NotificationManagerEx.getActiveNotifications(packageName)
                    ?.filterNotNull()
                    ?.forEach { active ->
                        if (trackedNotifications.any {
                                it.userId == active.userId && it.notificationId == active.id
                            }
                        ) {
                            restoreKeyguardIfNeeded(
                                context = context,
                                packageName = packageName,
                                active = active,
                                trackedIds = trackedNotifications
                                    .asSequence()
                                    .filter { it.userId == active.userId }
                                    .map(TrackedNotification::notificationId)
                                    .toSet(),
                            )
                        }
                    }
            }.onFailure {
                Napier.w("failed to inspect sweet notifications pkg=$packageName", it, tag = TAG)
            }
        }
    }

    private fun restoreKeyguardIfNeeded(
        context: Context,
        packageName: String,
        active: StatusBarNotification,
        trackedIds: Set<Int>,
    ) {
        val notification = active.notification ?: return
        val extras = notification.extras ?: return
        if (!shouldRestoreKeyguard(
                notificationId = active.id,
                trackedIds = trackedIds,
                remindStatus = extras.getString(EXTRA_REMIND_STATUS),
                keyguardEnabled = extras.getBoolean(EXTRA_ENABLE_KEYGUARD, true),
            )
        ) {
            return
        }
        val restored = runCatching {
            val copiedExtras = Bundle(extras).apply { putBoolean(EXTRA_ENABLE_KEYGUARD, true) }
            Notification.Builder.recoverBuilder(context, notification)
                .setExtras(copiedExtras)
                .build()
        }.onFailure {
            Napier.w("failed to restore sweet keyguard visibility pkg=$packageName id=${active.id}", it, tag = TAG)
        }.getOrNull() ?: return
        NotificationManagerEx.notify(packageName, active.tag, active.id, restored, active.userId)
    }

    private fun hasActiveReminder(packageName: String, notificationId: Int, userId: Int): Boolean {
        return findActiveNotification(packageName, notificationId, userId)
            ?.notification
            ?.extras
            ?.getString(EXTRA_REMIND_STATUS)
            ?.isNotEmpty() == true
    }

    private fun findActiveNotification(
        packageName: String,
        notificationId: Int,
        userId: Int,
    ): StatusBarNotification? {
        return runCatching {
            NotificationManagerEx.getActiveNotifications(packageName)
                ?.filterNotNull()
                ?.firstOrNull { it.userId == userId && it.id == notificationId }
        }.getOrNull()
    }

    private fun matches(notification: StatusBarNotification, reminder: ActiveReminder): Boolean {
        val extras = notification.notification?.extras ?: return false
        return matchesReminderIdentity(
            expectedGeneration = reminder.generation,
            expectedStatus = reminder.status,
            expectedSequence = reminder.sequence,
            candidateGeneration = extras.getLong(LOCAL_GENERATION, 0L),
            candidateStatus = extras.getString(EXTRA_REMIND_STATUS),
            candidateSequence = extras.getString(EXTRA_SEQUENCE),
        )
    }

    private fun pruneExpiredMilepostsLocked(context: Context, nowMs: Long): Map<String, String> {
        val preferences = context.getSharedPreferences(PREF_MILEPOST_STATUS, Context.MODE_PRIVATE)
        val retained = preferences.all.mapNotNull { (key, raw) ->
            val value = raw as? String
            if (value == null) {
                preferences.edit().remove(key).apply()
                removeString(context, PREF_CLICKED_STATUS, key)
                return@mapNotNull null
            }
            val milepost = parseMilepost(value)
            if (milepost != null && nowMs - milepost.createdAtMs < milepost.durationSeconds * 1_000L) {
                key to value
            } else {
                preferences.edit().remove(key).apply()
                removeString(context, PREF_CLICKED_STATUS, key)
                null
            }
        }.toMap()
        return retained
    }

    private fun readMilepost(context: Context, key: String): Milepost? {
        return parseMilepost(readString(context, PREF_MILEPOST_STATUS, key))
    }

    private fun writeMilepost(
        context: Context,
        key: String,
        status: String,
        durationSeconds: Int,
        nowMs: Long,
    ) {
        writeString(
            context,
            PREF_MILEPOST_STATUS,
            key,
            "$nowMs-$status-$durationSeconds",
        )
    }

    private fun clearStateLocked(context: Context, key: String) {
        removeString(context, PREF_MILEPOST_STATUS, key)
        removeString(context, PREF_CLICKED_STATUS, key)
        removeString(context, PREF_SEQUENCE, key)
    }

    private fun readString(context: Context, preferences: String, key: String): String? {
        return context.getSharedPreferences(preferences, Context.MODE_PRIVATE).getString(key, null)
    }

    private fun writeString(context: Context, preferences: String, key: String, value: String) {
        context.getSharedPreferences(preferences, Context.MODE_PRIVATE)
            .edit()
            .putString(key, value)
            .apply()
    }

    private fun removeString(context: Context, preferences: String, key: String) {
        context.getSharedPreferences(preferences, Context.MODE_PRIVATE)
            .edit()
            .remove(key)
            .apply()
    }

    private fun targetPackage(notification: Notification): String? {
        return notification.extras?.getString("target_package")?.takeIf(String::isNotEmpty)
            ?: notification.extras?.getString("miui.targetPkg")?.takeIf(String::isNotEmpty)
    }

    private fun stateKey(packageName: String, notificationId: Int, userId: Int): String {
        val userPrefix = if (userId == 0) "" else "$userId|"
        return "$userPrefix$packageName-$notificationId"
    }

    internal fun parseStateKey(key: String): TrackedNotification? {
        val separator = key.lastIndexOf('-')
        if (separator <= 0 || separator >= key.lastIndex) return null
        val packagePart = key.substring(0, separator)
        val userSeparator = packagePart.indexOf('|')
        val userId = if (userSeparator >= 0) {
            packagePart.substring(0, userSeparator).toIntOrNull() ?: return null
        } else {
            0
        }
        val packageName = packagePart.substringAfter('|').takeIf(String::isNotBlank) ?: return null
        val notificationId = key.substring(separator + 1).toIntOrNull() ?: return null
        return TrackedNotification(packageName, notificationId, userId.coerceAtLeast(0))
    }

    private fun isEligibleEnvironment(context: Context): Boolean {
        return MIUIUtils.isMIUI() && MIUIUtils.isXMSF(context)
    }

    private fun isTargetForeground(context: Context, packageName: String): Boolean {
        return runCatching {
            context.getSystemService(ActivityManager::class.java)
                ?.runningAppProcesses
                ?.firstOrNull()
                ?.processName == packageName
        }.getOrDefault(false)
    }

    private fun isDisplayOn(context: Context): Boolean {
        return runCatching {
            context.getSystemService(DisplayManager::class.java)
                ?.getDisplay(Display.DEFAULT_DISPLAY)
                ?.state == Display.STATE_ON
        }.getOrDefault(false)
    }
}
