package com.xiaomi.network;

import android.text.TextUtils;
import java.util.ArrayList;
import java.util.Iterator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/network/Fallbacks.class */
class Fallbacks {
    private String host;
    private final ArrayList<Fallback> mFallbacks = new ArrayList<>();

    public Fallbacks() {
    }

    public Fallbacks(String str) {
        if (TextUtils.isEmpty(str)) {
            throw new IllegalArgumentException("the host is empty");
        }
        this.host = str;
    }

    public void addFallback(Fallback fallback) {
        synchronized (this) {
            int i = 0;
            while (true) {
                if (i >= this.mFallbacks.size()) {
                    break;
                }
                if (this.mFallbacks.get(i).match(fallback)) {
                    this.mFallbacks.set(i, fallback);
                    break;
                }
                i++;
            }
            if (i >= this.mFallbacks.size()) {
                this.mFallbacks.add(fallback);
            }
        }
    }

    public void addFallbacks(ArrayList<Fallback> arrayList) {
        synchronized (this) {
            Iterator<Fallback> it = arrayList.iterator();
            while (it.hasNext()) {
                addFallback(it.next());
            }
        }
    }

    public Fallbacks fromJSON(JSONObject jSONObject) throws JSONException {
        synchronized (this) {
            this.host = jSONObject.getString("host");
            JSONArray jSONArray = jSONObject.getJSONArray("fbs");
            for (int i = 0; i < jSONArray.length(); i++) {
                this.mFallbacks.add(new Fallback(this.host).fromJSON(jSONArray.getJSONObject(i)));
            }
        }
        return this;
    }

    public Fallback getFallback() {
        synchronized (this) {
            for (int size = this.mFallbacks.size() - 1; size >= 0; size--) {
                Fallback fallback = this.mFallbacks.get(size);
                if (fallback.match()) {
                    HostManager.getInstance().setCurrentISP(fallback.getISP());
                    return fallback;
                }
            }
            return null;
        }
    }

    public ArrayList<Fallback> getFallbacks() {
        return this.mFallbacks;
    }

    public String getHost() {
        return this.host;
    }

    public void purge(boolean z) {
        synchronized (this) {
            for (int size = this.mFallbacks.size() - 1; size >= 0; size--) {
                Fallback fallback = this.mFallbacks.get(size);
                if (z) {
                    if (fallback.isExpired()) {
                        this.mFallbacks.remove(size);
                    }
                } else if (!fallback.isEffective()) {
                    this.mFallbacks.remove(size);
                }
            }
        }
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject jSONObject;
        synchronized (this) {
            jSONObject = new JSONObject();
            jSONObject.put("host", this.host);
            JSONArray jSONArray = new JSONArray();
            Iterator<Fallback> it = this.mFallbacks.iterator();
            while (it.hasNext()) {
                jSONArray.put(it.next().toJSON());
            }
            jSONObject.put("fbs", jSONArray);
        }
        return jSONObject;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.host);
        sb.append("\n");
        Iterator<Fallback> it = this.mFallbacks.iterator();
        while (it.hasNext()) {
            sb.append(it.next());
        }
        return sb.toString();
    }
}
