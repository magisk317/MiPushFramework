package com.xiaomi.mipush.sdk

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.text.TextUtils
import com.xiaomi.push.service.PushConstants
import com.xiaomi.channel.commonutils.logger.MyLog
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL

/** Complete stock notification-effect intent construction and resolution. */
internal object PushMessageProcessorNotificationIntentFactory {
    fun getNotificationMessageIntent(context: Context, packageName: String, map: Map<String, String>?): Intent? {
        if (map == null || !map.containsKey(PushConstants.EXTRA_PARAM_NOTIFY_EFFECT)) {
            return null
        }
        val notifyEffect = map[PushConstants.EXTRA_PARAM_NOTIFY_EFFECT]
        var intentFlags = -1
        val flags = map["intent_flag"]
        if (!TextUtils.isEmpty(flags)) {
            try {
                intentFlags = flags!!.toInt()
            } catch (e: NumberFormatException) {
                MyLog.e("Cause by intent_flag:" + e.message)
            }
        }
        var intent: Intent? = null
        if (PushConstants.NOTIFICATION_CLICK_DEFAULT == notifyEffect) {
            try {
                intent = context.packageManager.getLaunchIntentForPackage(packageName)
            } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
                MyLog.e("Cause:" + e.message)
            }
        } else if (PushConstants.NOTIFICATION_CLICK_INTENT == notifyEffect) {
            if (map.containsKey("intent_uri")) {
                val intentUri = map["intent_uri"]
                if (intentUri != null) {
                    try {
                        intent = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME)
                        intent.setPackage(packageName)
                    } catch (e: URISyntaxException) {
                        MyLog.e("Cause:" + e.message)
                    }
                }
            } else if (map.containsKey("class_name")) {
                intent = Intent().apply {
                    component = ComponentName(packageName, map["class_name"]!!)
                }
            }
        } else if (PushConstants.NOTIFICATION_CLICK_WEB_PAGE == notifyEffect) {
            val webUri = map["web_uri"]
            if (webUri != null) {
                var url = webUri.trim()
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "http://$url"
                }
                try {
                    val protocol = URL(url).protocol
                    if ("http" == protocol || "https" == protocol) {
                        intent = Intent("android.intent.action.VIEW").apply {
                            data = Uri.parse(url)
                        }
                    }
                } catch (e: MalformedURLException) {
                    MyLog.e("Cause:" + e.message)
                }
            }
        }
        if (intent == null) {
            return null
        }
        if (intentFlags >= 0) {
            intent.flags = intentFlags
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            val resolveActivity: ResolveInfo? = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            if (resolveActivity != null) {
                return intent
            }
            MyLog.w("not resolve activity:$intent")
        } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
            MyLog.e("Cause:" + e.message)
        }
        return null
    }
}
