package com.xiaomi.push.service;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.text.TextUtils;
import android.widget.RemoteViews;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.miui.pushads.sdk.NotifyAdsDef;
import com.xiaomi.xmpush.thrift.PushMetaInfo;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

final class MIPushNotificationCustomLayoutSupport {
    private static final String LAYOUT_NAME = "layout_name";
    private static final String LAYOUT_VALUE = "layout_value";

    private MIPushNotificationCustomLayoutSupport() {
    }

    static RemoteViews getNotificationForCustomLayout(Context context, XmPushActionContainer xmPushActionContainer) {
        PushMetaInfo metaInfo = xmPushActionContainer.getMetaInfo();
        String targetPackage = MIPushNotificationHelper.getTargetPackage(xmPushActionContainer);
        if (metaInfo == null || metaInfo.getExtra() == null) {
            return null;
        }
        Map<String, String> extra = metaInfo.getExtra();
        String str = extra.get(LAYOUT_NAME);
        String str2 = extra.get(LAYOUT_VALUE);
        if (TextUtils.isEmpty(str) || TextUtils.isEmpty(str2)) {
            return null;
        }
        try {
            Resources resourcesForApplication = context.getPackageManager().getResourcesForApplication(targetPackage);
            int identifier = resourcesForApplication.getIdentifier(str, "layout", targetPackage);
            if (identifier == 0) {
                return null;
            }
            RemoteViews remoteViews = new RemoteViews(targetPackage, identifier);
            bindLayoutValues(remoteViews, resourcesForApplication, targetPackage, str2);
            return remoteViews;
        } catch (JSONException e) {
            MyLog.e(e);
            return null;
        } catch (PackageManager.NameNotFoundException e2) {
            MyLog.e(e2);
            return null;
        }
    }

    private static void bindLayoutValues(RemoteViews remoteViews, Resources resources, String targetPackage, String layoutValue) throws JSONException {
        JSONObject jSONObject = new JSONObject(layoutValue);
        if (jSONObject.has("text")) {
            bindText(remoteViews, resources, targetPackage, jSONObject.getJSONObject("text"));
        }
        if (jSONObject.has("image")) {
            bindImage(remoteViews, resources, targetPackage, jSONObject.getJSONObject("image"));
        }
        if (jSONObject.has(NotifyAdsDef.JSON_TAG_ACTIONTIME)) {
            bindActionTime(remoteViews, resources, targetPackage, jSONObject.getJSONObject(NotifyAdsDef.JSON_TAG_ACTIONTIME));
        }
    }

    private static void bindText(RemoteViews remoteViews, Resources resources, String targetPackage, JSONObject jSONObject) throws JSONException {
        Iterator<String> itKeys = jSONObject.keys();
        while (itKeys.hasNext()) {
            String next = itKeys.next();
            int identifier = resources.getIdentifier(next, "id", targetPackage);
            if (identifier > 0) {
                remoteViews.setTextViewText(identifier, jSONObject.getString(next));
            }
        }
    }

    private static void bindImage(RemoteViews remoteViews, Resources resources, String targetPackage, JSONObject jSONObject) throws JSONException {
        Iterator<String> itKeys = jSONObject.keys();
        while (itKeys.hasNext()) {
            String next = itKeys.next();
            int identifier = resources.getIdentifier(next, "id", targetPackage);
            int identifier2 = resources.getIdentifier(jSONObject.getString(next), "drawable", targetPackage);
            if (identifier > 0) {
                remoteViews.setImageViewResource(identifier, identifier2);
            }
        }
    }

    private static void bindActionTime(RemoteViews remoteViews, Resources resources, String targetPackage, JSONObject jSONObject) throws JSONException {
        Iterator<String> itKeys = jSONObject.keys();
        while (itKeys.hasNext()) {
            String next = itKeys.next();
            String string = jSONObject.getString(next);
            String str = string.length() == 0 ? "yy-MM-dd hh:mm" : string;
            int identifier = resources.getIdentifier(next, "id", targetPackage);
            if (identifier > 0) {
                remoteViews.setTextViewText(identifier, new SimpleDateFormat(str).format(new Date(System.currentTimeMillis())));
            }
        }
    }
}
