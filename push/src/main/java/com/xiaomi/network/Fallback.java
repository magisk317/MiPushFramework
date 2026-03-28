package com.xiaomi.network;

import android.text.TextUtils;
import com.xiaomi.channel.commonutils.string.XMStringUtils;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/network/Fallback.class */
public class Fallback {
    protected static final String DEFAULT_DOMAIN = "s.mi1.cc";
    public static final double DEFAULT_UPLOAD_RATIO = 0.1d;
    public String city;
    public String country;
    public String host;
    public String ip;
    public String isp;
    private String mISP;
    public String networkLabel;
    public String province;
    private long timestamp;
    protected String xforward;
    private ArrayList<WeightedHost> fallbackHosts = new ArrayList<>();
    private double mPercent = 0.1d;
    private String mDomain = DEFAULT_DOMAIN;
    private long effectDuration = 86400000;

    public Fallback(String str) {
        this.networkLabel = "";
        if (TextUtils.isEmpty(str)) {
            throw new IllegalArgumentException("the host is empty");
        }
        this.timestamp = System.currentTimeMillis();
        this.fallbackHosts.add(new WeightedHost(str, -1));
        this.networkLabel = HostManager.getActiveNetworkLabel();
        this.host = str;
    }

    private void deleteWeightedHost(String str) {
        synchronized (this) {
            Iterator<WeightedHost> it = this.fallbackHosts.iterator();
            while (it.hasNext()) {
                if (TextUtils.equals(it.next().host, str)) {
                    it.remove();
                }
            }
        }
    }

    public void accessHost(String str, int i, long j, long j2, Exception exc) {
        accessHost(str, new AccessHistory(i, j, j2, exc));
    }

    public void accessHost(String str, AccessHistory accessHistory) {
        synchronized (this) {
            Iterator<WeightedHost> it = this.fallbackHosts.iterator();
            while (it.hasNext()) {
                WeightedHost next = it.next();
                if (TextUtils.equals(str, next.host)) {
                    next.addAccessHistory(accessHistory);
                    break;
                }
            }
        }
    }

    void addHost(WeightedHost weightedHost) {
        synchronized (this) {
            deleteWeightedHost(weightedHost.host);
            this.fallbackHosts.add(weightedHost);
        }
    }

    public void addHost(String str) {
        synchronized (this) {
            addHost(new WeightedHost(str));
        }
    }

    public void addPreferredHost(String[] strArr) {
        synchronized (this) {
            for (int size = this.fallbackHosts.size() - 1; size >= 0; size--) {
                int length = strArr.length;
                int i = 0;
                while (true) {
                    if (i < length) {
                        if (TextUtils.equals(this.fallbackHosts.get(size).host, strArr[i])) {
                            this.fallbackHosts.remove(size);
                            break;
                        }
                        i++;
                    }
                }
            }
            int i2 = 0;
            for (WeightedHost weightedHost : this.fallbackHosts) {
                int i3 = i2;
                if (weightedHost.weight > i2) {
                    i3 = weightedHost.weight;
                }
                i2 = i3;
            }
            for (int i4 = 0; i4 < strArr.length; i4++) {
                addHost(new WeightedHost(strArr[i4], (strArr.length + i2) - i4));
            }
        }
    }

    public void failedHost(String str, long j, long j2, Exception exc) {
        accessHost(str, -1, j, j2, exc);
    }

    public void failedUrl(String str, long j, long j2, Exception exc) {
        try {
            failedHost(new URL(str).getHost(), j, j2, exc);
        } catch (MalformedURLException e) {
        }
    }

    public Fallback fromJSON(JSONObject jSONObject) throws JSONException {
        synchronized (this) {
            this.networkLabel = jSONObject.optString("net");
            this.effectDuration = jSONObject.getLong("ttl");
            this.mPercent = jSONObject.getDouble("pct");
            this.timestamp = jSONObject.getLong("ts");
            this.city = jSONObject.optString("city");
            this.province = jSONObject.optString("prv");
            this.country = jSONObject.optString("cty");
            this.isp = jSONObject.optString("isp");
            this.ip = jSONObject.optString("ip");
            this.host = jSONObject.optString("host");
            this.xforward = jSONObject.optString("xf");
            JSONArray jSONArray = jSONObject.getJSONArray("fbs");
            for (int i = 0; i < jSONArray.length(); i++) {
                addHost(new WeightedHost().fromJSON(jSONArray.getJSONObject(i)));
            }
        }
        return this;
    }

    public String getDomainName() {
        String str = this.mDomain;
        return str == null ? DEFAULT_DOMAIN : str;
    }

    public ArrayList<String> getHosts() {
        ArrayList<String> hosts;
        synchronized (this) {
            hosts = getHosts(false);
        }
        return hosts;
    }

    public ArrayList<String> getHosts(boolean z) {
        ArrayList<String> arrayList;
        synchronized (this) {
            WeightedHost[] weightedHostArr = new WeightedHost[this.fallbackHosts.size()];
            this.fallbackHosts.toArray(weightedHostArr);
            Arrays.sort(weightedHostArr);
            arrayList = new ArrayList<>();
            for (WeightedHost weightedHost : weightedHostArr) {
                if (z) {
                    arrayList.add(weightedHost.host);
                } else {
                    int iIndexOf = weightedHost.host.indexOf(":");
                    if (iIndexOf != -1) {
                        arrayList.add(weightedHost.host.substring(0, iIndexOf));
                    } else {
                        arrayList.add(weightedHost.host);
                    }
                }
            }
        }
        return arrayList;
    }

    public String getISP() {
        synchronized (this) {
            if (!TextUtils.isEmpty(this.mISP)) {
                return this.mISP;
            }
            if (TextUtils.isEmpty(this.isp)) {
                return "hardcode_isp";
            }
            String strJoin = XMStringUtils.join(new String[]{this.isp, this.province, this.city, this.country, this.ip}, "_");
            this.mISP = strJoin;
            return strJoin;
        }
    }

    public double getPercent() {
        double d = this.mPercent;
        if (d < 1.0E-5d) {
            return 0.1d;
        }
        return d;
    }

    public ArrayList<String> getUrls(String str) throws MalformedURLException {
        if (TextUtils.isEmpty(str)) {
            throw new IllegalArgumentException("the url is empty.");
        }
        URL url = new URL(str);
        if (!TextUtils.equals(url.getHost(), this.host)) {
            throw new IllegalArgumentException("the url is not supported by the fallback");
        }
        ArrayList<String> arrayList = new ArrayList<>();
        Iterator<String> it = getHosts(true).iterator();
        while (it.hasNext()) {
            Host host = Host.parse(it.next(), url.getPort());
            arrayList.add(new URL(url.getProtocol(), host.getHost(), host.getPort(), url.getFile()).toString());
        }
        return arrayList;
    }

    ArrayList<WeightedHost> getWeightedHost() {
        return this.fallbackHosts;
    }

    public boolean isEffective() {
        return System.currentTimeMillis() - this.timestamp < this.effectDuration;
    }

    boolean isExpired() {
        long j = 864000000;
        if (864000000 < this.effectDuration) {
            j = this.effectDuration;
        }
        long jCurrentTimeMillis = System.currentTimeMillis();
        long j2 = this.timestamp;
        return jCurrentTimeMillis - j2 > j || (jCurrentTimeMillis - j2 > this.effectDuration && this.networkLabel.startsWith("WIFI-"));
    }

    public boolean match() {
        return TextUtils.equals(this.networkLabel, HostManager.getActiveNetworkLabel());
    }

    public boolean match(Fallback fallback) {
        return TextUtils.equals(this.networkLabel, fallback.networkLabel);
    }

    public void setDomainName(String str) {
        this.mDomain = str;
    }

    public void setEffectiveDuration(long j) {
        if (j > 0) {
            this.effectDuration = j;
            return;
        }
        throw new IllegalArgumentException("the duration is invalid " + j);
    }

    public void setPercent(double d) {
        this.mPercent = d;
    }

    public void succeedHost(String str, long j, long j2) {
        accessHost(str, 0, j, j2, null);
    }

    public void succeedUrl(String str, long j, long j2) {
        try {
            succeedHost(new URL(str).getHost(), j, j2);
        } catch (MalformedURLException e) {
        }
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject jSONObject;
        synchronized (this) {
            jSONObject = new JSONObject();
            jSONObject.put("net", this.networkLabel);
            jSONObject.put("ttl", this.effectDuration);
            jSONObject.put("pct", this.mPercent);
            jSONObject.put("ts", this.timestamp);
            jSONObject.put("city", this.city);
            jSONObject.put("prv", this.province);
            jSONObject.put("cty", this.country);
            jSONObject.put("isp", this.isp);
            jSONObject.put("ip", this.ip);
            jSONObject.put("host", this.host);
            jSONObject.put("xf", this.xforward);
            JSONArray jSONArray = new JSONArray();
            Iterator<WeightedHost> it = this.fallbackHosts.iterator();
            while (it.hasNext()) {
                jSONArray.put(it.next().toJSON());
            }
            jSONObject.put("fbs", jSONArray);
        }
        return jSONObject;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.networkLabel);
        sb.append("\n");
        sb.append(getISP());
        for (WeightedHost weightedHost : this.fallbackHosts) {
            sb.append("\n");
            sb.append(weightedHost.toString());
        }
        sb.append("\n");
        return sb.toString();
    }
}
