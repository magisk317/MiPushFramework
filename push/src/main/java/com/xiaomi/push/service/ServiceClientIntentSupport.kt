package com.xiaomi.push.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Messenger
import com.xiaomi.channel.commonutils.android.AppInfoUtils
import org.apache.http.NameValuePair

internal object ServiceClientIntentSupport {
    @JvmStatic
    fun createServiceIntent(context: Context, isMiuiPushServiceEnabled: Boolean): Intent {
        return if (isMiuiPushServiceEnabled) {
            Intent().apply {
                setPackage(PushConstants.PUSH_SERVICE_PACKAGE_NAME)
                setClassName(PushConstants.PUSH_SERVICE_PACKAGE_NAME, getPushServiceName(context))
                putExtra(PushConstants.EXTRA_PACKAGE_NAME, context.packageName)
                disableMyPushService(context)
            }
        } else {
            Intent(context, XMPushService::class.java).apply {
                putExtra(PushConstants.EXTRA_PACKAGE_NAME, context.packageName)
                enableMyPushService(context)
            }
        }
    }

    @JvmStatic
    fun getPushServiceName(context: Context): String {
        return runCatching {
            if (AppInfoUtils.getVersionCode(context, PushConstants.PUSH_SERVICE_PACKAGE_NAME) >= 106) {
                PushConstants.PUSH_SERVICE_CLASS_NAME_JAR
            } else {
                PushConstants.PUSH_SERVICE_CLASS_NAME
            }
        }.getOrDefault(PushConstants.PUSH_SERVICE_CLASS_NAME)
    }

    @JvmStatic
    fun putOpenParamsIntoIntent(
        intent: Intent,
        userId: String,
        channelId: String,
        token: String,
        authMethod: String,
        security: String,
        kick: Boolean,
        clientAttributes: Map<String, String>?,
        cloudAttributes: Map<String, String>?,
        session: String,
        messenger: Messenger,
    ) {
        intent.putExtra(PushConstants.EXTRA_USER_ID, userId)
        intent.putExtra(PushConstants.EXTRA_CHANNEL_ID, channelId)
        intent.putExtra(PushConstants.EXTRA_TOKEN, token)
        intent.putExtra(PushConstants.EXTRA_SECURITY, security)
        intent.putExtra(PushConstants.EXTRA_AUTH_METHOD, authMethod)
        intent.putExtra(PushConstants.EXTRA_KICK, kick)
        intent.putExtra(PushConstants.EXTRA_SESSION, session)
        intent.putExtra(PushConstants.EXTRA_MESSENGER, messenger)
        putJoinedAttributes(intent, PushConstants.EXTRA_CLIENT_ATTR, clientAttributes)
        putJoinedAttributes(intent, PushConstants.EXTRA_CLOUD_ATTR, cloudAttributes)
    }

    @JvmStatic
    fun translate(list: List<NameValuePair?>?): Map<String, String> {
        val translated = LinkedHashMap<String, String>()
        if (!list.isNullOrEmpty()) {
            list.forEach { pair ->
                if (pair != null) {
                    translated[pair.name] = pair.value
                }
            }
        }
        return translated
    }

    @JvmStatic
    fun joinAttributes(map: Map<String, String>): String {
        return map.entries.joinToString(",") { "${it.key}:${it.value}" }
    }

    private fun disableMyPushService(context: Context) {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, XMPushService::class.java),
            2,
            1,
        )
    }

    private fun enableMyPushService(context: Context) {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, XMPushService::class.java),
            1,
            1,
        )
    }

    private fun putJoinedAttributes(intent: Intent, key: String, attributes: Map<String, String>?) {
        if (attributes.isNullOrEmpty()) return
        val joinedAttributes = joinAttributes(attributes)
        if (joinedAttributes.isEmpty()) return
        intent.putExtra(key, joinedAttributes)
    }
}
