package io.github.magisk317.mipush.notification
import io.github.magisk317.mipush.common.R as CommonR

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.IconCompat
import io.github.magisk317.mipush.common.notification.iconpack.ICON_PACK_SOURCE_IDENTITY_EXTRA
import io.github.magisk317.mipush.common.notification.iconpack.IconPackResolver
import io.github.magisk317.mipush.common.notification.iconpack.ResolveFailure
import io.github.magisk317.mipush.common.notification.iconpack.ResolveResult
import io.github.magisk317.mipush.common.notification.iconpack.thirdPartyPackSourceIdentity
import co.touchlab.kermit.Logger
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.mipush.notification.NotificationManagerEx
import io.github.magisk317.mipush.service.runtime.MyMIPushNotificationHelper
import io.github.magisk317.mipush.service.runtime.MyMIPushNotificationStyleSupport
import io.github.magisk317.mipush.service.runtime.ExtensionNotificationContract
import com.xiaomi.push.service.MyNotificationIconHelper
import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.push.service.NotificationGroupHelper
import com.xiaomi.push.service.NotificationManagerHelper
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmsf.R
import com.xiaomi.xmsf.stock.StockNotificationMetadataBridge
import io.github.magisk317.mipush.notification.policy.NotificationStyle
import io.github.magisk317.mipush.common.notification.NotificationContentSupport
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.platform.support.XMPushUtils
import io.github.magisk317.mipush.push.pipeline.MockMessageRegistry
import io.github.magisk317.mipush.utils.ColorUtil
import io.github.magisk317.mipush.notification.policy.CustomConfiguration
import io.github.magisk317.mipush.common.utils.ImgUtils
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.platform.support.ManagerUiEntryPoints
import io.github.magisk317.mipush.runtime.PushRuntime

internal object NotificationMockTestSupport {
    private const val TAG = "NotificationController"
    private const val FOCUS_PARAM = "miui.focus.param"
    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    private const val ACTION_SHOW_ISLAND = "io.github.magisk317.mipush.action.SHOW_ISLAND"
    private const val EXTRA_ALLOW_ISLAND_PROXY = "mipush_island_allow_proxy"

fun testMock(
    context: Context,
    kind: io.github.magisk317.mipush.notification.mock.MockNotificationKind,
    packageName: String,
    resolveUserId: (Context, String) -> Int,
    applyStatusBarIcon: (Context, String, NotificationCompat.Builder, Boolean) -> Int,
) {
    PushRuntime.observeNotificationEvent(packageName, "mock_test_build_start", "NotificationController.testMock")
    val kindLabel = context.getString(kind.labelRes)
    val title = context.getString(R.string.debug_test_title, kindLabel)
    val description = context.getString(R.string.debug_test_content, kindLabel) + "\n" + java.util.Date()

    val nm = context.getSystemService(android.app.NotificationManager::class.java)
    val mockChannelId = "xmsf_mock_high"
    val mockChannel = android.app.NotificationChannel(
        mockChannelId,
        context.getString(R.string.mock_channel_name),
        android.app.NotificationManager.IMPORTANCE_HIGH,
    )
    nm.createNotificationChannel(mockChannel)

    val id = (System.currentTimeMillis() / 1000L).toInt()
    val builder = NotificationCompat.Builder(context, mockChannelId)
    val userId = resolveUserId(context, packageName)
    val colorStatusBarIcon = MiPushIslandPreferences.read(context, packageName, userId).colorStatusBarIcon
    val color = applyStatusBarIcon(context, packageName, builder, colorStatusBarIcon)
    builder.setWhen(System.currentTimeMillis())
    builder.setShowWhen(true)
    builder.setAutoCancel(true)

    val tag = "xmsf_mock_${kind.name}"
    NativeNotificationFeatureBuilder.releaseMediaSessionsForTag(
        packageName = packageName,
        tag = tag,
        userId = resolveUserId(context, packageName),
    )
    Logger.withTag(TAG).d { "mock test build kind=${kind.name} pkg=$packageName id=$id tag=$tag" }

    val notifyIntent = ManagerUiEntryPoints.mainActivityIntent(
        context = context,
        startRoute = "events",
    ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
    val notifyPendingIntent = PendingIntent.getActivity(
        context, 0, notifyIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    builder.setContentIntent(notifyPendingIntent)

    val targetExtras = Bundle().apply {
        putString("target_package", packageName)
        putString("miui.targetPkg", packageName)
        putString("xmsf_target_package", packageName)
    }
    builder.addExtras(targetExtras)

    var nativeFeature = NativeNotificationFeatureBuilder.Result.NONE
    when (kind) {
        io.github.magisk317.mipush.notification.mock.MockNotificationKind.PLAIN -> {
            builder.setContentTitle(title)
            builder.setContentText(description)
        }
        io.github.magisk317.mipush.notification.mock.MockNotificationKind.BIG_TEXT -> {
            builder.setContentTitle(title)
            builder.setContentText(description)
            val style = NotificationCompat.BigTextStyle()
            style.bigText("$description\n\nLine 2\nLine 3\nLine 4 (expand to view)")
            style.setBigContentTitle(title)
            style.setSummaryText(context.getString(R.string.mock_bigtext_summary))
            builder.setStyle(style)
        }
        io.github.magisk317.mipush.notification.mock.MockNotificationKind.BIG_PICTURE -> {
            builder.setContentTitle(title)
            builder.setContentText(description)
            val pic = createDemoBitmap(800, 400, 0xFF2962FFu.toInt())
            val style = NotificationCompat.BigPictureStyle()
            style.bigPicture(pic)
            style.setBigContentTitle(title)
            style.setSummaryText(context.getString(R.string.mock_bigpicture_summary))
            builder.setLargeIcon(pic)
            builder.setStyle(style)
        }
        io.github.magisk317.mipush.notification.mock.MockNotificationKind.INBOX -> {
            builder.setContentTitle(title)
            builder.setContentText("$description (3 lines)")
            val style = NotificationCompat.InboxStyle()
            style.setBigContentTitle(title)
            style.addLine(context.getString(R.string.mock_inbox_line1, description))
            style.addLine(context.getString(R.string.mock_inbox_line2))
            style.addLine(context.getString(R.string.mock_inbox_line3))
            style.setSummaryText(context.getString(R.string.mock_inbox_summary))
            builder.setStyle(style)
        }
        io.github.magisk317.mipush.notification.mock.MockNotificationKind.MESSAGING -> {
            val person = androidx.core.app.Person.Builder().setName(context.getString(R.string.mock_messaging_user)).build()
            val style = NotificationCompat.MessagingStyle(person)
            style.setConversationTitle(context.getString(R.string.mock_messaging_conversation))
            style.addMessage(description, System.currentTimeMillis(), person)
            style.addMessage(context.getString(R.string.mock_messaging_reply), System.currentTimeMillis() + 1000, person)
            builder.setStyle(style)
        }
        io.github.magisk317.mipush.notification.mock.MockNotificationKind.MEDIA -> {
            builder.setContentTitle(title)
            builder.setContentText(context.getString(R.string.mock_media_now_playing, description))
            builder.setOngoing(true)
            builder.addAction(CommonR.drawable.ic_notifications_black_24dp, context.getString(R.string.mock_media_prev), notifyPendingIntent)
            builder.addAction(CommonR.drawable.ic_notifications_black_24dp, context.getString(R.string.mock_media_pause), notifyPendingIntent)
            builder.addAction(CommonR.drawable.ic_notifications_black_24dp, context.getString(R.string.mock_media_next), notifyPendingIntent)
        }
        io.github.magisk317.mipush.notification.mock.MockNotificationKind.PROGRESS -> {
            builder.setContentTitle(title)
            builder.setContentText(context.getString(R.string.mock_progress_downloading))
            builder.setProgress(100, 65, false)
            builder.setOngoing(true)
            builder.setAutoCancel(false)
        }
        io.github.magisk317.mipush.notification.mock.MockNotificationKind.HEADS_UP -> {
            builder.setContentTitle(title)
            builder.setContentText(description)
            builder.priority = NotificationCompat.PRIORITY_HIGH
            builder.setCategory(Notification.CATEGORY_ALARM)
            builder.setDefaults(NotificationCompat.DEFAULT_ALL)
        }
        io.github.magisk317.mipush.notification.mock.MockNotificationKind.DYNAMIC_ISLAND -> {
            val mockFocusResult = handleMockFocusSemantic(
                context = context,
                builder = builder,
                kind = kind,
                packageName = packageName,
                notificationId = id,
                notificationTag = tag,
                title = title,
                description = description,
                contentIntent = notifyPendingIntent,
                resolveUserId = resolveUserId,
                style = NotificationStyle.PROMO,
                islandOuterGlow = true,
            )
            if (mockFocusResult.handled) {
                return
            }
            nativeFeature = mockFocusResult.nativeFeature
        }
        io.github.magisk317.mipush.notification.mock.MockNotificationKind.FOCUS_NOTIFICATION,
        io.github.magisk317.mipush.notification.mock.MockNotificationKind.FOCUS_MESSAGE,
        io.github.magisk317.mipush.notification.mock.MockNotificationKind.FOCUS_BANNER,
        io.github.magisk317.mipush.notification.mock.MockNotificationKind.FOCUS_ALERT,
        io.github.magisk317.mipush.notification.mock.MockNotificationKind.FOCUS_PROMO,
        io.github.magisk317.mipush.notification.mock.MockNotificationKind.FOCUS_MEDIA,
        io.github.magisk317.mipush.notification.mock.MockNotificationKind.FOCUS_PROGRESS -> {
            val focusStyle = kind.focusTemplateStyle ?: NotificationStyle.GENERAL
            val focusSpec = mockFocusSpec(kind, title, description)
            val mockFocusResult = handleMockFocusSemantic(
                context = context,
                builder = builder,
                kind = kind,
                packageName = packageName,
                notificationId = id,
                notificationTag = tag,
                title = focusSpec.title,
                description = focusSpec.content,
                contentIntent = notifyPendingIntent,
                resolveUserId = resolveUserId,
                style = focusStyle,
                isOngoing = focusStyle == NotificationStyle.MEDIA || focusStyle == NotificationStyle.PROGRESS,
            )
            if (mockFocusResult.handled) {
                return
            }
            nativeFeature = mockFocusResult.nativeFeature
        }
        io.github.magisk317.mipush.notification.mock.MockNotificationKind.VOIP_INCOMING -> {
            builder.setContentTitle(context.getString(R.string.mock_voip_title))
            builder.setContentText(description)
            builder.priority = NotificationCompat.PRIORITY_MAX
            builder.setCategory(Notification.CATEGORY_CALL)
            builder.setOngoing(true)
            builder.setAutoCancel(false)
            builder.setFullScreenIntent(notifyPendingIntent, true)
            val voipExtras = Bundle()
            voipExtras.putString("notification_style_type", "6")
            voipExtras.putString("msg_busi_type", "voip")
            voipExtras.putString("voip_type", "1")
            builder.addExtras(voipExtras)
            builder.addAction(CommonR.drawable.ic_notifications_black_24dp, context.getString(R.string.mock_voip_accept), notifyPendingIntent)
            builder.addAction(CommonR.drawable.ic_notifications_black_24dp, context.getString(R.string.mock_voip_decline), notifyPendingIntent)
        }
        io.github.magisk317.mipush.notification.mock.MockNotificationKind.LIVE_UPDATE_DELIVERY -> {
            val deliveryTitle = context.getString(R.string.mock_live_update_delivery_title)
            val deliveryDesc = context.getString(R.string.mock_live_update_delivery_desc)
            builder.setContentTitle(deliveryTitle)
            builder.setContentText(deliveryDesc)
            builder.priority = NotificationCompat.PRIORITY_HIGH
            val detection = LiveUpdateDetector.DetectionResult(
                isProgress = true,
                category = LiveUpdateDetector.ProgressCategory.DELIVERY,
                progressPercent = 65,
                progressText = deliveryDesc,
                startLabel = "商家",
                endLabel = "目的地",
                trackerLabel = "配送中"
            )
            val metaInfo = PushMetaInfo()
            metaInfo.setTitle(deliveryTitle)
            metaInfo.setDescription(deliveryDesc)
            ProgressStyleBuilder.applyProgressStyle(context, builder, metaInfo, detection)
            // Simulate progress updates
            Thread {
                val steps = listOf(65, 75, 85, 95, 100)
                val texts = listOf(
                    "骑手已取餐，预计15分钟送达",
                    "骑手距您约800米",
                    "骑手距您约500米",
                    "骑手距您约100米",
                    "骑手已到达，请取餐"
                )
                for (i in steps.indices) {
                    Thread.sleep(3000)
                    val updateBuilder = NotificationCompat.Builder(context, mockChannelId).apply {
                        applyStatusBarIcon(context, packageName, this, colorStatusBarIcon)
                        setWhen(System.currentTimeMillis())
                        setContentTitle(deliveryTitle)
                        setContentText(texts[i])
                        setOngoing(true)
                        setProgress(100, steps[i], false)
                        priority = NotificationCompat.PRIORITY_HIGH
                    }
                    val updateDetection = detection.copy(
                        progressPercent = steps[i],
                        progressText = texts[i]
                    )
                    val updateMetaInfo = PushMetaInfo()
                    updateMetaInfo.setTitle(deliveryTitle)
                    updateMetaInfo.setDescription(texts[i])
                    ProgressStyleBuilder.applyProgressStyle(context, updateBuilder, updateMetaInfo, updateDetection)
                    nm.notify(tag, id, ProgressStyleBuilder.buildNotification(context, updateBuilder))
                }
                // Auto-cancel when complete
                Thread.sleep(2000)
                nm.cancel(tag, id)
            }.start()
        }
    }

    val notification = NativeNotificationFeatureBuilder.buildNotification(context, builder, nativeFeature)
    nm.notify(tag, id, notification)
    Logger.withTag(TAG).d {
        "mock test posted kind=${kind.name} pkg=$packageName id=$id tag=$tag " +
            "focus=${notification.extras.containsKey(FOCUS_PARAM)} " +
            "contentIntent=${notification.contentIntent != null} nativeFeature=${nativeFeature.feature}"
    }
    PushRuntime.observeNotificationEvent(packageName, "mock_test_notification_posted", "NotificationController.testMock")
}

private data class MockFocusSemanticResult(
    val handled: Boolean = false,
    val nativeFeature: NativeNotificationFeatureBuilder.Result = NativeNotificationFeatureBuilder.Result.NONE,
)

private fun handleMockFocusSemantic(
    context: Context,
    builder: NotificationCompat.Builder,
    kind: io.github.magisk317.mipush.notification.mock.MockNotificationKind,
    packageName: String,
    notificationId: Int,
    notificationTag: String,
    title: String,
    description: String,
    contentIntent: PendingIntent,
    resolveUserId: (Context, String) -> Int,
    style: NotificationStyle,
    isOngoing: Boolean = false,
    islandOuterGlow: Boolean = false,
): MockFocusSemanticResult {
    builder.setContentTitle(title)
    builder.setContentText(description)
    if (isOngoing) {
        builder.setOngoing(true)
        builder.setAutoCancel(false)
    }

    val metaInfo = PushMetaInfo().apply {
        setTitle(title)
        setDescription(description)
    }
    val userId = resolveUserId(context, packageName)
    val options = MiPushIslandPreferences.read(context, packageName, userId)
    val generatedFocusParam = MiPushIslandPayloadBuilder.buildFocusParam(
        context = context,
        metaInfo = metaInfo,
        packageName = packageName,
        largeIcon = null,
        options = options,
        styleOverride = style,
    )
    val focusPlan = FocusSemanticTranslator.plan(
        context = context,
        metaInfo = metaInfo,
        packageName = packageName,
        configuredFocusParam = null,
        generatedFocusParam = generatedFocusParam,
        generatedFocusCandidate = generatedFocusParam != null,
    )

    if (focusPlan.allowIslandProxy && NotificationManagerEx.isHooked) {
        Logger.withTag(TAG).d {
            "mock test island broadcast kind=${kind.name} style=$style pkg=$packageName " +
                "id=$notificationId reason=${focusPlan.reason}"
        }
        PushRuntime.observeNotificationEvent(
            packageName,
            "mock_test_island_broadcast",
            "NotificationController.testMock",
        )
        context.sendMockIslandBroadcast(
            title = title,
            description = description,
            sourcePackage = packageName,
            notificationId = notificationId,
            contentIntent = contentIntent,
            resolveUserId = resolveUserId,
            style = style,
            smallOnly = false,
            isOngoing = isOngoing,
            islandOuterGlow = islandOuterGlow,
        )
        return MockFocusSemanticResult(handled = true)
    }

    if (focusPlan.allowIslandProxy) {
        builder.addExtras(
            Bundle().apply {
                putBoolean(EXTRA_ALLOW_ISLAND_PROXY, true)
            },
        )
        return MockFocusSemanticResult()
    }

    val nativeFeature = NativeNotificationFeatureBuilder.apply(
        context = context,
        builder = builder,
        metaInfo = metaInfo,
        packageName = packageName,
        focusPlan = focusPlan,
        styleOverride = style,
        contentIntent = contentIntent,
        notificationKey = NativeNotificationFeatureBuilder.notificationKey(
            packageName,
            notificationId,
            notificationTag,
            resolveUserId(context, packageName),
        ),
    )

    Logger.withTag(TAG).d {
        "mock test focus semantic kind=${kind.name} style=$style pkg=$packageName " +
            "id=$notificationId reason=${focusPlan.reason} nativeFeature=${nativeFeature.feature}"
    }
    return MockFocusSemanticResult(nativeFeature = nativeFeature)
}

private data class MockFocusSpec(
    val title: String,
    val content: String,
)

private fun mockFocusSpec(
    kind: io.github.magisk317.mipush.notification.mock.MockNotificationKind,
    fallbackTitle: String,
    fallbackContent: String,
): MockFocusSpec {
    return when (kind.focusTemplateStyle) {
        NotificationStyle.MESSAGE -> MockFocusSpec(
            title = "Alice",
            content = "focus chat message: $fallbackContent",
        )
        NotificationStyle.BANNER -> MockFocusSpec(
            title = fallbackTitle,
            content = "featured banner update: $fallbackContent",
        )
        NotificationStyle.ALERT -> MockFocusSpec(
            title = "focus countdown reminder",
            content = "countdown 15 minutes before the meeting",
        )
        NotificationStyle.PROMO -> MockFocusSpec(
            title = "focus coupon sale",
            content = "coupon discount 65% off for template testing",
        )
        NotificationStyle.MEDIA -> MockFocusSpec(
            title = "focus now playing",
            content = "media cover template: $fallbackContent",
        )
        NotificationStyle.PROGRESS -> MockFocusSpec(
            title = "focus download progress",
            content = "download progress 65%",
        )
        else -> MockFocusSpec(
            title = fallbackTitle,
            content = fallbackContent,
        )
    }
}

private fun Context.sendMockIslandBroadcast(
    title: String,
    description: String,
    sourcePackage: String,
    notificationId: Int,
    contentIntent: PendingIntent,
    resolveUserId: (Context, String) -> Int,
    style: NotificationStyle = NotificationStyle.GENERAL,
    smallOnly: Boolean = false,
    isOngoing: Boolean = false,
    islandOuterGlow: Boolean = true,
) {
    val userId = resolveUserId(this, sourcePackage)
    val options = MiPushIslandPreferences.read(this, sourcePackage, userId)
    val icon = MiPushIslandPayloadBuilder.resolveNotificationIcon(this, sourcePackage, null)
    Logger.withTag(TAG).d {
        "mock island broadcast sourcePkg=$sourcePackage notificationId=$notificationId " +
            "timeout=${options.timeoutSecs} firstFloat=${options.firstFloat} enableFloat=${options.enableFloat}"
    }
    sendBroadcast(
        Intent(ACTION_SHOW_ISLAND).apply {
            setPackage(SYSTEM_UI_PACKAGE)
            putExtra("title", title)
            putExtra("content", description)
            putExtra("icon", icon)
            putExtra("notificationId", notificationId)
            putExtra("timeoutSecs", options.timeoutSecs)
            putExtra("firstFloat", options.firstFloat)
            putExtra("enableFloat", options.enableFloat)
            putExtra("showNotification", false)
            putExtra("sourcePackage", sourcePackage)
            putExtra("userId", userId)
            putExtra("sourceChannelId", "mipush_mock_island")
            putExtra("contentIntent", contentIntent)
            putExtra("isOngoing", isOngoing)
            putExtra("showIslandIcon", true)
            putExtra("clearBeforePost", true)
            putExtra("islandOuterGlow", islandOuterGlow)
            putExtra("style", style.name)
            putExtra("smallOnly", smallOnly)
        },
    )
}


private fun createDemoBitmap(width: Int, height: Int, color: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(color)
    val paint = android.graphics.Paint().apply {
        this.color = Color.WHITE
        textSize = (height / 4).toFloat()
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    canvas.drawText("XMSF", (width / 2).toFloat(), height / 2 + paint.textSize / 3, paint)
    return bitmap
}
}
