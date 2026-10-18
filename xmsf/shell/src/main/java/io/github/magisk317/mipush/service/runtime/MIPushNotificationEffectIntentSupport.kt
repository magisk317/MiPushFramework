package io.github.magisk317.mipush.service.runtime

import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.common.utils.logE
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.text.TextUtils
import androidx.core.app.NotificationCompat
import io.github.magisk317.mipush.push.hook.ExplicitHookBridge
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.push.service.PushConstants
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL

/**
 * Payload `notify_effect` intent construction and style-button seams of
 * [MIPushNotificationIntentSupport], split out of the facade object so it keeps
 * only the click-routing decision chain and the identity helpers.
 *
 * Same-package extension functions on [MIPushNotificationIntentSupport]: call
 * sites keep the plain `MIPushNotificationIntentSupport.foo(...)` syntax (an
 * import is required outside this package), and inside this file the implicit
 * receiver carries the generic logW/logE extensions as well. The startability
 * gates these intents pass through
 * ([MIPushNotificationIntentSupport.isReceiverBuiltClickTarget] and
 * [MIPushNotificationIntentSupport.isStartableFromXmsfProcess]) stay with the
 * facade and are internal for this file's use; the style-button key constants
 * and [StyleActionKeys] moved here because only this pipeline reads them.
 */

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

internal data class StyleActionKeys(
    val name: String,
    val notifyEffect: String,
    val intentUri: String,
    val intentClass: String,
    val webUri: String
)

internal fun MIPushNotificationIntentSupport.addStyleActions(
    builder: NotificationCompat.Builder,
    context: Context,
    pkgName: String,
    notificationId: Int,
    metaExtra: Map<String, String>
) {
    for (place in 1..NOTIFICATION_ACTION_BUTTON_PLACE_RIGHT) {
        val title = getStyleActionTitle(place, metaExtra)
        if (TextUtils.isEmpty(title)) continue
        val pendingIntent = getStylePendingIntent(context, pkgName, place, metaExtra, notificationId)
        if (pendingIntent != null) {
            builder.addAction(0, title, pendingIntent)
        }
    }
}

internal fun MIPushNotificationIntentSupport.carryPendingIntentForTemporarilyWhitelisted(
    context: Context,
    container: XmPushActionContainer,
    notificationId: Int,
    builder: NotificationCompat.Builder
) {
    val targetIntent = MIPushNotificationPublishHelper.buildTargetIntentWithoutExtras(
        container.packageName,
        container.metaInfo
    ).apply {
        data = pendingIntentIdentity(container.packageName, notificationId, 0)
    }
    val pendingIntent = PendingIntent.getService(
        context,
        pendingIntentRequestCode(container.packageName, notificationId, 0),
        targetIntent,
        FLAG_IMMUTABLE_UPDATE_CURRENT
    )
    builder.extras.putParcelable("mipush.target", pendingIntent)
}

internal fun MIPushNotificationIntentSupport.getSdkIntent(
    context: Context,
    container: XmPushActionContainer,
    reportAvailability: Boolean = true,
): Intent? {
    val pkgName = container.packageName
    val extra = container.metaInfo.extra ?: return null
    if (!extra.containsKey(PushConstants.EXTRA_PARAM_NOTIFY_EFFECT)) {
        return null
    }

    val intent = buildIntentFromEffect(context, pkgName, extra) ?: return null
    if (isReceiverBuiltClickTarget(intent)) {
        // A payload pointing at a receiver-built click target carries no landing parameters
        // of its own; starting that activity from XMSF lands the click in an activity that
        // cannot resolve its target and dies silently. Report the intent as unavailable so
        // callers fall back to the app-side routes (BridgeActivity, the XMSF service click
        // dispatch, or the launcher pull-up), where the app's own receiver rebuilds the
        // intent. Known case: China Mobile's cn.richinfo.richpush.ClickResultActivity.
        logW(
            "$TAG receiver-built click target, skip: pkg=$pkgName " +
                "component=${intent.component?.flattenToShortString()}"
        )
        return null
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    if (reportAvailability) {
        ExplicitHookBridge.onIntentAvailabilityChecked(
            intent,
            resolveInfo != null,
            "MIPushNotificationIntentSupport.getSdkIntent"
        )
    }
    val activityInfo = resolveInfo?.activityInfo
    if (activityInfo == null) {
        return null
    }
    // Require the resolved activity to live inside the target package. An implicit intent (for
    // example an ACTION_VIEW web jump) can resolve to a generic activity of another app such as
    // a browser or a system viewer; without this check the click would start the wrong app.
    // Only an activity inside the target package counts as usable.
    if (activityInfo.packageName != pkgName) {
        logW(
            "$TAG sdk intent resolved outside target package, skip: pkg=$pkgName " +
                "resolved=${activityInfo.packageName}/${activityInfo.name}"
        )
        return null
    }
    if (!isStartableFromXmsfProcess(context, activityInfo)) {
        // The payload-declared click activity lives in the target app's UID while XMSF runs
        // as its own, non-platform-signed UID. A non-exported component (for example Baidu
        // push's com.baidu.android.pushservice.xmproxy.XmNotifyActivity) rejects the cross-UID
        // start with SecurityException, which consumes the click and launches nothing. Report
        // the intent as unavailable so callers fall through to the app-side delivery routes
        // (BridgeActivity, then the XMSF PushMessageHandler click dispatch) instead.
        logW(
            "$TAG sdk activity not startable from xmsf, skip: pkg=$pkgName " +
                "component=${activityInfo.packageName}/${activityInfo.name} " +
                "exported=${activityInfo.exported} permission=${activityInfo.permission}"
        )
        return null
    }
    return intent
}

private fun MIPushNotificationIntentSupport.buildIntentFromEffect(
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

internal fun MIPushNotificationIntentSupport.getLaunchIntent(context: Context, pkgName: String): Intent? {
    return try {
        context.packageManager.getLaunchIntentForPackage(pkgName)
    } catch (e: Exception) {
        logE("Failed to get launch intent: ${e.message}", e)
        null
    }
}

internal fun MIPushNotificationIntentSupport.getSdkExplicitIntent(pkgName: String, extra: Map<String, String>): Intent? {
    if (extra.containsKey(PushConstants.EXTRA_PARAM_INTENT_URI)) {
        val intentStr = extra[PushConstants.EXTRA_PARAM_INTENT_URI] ?: return null
        return try {
            constrainIntentToPackage(Intent.parseUri(intentStr, Intent.URI_INTENT_SCHEME), pkgName)
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

internal fun MIPushNotificationIntentSupport.getSdkWebIntent(extra: Map<String, String>): Intent? {
    return normalizeWebUri(extra[PushConstants.EXTRA_PARAM_WEB_URI])?.let { uri ->
        Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(uri)
        }
    }
}

internal fun MIPushNotificationIntentSupport.getExplicitIntentForButton(
    pkgName: String,
    place: Int,
    extra: Map<String, String>
): Intent? {
    val keys = styleActionKeys(place, extra)
    if (extra.containsKey(keys.intentUri)) {
        val intentStr = extra[keys.intentUri] ?: return null
        return try {
            constrainIntentToPackage(Intent.parseUri(intentStr, Intent.URI_INTENT_SCHEME), pkgName)
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

/**
 * An explicit component wins over Intent.package during resolution. Reject a payload that
 * tries to point an explicit component (including a selector component) outside its target
 * application before adding the target package constraint.
 */
internal fun MIPushNotificationIntentSupport.constrainIntentToPackage(intent: Intent, packageName: String): Intent? {
    if (packageName.isBlank()) return null
    val components = listOfNotNull(intent.component, intent.selector?.component)
    if (components.any { it.packageName != packageName }) return null
    return intent.apply { `package` = packageName }
}

internal fun MIPushNotificationIntentSupport.getWebIntent(place: Int, extra: Map<String, String>): Intent? {
    val keys = styleActionKeys(place, extra)
    return normalizeWebUri(extra[keys.webUri])?.let { uri ->
        Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(uri)
        }
    }
}

internal fun MIPushNotificationIntentSupport.getStyleActionTitle(place: Int, extra: Map<String, String>): String? =
    extra[styleActionKeys(place, extra).name]

internal fun MIPushNotificationIntentSupport.styleActionKeys(place: Int, extra: Map<String, String>): StyleActionKeys {
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

private fun MIPushNotificationIntentSupport.normalizeWebUri(uri: String?): String? {
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
