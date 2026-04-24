package com.xiaomi.network;

import org.json.JSONException;
import org.json.JSONObject;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/network/AccessHistory.class */
public class AccessHistory {
    private long cost;
    private String exceptionName;
    private long size;
    private long ts;
    private int weight;

    public AccessHistory() {
        this(0, 0L, 0L, null);
    }

    public AccessHistory(int i, long j, long j2, Exception exc) {
        this.weight = i;
        this.cost = j;
        this.size = j2;
        this.ts = System.currentTimeMillis();
        if (exc != null) {
            this.exceptionName = exc.getClass().getSimpleName();
        }
    }

    public AccessHistory fromJSON(JSONObject jSONObject) throws JSONException {
        this.cost = jSONObject.getLong("cost");
        this.size = jSONObject.getLong("size");
        this.ts = jSONObject.getLong("ts");
        this.weight = jSONObject.getInt("wt");
        this.exceptionName = jSONObject.optString("expt");
        return this;
    }

    public long getCost() {
        return this.cost;
    }

    public String getException() {
        return this.exceptionName;
    }

    public long getSize() {
        return this.size;
    }

    public long getTime() {
        return this.ts;
    }

    public int getWeight() {
        return this.weight;
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject jSONObject = new JSONObject();
        jSONObject.put("cost", this.cost);
        jSONObject.put("size", this.size);
        jSONObject.put("ts", this.ts);
        jSONObject.put("wt", this.weight);
        jSONObject.put("expt", this.exceptionName);
        return jSONObject;
    }
}
