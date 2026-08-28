package io.github.magisk317.mipush.notification

import android.app.Notification
import android.app.NotificationChannel
import android.content.Context
import android.os.Build
import io.github.magisk317.mipush.common.notification.ChannelNameEnricher
import io.github.magisk317.mipush.notification.policy.NotificationDumpCommandContract
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.platform.support.AppRootAccessFacade

/**
 * Resolves HyperOS-redacted channel names via dumpsys, and optionally probes still-ellipsized
 * channels by posting a short-lived silent notification so the system creates an
 * effectiveNotificationChannel record with the full name.
 *
 * Root execution stays here; pure parse/merge lives in [ChannelNameEnricher].
 */
internal class NotificationChannelNameRuntimeEnricher private constructor(
    private val dumpProvider: () -> String?,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val dumpTtlMillis: Long = DEFAULT_DUMP_TTL_MILLIS,
    private val channelProber: ChannelNameProber? = null,
    private val probeSettleMillis: Long = DEFAULT_PROBE_SETTLE_MILLIS,
    private val sleeper: (Long) -> Unit = { millis -> Thread.sleep(millis) },
) {
    private data class DumpSnapshot(
        val capturedAt: Long,
        val output: String,
    )

    private val dumpLock = Any()

    @Volatile
    private var dumpSnapshot: DumpSnapshot? = null

    fun enrich(
        packageName: String,
        channels: List<NotificationChannel>,
        packageUid: Int? = null,
    ): List<NotificationChannel> {
        if (channels.none { it.name?.toString().orEmpty().looksEllipsized() }) {
            return channels
        }

        val firstDump = currentDump() ?: return channels
        var enriched = ChannelNameEnricher.enrich(packageName, channels, firstDump, packageUid)
        val remaining = enriched.filter { channel ->
            channel.name?.toString().orEmpty().looksEllipsized() && !channel.id.isNullOrBlank()
        }
        val prober = channelProber
        if (remaining.isEmpty() || prober == null) {
            return enriched
        }

        val probed = runCatching {
            // Android app UIDs are allocated in PER_USER_RANGE blocks; packageUid is already
            // the target package UID returned by the runtime reader.
            val targetUserId = packageUid?.div(100_000) ?: Utils.myUserId()
            prober.probe(packageName, remaining.mapNotNull { it.id }, targetUserId)
        }.onFailure {
            logW("channel name probe failed pkg=$packageName: ${it.message}")
        }.getOrDefault(false)
        if (!probed) {
            return enriched
        }

        invalidate()
        sleeper(probeSettleMillis)
        val secondDump = currentDump() ?: return enriched
        return ChannelNameEnricher.enrich(packageName, enriched, secondDump, packageUid)
    }

    fun invalidate() {
        synchronized(dumpLock) {
            dumpSnapshot = null
        }
    }

    private fun currentDump(): String? {
        val now = clockMillis()
        dumpSnapshot?.let { cached ->
            if (now - cached.capturedAt < dumpTtlMillis) return cached.output
        }
        synchronized(dumpLock) {
            val refreshedAt = clockMillis()
            dumpSnapshot?.let { cached ->
                if (refreshedAt - cached.capturedAt < dumpTtlMillis) return cached.output
            }
            val output = dumpProvider()?.takeIf { it.contains("NotificationChannel{") } ?: return null
            dumpSnapshot = DumpSnapshot(refreshedAt, output)
            return output
        }
    }

    private fun String.looksEllipsized(): Boolean {
        val trimmed = trim()
        return trimmed.endsWith("...") || trimmed.endsWith("…")
    }

    companion object {
        private const val DEFAULT_DUMP_TTL_MILLIS = 1_000L
        private const val DEFAULT_PROBE_SETTLE_MILLIS = 450L

        fun create(
            dumpProvider: () -> String?,
            clockMillis: () -> Long = System::currentTimeMillis,
            dumpTtlMillis: Long = DEFAULT_DUMP_TTL_MILLIS,
            channelProber: ChannelNameProber? = null,
            probeSettleMillis: Long = DEFAULT_PROBE_SETTLE_MILLIS,
            sleeper: (Long) -> Unit = { millis -> Thread.sleep(millis) },
        ): NotificationChannelNameRuntimeEnricher = NotificationChannelNameRuntimeEnricher(
            dumpProvider = dumpProvider,
            clockMillis = clockMillis,
            dumpTtlMillis = dumpTtlMillis,
            channelProber = channelProber,
            probeSettleMillis = probeSettleMillis,
            sleeper = sleeper,
        )
    }
}

internal fun interface ChannelNameProber {
    fun probe(packageName: String, channelIds: List<String>, userId: Int): Boolean

    fun probe(packageName: String, channelIds: List<String>): Boolean =
        probe(packageName, channelIds, Utils.myUserId())
}

/**
 * Posts one silent, local-only notification per channel under the target package, then cancels.
 * HyperOS only keeps full channel names on effectiveNotificationChannel after a real post.
 */
internal class NotificationManagerChannelNameProber(
    private val contextProvider: () -> Context? = { Utils.getApplication() },
    private val maxChannels: Int = DEFAULT_MAX_CHANNELS,
    private val holdMillis: Long = DEFAULT_HOLD_MILLIS,
    private val sleeper: (Long) -> Unit = { millis -> Thread.sleep(millis) },
) : ChannelNameProber {
    override fun probe(packageName: String, channelIds: List<String>, userId: Int): Boolean {
        val context = contextProvider() ?: return false
        val targets = channelIds
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(maxChannels)
            .toList()
        if (targets.isEmpty()) return false

        val posted = mutableListOf<Pair<String, Int>>()
        targets.forEachIndexed { index, channelId ->
            val notificationId = PROBE_ID_BASE + index
            val notification = buildProbeNotification(context, channelId) ?: return@forEachIndexed
            val ok = runCatching {
                NotificationShellBridge.notify(packageName, PROBE_TAG, notificationId, notification, userId)
            }.getOrDefault(false)
            if (ok) {
                posted += PROBE_TAG to notificationId
            }
        }
        if (posted.isEmpty()) {
            logD("channel name probe posted none pkg=$packageName count=${targets.size}")
            return false
        }

        logD("channel name probe posted=${posted.size}/${targets.size} pkg=$packageName")
        sleeper(holdMillis)
        posted.forEach { (tag, id) ->
            runCatching { NotificationShellBridge.cancelNotification(packageName, tag, id, userId) }
        }
        return true
    }

    private fun buildProbeNotification(context: Context, channelId: String): Notification? {
        return runCatching {
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, channelId)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }
            builder
                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setContentTitle(" ")
                .setContentText(" ")
                .setOnlyAlertOnce(true)
                .setLocalOnly(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_SECRET)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                builder.setGroup(PROBE_GROUP)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setTimeoutAfter(1_500L)
            }
            @Suppress("DEPRECATION")
            builder.setPriority(Notification.PRIORITY_MIN)
            @Suppress("DEPRECATION")
            builder.setSound(null)
            @Suppress("DEPRECATION")
            builder.setVibrate(longArrayOf(0L))
            if (Build.VERSION.SDK_INT >= 29) {
                runCatching {
                    val method = builder.javaClass.getMethod("setSilent", Boolean::class.javaPrimitiveType)
                    method.invoke(builder, true)
                }
            }
            val notification = builder.build()
            notification.flags = notification.flags or Notification.FLAG_LOCAL_ONLY
            notification.extras?.putBoolean(EXTRA_CHANNEL_NAME_PROBE, true)
            notification
        }.onFailure {
            logW("build channel name probe notification failed channelId=$channelId: ${it.message}")
        }.getOrNull()
    }

    private companion object {
        const val PROBE_TAG = "mipush_channel_name_probe"
        const val PROBE_GROUP = "mipush_channel_name_probe_group"
        const val PROBE_ID_BASE = 0x4d5043 // 'MPC'
        const val DEFAULT_MAX_CHANNELS = 24
        const val DEFAULT_HOLD_MILLIS = 350L
        const val EXTRA_CHANNEL_NAME_PROBE = "mipush_channel_name_probe"
    }
}

object RuntimeNotificationChannelNameEnricher {
    private const val DUMP_TIMEOUT_MILLIS = 12_000L

    private val delegate = NotificationChannelNameRuntimeEnricher.create(
        dumpProvider = {
            readNotificationServiceDump(::dumpNotificationService)
        },
    )

    private fun dumpNotificationService(command: String): String? {
        return AppRootAccessFacade.runRootCommand(
            command = command,
            timeoutMs = DUMP_TIMEOUT_MILLIS,
        ).takeIf { it.isSuccess }
            ?.stdoutText
    }

    fun enrich(
        packageName: String,
        channels: List<NotificationChannel>,
    ): List<NotificationChannel> {
        val packageUid = runCatching {
            Utils.getApplication()?.packageManager?.getPackageUid(packageName, 0)
        }.getOrNull()
        return delegate.enrich(packageName, channels, packageUid)
    }

    fun invalidate() = delegate.invalidate()
}

internal fun readNotificationServiceDump(runCommand: (String) -> String?): String? =
    NotificationDumpCommandContract.readDump(
        runCommand = runCommand,
        isUsable = { output -> output.contains("NotificationChannel{") },
    )
