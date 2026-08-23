package com.xiaomi.push.service

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.widget.RemoteViews
import com.xiaomi.miui.pushads.sdk.NotifyAdsDef
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.channel.commonutils.logger.KermitLoggerCompat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date

object MIPushNotificationCustomLayoutSupport {
    private const val LAYOUT_NAME = "layout_name"
    private const val LAYOUT_VALUE = "layout_value"

    @JvmStatic
    fun getNotificationForCustomLayout(context: Context, container: XmPushActionContainer): RemoteViews? {
        val metaInfo: PushMetaInfo = container.metaInfo ?: return null
        val extra = metaInfo.extra ?: return null
        val targetPackage = MIPushNotificationHelper.getTargetPackage(container)
        val layoutName = extra[LAYOUT_NAME]
        val layoutValue = extra[LAYOUT_VALUE]
        if (layoutName.isNullOrEmpty() || layoutValue.isNullOrEmpty()) {
            return null
        }
        return try {
            val resources = context.packageManager.getResourcesForApplication(targetPackage)
            val layoutId = resources.getIdentifier(layoutName, "layout", targetPackage)
            if (layoutId == 0) {
                return null
            }
            RemoteViews(targetPackage, layoutId).also { remoteViews ->
                bindLayoutValues(remoteViews, resources, targetPackage, layoutValue)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            KermitLoggerCompat.e("Package not found for custom layout", e)
            null
        } catch (e: Exception) {
            KermitLoggerCompat.e("Custom layout binding failed", e)
            null
        }
    }

    private fun bindLayoutValues(
        remoteViews: RemoteViews,
        resources: Resources,
        targetPackage: String,
        layoutValue: String,
    ) {
        val valueJson = Json.parseToJsonElement(layoutValue).jsonObject
        valueJson["text"]?.jsonObject?.let {
            bindText(remoteViews, resources, targetPackage, it)
        }
        valueJson["image"]?.jsonObject?.let {
            bindImage(remoteViews, resources, targetPackage, it)
        }
        valueJson[NotifyAdsDef.JSON_TAG_ACTIONTIME]?.jsonObject?.let {
            bindActionTime(remoteViews, resources, targetPackage, it)
        }
    }

    private fun bindText(
        remoteViews: RemoteViews,
        resources: Resources,
        targetPackage: String,
        values: JsonObject,
    ) {
        for ((key, value) in values) {
            val viewId = resources.getIdentifier(key, "id", targetPackage)
            if (viewId > 0) {
                remoteViews.setTextViewText(viewId, value.jsonPrimitive.content)
            }
        }
    }

    private fun bindImage(
        remoteViews: RemoteViews,
        resources: Resources,
        targetPackage: String,
        values: JsonObject,
    ) {
        for ((key, value) in values) {
            val viewId = resources.getIdentifier(key, "id", targetPackage)
            val drawableId = resources.getIdentifier(value.jsonPrimitive.content, "drawable", targetPackage)
            if (viewId > 0) {
                remoteViews.setImageViewResource(viewId, drawableId)
            }
        }
    }

    private fun bindActionTime(
        remoteViews: RemoteViews,
        resources: Resources,
        targetPackage: String,
        values: JsonObject,
    ) {
        for ((key, value) in values) {
            val pattern = value.jsonPrimitive.content.ifEmpty { "yy-MM-dd hh:mm" }
            val viewId = resources.getIdentifier(key, "id", targetPackage)
            if (viewId > 0) {
                remoteViews.setTextViewText(viewId, SimpleDateFormat(pattern, java.util.Locale.US).format(Date(System.currentTimeMillis())))
            }
        }
    }
}
