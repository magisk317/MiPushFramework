package com.xiaomi.push.service.awake;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.string.XMStringUtils;
import java.util.HashMap;
import org.json.JSONException;
import org.json.JSONObject;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/awake/AwakeDataHelper.class */
public class AwakeDataHelper {
    private static final String AWAKENED_APP = "awakened_app";
    private static final String AWAKE_APP = "awake_app";
    private static final String AWAKE_TYPE = "awake_type";
    private static final String FLOW_ID = "flow_id";
    private static final String JOB_KEY = "jobkey";
    private static final String MSG_ID = "msg_id";
    private static final String PLAN_ID = "__planId__";

    public static String decode(String str) {
        return XMStringUtils.bytesToString(Base64.decode(str, 2));
    }

    public static String encode(String str) {
        return Base64.encodeToString(XMStringUtils.getBytes(str), 2);
    }

    public static Uri getContentUri(String str, String str2) {
        return Uri.parse("content://" + str).buildUpon().appendPath(str2).build();
    }

    public static String getString(HashMap<String, String> map) {
        if (map == null) {
            return "";
        }
        JSONObject jSONObject = new JSONObject();
        try {
            for (String str : map.keySet()) {
                jSONObject.put(str, map.get(str));
            }
        } catch (JSONException e) {
            MyLog.e(e);
        }
        return jSONObject.toString();
    }

    public static String obfuscateLogContent(HashMap<String, String> map) {
        HashMap map2 = new HashMap();
        if (map != null) {
            map2.put(AwakeUploadHelper.KEY_EVENT_TYPE, map.get(AwakeUploadHelper.KEY_EVENT_TYPE) + "");
            map2.put(AwakeUploadHelper.KEY_DESCRIPTION, map.get(AwakeUploadHelper.KEY_DESCRIPTION) + "");
            String str = map.get(AwakeUploadHelper.KEY_AWAKE_INFO);
            if (!TextUtils.isEmpty(str)) {
                try {
                    JSONObject jSONObject = new JSONObject(str);
                    map2.put(PLAN_ID, String.valueOf(jSONObject.opt(PLAN_ID)));
                    map2.put(FLOW_ID, String.valueOf(jSONObject.opt(FLOW_ID)));
                    map2.put("jobkey", String.valueOf(jSONObject.opt("jobkey")));
                    map2.put(MSG_ID, String.valueOf(jSONObject.opt(MSG_ID)));
                    map2.put("A", String.valueOf(jSONObject.opt(AWAKE_APP)));
                    map2.put("B", String.valueOf(jSONObject.opt(AWAKENED_APP)));
                    map2.put("module", String.valueOf(jSONObject.opt(AWAKE_TYPE)));
                } catch (JSONException e) {
                    MyLog.e(e);
                }
            }
        }
        return getString(map2);
    }
}
