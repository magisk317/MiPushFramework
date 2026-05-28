package io.github.magisk317.mipush.service.runtime

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

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
import io.github.aakira.napier.Napier
import io.github.magisk317.mipush.push.hook.ExplicitHookBridge
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.push.service.PushConstants
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL

internal object MyMIPushNotificationIntentSupport {
    private const val TAG = "MyNotificationIntent"

    private const val KEY_NOTIFICATION_STYLE_TYPE = "notification_style_type"
    private const val STYLE_TYPE_VOIP = "6"
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

    private val FLAG_IMMUTABLE_UPDATE_CURRENT =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    internal data class StyleActionKeys(
        val name: String,
        val notifyEffect: String,
        val intentUri: String,
        val intentClass: String,
        val webUri: String
    )

    fun addStyleActions(
        builder: NotificationCompat.Builder,
        context: Context,
        pkgName: String,
        metaExtra: Map<String, String>
    ) {
        for (place in 1..NOTIFICATION_ACTION_BUTTON_PLACE_RIGHT) {
            val title = getStyleActionTitle(place, metaExtra)
            if (TextUtils.isEmpty(title)) continue
            val pendingIntent = getStylePendingIntent(context, pkgName, place, metaExtra)
            if (pendingIntent != null) {
                builder.addAction(0, title, pendingIntent)
            }
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

        val activityIntent = getSdkIntent(context, container)
        if (!shouldUseSdkActivityClick(activityIntent != null)) {
            return PendingIntent.getService(context, notificationId, serviceIntent, FLAG_IMMUTABLE_UPDATE_CURRENT)
        }

        activityIntent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activityIntent.putExtra("mipush_serviceIntent", serviceIntent)
        activityIntent.putExtras(serviceIntent)
        return PendingIntent.getActivity(context, notificationId, activityIntent, FLAG_IMMUTABLE_UPDATE_CURRENT)
    }

    internal fun shouldUseSdkActivityClick(sdkIntentAvailable: Boolean): Boolean = false

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
            place,
            intent,
            FLAG_IMMUTABLE_UPDATE_CURRENT
        )
    }

    private fun getPendingIntentFromExtra(
        context: Context,
        pkgName: String,
        place: Int,
        extra: Map<String, String>
    ): Intent? {
        val keys = styleActionKeys(place, extra)
        val typeId = extra[keys.notifyEffect]
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
            logE("Failed to resolve activity: ${e.message}", e)
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
            logE("Failed to get launch intent: ${e.message}", e)
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
                logE("Failed to parse intent URI: ${e.message}", e)
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
                logE("Cause by intent_flag: ${e.message}", e)
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
        val keys = styleActionKeys(place, extra)
        if (extra.containsKey(keys.intentUri)) {
            val intentStr = extra[keys.intentUri] ?: return null
            return try {
                Intent.parseUri(intentStr, Intent.URI_INTENT_SCHEME).apply {
                    `package` = pkgName
                }
            } catch (e: URISyntaxException) {
                logE("Failed to parse button intent URI: ${e.message}", e)
                null
            }
        }
        if (!extra.containsKey(keys.intentClass)) {
            return null
        }
        val className = extra[keys.intentClass] ?: return null
        return Intent().apply {
            component = ComponentName(pkgName, className)
        }
    }

    private fun getWebIntent(place: Int, extra: Map<String, String>): Intent? {
        val keys = styleActionKeys(place, extra)
        return normalizeWebUri(extra[keys.webUri])?.let { uri ->
            Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(uri)
            }
        }
    }

    internal fun getStyleActionTitle(place: Int, extra: Map<String, String>): String? =
        extra[styleActionKeys(place, extra).name]

    internal fun styleActionKeys(place: Int, extra: Map<String, String>): StyleActionKeys {
        if (extra[KEY_NOTIFICATION_STYLE_TYPE] == STYLE_TYPE_VOIP) {
            return StyleActionKeys(
                name = "cust_btn_${place}_n",
                notifyEffect = "cust_btn_${place}_ne",
                intentUri = "cust_btn_${place}_iu",
                intentClass = "cust_btn_${place}_ic",
                webUri = "cust_btn_${place}_wu"
            )
        }
        return when {
            place < NOTIFICATION_ACTION_BUTTON_PLACE_MID -> StyleActionKeys(
                name = NOTIFICATION_STYLE_BUTTON_LEFT_NAME,
                notifyEffect = NOTIFICATION_STYLE_BUTTON_LEFT_NOTIFY_EFFECT,
                intentUri = NOTIFICATION_STYLE_BUTTON_LEFT_INTENT_URI,
                intentClass = NOTIFICATION_STYLE_BUTTON_LEFT_INTENT_CLASS,
                webUri = NOTIFICATION_STYLE_BUTTON_LEFT_WEB_URI
            )
            place < NOTIFICATION_ACTION_BUTTON_PLACE_RIGHT -> StyleActionKeys(
                name = NOTIFICATION_STYLE_BUTTON_MID_NAME,
                notifyEffect = NOTIFICATION_STYLE_BUTTON_MID_NOTIFY_EFFECT,
                intentUri = NOTIFICATION_STYLE_BUTTON_MID_INTENT_URI,
                intentClass = NOTIFICATION_STYLE_BUTTON_MID_INTENT_CLASS,
                webUri = NOTIFICATION_STYLE_BUTTON_MID_WEB_URI
            )
            else -> StyleActionKeys(
                name = NOTIFICATION_STYLE_BUTTON_RIGHT_NAME,
                notifyEffect = NOTIFICATION_STYLE_BUTTON_RIGHT_NOTIFY_EFFECT,
                intentUri = NOTIFICATION_STYLE_BUTTON_RIGHT_INTENT_URI,
                intentClass = NOTIFICATION_STYLE_BUTTON_RIGHT_INTENT_CLASS,
                webUri = NOTIFICATION_STYLE_BUTTON_RIGHT_WEB_URI
            )
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
            logE("Malformed web URI: ${e.message}", e)
            null
        }
    }

    private fun inFetchIntentBlackList(pkg: String): Boolean {
        // Known problematic packages
        return pkg.contains("youku") || pkg.contains("tudou")
    }
}
