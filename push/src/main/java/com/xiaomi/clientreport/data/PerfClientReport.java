package com.xiaomi.clientreport.data;

import com.xiaomi.channel.commonutils.logger.MyLog;
import org.json.JSONException;
import org.json.JSONObject;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/clientreport/data/PerfClientReport.class */
public class PerfClientReport extends BaseClientReport {
    private static final long DEFAULT_VALUE = -1;
    public int code;
    public long perfCounts = DEFAULT_VALUE;
    public long perfLatencies = DEFAULT_VALUE;

    public static PerfClientReport getBlankInstance() {
        return new PerfClientReport();
    }

    @Override // com.xiaomi.clientreport.data.BaseClientReport
    public JSONObject toJson() {
        try {
            JSONObject json = super.toJson();
            if (json == null) {
                return null;
            }
            json.put("code", this.code);
            json.put("perfCounts", this.perfCounts);
            json.put("perfLatencies", this.perfLatencies);
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
