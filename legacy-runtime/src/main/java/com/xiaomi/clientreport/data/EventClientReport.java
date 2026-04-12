package com.xiaomi.clientreport.data;

import com.xiaomi.channel.commonutils.logger.MyLog;
import org.json.JSONException;
import org.json.JSONObject;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/clientreport/data/EventClientReport.class */
public class EventClientReport extends BaseClientReport {
    public String eventContent;
    public String eventId;
    public long eventTime;
    public int eventType;

    public static EventClientReport getBlankInstance() {
        return new EventClientReport();
    }

    @Override // com.xiaomi.clientreport.data.BaseClientReport
    public JSONObject toJson() {
        try {
            JSONObject json = super.toJson();
            if (json == null) {
                return null;
            }
            json.put("eventId", this.eventId);
            json.put("eventType", this.eventType);
            json.put("eventTime", this.eventTime);
            String str = this.eventContent;
            String str2 = str;
            if (str == null) {
                str2 = "";
            }
            json.put("eventContent", str2);
            return json;
        } catch (JSONException e) {
            MyLog.e(e);
            return null;
        }
    }

    @Override // com.xiaomi.clientreport.data.BaseClientReport
    public String toJsonString() {
        return super.toJsonString();
    }
}
