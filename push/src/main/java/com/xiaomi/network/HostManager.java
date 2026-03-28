package com.xiaomi.network;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Process;
import android.text.TextUtils;
import com.xiaomi.BuildConfig;
import com.xiaomi.channel.commonutils.file.IOUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.network.BasicNameValuePair;
import com.xiaomi.channel.commonutils.network.NameValuePair;
import com.xiaomi.channel.commonutils.network.Network;
import com.xiaomi.channel.commonutils.string.XMStringUtils;
import com.xiaomi.common.logger.thrift.mfs.HostInfo;
import com.xiaomi.common.logger.thrift.mfs.HttpApi;
import com.xiaomi.common.logger.thrift.mfs.LandNodeInfo;
import com.xiaomi.common.logger.thrift.mfs.Location;
import com.xiaomi.mipush.sdk.Constants;
import com.xiaomi.push.service.AppRegionStorage;
import com.xiaomi.push.service.module.PushChannelRegion;
import com.xiaomi.slim.Blob;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/network/HostManager.class */
public class HostManager {
    private static final String BUCKET_URL = "https://%1$s/gslb/?ver=4.0";
    private static final String HOST = "resolver.msg.xiaomi.net";
    private static final String HOST_GLOBAL = "resolver.msg.global.xiaomi.net";
    private static HostManagerFactory factory;
    protected static Context sAppContext;
    private static String sAppName;
    private static String sAppVersion;
    private static HostManager sInstance;
    private final long MAX_REQUEST_FAILURE_CNT;
    private String currentISP;
    private long lastRemoteRequestTimestamp;
    protected Map<String, Fallbacks> mHostsMapping;
    private long remoteRequestFailureCount;
    private HostFilter sHostFilter;
    protected HttpGet sHttpGetter;
    private String sUserId;
    protected static Map<String, Fallback> sReservedHosts = new HashMap();
    protected static boolean hostLoaded = false;

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/network/HostManager$HostManagerFactory.class */
    public interface HostManagerFactory {
        HostManager createHostManager(Context context, HostFilter hostFilter, HttpGet httpGet, String str);
    }

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/network/HostManager$HttpGet.class */
    public interface HttpGet {
        String doGet(String str) throws IOException;
    }

    protected HostManager(Context context, HostFilter hostFilter, HttpGet httpGet, String str) {
        this(context, hostFilter, httpGet, str, null, null);
    }

    protected HostManager(Context context, HostFilter hostFilter, HttpGet httpGet, String str, String str2, String str3) {
        this.mHostsMapping = new HashMap();
        this.sUserId = Blob.CLIENT_PING_ID;
        this.remoteRequestFailureCount = 0L;
        this.MAX_REQUEST_FAILURE_CNT = 15L;
        this.lastRemoteRequestTimestamp = 0L;
        this.currentISP = "isp_prov_city_country_ip";
        this.sHttpGetter = httpGet;
        if (hostFilter == null) {
            this.sHostFilter = new HostFilter() { // from class: com.xiaomi.network.HostManager.1
                @Override // com.xiaomi.network.HostFilter
                public boolean accept(String str4) {
                    return true;
                }
            };
        } else {
            this.sHostFilter = hostFilter;
        }
        this.sUserId = str;
        sAppName = str2 == null ? context.getPackageName() : str2;
        sAppVersion = str3 != null ? str3 : getVersionName();
    }

    public static void addReservedHost(String str, String str2) {
        Fallback fallback = sReservedHosts.get(str);
        synchronized (sReservedHosts) {
            if (fallback == null) {
                Fallback fallback2 = new Fallback(str);
                fallback2.setEffectiveDuration(604800000L);
                fallback2.addHost(str2);
                sReservedHosts.put(str, fallback2);
            } else {
                fallback.addHost(str2);
            }
        }
    }

    static String getActiveNetworkLabel() {
        NetworkInfo activeNetworkInfo;
        Context context = sAppContext;
        if (context == null) {
            return "unknown";
        }
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService("connectivity");
            if (connectivityManager == null || (activeNetworkInfo = connectivityManager.getActiveNetworkInfo()) == null) {
                return "unknown";
            }
            if (activeNetworkInfo.getType() != 1) {
                return activeNetworkInfo.getTypeName() + Constants.ACCEPT_TIME_SEPARATOR_SERVER + activeNetworkInfo.getSubtypeName();
            }
            WifiManager wifiManager = (WifiManager) sAppContext.getSystemService(Network.NETWORK_TYPE_WIFI);
            if (wifiManager == null || wifiManager.getConnectionInfo() == null) {
                return "unknown";
            }
            return "WIFI-" + wifiManager.getConnectionInfo().getSSID();
        } catch (Throwable th) {
            return "unknown";
        }
    }

    public static HostManager getInstance() {
        HostManager hostManager;
        synchronized (HostManager.class) {
            try {
                hostManager = sInstance;
                if (hostManager == null) {
                    throw new IllegalStateException("the host manager is not initialized yet.");
                }
            } finally {
            }
        }
        return hostManager;
    }

    private String getVersionName() {
        try {
            PackageInfo packageInfo = sAppContext.getPackageManager().getPackageInfo(sAppContext.getPackageName(), 16384);
            return packageInfo != null ? packageInfo.versionName : Blob.CLIENT_PING_ID;
        } catch (Exception e) {
            return Blob.CLIENT_PING_ID;
        }
    }

    public static void init(Context context, HostFilter hostFilter, HttpGet httpGet, String str) {
        synchronized (HostManager.class) {
            try {
                init(context, hostFilter, httpGet, str, null, null);
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    public static void init(Context context, HostFilter hostFilter, HttpGet httpGet, String str, String str2, String str3) {
        synchronized (HostManager.class) {
            try {
                Context applicationContext = context.getApplicationContext();
                sAppContext = applicationContext;
                if (applicationContext == null) {
                    sAppContext = context;
                }
                if (sInstance == null) {
                    HostManagerFactory hostManagerFactory = factory;
                    if (hostManagerFactory == null) {
                        sInstance = new HostManager(context, hostFilter, httpGet, str, str2, str3);
                    } else {
                        sInstance = hostManagerFactory.createHostManager(context, hostFilter, httpGet, str);
                    }
                }
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    static String obfuscate(String str) {
        try {
            int length = str.length();
            byte[] bytes = str.getBytes("UTF-8");
            for (int i = 0; i < bytes.length; i++) {
                if ((bytes[i] & 240) != 240) {
                    byte b = bytes[i];
                    bytes[i] = (byte) ((b & 240) | ((b & 15) ^ ((byte) (((b >> 4) + length) & 15))));
                }
            }
            return new String(bytes);
        } catch (UnsupportedEncodingException e) {
            return str;
        }
    }

    private String processNetwork(String str) {
        return TextUtils.isEmpty(str) ? "unknown" : str.startsWith("WIFI") ? "WIFI" : str;
    }

    private ArrayList<Fallback> requestRemoteFallbacks(ArrayList<String> arrayList) throws Throwable {
        purge();
        synchronized (this.mHostsMapping) {
            checkHostMapping();
            for (String str : this.mHostsMapping.keySet()) {
                if (!arrayList.contains(str)) {
                    arrayList.add(str);
                }
            }
        }
        boolean z = sReservedHosts.isEmpty();
        synchronized (sReservedHosts) {
            Object[] array = sReservedHosts.values().toArray();
            for (Object obj : array) {
                Fallback fallback = (Fallback) obj;
                if (!fallback.isEffective()) {
                    z = true;
                    sReservedHosts.remove(fallback.host);
                }
            }
        }
        String host = getHost();
        if (!arrayList.contains(host)) {
            arrayList.add(host);
        }
        ArrayList<Fallback> arrayList2 = new ArrayList<>(arrayList.size());
        for (int i = 0; i < arrayList.size(); i++) {
            arrayList2.add(null);
        }
        try {
            String str2 = Network.isWIFIConnected(sAppContext) ? "wifi" : "wap";
            String remoteFallbackJSON = getRemoteFallbackJSON(arrayList, str2, this.sUserId, z);
            if (!TextUtils.isEmpty(remoteFallbackJSON)) {
                JSONObject jSONObject = new JSONObject(remoteFallbackJSON);
                MyLog.i(remoteFallbackJSON);
                if ("OK".equalsIgnoreCase(jSONObject.getString("S"))) {
                    JSONObject jSONObject2 = jSONObject.getJSONObject("R");
                    String string = jSONObject2.getString("province");
                    String string2 = jSONObject2.getString("city");
                    String string3 = jSONObject2.getString("isp");
                    String string4 = jSONObject2.getString("ip");
                    String string5 = jSONObject2.getString("country");
                    JSONObject jSONObject3 = jSONObject2.getJSONObject(str2);
                    MyLog.v("get bucket: net=" + string3 + ", hosts=" + jSONObject3.toString());
                    for (int i2 = 0; i2 < arrayList.size(); i2++) {
                        String str3 = arrayList.get(i2);
                        JSONArray jSONArrayOptJSONArray = jSONObject3.optJSONArray(str3);
                        if (jSONArrayOptJSONArray == null) {
                            MyLog.w("no bucket found for " + str3);
                        } else {
                            Fallback fallback2 = new Fallback(str3);
                            for (int i3 = 0; i3 < jSONArrayOptJSONArray.length(); i3++) {
                                String string6 = jSONArrayOptJSONArray.getString(i3);
                                if (!TextUtils.isEmpty(string6)) {
                                    fallback2.addHost(new WeightedHost(string6, jSONArrayOptJSONArray.length() - i3));
                                }
                            }
                            arrayList2.set(i2, fallback2);
                            fallback2.country = string5;
                            fallback2.province = string;
                            fallback2.isp = string3;
                            fallback2.ip = string4;
                            fallback2.city = string2;
                            if (jSONObject2.has("stat-percent")) {
                                fallback2.setPercent(jSONObject2.getDouble("stat-percent"));
                            }
                            if (jSONObject2.has("stat-domain")) {
                                fallback2.setDomainName(jSONObject2.getString("stat-domain"));
                            }
                            if (jSONObject2.has("ttl")) {
                                fallback2.setEffectiveDuration(jSONObject2.getInt("ttl") * 1000L);
                            }
                            setCurrentISP(fallback2.getISP());
                        }
                    }
                    JSONObject jSONObjectOptJSONObject = jSONObject2.optJSONObject("reserved");
                    if (jSONObjectOptJSONObject != null) {
                        long j = 604800000L;
                        if (jSONObject2.has("reserved-ttl")) {
                            j = jSONObject2.getInt("reserved-ttl") * 1000L;
                        }
                        Iterator<String> it = jSONObjectOptJSONObject.keys();
                        while (it.hasNext()) {
                            String next = it.next();
                            JSONArray jSONArrayOptJSONArray2 = jSONObjectOptJSONObject.optJSONArray(next);
                            if (jSONArrayOptJSONArray2 == null) {
                                MyLog.w("no bucket found for " + next);
                            } else {
                                Fallback fallback3 = new Fallback(next);
                                fallback3.setEffectiveDuration(j);
                                for (int i4 = 0; i4 < jSONArrayOptJSONArray2.length(); i4++) {
                                    String string7 = jSONArrayOptJSONArray2.getString(i4);
                                    if (!TextUtils.isEmpty(string7)) {
                                        fallback3.addHost(new WeightedHost(string7, jSONArrayOptJSONArray2.length() - i4));
                                    }
                                }
                                synchronized (sReservedHosts) {
                                    if (this.sHostFilter.accept(next)) {
                                        sReservedHosts.put(next, fallback3);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            MyLog.w("failed to get bucket" + e.getMessage());
        }
        for (int i5 = 0; i5 < arrayList.size(); i5++) {
            Fallback fallback4 = arrayList2.get(i5);
            if (fallback4 != null) {
                updateFallbacks(arrayList.get(i5), fallback4);
            }
        }
        persist();
        return arrayList2;
    }

    public static void setHostManagerFactory(HostManagerFactory hostManagerFactory) {
        synchronized (HostManager.class) {
            try {
                factory = hostManagerFactory;
                sInstance = null;
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    protected boolean checkHostMapping() {
        synchronized (this.mHostsMapping) {
            if (hostLoaded) {
                return true;
            }
            hostLoaded = true;
            this.mHostsMapping.clear();
            try {
                String strLoadHosts = loadHosts();
                if (!TextUtils.isEmpty(strLoadHosts)) {
                    fromJSON(strLoadHosts);
                    MyLog.i("loading the new hosts succeed");
                    return true;
                }
            } catch (Throwable th) {
                MyLog.w("load bucket failure: " + th.getMessage());
            }
            return false;
        }
    }

    public void clear() {
        synchronized (this.mHostsMapping) {
            this.mHostsMapping.clear();
        }
    }

    public String dump() {
        StringBuilder sb = new StringBuilder();
        synchronized (this.mHostsMapping) {
            for (Map.Entry<String, Fallbacks> entry : this.mHostsMapping.entrySet()) {
                sb.append(entry.getKey());
                sb.append(":\n");
                sb.append(entry.getValue().toString());
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    protected void fromJSON(String str) throws JSONException {
        synchronized (this.mHostsMapping) {
            this.mHostsMapping.clear();
            JSONObject jSONObject = new JSONObject(str);
            if (jSONObject.optInt("ver") != 2) {
                throw new JSONException("Bad version");
            }
            JSONArray jSONArrayOptJSONArray = jSONObject.optJSONArray("data");
            for (int i = 0; i < jSONArrayOptJSONArray.length(); i++) {
                Fallbacks fallbacksFromJSON = new Fallbacks().fromJSON(jSONArrayOptJSONArray.getJSONObject(i));
                this.mHostsMapping.put(fallbacksFromJSON.getHost(), fallbacksFromJSON);
            }
            JSONArray jSONArrayOptJSONArray2 = jSONObject.optJSONArray("reserved");
            for (int i2 = 0; i2 < jSONArrayOptJSONArray2.length(); i2++) {
                JSONObject jSONObject2 = jSONArrayOptJSONArray2.getJSONObject(i2);
                Fallback fallbackFromJSON = new Fallback(jSONObject2.optString("host")).fromJSON(jSONObject2);
                sReservedHosts.put(fallbackFromJSON.host, fallbackFromJSON);
            }
        }
    }

    public ArrayList<HttpApi> generateHostStats() throws JSONException {
        ArrayList<HttpApi> arrayList;
        Iterator<WeightedHost> it;
        Fallback fallback;
        synchronized (this.mHostsMapping) {
            HashMap map = new HashMap();
            Iterator<String> it2 = this.mHostsMapping.keySet().iterator();
            while (true) {
                Iterator<String> it3 = it2;
                if (!it3.hasNext()) {
                    break;
                }
                Fallbacks fallbacks = this.mHostsMapping.get(it3.next());
                if (fallbacks != null) {
                    Iterator<Fallback> it4 = fallbacks.getFallbacks().iterator();
                    while (it4.hasNext()) {
                        Fallback next = it4.next();
                        HttpApi httpApi = (HttpApi) map.get(next.getISP());
                        HttpApi httpApi2 = httpApi;
                        if (httpApi == null) {
                            httpApi2 = new HttpApi();
                            httpApi2.setCategory("httpapi");
                            httpApi2.setClient_ip(next.ip);
                            httpApi2.setNetwork(processNetwork(next.networkLabel));
                            httpApi2.setUuid(this.sUserId);
                            httpApi2.setVersion(sAppVersion);
                            httpApi2.setVersion_type(sAppName);
                            httpApi2.setApp_name(sAppContext.getPackageName());
                            httpApi2.setApp_version(getVersionName());
                            Location location = new Location();
                            location.setCity(next.city);
                            location.setContry(next.country);
                            location.setProvince(next.province);
                            location.setIsp(next.isp);
                            httpApi2.setLocation(location);
                            map.put(next.getISP(), httpApi2);
                        }
                        HostInfo hostInfo = new HostInfo();
                        hostInfo.setHost(next.host);
                        ArrayList arrayList2 = new ArrayList();
                        Iterator<WeightedHost> it5 = next.getWeightedHost().iterator();
                        while (it5.hasNext()) {
                            WeightedHost next2 = it5.next();
                            ArrayList<AccessHistory> unTouchedAccessHistory = next2.getUnTouchedAccessHistory();
                            if (unTouchedAccessHistory.isEmpty()) {
                                Fallback fallback2 = next;
                                it = it5;
                                fallback = fallback2;
                            } else {
                                LandNodeInfo landNodeInfo = new LandNodeInfo();
                                landNodeInfo.setIp(next2.host);
                                int i = 0;
                                HashMap map2 = new HashMap();
                                int i2 = 0;
                                long j = 0;
                                int size = 0;
                                for (AccessHistory accessHistory : unTouchedAccessHistory) {
                                    if (accessHistory.getWeight() >= 0) {
                                        i++;
                                        long cost = accessHistory.getCost();
                                        size = (int) (((long) size) + accessHistory.getSize());
                                        j += cost;
                                    } else {
                                        String exception = accessHistory.getException();
                                        if (!TextUtils.isEmpty(exception)) {
                                            Integer num = (Integer) map2.get(exception);
                                            map2.put(exception, Integer.valueOf(num != null ? num.intValue() + 1 : 1));
                                        }
                                        i2++;
                                    }
                                }
                                Fallback fallback3 = next;
                                it = it5;
                                landNodeInfo.setExp_info(map2);
                                landNodeInfo.setSuccess_count(i);
                                landNodeInfo.setFailed_count(i2);
                                landNodeInfo.setDuration(j);
                                landNodeInfo.setSize(size);
                                arrayList2.add(landNodeInfo);
                                fallback = fallback3;
                            }
                            it5 = it;
                            next = fallback;
                        }
                        if (!arrayList2.isEmpty()) {
                            hostInfo.setLand_node_info(arrayList2);
                            httpApi2.addToHost_info(hostInfo);
                        }
                    }
                }
                it2 = it3;
            }
            arrayList = new ArrayList<>();
            for (Object value : map.values()) {
                HttpApi httpApi3 = (HttpApi) value;
                if (httpApi3.getHost_infoSize() > 0) {
                    arrayList.add(httpApi3);
                }
            }
        }
        return arrayList;
    }

    public String getCurrentISP() {
        return this.currentISP;
    }

    public Fallback getFallbacksByHost(String str) {
        return getFallbacksByHost(str, true);
    }

    public Fallback getFallbacksByHost(String str, boolean z) {
        Fallback fallbackRequestRemoteFallback;
        if (TextUtils.isEmpty(str)) {
            throw new IllegalArgumentException("the host is empty");
        }
        if (!this.sHostFilter.accept(str)) {
            return null;
        }
        Fallback localFallback = getLocalFallback(str);
        if (localFallback != null && localFallback.isEffective()) {
            return localFallback;
        }
        if (z && Network.hasNetwork(sAppContext) && (fallbackRequestRemoteFallback = requestRemoteFallback(str)) != null) {
            return fallbackRequestRemoteFallback;
        }
        Fallback fallback = new Fallback(str);
        if (localFallback != null) {
            fallback.ip = localFallback.ip;
        }
        return fallback;
    }

    public Fallback getFallbacksByURL(String str) throws MalformedURLException {
        if (TextUtils.isEmpty(str)) {
            throw new IllegalArgumentException("the url is empty");
        }
        return getFallbacksByHost(new URL(str).getHost(), true);
    }

    protected String getHost() {
        String region = AppRegionStorage.getInstance(sAppContext).getRegion();
        boolean zIsEmpty = TextUtils.isEmpty(region);
        String str = HOST;
        if (zIsEmpty) {
            return HOST;
        }
        if (!PushChannelRegion.China.name().equals(region)) {
            str = HOST_GLOBAL;
        }
        return str;
    }

    protected Fallback getLocalFallback(String str) {
        Fallbacks fallbacks;
        Fallback fallback;
        synchronized (this.mHostsMapping) {
            checkHostMapping();
            fallbacks = this.mHostsMapping.get(str);
        }
        if (fallbacks == null || (fallback = fallbacks.getFallback()) == null) {
            return null;
        }
        return fallback;
    }

    protected String getProcessName() {
        List<ActivityManager.RunningAppProcessInfo> runningAppProcesses = ((ActivityManager) sAppContext.getSystemService("activity")).getRunningAppProcesses();
        if (runningAppProcesses == null) {
            return BuildConfig.APPLICATION_ID;
        }
        for (ActivityManager.RunningAppProcessInfo runningAppProcessInfo : runningAppProcesses) {
            if (runningAppProcessInfo.pid == Process.myPid()) {
                return runningAppProcessInfo.processName;
            }
        }
        return BuildConfig.APPLICATION_ID;
    }

    protected String getRemoteFallbackJSON(ArrayList<String> arrayList, String str, String str2, boolean z) throws IOException {
        ArrayList<String> urls;
        ArrayList<NameValuePair> arrayList3 = new ArrayList();
        arrayList3.add(new BasicNameValuePair("type", str));
        if (str.equals("wap")) {
            arrayList3.add(new BasicNameValuePair("conpt", obfuscate(Network.getActiveConnPoint(sAppContext))));
        }
        if (z) {
            arrayList3.add(new BasicNameValuePair("reserved", "1"));
        }
        arrayList3.add(new BasicNameValuePair("uuid", str2));
        arrayList3.add(new BasicNameValuePair("list", XMStringUtils.join(arrayList, ",")));
        arrayList3.add(new BasicNameValuePair("countrycode", AppRegionStorage.getInstance(sAppContext).getCountryCode()));
        Fallback localFallback = getLocalFallback(getHost());
        String str3 = String.format(Locale.US, BUCKET_URL, getHost());
        ArrayList<String> arrayList2 = new ArrayList<>();
        synchronized (sReservedHosts) {
            Fallback fallback = sReservedHosts.get(HOST);
            if (fallback != null) {
                Iterator<String> it = fallback.getHosts(true).iterator();
                while (it.hasNext()) {
                    arrayList2.add(it.next());
                }
            }
        }
        HostRequestUrlsPlan planRequestUrls = HostManagerRuntime.planRequestUrls(str3, localFallback == null ? null : localFallback.getUrls(str3), arrayList2);
        urls = new ArrayList<>(planRequestUrls.getUrls());
        Iterator<String> it2 = urls.iterator();
        IOException e = null;
        while (it2.hasNext()) {
            Uri.Builder builderBuildUpon = Uri.parse(it2.next()).buildUpon();
            for (NameValuePair nameValuePair : arrayList3) {
                builderBuildUpon.appendQueryParameter(nameValuePair.getName(), nameValuePair.getValue());
            }
            try {
                HttpGet httpGet = this.sHttpGetter;
                return httpGet == null ? Network.downloadXml(sAppContext, new URL(builderBuildUpon.toString())) : httpGet.doGet(builderBuildUpon.toString());
            } catch (IOException e2) {
                e = e2;
            }
        }
        if (e == null) {
            return null;
        }
        MyLog.w("network exception: " + e.getMessage());
        throw e;
    }

    protected String loadHosts() {
        BufferedReader bufferedReader = null;
        BufferedReader bufferedReader2 = null;
        try {
            File file = new File(sAppContext.getFilesDir(), getProcessName());
            if (file.isFile()) {
                BufferedReader bufferedReader3 = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
                StringBuilder sb = new StringBuilder();
                while (true) {
                    String line = bufferedReader3.readLine();
                    if (line == null) {
                        bufferedReader2 = bufferedReader3;
                        String string = sb.toString();
                        IOUtils.closeQuietly(bufferedReader3);
                        return string;
                    }
                    sb.append(line);
                }
            }
        } catch (Throwable th) {
            try {
                MyLog.w("load host exception " + th.getMessage());
                bufferedReader = bufferedReader2;
            } finally {
                IOUtils.closeQuietly(bufferedReader2);
            }
        }
        return null;
    }

    public void persist() {
        synchronized (this.mHostsMapping) {
            try {
                BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(sAppContext.openFileOutput(getProcessName(), 0)));
                String string = toJSON().toString();
                if (!TextUtils.isEmpty(string)) {
                    bufferedWriter.write(string);
                }
                bufferedWriter.close();
            } catch (Exception e) {
                MyLog.w("persist bucket failure: " + e.getMessage());
            }
        }
    }

    public void purge() {
        synchronized (this.mHostsMapping) {
            Iterator<Fallbacks> it = this.mHostsMapping.values().iterator();
            while (it.hasNext()) {
                it.next().purge(true);
            }
            boolean z = false;
            while (!z) {
                Iterator<String> it2 = this.mHostsMapping.keySet().iterator();
                while (true) {
                    z = true;
                    if (it2.hasNext()) {
                        String next = it2.next();
                        if (this.mHostsMapping.get(next).getFallbacks().isEmpty()) {
                            this.mHostsMapping.remove(next);
                            z = false;
                            break;
                        }
                    }
                }
            }
        }
    }

    public void refreshFallbacks() throws Throwable {
        ArrayList<String> arrayList;
        ArrayList<String> arrayList2;
        synchronized (this.mHostsMapping) {
            checkHostMapping();
            arrayList = new ArrayList<>(this.mHostsMapping.keySet());
            arrayList2 = new ArrayList<>();
            for (String str : arrayList) {
                Fallbacks fallbacks = this.mHostsMapping.get(str);
                if (fallbacks != null && fallbacks.getFallback() != null) {
                    arrayList2.add(str);
                }
            }
        }
        HostRefreshTargetsPlan planRefreshTargets = HostManagerRuntime.planRefreshTargets(arrayList, new java.util.HashSet(arrayList2));
        ArrayList<String> arrayList3 = new ArrayList<>(planRefreshTargets.getTargetHosts());
        ArrayList<Fallback> arrayListRequestRemoteFallbacks = requestRemoteFallbacks(arrayList3);
        for (int i = 0; i < arrayList3.size(); i++) {
            if (arrayListRequestRemoteFallbacks.get(i) != null) {
                updateFallbacks(arrayList3.get(i), arrayListRequestRemoteFallbacks.get(i));
            }
        }
    }

    protected Fallback requestRemoteFallback(String str) {
        HostRequestThrottlePlan planRemoteFallbackRequest = HostManagerRuntime.planRemoteFallbackRequest(System.currentTimeMillis(), this.lastRemoteRequestTimestamp, this.remoteRequestFailureCount);
        if (!planRemoteFallbackRequest.getShouldRequest()) {
            return null;
        }
        this.lastRemoteRequestTimestamp = planRemoteFallbackRequest.getNextTimestampMs();
        ArrayList<String> arrayList = new ArrayList<>();
        arrayList.add(str);
        Fallback fallback = null;
        try {
            fallback = requestRemoteFallbacks(arrayList).get(0);
        } catch (Throwable th) {
            MyLog.e(th);
        }
        if (fallback != null) {
            this.remoteRequestFailureCount = 0L;
            return fallback;
        }
        long j = this.remoteRequestFailureCount;
        if (j >= 15) {
            return null;
        }
        this.remoteRequestFailureCount = j + 1;
        return null;
    }

    public void setCurrentISP(String str) {
        this.currentISP = str;
    }

    protected JSONObject toJSON() throws JSONException {
        JSONObject jSONObject;
        synchronized (this.mHostsMapping) {
            jSONObject = new JSONObject();
            jSONObject.put("ver", 2);
            JSONArray jSONArray = new JSONArray();
            Iterator<Fallbacks> it = this.mHostsMapping.values().iterator();
            while (it.hasNext()) {
                jSONArray.put(it.next().toJSON());
            }
            jSONObject.put("data", jSONArray);
            JSONArray jSONArray2 = new JSONArray();
            Iterator<Fallback> it2 = sReservedHosts.values().iterator();
            while (it2.hasNext()) {
                jSONArray2.put(it2.next().toJSON());
            }
            jSONObject.put("reserved", jSONArray2);
        }
        return jSONObject;
    }

    public void updateFallbacks(String str, Fallback fallback) {
        if (TextUtils.isEmpty(str) || fallback == null) {
            throw new IllegalArgumentException("the argument is invalid " + str + ", " + fallback);
        }
        if (this.sHostFilter.accept(str)) {
            synchronized (this.mHostsMapping) {
                checkHostMapping();
                if (this.mHostsMapping.containsKey(str)) {
                    this.mHostsMapping.get(str).addFallback(fallback);
                } else {
                    Fallbacks fallbacks = new Fallbacks(str);
                    fallbacks.addFallback(fallback);
                    this.mHostsMapping.put(str, fallbacks);
                }
            }
        }
    }

    public void updateHostsMapping(Map<String, Fallbacks> map) {
        synchronized (this.mHostsMapping) {
            this.mHostsMapping.clear();
            this.mHostsMapping.putAll(map);
        }
    }
}
