package io.github.magisk317.mipush.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import androidx.core.app.NotificationCompat
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.aakira.napier.Napier
import com.xiaomi.xmsf.R

/**
 * Handles VoIP call notification styles.
 *
 * Based on stock xmsf 7.4.67 `com.xiaomi.push.voip` package.
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
    private const val MAX_SEQUENCE_CACHE_SIZE = 128

    private val sequenceCache = object : LinkedHashMap<String, Long>(MAX_SEQUENCE_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
            return size > MAX_SEQUENCE_CACHE_SIZE
        }
    }

    @JvmStatic
    fun isVoipNotification(metaInfo: PushMetaInfo?): Boolean {
        val extras = metaInfo?.extra ?: return false
        return isVoipPayload(extras) && voipType(extras) in VOIP_TYPE_VOICE..VOIP_TYPE_VIDEO
    }

    @JvmStatic
    fun isVoipEndEvent(metaInfo: PushMetaInfo?): Boolean {
        val extras = metaInfo?.extra ?: return false
        return isVoipPayload(extras) && voipType(extras) == VOIP_TYPE_END
    }

    @JvmStatic
    fun shouldDropStale(metaInfo: PushMetaInfo?, packageName: String): Boolean {
        val extras = metaInfo?.extra ?: return false
        if (!isVoipPayload(extras)) return false

        val sequence = sequence(extras)
        synchronized(sequenceCache) {
            val previous = sequenceCache[packageName] ?: 0L
            if (previous > sequence) {
                Napier.d("drop stale VoIP notification pkg=$packageName sequence=$sequence previous=$previous", tag = TAG)
                return true
            }
            sequenceCache[packageName] = sequence
            return false
        }
    }

    @JvmStatic
    fun buildVoipNotification(
        context: Context,
        container: XmPushActionContainer,
        metaInfo: PushMetaInfo,
        notificationId: Int,
        fullScreenPendingIntent: PendingIntent?
    ): NotificationCompat.Builder? {
        val extras = metaInfo.extra ?: return null
        val voipType = voipType(extras)
        if (voipType !in VOIP_TYPE_VOICE..VOIP_TYPE_VIDEO) return null
        val packageName = container.packageName

        val channelId = NotificationController.getExistsChannelId(context, metaInfo, packageName)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notifications_black_24dp)
            .setContentTitle(metaInfo.title ?: "Incoming Call")
            .setContentText(SweetTagHandler.renderFtHtmlIfNeeded(metaInfo.description))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(Notification.CATEGORY_CALL)
            .setAutoCancel(false)
            .setOngoing(true)

        if (fullScreenPendingIntent != null) {
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
        }

        val largeIcon = getLargeIcon(context, metaInfo)
        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon)
        }

        val targetBundle = Bundle()
        targetBundle.putString("target_package", packageName)
        targetBundle.putString("miui.targetPkg", packageName)
        targetBundle.putString(KEY_MESSAGE_BUSINESS_TYPE, BUSINESS_TYPE_VOIP)
        targetBundle.putInt(KEY_VOIP_TYPE, voipType)
        builder.addExtras(targetBundle)

        Napier.d("built VoIP notification type=$voipType pkg=$packageName id=$notificationId", tag = TAG)
        return builder
    }

    private fun getLargeIcon(context: Context, metaInfo: PushMetaInfo): android.graphics.Bitmap? {
        val iconUri = metaInfo.extra?.get("large_icon") ?: return null
        return try {
            val uri = android.net.Uri.parse(iconUri)
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

    internal fun isVoipPayload(extras: Map<String, String>): Boolean {
        return extras[KEY_NOTIFICATION_STYLE_TYPE] == STYLE_TYPE_VOIP ||
            extras[KEY_MESSAGE_BUSINESS_TYPE] == BUSINESS_TYPE_VOIP
    }

    internal fun voipType(extras: Map<String, String>): Int {
        return when (extras[KEY_VOIP_TYPE]?.lowercase()) {
            "voice" -> VOIP_TYPE_VOICE
            "video" -> VOIP_TYPE_VIDEO
            else -> extras[KEY_VOIP_TYPE]?.toIntOrNull() ?: VOIP_TYPE_END
        }
    }

    private fun sequence(extras: Map<String, String>): Long {
        return extras[KEY_SEQUENCE]?.toLongOrNull() ?: 0L
    }
}
