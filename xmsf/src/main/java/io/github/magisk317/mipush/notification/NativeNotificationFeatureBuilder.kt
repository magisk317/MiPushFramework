package io.github.magisk317.mipush.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmsf.R
import io.github.aakira.napier.Napier
import io.github.magisk317.mipush.common.NotificationStyle
import java.util.concurrent.ConcurrentHashMap

internal object NativeNotificationFeatureBuilder {
    private const val TAG = "NativeNotificationFeatureBuilder"
    private const val EXTRA_NATIVE_FEATURE = "xmsf.native_feature"
    private const val EXTRA_NATIVE_STYLE = "xmsf.native_feature.style"

    enum class Feature {
        NONE,
        CATEGORY,
        MESSAGING,
        MEDIA,
        PROGRESS,
    }

    data class Result(
        val feature: Feature,
        val style: NotificationStyle?,
        val mediaSessionToken: MediaSession.Token? = null,
        val preventsAutoCancel: Boolean = false,
    ) {
        val usesNativeFeature: Boolean
            get() = feature != Feature.NONE

        companion object {
            val NONE = Result(Feature.NONE, null)
        }
    }

    fun apply(
        context: Context,
        builder: NotificationCompat.Builder,
        metaInfo: PushMetaInfo,
        packageName: String,
        focusPlan: FocusSemanticTranslator.Plan,
        styleOverride: NotificationStyle? = null,
        contentIntent: PendingIntent? = null,
        notificationKey: String? = null,
    ): Result {
        if (focusPlan.attachMiuiFocusExtras || focusPlan.allowIslandProxy) {
            return Result.NONE
        }

        val style = styleOverride ?: focusPlan.semantic?.style
        val nativeCategory = nativeCategory(style, focusPlan)
        if (nativeCategory != null) {
            builder.setCategory(nativeCategory)
        }

        if (style == NotificationStyle.ALERT) {
            val preventsAutoCancel = applyAlertStyle(builder, metaInfo)
            markNativeFeature(builder, Feature.CATEGORY, style)
            return Result(Feature.CATEGORY, style, preventsAutoCancel = preventsAutoCancel)
        }

        if (focusPlan.useNativeProgress && focusPlan.nativeDetection != null) {
            Napier.d(
                "Applying native progress feature pkg=$packageName " +
                    "source=${focusPlan.semantic?.source} category=${focusPlan.nativeDetection.category} " +
                    "progress=${focusPlan.nativeDetection.progressPercent} reason=${focusPlan.reason}",
                tag = TAG,
            )
            ProgressStyleBuilder.applyProgressStyle(
                context = context,
                builder = builder,
                metaInfo = metaInfo,
                detectionResult = focusPlan.nativeDetection,
                semanticStyle = focusPlan.semantic?.semanticStyle
                    ?: FocusSemanticTranslator.semanticStyleForCategory(focusPlan.nativeDetection.category),
            )
            markNativeFeature(builder, Feature.PROGRESS, style ?: NotificationStyle.PROGRESS)
            return Result(
                Feature.PROGRESS,
                style ?: NotificationStyle.PROGRESS,
                preventsAutoCancel = true,
            )
        }

        return when (style) {
            NotificationStyle.MESSAGE -> {
                applyMessagingStyle(builder, metaInfo)
                markNativeFeature(builder, Feature.MESSAGING, style)
                Result(Feature.MESSAGING, style)
            }
            NotificationStyle.MEDIA -> {
                val token = applyMediaCompatFields(
                    context = context,
                    builder = builder,
                    metaInfo = metaInfo,
                    packageName = packageName,
                    contentIntent = contentIntent,
                    notificationKey = notificationKey,
                )
                markNativeFeature(builder, Feature.MEDIA, style)
                Result(Feature.MEDIA, style, token, preventsAutoCancel = true)
            }
            NotificationStyle.ALERT,
            NotificationStyle.PROMO,
            NotificationStyle.BANNER,
            NotificationStyle.GENERAL -> {
                if (nativeCategory != null) {
                    markNativeFeature(builder, Feature.CATEGORY, style)
                    Result(Feature.CATEGORY, style)
                } else {
                    Result.NONE
                }
            }
            NotificationStyle.PROGRESS -> Result.NONE
            null -> {
                if (nativeCategory != null) {
                    markNativeFeature(builder, Feature.CATEGORY, null)
                    Result(Feature.CATEGORY, null)
                } else {
                    Result.NONE
                }
            }
        }
    }

    fun buildNotification(
        context: Context,
        builder: NotificationCompat.Builder,
        nativeFeature: Result,
    ): Notification {
        val notification = ProgressStyleBuilder.buildNotification(context, builder)
        return if (nativeFeature.feature == Feature.MEDIA) {
            applyPlatformMediaStyle(context, notification, nativeFeature.mediaSessionToken)
        } else {
            notification
        }
    }

    fun notificationKey(packageName: String, notificationId: Int, tag: String?): String {
        return "$packageName:$notificationId:${tag.orEmpty()}"
    }

    fun releaseMediaSession(packageName: String, notificationId: Int, tag: String?) {
        releaseMediaSession(notificationKey(packageName, notificationId, tag))
    }

    fun releaseMediaSessionsForTag(packageName: String, tag: String?) {
        val prefix = "$packageName:"
        val suffix = ":${tag.orEmpty()}"
        mediaSessions.keys
            .filter { it.startsWith(prefix) && it.endsWith(suffix) }
            .forEach(::releaseMediaSession)
    }

    private fun releaseMediaSession(key: String) {
        mediaSessions.remove(key)?.runCatching {
            isActive = false
            release()
        }
    }

    private fun nativeCategory(
        style: NotificationStyle?,
        focusPlan: FocusSemanticTranslator.Plan,
    ): String? {
        if (focusPlan.useNativeProgress) {
            return Notification.CATEGORY_PROGRESS
        }
        return when (style) {
            NotificationStyle.MESSAGE -> Notification.CATEGORY_MESSAGE
            NotificationStyle.MEDIA -> Notification.CATEGORY_TRANSPORT
            NotificationStyle.PROGRESS -> Notification.CATEGORY_PROGRESS
            NotificationStyle.ALERT -> Notification.CATEGORY_ALARM
            NotificationStyle.PROMO -> Notification.CATEGORY_PROMO
            NotificationStyle.BANNER -> Notification.CATEGORY_RECOMMENDATION
            NotificationStyle.GENERAL -> Notification.CATEGORY_STATUS
            null -> null
        }
    }

    private fun applyMessagingStyle(
        builder: NotificationCompat.Builder,
        metaInfo: PushMetaInfo,
    ) {
        val sender = metaInfo.title?.takeIf { it.isNotBlank() } ?: "Message"
        val body = metaInfo.description?.takeIf { it.isNotBlank() } ?: sender
        val person = Person.Builder().setName(sender).build()
        builder.setStyle(
            NotificationCompat.MessagingStyle(person)
                .setConversationTitle(sender)
                .addMessage(body, System.currentTimeMillis(), person),
        )
        builder.priority = NotificationCompat.PRIORITY_HIGH
    }

    private fun applyAlertStyle(
        builder: NotificationCompat.Builder,
        metaInfo: PushMetaInfo,
    ): Boolean {
        builder.setCategory(Notification.CATEGORY_ALARM)
        builder.priority = NotificationCompat.PRIORITY_HIGH
        builder.setOnlyAlertOnce(true)
        val durationMs = extractDurationMs("${metaInfo.title.orEmpty()} ${metaInfo.description.orEmpty()}")
        if (durationMs > 0) {
            builder.setWhen(System.currentTimeMillis() + durationMs)
            builder.setUsesChronometer(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setChronometerCountDown(true)
            }
            builder.setOngoing(true)
            builder.setAutoCancel(false)
            return true
        }
        return false
    }

    private fun applyMediaCompatFields(
        context: Context,
        builder: NotificationCompat.Builder,
        metaInfo: PushMetaInfo,
        packageName: String,
        contentIntent: PendingIntent?,
        notificationKey: String?,
    ): MediaSession.Token? {
        builder.setOngoing(true)
        builder.setAutoCancel(false)
        builder.setOnlyAlertOnce(true)
        builder.setShowWhen(false)
        builder.priority = NotificationCompat.PRIORITY_HIGH
        if (contentIntent != null) {
            builder.addAction(R.drawable.ic_notifications_black_24dp, context.getString(R.string.mock_media_prev), contentIntent)
            builder.addAction(R.drawable.ic_notifications_black_24dp, context.getString(R.string.mock_media_pause), contentIntent)
            builder.addAction(R.drawable.ic_notifications_black_24dp, context.getString(R.string.mock_media_next), contentIntent)
        }
        return notificationKey?.let {
            mediaSessionToken(context, it, packageName, metaInfo, contentIntent)
        }
    }

    private fun applyPlatformMediaStyle(
        context: Context,
        notification: Notification,
        mediaSessionToken: MediaSession.Token?,
    ): Notification {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return notification
        }
        return runCatching {
            val actionCount = notification.actions?.size ?: 0
            val compactActions = IntArray(minOf(actionCount, 3)) { it }
            val style = Notification.MediaStyle()
            if (mediaSessionToken != null) {
                style.setMediaSession(mediaSessionToken)
            }
            if (compactActions.isNotEmpty()) {
                style.setShowActionsInCompactView(*compactActions)
            }
            Notification.Builder.recoverBuilder(context, notification)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setStyle(style)
                .setOngoing(true)
                .setAutoCancel(false)
                .build()
        }.getOrElse { error ->
            Napier.d("Failed to apply platform media style: ${error.message}", error, tag = TAG)
            notification
        }
    }

    private fun mediaSessionToken(
        context: Context,
        key: String,
        packageName: String,
        metaInfo: PushMetaInfo,
        contentIntent: PendingIntent?,
    ): MediaSession.Token {
        val session = mediaSessions.compute(key) { _, existing ->
            existing ?: MediaSession(context.applicationContext, "MiPushFramework:$packageName").apply {
                @Suppress("DEPRECATION")
                setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            }
        }!!
        contentIntent?.let(session::setSessionActivity)
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, metaInfo.title.orEmpty())
                .putString(MediaMetadata.METADATA_KEY_ARTIST, metaInfo.description.orEmpty())
                .build(),
        )
        session.setPlaybackState(
            PlaybackState.Builder()
                .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                .setActions(
                    PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_SKIP_TO_NEXT,
                )
                .build(),
        )
        session.isActive = true
        return session.sessionToken
    }

    private fun extractDurationMs(text: String): Long {
        Regex("(\\d+)\\s*(?:分钟|min|mins|minute|minutes)").find(text)?.let {
            return it.groupValues[1].toLongOrNull()?.takeIf { value -> value in 1..1440 }
                ?.times(60_000L) ?: 0L
        }
        Regex("(\\d+)\\s*(?:小时|hour|hours|hr|hrs)").find(text)?.let {
            return it.groupValues[1].toLongOrNull()?.takeIf { value -> value in 1..72 }
                ?.times(3_600_000L) ?: 0L
        }
        Regex("(\\d+)\\s*(?:秒|sec|second|seconds)").find(text)?.let {
            return it.groupValues[1].toLongOrNull()?.takeIf { value -> value in 1..3600 }
                ?.times(1_000L) ?: 0L
        }
        return 0L
    }

    private fun markNativeFeature(
        builder: NotificationCompat.Builder,
        feature: Feature,
        style: NotificationStyle?,
    ) {
        val extras = builder.extras
        extras.putString(EXTRA_NATIVE_FEATURE, feature.name)
        extras.putString(EXTRA_NATIVE_STYLE, style?.name)
        builder.addExtras(extras)
    }

    private val mediaSessions = ConcurrentHashMap<String, MediaSession>()
}
