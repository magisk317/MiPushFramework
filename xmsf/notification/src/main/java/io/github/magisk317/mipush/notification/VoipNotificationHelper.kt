package io.github.magisk317.mipush.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import com.xiaomi.xmpush.thrift.PushMetaInfo
import io.github.magisk317.mipush.common.R as CommonR
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.xposed.logging.MagiskOtel

/**
 * Handles VoIP call notification styles.
 *
 * Based on stock xmsf 7.4.67-C `com.xiaomi.push.voip` package.
 * Detects stock VoIP style extras and builds a call-style notification.
 * Action buttons are added from stock `cust_btn_*` or normal `notification_style_button_*` extras.
 */
object VoipNotificationHelper {

    private const val TAG = "VoipNotificationHelper"
    private const val KEY_NOTIFICATION_STYLE_TYPE = "notification_style_type"
    private const val KEY_MESSAGE_BUSINESS_TYPE = "msg_busi_type"
    private const val KEY_VOIP_TYPE = "voip_type"
    private const val KEY_SEQUENCE = "sequence"
    private const val STYLE_TYPE_VOIP = "6"
    private const val BUSINESS_TYPE_VOIP = "voip"
    internal const val VOIP_TYPE_END = 0
    internal const val VOIP_TYPE_VOICE = 1
    internal const val VOIP_TYPE_VIDEO = 2

    // Keep sequence state per Android user and package. A cloned package can legitimately reuse
    // the same sequence range as its owner; sharing the key would suppress a valid call there.
    private data class SequenceKey(
        val userId: Int,
        val packageName: String,
    )

    // Stock 7.4.67-C com.xiaomi.push.voip.b keeps one process-lifetime sequence per package in an
    // unbounded HashMap. The old 128-entry LRU could evict an active package and admit a stale call.
    private val sequenceCache = hashMapOf<SequenceKey, Long>()

    @JvmStatic
    fun isVoipNotification(metaInfo: PushMetaInfo?): Boolean {
        val extras = metaInfo?.extra ?: return false
        // Stock 7.4.67-C t$b.f selects the VoIP builder only from style type 6. The old
        // shared OR predicate also treated msg_busi_type=voip as a style selector.
        return hasVoipStyle(extras)
    }

    @JvmStatic
    fun isVoipEndEvent(metaInfo: PushMetaInfo?): Boolean {
        val extras = metaInfo?.extra ?: return false
        // Stock 7.4.67-C com.xiaomi.push.voip.b.g gates cancellation on the business
        // marker. The old shared predicate let a malformed style-only push cancel a call.
        return isVoipBusiness(extras) && voipType(extras) == VOIP_TYPE_END
    }

    @JvmStatic
    fun shouldDropStale(
        metaInfo: PushMetaInfo?,
        packageName: String,
        userId: Int = currentUserId(),
    ): Boolean {
        val extras = metaInfo?.extra ?: return false
        // Stock 7.4.67-C com.xiaomi.push.voip.b.i applies sequence ordering only to
        // msg_busi_type=voip. Style type 6 is independent notification presentation data.
        if (!isVoipBusiness(extras)) return false

        val sequence = sequence(extras)
        val key = SequenceKey(Utils.requireValidUserId(userId), packageName)
        synchronized(sequenceCache) {
            val previous = sequenceCache[key] ?: 0L
            if (previous > sequence) {
                Logger.withTag(TAG).d {
                    "drop stale VoIP notification user=${key.userId} pkg=$packageName sequence=$sequence previous=$previous"
                }
                MagiskOtel.event(
                    name = "push.event",
                    attributes = mapOf(
                        "result" to "skip",
                        "duration_ms" to "0",
                        "process" to "app",
                        "stage" to "voip_stale_drop",
                        "reason" to "stale_sequence",
                        "target_package" to packageName,
                        "user_id" to key.userId.toString(),
                    ),
                    statusOk = true,
                )
                return true
            }
            sequenceCache[key] = sequence
            return false
        }
    }

    @JvmStatic
    fun buildVoipNotification(
        context: Context,
        targetPackage: String,
        metaInfo: PushMetaInfo,
        notificationId: Int,
        fullScreenPendingIntent: PendingIntent?
    ): NotificationCompat.Builder? {
        val extras = metaInfo.extra ?: return null
        val voipType = voipType(extras)

        val channelId = NotificationAvailabilityShellBridge.resolveChannelId(context, metaInfo, targetPackage)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(CommonR.drawable.ic_notifications_black_24dp)
            .setContentTitle(metaInfo.title ?: "Incoming Call")
            .setContentText(SweetTagHandler.renderFtHtmlIfNeeded(metaInfo.description))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(Notification.CATEGORY_CALL)
            .setAutoCancel(false)
            .setOngoing(true)

        if (fullScreenPendingIntent != null && voipType in VOIP_TYPE_VOICE..VOIP_TYPE_VIDEO) {
            // Stock 7.4.67-C presents style 6 through Xiaomi's private heads-up/CallKit
            // stack and does not attach this full-screen intent. MiPushFramework cannot
            // host that system UI on AOSP, so keep a standard incoming-call fallback only
            // for the two numeric stock call types; type 0/control payloads never launch it.
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
        }

        val largeIcon = getLargeIcon(context, metaInfo)
        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon)
        }

        val targetBundle = Bundle()
        buildVoipMetadata(extras, targetPackage).forEach(targetBundle::putString)
        builder.addExtras(targetBundle)

        Logger.withTag(TAG).d { "built VoIP notification type=$voipType pkg=$targetPackage id=$notificationId" }
        return builder
    }

    private fun getLargeIcon(context: Context, metaInfo: PushMetaInfo): android.graphics.Bitmap? {
        val iconUri = metaInfo.extra?.get("large_icon") ?: return null
        return try {
            val uri = iconUri.toUri()
            context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream)
            }
        } catch (_: Exception) {
            null
        }
    }

    internal fun resetForTest() {
        synchronized(sequenceCache) {
            sequenceCache.clear()
        }
    }

    fun clearPackageState(packageName: String, userId: Int = currentUserId()) {
        val key = SequenceKey(Utils.requireValidUserId(userId), packageName)
        synchronized(sequenceCache) {
            sequenceCache.remove(key)
        }
    }

    private fun currentUserId(): Int = Utils.requireValidUserId(Utils.myUserId())

    internal fun hasVoipStyle(extras: Map<String, String>): Boolean {
        return extras[KEY_NOTIFICATION_STYLE_TYPE] == STYLE_TYPE_VOIP
    }

    internal fun isVoipBusiness(extras: Map<String, String>): Boolean {
        return extras[KEY_MESSAGE_BUSINESS_TYPE] == BUSINESS_TYPE_VOIP
    }

    internal fun voipType(extras: Map<String, String>): Int {
        // Stock 7.4.67-C parses voip_type as an integer with 0 as the fallback. The old
        // voice/video aliases accepted payloads that stock rejects and could change lifecycle.
        return extras[KEY_VOIP_TYPE]?.toIntOrNull() ?: VOIP_TYPE_END
    }

    internal fun buildVoipMetadata(
        extras: Map<String, String>,
        targetPackage: String,
    ): Map<String, String> = buildMap {
        put("target_package", targetPackage)
        put("miui.targetPkg", targetPackage)
        listOf(KEY_MESSAGE_BUSINESS_TYPE, KEY_VOIP_TYPE, "mipush_custom_extra").forEach { key ->
            extras[key]?.takeIf(String::isNotEmpty)?.let { value -> put(key, value) }
        }
    }

    private fun sequence(extras: Map<String, String>): Long {
        return extras[KEY_SEQUENCE]?.toLongOrNull() ?: 0L
    }
}
