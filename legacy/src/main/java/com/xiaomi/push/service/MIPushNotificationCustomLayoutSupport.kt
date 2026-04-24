package com.xiaomi.push.service
import io.github.magisk317.mipush.protocol.model.*

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.text.TextUtils
import android.widget.RemoteViews
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.miui.pushads.sdk.NotifyAdsDef
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import org.json.JSONException
import org.json.JSONObject
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
        if (TextUtils.isEmpty(layoutName) || TextUtils.isEmpty(layoutValue)) {
            return null
        }
        val layoutValueText = requireNotNull(layoutValue)
        return try {
            val resources = context.packageManager.getResourcesForApplication(targetPackage)
            val layoutId = resources.getIdentifier(layoutName, "layout", targetPackage)
            if (layoutId == 0) {
                return null
            }
            RemoteViews(targetPackage, layoutId).also { remoteViews ->
                bindLayoutValues(remoteViews, resources, targetPackage, layoutValueText)
            }
        } catch (e: JSONException) {
            MyLog.e(e)
            null
        } catch (e: PackageManager.NameNotFoundException) {
            MyLog.e(e)
            null
        }
    }

    @Throws(JSONException::class)
    private fun bindLayoutValues(
        remoteViews: RemoteViews,
        resources: Resources,
        targetPackage: String,
        layoutValue: String,
    ) {
        val valueJson = JSONObject(layoutValue)
        if (valueJson.has("text")) {
            bindText(remoteViews, resources, targetPackage, valueJson.getJSONObject("text"))
        }
        if (valueJson.has("image")) {
            bindImage(remoteViews, resources, targetPackage, valueJson.getJSONObject("image"))
        }
        if (valueJson.has(NotifyAdsDef.JSON_TAG_ACTIONTIME)) {
            bindActionTime(remoteViews, resources, targetPackage, valueJson.getJSONObject(NotifyAdsDef.JSON_TAG_ACTIONTIME))
        }
    }

    @Throws(JSONException::class)
    private fun bindText(
        remoteViews: RemoteViews,
        resources: Resources,
        targetPackage: String,
        values: JSONObject,
    ) {
        val keys = values.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val viewId = resources.getIdentifier(key, "id", targetPackage)
            if (viewId > 0) {
                remoteViews.setTextViewText(viewId, values.getString(key))
            }
        }
    }

    @Throws(JSONException::class)
    private fun bindImage(
        remoteViews: RemoteViews,
        resources: Resources,
        targetPackage: String,
        values: JSONObject,
    ) {
        val keys = values.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val viewId = resources.getIdentifier(key, "id", targetPackage)
            val drawableId = resources.getIdentifier(values.getString(key), "drawable", targetPackage)
            if (viewId > 0) {
                remoteViews.setImageViewResource(viewId, drawableId)
            }
        }
    }

    @Throws(JSONException::class)
    private fun bindActionTime(
        remoteViews: RemoteViews,
        resources: Resources,
        targetPackage: String,
        values: JSONObject,
    ) {
        val keys = values.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val pattern = values.getString(key).ifEmpty { "yy-MM-dd hh:mm" }
            val viewId = resources.getIdentifier(key, "id", targetPackage)
            if (viewId > 0) {
                remoteViews.setTextViewText(viewId, SimpleDateFormat(pattern).format(Date(System.currentTimeMillis())))
            }
        }
    }
}
