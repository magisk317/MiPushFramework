package com.xiaomi.push.service

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import androidx.core.app.NotificationCompat
import com.magisk317.push.hook.ExplicitHookBridge
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL

internal object MyMIPushNotificationIntentSupport {
    private const val NOTIFICATION_ACTION_BUTTON_PLACE_MID = 2
    private const val NOTIFICATION_ACTION_BUTTON_PLACE_RIGHT = 3
    private const val NOTIFICATION_STYLE_BUTTON_LEFT_INTENT_CLASS = "notification_style_button_left_intent_class"
    private const val NOTIFICATION_STYLE_BUTTON_LEFT_INTENT_URI = "notification_style_button_left_intent_uri"
    private const val NOTIFICATION_STYLE_BUTTON_LEFT_NAME = "notification_style_button_left_name"
    private const val NOTIFICATION_STYLE_BUTTON_LEFT_NOTIFY_EFFECT = "notification_style_button_left_notify_effect"
    private const val NOTIFICATION_STYLE_BUTTON_LEFT_WEB_URI = "notification_style_button_left_web_uri"
    private const val NOTIFICATION_STYLE_BUTTON_MID_INTENT_CLASS = "notification_style_button_mid_intent_class"
    private const val NOTIFICATION_STYLE_BUTTON_MID_INTENT_URI = "notification_style_button_mid_intent_uri"
    private const val NOTIFICATION_STYLE_BUTTON_MID_NAME = "notification_style_button_mid_name"
    private const val NOTIFICATION_STYLE_BUTTON_MID_NOTIFY_EFFECT = "notification_style_button_mid_notify_effect"
    private const val NOTIFICATION_STYLE_BUTTON_MID_WEB_URI = "notification_style_button_mid_web_uri"
    private const val NOTIFICATION_STYLE_BUTTON_RIGHT_INTENT_CLASS = "notification_style_button_right_intent_class"
    private const val NOTIFICATION_STYLE_BUTTON_RIGHT_INTENT_URI = "notification_style_button_right_intent_uri"
    private const val NOTIFICATION_STYLE_BUTTON_RIGHT_NAME = "notification_style_button_right_name"
    private const val NOTIFICATION_STYLE_BUTTON_RIGHT_NOTIFY_EFFECT = "notification_style_button_right_notify_effect"
    private const val NOTIFICATION_STYLE_BUTTON_RIGHT_WEB_URI = "notification_style_button_right_web_uri"
    private const val FLAG_IMMUTABLE_UPDATE_CURRENT =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    fun addStyleActions(
        builder: NotificationCompat.Builder,
        context: Context,
        pkgName: String,
        metaExtra: Map<String, String>
    ) {
        val left = getStylePendingIntent(context, pkgName, 1, metaExtra)
        if (left != null && !TextUtils.isEmpty(metaExtra[NOTIFICATION_STYLE_BUTTON_LEFT_NAME])) {
            builder.addAction(0, metaExtra[NOTIFICATION_STYLE_BUTTON_LEFT_NAME], left)
        }
        val mid = getStylePendingIntent(context, pkgName, NOTIFICATION_ACTION_BUTTON_PLACE_MID, metaExtra)
        if (mid != null && !TextUtils.isEmpty(metaExtra[NOTIFICATION_STYLE_BUTTON_MID_NAME])) {
            builder.addAction(0, metaExtra[NOTIFICATION_STYLE_BUTTON_MID_NAME], mid)
        }
        val right = getStylePendingIntent(context, pkgName, NOTIFICATION_ACTION_BUTTON_PLACE_RIGHT, metaExtra)
        if (right != null && !TextUtils.isEmpty(metaExtra[NOTIFICATION_STYLE_BUTTON_RIGHT_NAME])) {
            builder.addAction(0, metaExtra[NOTIFICATION_STYLE_BUTTON_RIGHT_NAME], right)
        }
    }

    fun carryPendingIntentForTemporarilyWhitelisted(
        context: Context,
        container: XmPushActionContainer,
        builder: NotificationCompat.Builder
    ) {
        val targetIntent = MyMIPushNotificationHelper.buildTargetIntentWithoutExtras(
            container.packageName,
            container.metaInfo
        )
        val pendingIntent = PendingIntent.getService(
            context,
            0,
            targetIntent,
            FLAG_IMMUTABLE_UPDATE_CURRENT
        )
        builder.extras.putParcelable("mipush.target", pendingIntent)
    }

    fun buildClickedPendingIntent(
        context: Context,
        container: XmPushActionContainer,
        decryptedContent: ByteArray,
        notificationId: Int,
        extra: Bundle?
    ): PendingIntent? {
        val metaInfo = container.metaInfo ?: return null
        val urlJump = resolveClickedUrl(metaInfo)
        if (!TextUtils.isEmpty(urlJump)) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(urlJump)
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            return PendingIntent.getActivity(context, notificationId, intent, FLAG_IMMUTABLE_UPDATE_CURRENT)
        }

        val serviceIntent = Intent().apply {
            component = ComponentName(
                "com.xiaomi.xmsf",
                if (MIPushNotificationHelper.isBusinessMessage(container)) {
                    "com.xiaomi.mipush.sdk.PushMessageHandler"
                } else {
                    "com.xiaomi.push.sdk.MyPushMessageHandler"
                }
            )
            putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, decryptedContent)
            putExtra(MIPushNotificationHelper.FROM_NOTIFICATION, true)
            if (extra != null) {
                putExtras(extra)
            }
            addCategory(metaInfo.notifyId.toString())
        }

        val configuration = com.magisk317.XMPushUtils.getConfiguration(metaInfo)
        val activityIntent = getSdkIntent(context, container)
        if (!configuration.useClickedActivity(false) || activityIntent == null) {
            return PendingIntent.getService(context, notificationId, serviceIntent, FLAG_IMMUTABLE_UPDATE_CURRENT)
        }

        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activityIntent.putExtra("mipush_serviceIntent", serviceIntent)
        activityIntent.putExtras(serviceIntent)
        return PendingIntent.getActivity(context, notificationId, activityIntent, FLAG_IMMUTABLE_UPDATE_CURRENT)
    }

    fun getSdkIntent(context: Context, container: XmPushActionContainer): Intent? {
        val pkgName = container.packageName
        val extra = container.metaInfo.extra ?: return null
        if (!extra.containsKey(PushConstants.EXTRA_PARAM_NOTIFY_EFFECT)) {
            return null
        }

        val intent = buildIntentFromEffect(context, pkgName, extra) ?: return null
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val available = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
        ExplicitHookBridge.onIntentAvailabilityChecked(
            intent,
            available,
            "MyMIPushNotificationIntentSupport.getSdkIntent"
        )
        if (!available || inFetchIntentBlackList(pkgName)) {
            return null
        }
        return intent
    }

    fun startServicePendingIntent(
        context: Context,
        container: XmPushActionContainer,
        pushMetaInfo: PushMetaInfo?,
        payload: ByteArray
    ): PendingIntent? {
        if (pushMetaInfo == null) {
            return null
        }
        val localIntent = if (MIPushNotificationHelper.isBusinessMessage(container)) {
            Intent().setComponent(
                ComponentName("com.xiaomi.xmsf", "com.xiaomi.mipush.sdk.PushMessageHandler")
            )
        } else {
            Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE).setComponent(
                ComponentName(container.packageName, "com.xiaomi.mipush.sdk.PushMessageHandler")
            )
        }
        localIntent.putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, payload)
        localIntent.putExtra(MIPushNotificationHelper.FROM_NOTIFICATION, true)
        localIntent.addCategory(pushMetaInfo.notifyId.toString())
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(context, 0, localIntent, FLAG_IMMUTABLE_UPDATE_CURRENT)
        } else {
            PendingIntent.getService(context, 0, localIntent, FLAG_IMMUTABLE_UPDATE_CURRENT)
        }
    }

    private fun resolveClickedUrl(metaInfo: PushMetaInfo): String? {
        if (!TextUtils.isEmpty(metaInfo.url)) {
            return metaInfo.url
        }
        return metaInfo.extra?.get(PushConstants.EXTRA_PARAM_WEB_URI)
    }

    private fun getStylePendingIntent(
        context: Context,
        pkgName: String,
        place: Int,
        metaExtra: Map<String, String>?
    ): PendingIntent? {
        val intent = if (metaExtra == null) null else getPendingIntentFromExtra(context, pkgName, place, metaExtra)
        return if (intent == null) null else PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getPendingIntentFromExtra(
        context: Context,
        pkgName: String,
        place: Int,
        extra: Map<String, String>
    ): Intent? {
        val typeKey = when {
            place < NOTIFICATION_ACTION_BUTTON_PLACE_MID -> NOTIFICATION_STYLE_BUTTON_LEFT_NOTIFY_EFFECT
            place < NOTIFICATION_ACTION_BUTTON_PLACE_RIGHT -> NOTIFICATION_STYLE_BUTTON_MID_NOTIFY_EFFECT
            else -> NOTIFICATION_STYLE_BUTTON_RIGHT_NOTIFY_EFFECT
        }
        val typeId = extra[typeKey]
        if (TextUtils.isEmpty(typeId)) {
            return null
        }

        val intent = when (typeId) {
            PushConstants.NOTIFICATION_CLICK_DEFAULT -> getLaunchIntent(context, pkgName)
            PushConstants.NOTIFICATION_CLICK_INTENT -> getExplicitIntentForButton(pkgName, place, extra)
            PushConstants.NOTIFICATION_CLICK_WEB_PAGE -> getWebIntent(place, extra)
            else -> null
        } ?: return null

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            val resolveInfo: ResolveInfo? =
                context.packageManager.resolveActivity(intent, Intent.FLAG_ACTIVITY_NO_ANIMATION)
            ExplicitHookBridge.onIntentAvailabilityChecked(
                intent,
                resolveInfo != null,
                "MyMIPushNotificationIntentSupport.getPendingIntentFromExtra"
            )
            if (resolveInfo != null) {
                return intent
            }
        } catch (e: Exception) {
            logError("Cause: ${e.message}", e)
        }
        return null
    }

    private fun buildIntentFromEffect(
        context: Context,
        pkgName: String,
        extra: Map<String, String>
    ): Intent? {
        return when (extra[PushConstants.EXTRA_PARAM_NOTIFY_EFFECT]) {
            PushConstants.NOTIFICATION_CLICK_DEFAULT -> getLaunchIntent(context, pkgName)
            PushConstants.NOTIFICATION_CLICK_INTENT -> getSdkExplicitIntent(pkgName, extra)
            PushConstants.NOTIFICATION_CLICK_WEB_PAGE -> getSdkWebIntent(extra)
            else -> null
        }
    }

    private fun getLaunchIntent(context: Context, pkgName: String): Intent? {
        return try {
            context.packageManager.getLaunchIntentForPackage(pkgName)
        } catch (e: Exception) {
            logError("Cause: ${e.message}", e)
            null
        }
    }

    private fun getSdkExplicitIntent(pkgName: String, extra: Map<String, String>): Intent? {
        if (extra.containsKey(PushConstants.EXTRA_PARAM_INTENT_URI)) {
            val intentStr = extra[PushConstants.EXTRA_PARAM_INTENT_URI] ?: return null
            return try {
                Intent.parseUri(intentStr, Intent.URI_INTENT_SCHEME).apply {
                    `package` = pkgName
                }
            } catch (e: URISyntaxException) {
                logError("Cause: ${e.message}", e)
                null
            }
        }
        if (!extra.containsKey(PushConstants.EXTRA_PARAM_CLASS_NAME)) {
            return null
        }
        val className = extra[PushConstants.EXTRA_PARAM_CLASS_NAME] ?: return null
        return Intent().apply {
            component = ComponentName(pkgName, className)
            try {
                if (extra.containsKey(PushConstants.EXTRA_PARAM_INTENT_FLAG)) {
                    flags = extra[PushConstants.EXTRA_PARAM_INTENT_FLAG]!!.toInt()
                }
            } catch (e: NumberFormatException) {
                logError("Cause by intent_flag: ${e.message}", e)
            }
        }
    }

    private fun getSdkWebIntent(extra: Map<String, String>): Intent? {
        return normalizeWebUri(extra[PushConstants.EXTRA_PARAM_WEB_URI])?.let { uri ->
            Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(uri)
            }
        }
    }

    private fun getExplicitIntentForButton(
        pkgName: String,
        place: Int,
        extra: Map<String, String>
    ): Intent? {
        val intentUriKey = when {
            place < NOTIFICATION_ACTION_BUTTON_PLACE_MID -> NOTIFICATION_STYLE_BUTTON_LEFT_INTENT_URI
            place < NOTIFICATION_ACTION_BUTTON_PLACE_RIGHT -> NOTIFICATION_STYLE_BUTTON_MID_INTENT_URI
            else -> NOTIFICATION_STYLE_BUTTON_RIGHT_INTENT_URI
        }
        val intentClassKey = when {
            place < NOTIFICATION_ACTION_BUTTON_PLACE_MID -> NOTIFICATION_STYLE_BUTTON_LEFT_INTENT_CLASS
            place < NOTIFICATION_ACTION_BUTTON_PLACE_RIGHT -> NOTIFICATION_STYLE_BUTTON_MID_INTENT_CLASS
            else -> NOTIFICATION_STYLE_BUTTON_RIGHT_INTENT_CLASS
        }
        if (extra.containsKey(intentUriKey)) {
            val intentStr = extra[intentUriKey] ?: return null
            return try {
                Intent.parseUri(intentStr, Intent.URI_INTENT_SCHEME).apply {
                    `package` = pkgName
                }
            } catch (e: URISyntaxException) {
                logError("Cause: ${e.message}", e)
                null
            }
        }
        if (!extra.containsKey(intentClassKey)) {
            return null
        }
        val className = extra[intentClassKey] ?: return null
        return Intent().apply {
            component = ComponentName(pkgName, className)
        }
    }

    private fun getWebIntent(place: Int, extra: Map<String, String>): Intent? {
        val webUriKey = when {
            place < NOTIFICATION_ACTION_BUTTON_PLACE_MID -> NOTIFICATION_STYLE_BUTTON_LEFT_WEB_URI
            place < NOTIFICATION_ACTION_BUTTON_PLACE_RIGHT -> NOTIFICATION_STYLE_BUTTON_MID_WEB_URI
            else -> NOTIFICATION_STYLE_BUTTON_RIGHT_WEB_URI
        }
        return normalizeWebUri(extra[webUriKey])?.let { uri ->
            Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(uri)
            }
        }
    }

    private fun normalizeWebUri(uri: String?): String? {
        if (TextUtils.isEmpty(uri)) {
            return null
        }
        var candidate = uri!!.trim()
        if (!(candidate.startsWith("http://") || candidate.startsWith("https://"))) {
            candidate = "http://$candidate"
        }
        return try {
            val protocol = URL(candidate).protocol
            if (protocol == "http" || protocol == "https") candidate else null
        } catch (e: MalformedURLException) {
            logError("Cause: ${e.message}", e)
            null
        }
    }

    private fun inFetchIntentBlackList(pkg: String): Boolean {
        return pkg.contains("youku")
    }

    private fun logError(message: String, throwable: Throwable? = null) {
        MyMIPushNotificationLogs.logger.e(message, throwable)
    }
}
