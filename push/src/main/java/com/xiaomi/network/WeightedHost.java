package com.xiaomi.network;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/network/WeightedHost.class */
class WeightedHost implements Comparable<WeightedHost> {
    private static final int HISTORY_SIZE = 30;
    private final LinkedList<AccessHistory> accessHistories;
    String host;
    private long touchedTime;
    protected int weight;

    public WeightedHost() {
        this(null, 0);
    }

    public WeightedHost(String str) {
        this(str, 0);
    }

    public WeightedHost(String str, int i) {
        this.accessHistories = new LinkedList<>();
        this.touchedTime = 0L;
        this.host = str;
        this.weight = i;
    }

    protected void addAccessHistory(AccessHistory accessHistory) {
        synchronized (this) {
            if (accessHistory != null) {
                this.accessHistories.add(accessHistory);
                int weight = accessHistory.getWeight();
                if (weight > 0) {
                    this.weight += accessHistory.getWeight();
                } else {
                    int i = 0;
                    for (int size = this.accessHistories.size() - 1; size >= 0 && this.accessHistories.get(size).getWeight() < 0; size--) {
                        i++;
                    }
                    this.weight += weight * i;
                }
                if (this.accessHistories.size() > 30) {
                    this.weight -= this.accessHistories.remove().getWeight();
                }
            }
        }
    }

    @Override // java.lang.Comparable
    public int compareTo(WeightedHost weightedHost) {
        if (weightedHost == null) {
            return 1;
        }
        return weightedHost.weight - this.weight;
    }

    public WeightedHost fromJSON(JSONObject jSONObject) throws JSONException {
        synchronized (this) {
            this.touchedTime = jSONObject.getLong("tt");
            this.weight = jSONObject.getInt("wt");
            this.host = jSONObject.getString("host");
            JSONArray jSONArray = jSONObject.getJSONArray("ah");
            for (int i = 0; i < jSONArray.length(); i++) {
                this.accessHistories.add(new AccessHistory().fromJSON(jSONArray.getJSONObject(i)));
            }
        }
        return this;
    }

    public ArrayList<AccessHistory> getAccessHistory() {
        ArrayList<AccessHistory> arrayList;
        synchronized (this) {
            arrayList = new ArrayList<>(this.accessHistories);
        }
        return arrayList;
    }

    public ArrayList<AccessHistory> getUnTouchedAccessHistory() {
        ArrayList<AccessHistory> arrayList;
        synchronized (this) {
            arrayList = new ArrayList<>();
            for (AccessHistory accessHistory : this.accessHistories) {
                if (accessHistory.getTime() > this.touchedTime) {
                    arrayList.add(accessHistory);
                }
            }
            this.touchedTime = System.currentTimeMillis();
        }
        return arrayList;
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject jSONObject;
        synchronized (this) {
            jSONObject = new JSONObject();
            jSONObject.put("tt", this.touchedTime);
            jSONObject.put("wt", this.weight);
            jSONObject.put("host", this.host);
            JSONArray jSONArray = new JSONArray();
            Iterator<AccessHistory> it = this.accessHistories.iterator();
            while (it.hasNext()) {
                jSONArray.put(it.next().toJSON());
            }
            jSONObject.put("ah", jSONArray);
        }
        return jSONObject;
    }

    public String toString() {
        return this.host + ":" + this.weight;
    }
}
