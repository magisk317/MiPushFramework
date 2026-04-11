package com.xiaomi.push.service

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.notification.BuilderCompat
import com.xiaomi.push.service.clientReport.ReportConstants
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL

internal object MIPushNotificationActionSupport {
    const val DEFAULT_PENDING_INTENT_FLAGS: Int =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private const val STYLE_TYPE = "notification_style_type"
    private const val STYLE_COLORFUL = "3"
    private const val STYLE_BANNER = "4"
    private const val STYLE_BUTTON_LEFT_NOTIFY_EFFECT = "notification_style_button_left_notify_effect"
    private const val STYLE_BUTTON_LEFT_INTENT_URI = "notification_style_button_left_intent_uri"
    private const val STYLE_BUTTON_LEFT_INTENT_CLASS = "notification_style_button_left_intent_class"
    private const val STYLE_BUTTON_LEFT_WEB_URI = "notification_style_button_left_web_uri"
    private const val STYLE_BUTTON_LEFT_NAME = "notification_style_button_left_name"
    private const val STYLE_BUTTON_MID_NOTIFY_EFFECT = "notification_style_button_mid_notify_effect"
    private const val STYLE_BUTTON_MID_INTENT_URI = "notification_style_button_mid_intent_uri"
    private const val STYLE_BUTTON_MID_INTENT_CLASS = "notification_style_button_mid_intent_class"
    private const val STYLE_BUTTON_MID_WEB_URI = "notification_style_button_mid_web_uri"
    private const val STYLE_BUTTON_MID_NAME = "notification_style_button_mid_name"
    private const val STYLE_BUTTON_RIGHT_NOTIFY_EFFECT = "notification_style_button_right_notify_effect"
    private const val STYLE_BUTTON_RIGHT_INTENT_URI = "notification_style_button_right_intent_uri"
    private const val STYLE_BUTTON_RIGHT_INTENT_CLASS = "notification_style_button_right_intent_class"
    private const val STYLE_BUTTON_RIGHT_WEB_URI = "notification_style_button_right_web_uri"
    private const val STYLE_BUTTON_RIGHT_NAME = "notification_style_button_right_name"

    @JvmStatic
    fun getClickedPendingIntent(
        context: Context,
        container: XmPushActionContainer,
        metaInfo: PushMetaInfo?,
        payload: ByteArray,
        notificationId: Int,
    ): PendingIntent? {
        val eventType = when {
            MIPushNotificationHelper.isNormalNotificationMessage(container) -> 1000
            MIPushNotificationHelper.isBusinessMessage(container) -> 3000
            else -> -1
        }
        val messageId = metaInfo?.id ?: ""
        if (metaInfo != null && !TextUtils.isEmpty(metaInfo.url)) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(metaInfo.url)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("messageId", messageId)
                putExtra(ReportConstants.EVENT_MESSAGE_TYPE, eventType)
            }
            return PendingIntent.getActivity(context, 0, intent, DEFAULT_PENDING_INTENT_FLAGS)
        }

        val targetIntent = if (MIPushNotificationHelper.isBusinessMessage(container)) {
            Intent().apply {
                component = ComponentName(PushConstants.PUSH_SERVICE_PACKAGE_NAME, "com.xiaomi.mipush.sdk.PushMessageHandler")
                putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, payload)
                putExtra(MIPushNotificationHelper.FROM_NOTIFICATION, true)
                addCategory(notificationId.toString())
                addCategory(messageId)
            }
        } else {
            Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE).apply {
                component = ComponentName(container.packageName, "com.xiaomi.mipush.sdk.PushMessageHandler")
                putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, payload)
                putExtra(MIPushNotificationHelper.FROM_NOTIFICATION, true)
                addCategory(notificationId.toString())
                addCategory(messageId)
            }
        }
        targetIntent.putExtra("messageId", messageId)
        targetIntent.putExtra(ReportConstants.EVENT_MESSAGE_TYPE, eventType)

        val bridgeActivity = ComponentName(container.packageName, "com.xiaomi.mipush.sdk.BridgeActivity")
        if (!ComponentHelper.checkActivity(context, bridgeActivity)) {
            return PendingIntent.getService(context, 0, targetIntent, DEFAULT_PENDING_INTENT_FLAGS)
        }
        val bridgeIntent = Intent().apply {
            component = bridgeActivity
            addFlags(276824064)
            putExtra(PushConstants.MIPUSH_EXTRA_INTENT_PAYLOAD, targetIntent)
            addCategory(notificationId.toString())
            addCategory(messageId)
        }
        return PendingIntent.getActivity(context, 0, bridgeIntent, DEFAULT_PENDING_INTENT_FLAGS)
    }

    @JvmStatic
    fun setNotificationStyleAction(
        builder: BuilderCompat,
        context: Context,
        packageName: String,
        extra: Map<String, String>?,
    ): BuilderCompat {
        if (extra == null || TextUtils.equals(STYLE_COLORFUL, extra[STYLE_TYPE]) || TextUtils.equals(STYLE_BANNER, extra[STYLE_TYPE])) {
            return builder
        }
        val left = getStylePendingIntent(
            context,
            packageName,
            extra,
            STYLE_BUTTON_LEFT_NOTIFY_EFFECT,
            STYLE_BUTTON_LEFT_INTENT_URI,
            STYLE_BUTTON_LEFT_INTENT_CLASS,
            STYLE_BUTTON_LEFT_WEB_URI,
        )
        if (left != null && !TextUtils.isEmpty(extra[STYLE_BUTTON_LEFT_NAME])) {
            builder.addAction(0, extra[STYLE_BUTTON_LEFT_NAME], left)
        }
        val middle = getStylePendingIntent(
            context,
            packageName,
            extra,
            STYLE_BUTTON_MID_NOTIFY_EFFECT,
            STYLE_BUTTON_MID_INTENT_URI,
            STYLE_BUTTON_MID_INTENT_CLASS,
            STYLE_BUTTON_MID_WEB_URI,
        )
        if (middle != null && !TextUtils.isEmpty(extra[STYLE_BUTTON_MID_NAME])) {
            builder.addAction(0, extra[STYLE_BUTTON_MID_NAME], middle)
        }
        val right = getStylePendingIntent(
            context,
            packageName,
            extra,
            STYLE_BUTTON_RIGHT_NOTIFY_EFFECT,
            STYLE_BUTTON_RIGHT_INTENT_URI,
            STYLE_BUTTON_RIGHT_INTENT_CLASS,
            STYLE_BUTTON_RIGHT_WEB_URI,
        )
        if (right != null && !TextUtils.isEmpty(extra[STYLE_BUTTON_RIGHT_NAME])) {
            builder.addAction(0, extra[STYLE_BUTTON_RIGHT_NAME], right)
        }
        return builder
    }

    @JvmStatic
    fun getStylePendingIntent(
        context: Context,
        packageName: String,
        extra: Map<String, String>?,
        effectKey: String,
        intentUriKey: String,
        intentClassKey: String,
        webUriKey: String,
    ): PendingIntent? {
        val intent = getPendingIntentFromExtra(context, packageName, extra, effectKey, intentUriKey, intentClassKey, webUriKey) ?: return null
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun getPendingIntentFromExtra(
        context: Context,
        packageName: String,
        extra: Map<String, String>?,
        effectKey: String,
        intentUriKey: String,
        intentClassKey: String,
        webUriKey: String,
    ): Intent? {
        val effect = extra?.get(effectKey)
        if (TextUtils.isEmpty(effect)) {
            return null
        }
        val intent: Intent? = when (effect) {
            PushConstants.NOTIFICATION_CLICK_DEFAULT -> {
                runCatching { context.packageManager.getLaunchIntentForPackage(packageName) }
                    .onFailure { MyLog.e("Cause:${it.message}") }
                    .getOrNull()
            }

            PushConstants.NOTIFICATION_CLICK_INTENT -> {
                when {
                    extra.containsKey(intentUriKey) -> {
                        val intentUri = extra[intentUriKey]
                        if (intentUri != null) {
                            try {
                                Intent.parseUri(intentUri, 1).apply { setPackage(packageName) }
                            } catch (e: URISyntaxException) {
                                MyLog.e("Cause:${e.message}")
                                null
                            }
                        } else {
                            null
                        }
                    }
                    extra.containsKey(intentClassKey) -> {
                        extra[intentClassKey]?.let { className ->
                            Intent().apply {
                                component = ComponentName(packageName, className)
                            }
                        }
                    }
                    else -> null
                }
            }

            PushConstants.NOTIFICATION_CLICK_WEB_PAGE -> {
                val webUri = extra[webUriKey]
                if (!TextUtils.isEmpty(webUri)) {
                    var normalized = webUri!!.trim()
                    if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
                        normalized = "http://$normalized"
                    }
                    try {
                        val protocol = URL(normalized).protocol
                        if (protocol == "http" || protocol == "https") {
                            Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse(normalized)
                                NotificationUtils.setXiaomiBrowserAsDefault(context, this)
                            }
                        } else {
                            null
                        }
                    } catch (e: MalformedURLException) {
                        MyLog.e("Cause:${e.message}")
                        null
                    }
                } else {
                    null
                }
            }

            else -> null
        }
        intent ?: return null
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            if (context.packageManager.resolveActivity(intent, 65536) != null) intent else null
        } catch (e: Exception) {
            MyLog.e("Cause:${e.message}")
            null
        }
    }
}
