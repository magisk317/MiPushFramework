package com.xiaomi.mipush.sdk.stat.client;

import android.text.TextUtils;
import com.xiaomi.push.service.PushServiceConstants;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.json.JSONException;
import org.json.JSONObject;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/stat/client/EventDataItem.class */
public class EventDataItem {
    private static final String COUNT = "count";
    private static final String EVENT = "event";
    private static final String NUMERIC = "numeric";
    private static final String PROPERTY = "property";
    private String mCategory;
    private String mKey;
    private Map<String, String> mParams = new HashMap();
    private long mTimeStamp;
    private String mType;
    private String mValue;

    public EventDataItem() {
    }

    public EventDataItem(String str, String str2, String str3, String str4) {
        this.mCategory = str;
        this.mKey = str2;
        this.mValue = str3;
        this.mType = str4;
        setTimeStamp(System.currentTimeMillis());
    }

    public EventDataItem(String str, String str2, String str3, String str4, Map<String, String> map) {
        setCategory(str);
        setKey(str2);
        setValue(str3);
        setType(str4);
        setParams(map);
        setTimeStamp(System.currentTimeMillis());
    }

    public static EventDataItem getCalculateEvent(String str, String str2, long j) {
        EventDataItem eventDataItem = new EventDataItem();
        eventDataItem.setCategory(str);
        eventDataItem.setKey(str2);
        eventDataItem.setValue(String.valueOf(j));
        eventDataItem.setType(COUNT);
        eventDataItem.setTimeStamp(System.currentTimeMillis());
        return eventDataItem;
    }

    public static EventDataItem getCalculateEvent(String str, String str2, long j, Map<String, String> map) {
        EventDataItem eventDataItem = new EventDataItem();
        eventDataItem.setCategory(str);
        eventDataItem.setKey(str2);
        eventDataItem.setValue(String.valueOf(j));
        eventDataItem.setType(COUNT);
        eventDataItem.setParams(map);
        eventDataItem.setTimeStamp(System.currentTimeMillis());
        return eventDataItem;
    }

    public static EventDataItem getCountEvent(String str, String str2) {
        EventDataItem eventDataItem = new EventDataItem();
        eventDataItem.setCategory(str);
        eventDataItem.setKey(str2);
        eventDataItem.setValue(String.valueOf(1));
        eventDataItem.setType("event");
        eventDataItem.setTimeStamp(System.currentTimeMillis());
        return eventDataItem;
    }

    public static EventDataItem getCountEvent(String str, String str2, Map<String, String> map) {
        EventDataItem eventDataItem = new EventDataItem();
        eventDataItem.setCategory(str);
        eventDataItem.setKey(str2);
        eventDataItem.setValue(String.valueOf(1));
        eventDataItem.setType("event");
        eventDataItem.setParams(map);
        eventDataItem.setTimeStamp(System.currentTimeMillis());
        return eventDataItem;
    }

    public static EventDataItem getNumericPropertyEvent(String str, String str2, long j) {
        EventDataItem eventDataItem = new EventDataItem();
        eventDataItem.setCategory(str);
        eventDataItem.setKey(str2);
        eventDataItem.setValue(String.valueOf(j));
        eventDataItem.setType(NUMERIC);
        eventDataItem.setTimeStamp(System.currentTimeMillis());
        return eventDataItem;
    }

    public static EventDataItem getPropertyEvent(String str, String str2, String str3) {
        EventDataItem eventDataItem = new EventDataItem();
        eventDataItem.setCategory(str);
        eventDataItem.setKey(str2);
        eventDataItem.setValue(str3);
        eventDataItem.setType(PROPERTY);
        eventDataItem.setTimeStamp(System.currentTimeMillis());
        return eventDataItem;
    }

    private static String mapToJson(Map<String, String> map) {
        JSONObject jSONObject = new JSONObject();
        if (map != null && map.size() > 0) {
            try {
                Set<String> setKeySet = map.keySet();
                if (setKeySet != null) {
                    for (String str : setKeySet) {
                        if (!TextUtils.isEmpty(str)) {
                            jSONObject.put(str, map.get(str) + "");
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return jSONObject.toString();
    }

    public void setCategory(String str) {
        this.mCategory = str;
    }

    public void setKey(String str) {
        this.mKey = str;
    }

    public void setParams(Map<String, String> map) {
        if (map == null || map.size() <= 0) {
            return;
        }
        this.mParams.putAll(map);
    }

    public void setTimeStamp(long j) {
        this.mTimeStamp = j;
    }

    public void setType(String str) {
        this.mType = str;
    }

    public void setValue(String str) {
        this.mValue = str;
    }

    public JSONObject toJson() {
        JSONObject jSONObject = new JSONObject();
        try {
            if (!TextUtils.isEmpty(this.mCategory)) {
                jSONObject.put("category", this.mCategory);
            }
            if (!TextUtils.isEmpty(this.mKey)) {
                jSONObject.put("key", this.mKey);
            }
            if (!TextUtils.isEmpty(this.mValue)) {
                jSONObject.put(PushServiceConstants.EXTENSION_ATTRIBUTE_APP_PARAM_VALUE, this.mValue);
            }
            if (!TextUtils.isEmpty(this.mType)) {
                jSONObject.put("type", this.mType);
            }
            Map<String, String> map = this.mParams;
            if (map != null && map.size() > 0) {
                jSONObject.put("params", mapToJson(this.mParams));
            }
            jSONObject.put("timeStamp", this.mTimeStamp);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jSONObject;
    }
}
