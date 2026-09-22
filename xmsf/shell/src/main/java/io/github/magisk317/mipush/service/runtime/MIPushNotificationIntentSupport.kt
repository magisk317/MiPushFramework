package io.github.magisk317.mipush.service.runtime

import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.magisk317.mipush.push.hook.ExplicitHookBridge
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import com.xiaomi.mipush.sdk.MiPushMessage
import com.xiaomi.mipush.sdk.PushMessageHelper
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionSendMessage
import com.xiaomi.push.service.ComponentHelper
import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.push.service.PushConstants
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL

internal object MIPushNotificationIntentSupport {
    private const val TAG = "MyNotificationIntent"
    internal const val EXTRA_STYLE_TARGET_INTENT = "mipush_style_target_intent"

    internal fun shouldUseLauncherFallback(packageName: String?): Boolean {
        val pkg = packageName?.takeIf(String::isNotBlank) ?: return false
        // Per-app fallback lives in the runtime store (REGISTERED_APPLICATION.click_fallback_enabled,
        // default off). A store failure must never break click routing, so it degrades to the
        // normal SDK route instead of the fallback.
        return runCatching { RegisteredApplicationDb.isClickFallbackEnabled(pkg) }.getOrDefault(false)
    }

    private const val BRIDGE_ACTIVITY_CLASS = "com.xiaomi.mipush.sdk.BridgeActivity"
    private val BRIDGE_ACTIVITY_FLAGS =
        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS

    // In-app push SDKs whose click targets must be rebuilt by the target app's own message
    // receiver instead of being carried by the payload (see isReceiverBuiltClickTarget).
    // Extend only when another SDK shows the same "bare component needs receiver-built
    // data" signature.
    private val RECEIVER_BUILT_CLICK_TARGET_PREFIXES = listOf("cn.richinfo.richpush.")

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

    fun carryPendingIntentForTemporarilyWhitelisted(
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

    /**
     * Retrieves the target service PendingIntent created with the notification and clones it
     * through the captured active notification so Android can carry the notification's temporary
     * background-start authorization into the target service.
     */
    internal fun cloneTargetPendingIntentForBackgroundActivityStart(
        context: Context,
        container: XmPushActionContainer,
        notificationId: Int,
    ): PendingIntent? {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) return null
        val metaInfo = container.metaInfo ?: return null
        val targetIntent = MIPushNotificationPublishHelper.buildTargetIntentWithoutExtras(
            container.packageName,
            metaInfo,
        ).apply {
            data = pendingIntentIdentity(container.packageName, notificationId, 0)
        }
        val pendingIntent = PendingIntent.getService(
            context,
            pendingIntentRequestCode(container.packageName, notificationId, 0),
            targetIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return null
        return BackgroundActivityStartEnabler.clonePendingIntentForBackgroundActivityStart(pendingIntent)
    }

    fun buildClickedPendingIntent(
        context: Context,
        container: XmPushActionContainer,
        decryptedContent: ByteArray,
        notificationId: Int,
        extra: Bundle?,
        sendMessage: XmPushActionSendMessage?,
        targetPackageName: String = MIPushNotificationHelper.getTargetPackage(container),
    ): PendingIntent? {
        val metaInfo = container.metaInfo ?: return null
        val messageId = metaInfo.id.orEmpty()
        val requestCode = pendingIntentRequestCode(
            container.packageName,
            notificationId,
            messageId.hashCode(),
        )
        val urlJump = resolveClickedUrl(metaInfo)
        if (!TextUtils.isEmpty(urlJump)) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(urlJump)
            applyPendingIntentIdentity(intent, container.packageName, notificationId, messageId)
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            logClickRoute("url", container.packageName, notificationId)
            return PendingIntent.getActivity(context, requestCode, intent, FLAG_IMMUTABLE_UPDATE_CURRENT)
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
            applyPendingIntentIdentity(this, container.packageName, notificationId, messageId)
        }

        if (shouldUseLauncherFallback(targetPackageName)) {
            buildLauncherFallbackPendingIntent(
                context = context,
                packageName = targetPackageName,
                notificationId = notificationId,
                messageId = messageId,
            )?.let {
                logClickRoute("launcher_fallback", targetPackageName, notificationId)
                return it
            }
        }

        val activityIntent = getSdkIntent(context, container)
        if (shouldUseSdkActivityClick(activityIntent != null)) {
            activityIntent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activityIntent.putExtra("mipush_serviceIntent", serviceIntent)
            activityIntent.putExtras(serviceIntent)
            buildKeyMessage(sendMessage, container)?.let { message ->
                activityIntent.putExtra(PushMessageHelper.KEY_MESSAGE, message)
            }
            applyPendingIntentIdentity(activityIntent, container.packageName, notificationId, messageId)
            // applyPendingIntentIdentity stamps data=mipush-action://... onto payload intents that
            // carry no data of their own. Intent filters without any <data> declaration (for
            // example Meituan's HWPushDetailActivity for dpmtpush.action.xiaomipush) stop matching
            // once that URI is attached, so AMS fails the click with "unable to resolve Intent"
            // (result code=-91) even though the same intent resolved cleanly inside getSdkIntent
            // before the identity was applied. Re-resolve the final intent here; if the identity
            // broke matching, drop the data (keeping the identity as Intent.identifier, which
            // never participates in filter matching) and re-check. If it still does not resolve,
            // fall through to the app-side BridgeActivity / XMSF service dispatch routes.
            if (isClickIntentResolvable(context, activityIntent)) {
                logClickRoute("sdk_activity", container.packageName, notificationId)
                return PendingIntent.getActivity(context, requestCode, activityIntent, FLAG_IMMUTABLE_UPDATE_CURRENT)
            }
            val identity = pendingIntentIdentity(
                container.packageName,
                notificationId,
                messageId,
            ).toString()
            activityIntent.data = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                activityIntent.identifier = identity
            }
            if (isClickIntentResolvable(context, activityIntent)) {
                logW(
                    "$TAG sdk click identity data dropped to keep resolution: pkg=${container.packageName} " +
                        "action=${activityIntent.action}"
                )
                logClickRoute("sdk_activity_nodata", container.packageName, notificationId)
                return PendingIntent.getActivity(context, requestCode, activityIntent, FLAG_IMMUTABLE_UPDATE_CURRENT)
            }
            logW("$TAG sdk click intent unresolved after identity drop, fall through: pkg=${container.packageName}")
        }

        // Prefer the target app's own BridgeActivity so the click launches in the target
        // process (a foreground user action, allowed by AMS) instead of XMSF starting the
        // target's PushMessageHandler from the background (blocked on Samsung/AOSP as
        // "Background start not allowed"). Mirrors stock MIPushNotificationActionSupport.
        val bridgePendingIntent = buildBridgePendingIntent(
            context, container, decryptedContent, notificationId, extra
        )
        if (bridgePendingIntent != null) {
            logClickRoute("bridge_activity", container.packageName, notificationId)
            return bridgePendingIntent
        }

        logClickRoute("xmsf_service", container.packageName, notificationId)
        return PendingIntent.getService(context, requestCode, serviceIntent, FLAG_IMMUTABLE_UPDATE_CURRENT)
    }

    private fun buildKeyMessage(
        sendMessage: XmPushActionSendMessage?,
        container: XmPushActionContainer,
    ): MiPushMessage? {
        if (sendMessage == null) return null
        return runCatching {
            PushMessageHelper.generateMessage(sendMessage, container.metaInfo, true)
        }.onFailure {
            logW("$TAG key_message generation failed: ${it.message ?: it.javaClass.simpleName}")
        }.getOrNull()
    }

    /**
     * Builds an Activity PendingIntent targeting the destination app's `BridgeActivity`, carrying a
     * target-pointed `PushMessageHandler` intent as [PushConstants.MIPUSH_EXTRA_INTENT_PAYLOAD].
     * Returns null when the target app has no usable BridgeActivity, in which case the caller keeps
     * the legacy XMSF service fallback.
     */
    private fun buildBridgePendingIntent(
        context: Context,
        container: XmPushActionContainer,
        decryptedContent: ByteArray,
        notificationId: Int,
        extra: Bundle?
    ): PendingIntent? {
        val metaInfo = container.metaInfo ?: return null
        val bridgeActivity = ComponentName(container.packageName, BRIDGE_ACTIVITY_CLASS)
        if (!ComponentHelper.checkActivity(context, bridgeActivity)) {
            return null
        }

        val messageId = metaInfo.id ?: ""
        val targetIntent = if (MIPushNotificationHelper.isBusinessMessage(container)) {
            Intent().apply {
                component = ComponentName(
                    PushConstants.PUSH_SERVICE_PACKAGE_NAME,
                    "com.xiaomi.mipush.sdk.PushMessageHandler"
                )
            }
        } else {
            Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE).apply {
                component = ComponentName(
                    container.packageName,
                    "com.xiaomi.mipush.sdk.PushMessageHandler"
                )
            }
        }.apply {
            putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, decryptedContent)
            putExtra(MIPushNotificationHelper.FROM_NOTIFICATION, true)
            if (extra != null) {
                putExtras(extra)
            }
            addCategory(metaInfo.notifyId.toString())
            addCategory(messageId)
        }

        val bridgeIntent = Intent().apply {
            component = bridgeActivity
            addFlags(BRIDGE_ACTIVITY_FLAGS)
            putExtra(PushConstants.MIPUSH_EXTRA_INTENT_PAYLOAD, targetIntent)
            addCategory(metaInfo.notifyId.toString())
            addCategory(messageId)
            applyPendingIntentIdentity(this, container.packageName, notificationId, messageId)
        }
        return PendingIntent.getActivity(
            context,
            pendingIntentRequestCode(container.packageName, notificationId, messageId.hashCode()),
            bridgeIntent,
            FLAG_IMMUTABLE_UPDATE_CURRENT
        )
    }

    private fun buildLauncherFallbackPendingIntent(
        context: Context,
        packageName: String?,
        notificationId: Int,
        messageId: String,
    ): PendingIntent? {
        val targetPackage = packageName?.takeIf(String::isNotBlank) ?: return null
        val launcher = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            `package` = targetPackage
        }
        // MAIN/LAUNCHER entries do not need CATEGORY_DEFAULT; using MATCH_DEFAULT_ONLY
        // incorrectly rejects normal launcher activities on Android package managers.
        val activityInfo = context.packageManager.resolveActivity(launcher, 0)
            ?.activityInfo
            ?: return null
        val explicitLauncher = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(activityInfo.packageName, activityInfo.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            applyPendingIntentIdentity(this, targetPackage, notificationId, messageId)
        }
        return PendingIntent.getActivity(
            context,
            pendingIntentRequestCode(targetPackage, notificationId, messageId.hashCode()),
            explicitLauncher,
            FLAG_IMMUTABLE_UPDATE_CURRENT,
        )
    }

    private fun logClickRoute(route: String, packageName: String?, notificationId: Int) {
        logD("$TAG click route=$route pkg=$packageName notificationId=$notificationId")
        MagiskOtel.event(
            name = "push.dispatch",
            attributes = buildMap {
                put("result", "ok")
                put("duration_ms", "0")
                put("process", "xmsf")
                put("stage", "click_route")
                put("reason", route)
                if (!packageName.isNullOrBlank()) {
                    put("target_package", packageName)
                }
            },
            statusOk = true,
        )
    }

    internal fun shouldUseSdkActivityClick(sdkIntentAvailable: Boolean): Boolean = sdkIntentAvailable

    fun getSdkIntent(context: Context, container: XmPushActionContainer): Intent? {
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
        ExplicitHookBridge.onIntentAvailabilityChecked(
            intent,
            resolveInfo != null,
            "MIPushNotificationIntentSupport.getSdkIntent"
        )
        val activityInfo = resolveInfo?.activityInfo
        if (activityInfo == null) {
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

    /**
     * Detects click targets owned by a third-party in-app push SDK whose landing intent is
     * rebuilt by the target app's own message receiver instead of being carried by the payload.
     *
     * The RichPush SDK (package prefix `cn.richinfo.richpush`, embedded by apps such as China
     * Mobile) delivers notification clicks through `ClickResultActivity`, which reads a
     * `richpush://` data URI that only the app's own `XiaoMiMessageReceiver` constructs from the
     * push payload. When the payload's `intent_uri` names such an activity as a bare component —
     * no data, no extras — the direct sdk_activity route can never reproduce the landing
     * parameters (and [applyPendingIntentIdentity] occupies the data slot with the tracking
     * identity anyway), so the click must be dispatched to the app-side receiver instead.
     * Bare-component payloads of other SDKs (QQ's JumpActivity, Alipay's MPaaSNcActivity) are
     * not affected: they consume the MiPush SDK extras attached by the sdk_activity route.
     */
    private fun isReceiverBuiltClickTarget(intent: Intent): Boolean {
        if (intent.data != null) return false
        val extras = intent.extras
        if (extras != null && !extras.isEmpty) return false
        val className = intent.component?.className ?: return false
        return RECEIVER_BUILT_CLICK_TARGET_PREFIXES.any { className.startsWith(it) }
    }

    /**
     * XMSF starts payload-declared click activities from its own UID, which is not
     * platform-signed and holds no START_ANY_ACTIVITY privilege. Such an activity is only usable
     * when it is exported and, when permission-guarded, when XMSF holds that permission.
     */
    private fun isStartableFromXmsfProcess(context: Context, activityInfo: ActivityInfo): Boolean {
        if (!activityInfo.exported) return false
        val permission = activityInfo.permission
        if (permission.isNullOrBlank()) return true
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * Re-resolves a fully built click intent (identity data included) against PackageManager.
     *
     * Guards the sdk_activity route against the tracking identity added by
     * [applyPendingIntentIdentity]: a payload-declared activity whose intent filter declares no
     * <data> (Meituan HWPushDetailActivity is the known case) resolves before the identity is
     * attached and fails afterwards, which only surfaces as an AMS "unable to resolve Intent"
     * (result code=-91) at click time.
     */
    private fun isClickIntentResolvable(context: Context, intent: Intent): Boolean =
        try {
            val resolveInfo =
                context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ExplicitHookBridge.onIntentAvailabilityChecked(
                intent,
                resolveInfo != null,
                "MIPushNotificationIntentSupport.isClickIntentResolvable",
            )
            resolveInfo != null
        } catch (e: Exception) {
            logE("Failed to resolve sdk click intent: ${e.message}", e)
            false
        }

    /**
     * Returns the Activity intent shape that can safely be persisted by the stock BoxMessage
     * surface. Stock rejects service/broadcast PendingIntents for box messages.
     */
    fun buildBoxActivityIntent(
        context: Context,
        container: XmPushActionContainer,
        payload: ByteArray,
    ): Intent? {
        getSdkIntent(context, container)?.let { return it }
        val metaInfo = container.metaInfo ?: return null
        val packageName = container.packageName?.takeIf(String::isNotBlank) ?: return null
        val bridgeActivity = ComponentName(packageName, BRIDGE_ACTIVITY_CLASS)
        if (!ComponentHelper.checkActivity(context, bridgeActivity)) return null

        val targetIntent = if (MIPushNotificationHelper.isBusinessMessage(container)) {
            Intent().setComponent(
                ComponentName(PushConstants.PUSH_SERVICE_PACKAGE_NAME, "com.xiaomi.mipush.sdk.PushMessageHandler"),
            )
        } else {
            Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE).setComponent(
                ComponentName(packageName, "com.xiaomi.mipush.sdk.PushMessageHandler"),
            )
        }.apply {
            putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, payload)
            putExtra(MIPushNotificationHelper.FROM_NOTIFICATION, true)
            addCategory(metaInfo.notifyId.toString())
            addCategory(metaInfo.id.orEmpty())
        }
        return Intent().apply {
            component = bridgeActivity
            addFlags(BRIDGE_ACTIVITY_FLAGS)
            putExtra(PushConstants.MIPUSH_EXTRA_INTENT_PAYLOAD, targetIntent)
            addCategory(metaInfo.notifyId.toString())
            addCategory(metaInfo.id.orEmpty())
        }
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
        applyPendingIntentIdentity(
            localIntent,
            container.packageName,
            pushMetaInfo.notifyId,
            pushMetaInfo.id.orEmpty(),
        )
        val requestCode = pendingIntentRequestCode(
            container.packageName,
            pushMetaInfo.notifyId,
            pushMetaInfo.id.orEmpty().hashCode(),
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(context, requestCode, localIntent, FLAG_IMMUTABLE_UPDATE_CURRENT)
        } else {
            PendingIntent.getService(context, requestCode, localIntent, FLAG_IMMUTABLE_UPDATE_CURRENT)
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
    ): PendingIntent? = getStylePendingIntent(context, pkgName, place, metaExtra, 0)

    private fun getStylePendingIntent(
        context: Context,
        pkgName: String,
        place: Int,
        metaExtra: Map<String, String>?,
        notificationId: Int,
    ): PendingIntent? {
        if (metaExtra == null) return null
        val keys = styleActionKeys(place, metaExtra)
        val typeId = metaExtra[keys.notifyEffect]
        val intent = getPendingIntentFromExtra(context, pkgName, place, metaExtra) ?: return null
        return when (typeId) {
            PushConstants.NOTIFICATION_CLICK_WEB_PAGE -> PendingIntent.getActivity(
                context,
                pendingIntentRequestCode(pkgName, notificationId, place),
                intent.apply { addCategory(pendingIntentCategory(pkgName, notificationId, place)) },
                FLAG_IMMUTABLE_UPDATE_CURRENT
            )
            PushConstants.NOTIFICATION_CLICK_DEFAULT,
            PushConstants.NOTIFICATION_CLICK_INTENT -> PendingIntent.getService(
                context,
                pendingIntentRequestCode(pkgName, notificationId, place),
                Intent().apply {
                    component = ComponentName("com.xiaomi.xmsf", "com.xiaomi.push.sdk.MyPushMessageHandler")
                    putExtra(EXTRA_STYLE_TARGET_INTENT, intent)
                    data = pendingIntentIdentity(pkgName, notificationId, place)
                },
                FLAG_IMMUTABLE_UPDATE_CURRENT
            )
            else -> null
        }
    }

    internal fun pendingIntentRequestCode(packageName: String?, notificationId: Int, place: Int): Int =
        "${packageName.orEmpty()}:$notificationId:$place".hashCode()

    internal fun pendingIntentIdentity(packageName: String, notificationId: Int, place: Int): Uri =
        Uri.Builder()
            .scheme("mipush-action")
            .authority(packageName)
            .appendPath(notificationId.toString())
            .appendPath(place.toString())
            .build()

    internal fun pendingIntentIdentity(
        packageName: String?,
        notificationId: Int,
        discriminator: String,
    ): Uri = Uri.Builder()
        .scheme("mipush-action")
        .authority(packageName?.takeIf { it.isNotBlank() } ?: "unknown")
        .appendPath(notificationId.toString())
        .appendPath(discriminator)
        .build()

    internal fun applyPendingIntentIdentity(
        intent: Intent,
        packageName: String?,
        notificationId: Int,
        discriminator: String,
    ) {
        val identity = pendingIntentIdentity(packageName, notificationId, discriminator)
        if (intent.data == null) {
            intent.data = identity
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            intent.identifier = identity.toString()
        }
    }

    private fun pendingIntentCategory(packageName: String, notificationId: Int, place: Int): String =
        "$packageName.mipush_action.$notificationId.$place"

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
                "MIPushNotificationIntentSupport.getPendingIntentFromExtra"
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
    internal fun constrainIntentToPackage(intent: Intent, packageName: String): Intent? {
        if (packageName.isBlank()) return null
        val components = listOfNotNull(intent.component, intent.selector?.component)
        if (components.any { it.packageName != packageName }) return null
        return intent.apply { `package` = packageName }
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

}
