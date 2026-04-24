package com.xiaomi.channel.commonutils.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import com.xiaomi.channel.commonutils.android.TelephonyUtils;
import com.xiaomi.channel.commonutils.file.IOUtils;
import com.xiaomi.channel.commonutils.string.MD5;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/network/Network.class */
public class Network {
    public static final String CHINA_3G_CDMA2000 = "CDMA2000";
    public static final String CHINA_3G_TD_SCDMA = "TD-SCDMA";
    public static final String CHINA_3G_WCDMA = "WCDMA";
    public static final String CMWAP_GATEWAY = "10.0.0.172";
    public static final String CMWAP_HEADER_HOST_KEY = "X-Online-Host";
    public static final int CMWAP_PORT = 80;
    public static final int CONNECTION_TIMEOUT = 10000;
    private static final String LogTag = "com.xiaomi.common.Network";
    public static final String NETWORK_TYPE_3GNET = "3gnet";
    public static final String NETWORK_TYPE_3GWAP = "3gwap";
    public static final String NETWORK_TYPE_CHINATELECOM = "#777";
    public static final String NETWORK_TYPE_WIFI = "wifi";
    public static final int READ_TIMEOUT = 15000;
    public static final String USER_AGENT = "User-Agent";
    public static final String UserAgent_PC_Chrome = "Mozilla/5.0 (Windows; U; Windows NT 6.1; en-US) AppleWebKit/534.3 (KHTML, like Gecko) Chrome/6.0.464.0 Safari/534.3";
    public static final String UserAgent_PC_Chrome_6_0_464_0 = "Mozilla/5.0 (Windows; U; Windows NT 6.1; en-US) AppleWebKit/534.3 (KHTML, like Gecko) Chrome/6.0.464.0 Safari/534.3";
    public static final Pattern ContentTypePattern_MimeType = Pattern.compile("([^\\s;]+)(.*)");
    public static final Pattern ContentTypePattern_Charset = Pattern.compile("(.*?charset\\s*=[^a-zA-Z0-9]*)([-a-zA-Z0-9]+)(.*)", 2);
    public static final Pattern ContentTypePattern_XmlEncoding = Pattern.compile("(\\<\\?xml\\s+.*?encoding\\s*=[^a-zA-Z0-9]*)([-a-zA-Z0-9]+)(.*)", 2);
    private static final ExecutorService DOWNLOAD_EXECUTOR = Executors.newCachedThreadPool();

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/network/Network$DoneHandlerInputStream.class */
    public static final class DoneHandlerInputStream extends FilterInputStream {
        private boolean done;

        public DoneHandlerInputStream(InputStream inputStream) {
            super(inputStream);
        }

        @Override // java.io.FilterInputStream, java.io.InputStream
        public int read(byte[] bArr, int i, int i2) throws IOException {
            int i3;
            if (!this.done && (i3 = super.read(bArr, i, i2)) != -1) {
                return i3;
            }
            this.done = true;
            return -1;
        }
    }

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/network/Network$HttpHeaderInfo.class */
    public static class HttpHeaderInfo {
        public Map<String, String> AllHeaders;
        public String ContentType;
        public int ResponseCode;
        public String UserAgent;
        public String realUrl;

        public String toString() {
            return String.format("resCode = %1$d, headers = %2$s", Integer.valueOf(this.ResponseCode), this.AllHeaders.toString());
        }
    }

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/network/Network$PostDownloadHandler.class */
    public interface PostDownloadHandler {
        void OnPostDownload(boolean z);
    }

    public static void beginDownloadFile(String str, OutputStream outputStream, Context context, boolean z, PostDownloadHandler postDownloadHandler) {
        DOWNLOAD_EXECUTOR.execute(() -> postDownloadHandler.OnPostDownload(downloadFile(str, outputStream, z, context)));
    }

    public static void beginDownloadFile(String str, OutputStream outputStream, PostDownloadHandler postDownloadHandler) {
        DOWNLOAD_EXECUTOR.execute(() -> postDownloadHandler.OnPostDownload(downloadFile(str, outputStream)));
    }

    public static HttpResponse doHttpPost(Context context, String str, Map<String, String> map) throws IOException {
        return httpRequest(context, str, "POST", null, fromParamsMapToString(map));
    }

    public static boolean downloadFile(String str, OutputStream outputStream) {
        return downloadFile(str, outputStream, false, null);
    }

    public static boolean downloadFile(String str, OutputStream outputStream, Context context) {
        try {
            HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(str).openConnection();
            HttpURLConnection.setFollowRedirects(true);
            httpURLConnection.setConnectTimeout(CONNECTION_TIMEOUT);
            httpURLConnection.setReadTimeout(15000);
            httpURLConnection.connect();
            InputStream inputStream = httpURLConnection.getInputStream();
            byte[] bArr = new byte[1024];
            while (true) {
                int i = inputStream.read(bArr);
                if (i <= 0) {
                    inputStream.close();
                    outputStream.close();
                    return true;
                }
                outputStream.write(bArr, 0, i);
            }
        } catch (IOException e) {
            Log.e(LogTag, "error while download file:" + e.getClass().getSimpleName());
            return false;
        } catch (Throwable th) {
            Log.e(LogTag, "error while download file" + th);
            return false;
        }
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference failed for: r0v35, types: [java.io.Closeable] */
    /* JADX WARN: Type inference failed for: r5v0, types: [java.lang.String] */
    /* JADX WARN: Type inference failed for: r5v11 */
    /* JADX WARN: Type inference failed for: r5v12 */
    /* JADX WARN: Type inference failed for: r5v5 */
    /* JADX WARN: Type inference failed for: r5v6 */
    public static boolean downloadFile(String str, OutputStream outputStream, boolean z, Context context) {
        boolean z2;
        InputStream inputStream = null;
        InputStream inputStream2 = null;
        try {
            try {
                HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(str).openConnection();
                httpURLConnection.setConnectTimeout(CONNECTION_TIMEOUT);
                httpURLConnection.setReadTimeout(15000);
                HttpURLConnection.setFollowRedirects(true);
                httpURLConnection.connect();
                InputStream inputStream3 = httpURLConnection.getInputStream();
                byte[] bArr = new byte[1024];
                while (true) {
                    inputStream = inputStream3;
                    inputStream2 = inputStream3;
                    int i = inputStream3.read(bArr);
                    z2 = false;
                    if (i == -1) {
                        break;
                    }
                    outputStream.write(bArr, 0, i);
                    if (z && context != null && !isWIFIConnected(context)) {
                        z2 = true;
                        break;
                    }
                }
                IOUtils.closeQuietly(inputStream3);
                IOUtils.closeQuietly(outputStream);
                return !z2;
            } catch (Throwable th) {
                IOUtils.closeQuietly(inputStream);
                IOUtils.closeQuietly(outputStream);
                throw th;
            }
        } catch (IOException e) {
            StringBuilder sb = new StringBuilder();
            sb.append("error while download file:");
            sb.append(e.getClass().getSimpleName());
            Log.e(LogTag, sb.toString());
            inputStream = inputStream2;
            IOUtils.closeQuietly(inputStream);
            IOUtils.closeQuietly(outputStream);
            return false;
        } catch (Throwable th2) {
            StringBuilder sb2 = new StringBuilder();
            sb2.append("error while download file");
            sb2.append(th2);
            Log.e(LogTag, sb2.toString());
            IOUtils.closeQuietly(inputStream);
            IOUtils.closeQuietly(outputStream);
            return false;
        }
    }

    public static String downloadXml(Context context, URL url) throws IOException {
        return downloadXml(context, url, false, (String) null, "UTF-8", (String) null);
    }

    public static String downloadXml(Context context, URL url, String str, String str2, Map<String, String> map, HttpHeaderInfo httpHeaderInfo) throws IOException {
        InputStream inputStream = null;
        try {
            InputStream inputStreamDownloadXmlAsStream = downloadXmlAsStream(context, url, true, str, str2, map, httpHeaderInfo);
            StringBuilder sb = new StringBuilder(1024);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStreamDownloadXmlAsStream, "UTF-8"));
            char[] cArr = new char[4096];
            while (true) {
                inputStream = inputStreamDownloadXmlAsStream;
                int i = bufferedReader.read(cArr);
                if (-1 == i) {
                    IOUtils.closeQuietly(inputStreamDownloadXmlAsStream);
                    return sb.toString();
                }
                sb.append(cArr, 0, i);
            }
        } catch (Throwable th) {
            IOUtils.closeQuietly(inputStream);
            throw th;
        }
    }

    public static String downloadXml(Context context, URL url, boolean z, String str, String str2, String str3) throws IOException {
        InputStream inputStream = null;
        try {
            InputStream inputStreamDownloadXmlAsStream = downloadXmlAsStream(context, url, z, str, str3);
            StringBuilder sb = new StringBuilder(1024);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStreamDownloadXmlAsStream, str2));
            char[] cArr = new char[4096];
            while (true) {
                inputStream = inputStreamDownloadXmlAsStream;
                int i = bufferedReader.read(cArr);
                if (-1 == i) {
                    IOUtils.closeQuietly(inputStreamDownloadXmlAsStream);
                    return sb.toString();
                }
                sb.append(cArr, 0, i);
            }
        } catch (Throwable th) {
            IOUtils.closeQuietly(inputStream);
            throw th;
        }
    }

    public static InputStream downloadXmlAsStream(Context context, URL url) throws IOException {
        return downloadXmlAsStream(context, url, true, null, null, null, null);
    }

    public static InputStream downloadXmlAsStream(Context context, URL url, boolean z, String str, String str2) throws IOException {
        return downloadXmlAsStream(context, url, z, str, str2, null, null);
    }

    public static InputStream downloadXmlAsStream(Context context, URL url, boolean z, String str, String str2, Map<String, String> map, HttpHeaderInfo httpHeaderInfo) throws IOException {
        if (context == null) {
            throw new IllegalArgumentException("context");
        }
        if (url == null) {
            throw new IllegalArgumentException("url");
        }
        URL url2 = url;
        if (!z) {
            url2 = new URL(encryptURL(url.toString()));
        }
        try {
            HttpURLConnection.setFollowRedirects(true);
            HttpURLConnection httpUrlConnection = getHttpUrlConnection(context, url2);
            httpUrlConnection.setConnectTimeout(CONNECTION_TIMEOUT);
            httpUrlConnection.setReadTimeout(15000);
            if (!TextUtils.isEmpty(str)) {
                httpUrlConnection.setRequestProperty(USER_AGENT, str);
            }
            if (str2 != null) {
                httpUrlConnection.setRequestProperty("Cookie", str2);
            }
            if (map != null) {
                for (String str3 : map.keySet()) {
                    httpUrlConnection.setRequestProperty(str3, map.get(str3));
                }
            }
            if (httpHeaderInfo != null && (url.getProtocol().equals("http") || url.getProtocol().equals("https"))) {
                httpHeaderInfo.ResponseCode = httpUrlConnection.getResponseCode();
                if (httpHeaderInfo.AllHeaders == null) {
                    httpHeaderInfo.AllHeaders = new HashMap<>();
                }
                int i = 0;
                while (true) {
                    String headerFieldKey = httpUrlConnection.getHeaderFieldKey(i);
                    String headerField = httpUrlConnection.getHeaderField(i);
                    if (headerFieldKey == null && headerField == null) {
                        break;
                    }
                    if (!TextUtils.isEmpty(headerFieldKey) && !TextUtils.isEmpty(headerField)) {
                        httpHeaderInfo.AllHeaders.put(headerFieldKey, headerField);
                    }
                    i++;
                }
            }
            return new DoneHandlerInputStream(httpUrlConnection.getInputStream());
        } catch (IOException e) {
            throw new IOException("IOException:" + e.getClass().getSimpleName());
        } catch (Throwable th) {
            throw new IOException(th.getMessage());
        }
    }

    public static InputStream downloadXmlAsStreamWithoutRedirect(URL url, String str, String str2) throws IOException {
        try {
            HttpURLConnection.setFollowRedirects(false);
            HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
            httpURLConnection.setConnectTimeout(CONNECTION_TIMEOUT);
            httpURLConnection.setReadTimeout(READ_TIMEOUT);
            if (!TextUtils.isEmpty(str)) {
                httpURLConnection.setRequestProperty(USER_AGENT, str);
            }
            if (str2 != null) {
                httpURLConnection.setRequestProperty("Cookie", str2);
            }
            int responseCode = httpURLConnection.getResponseCode();
            InputStream inputStream = responseCode >= 400 ? httpURLConnection.getErrorStream() : httpURLConnection.getInputStream();
            return new DoneHandlerInputStream(inputStream);
        } catch (IOException e) {
            throw new IOException("IOException:" + e.getClass().getSimpleName());
        } catch (Throwable th) {
            throw new IOException(th.getMessage());
        }
    }

    public static String encryptURL(String str) {
        if (TextUtils.isEmpty(str)) {
            return null;
        }
        new String();
        return String.format("%s&key=%s", str, MD5.MD5_32(String.format("%sbe988a6134bc8254465424e5a70ef037", str)));
    }

    public static String fromParamsMapToString(Map<String, String> map) {
        if (map == null || map.size() <= 0) {
            return null;
        }
        StringBuffer stringBuffer = new StringBuffer();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                try {
                    stringBuffer.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
                    stringBuffer.append("=");
                    stringBuffer.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
                    stringBuffer.append("&");
                } catch (UnsupportedEncodingException e) {
                    Log.d(LogTag, "Failed to convert from params map to string: " + e.toString());
                    Log.d(LogTag, "map: " + map.toString());
                    return null;
                }
            }
        }
        StringBuffer stringBufferDeleteCharAt = stringBuffer;
        if (stringBuffer.length() > 0) {
            stringBufferDeleteCharAt = stringBuffer.deleteCharAt(stringBuffer.length() - 1);
        }
        return stringBufferDeleteCharAt.toString();
    }

    public static String getActiveConnPoint(Context context) {
        if (isWIFIConnected(context)) {
            return NETWORK_TYPE_WIFI;
        }
        try {
            NetworkCapabilities activeNetworkCapabilities = getActiveNetworkCapabilities(context);
            if (activeNetworkCapabilities == null) {
                return "";
            }
            if (activeNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                return joinNetworkPoint("mobile", getActiveCellularSubtypeName(context), getLocalNetworkType(context));
            }
            return getActiveNetworkName(context).toLowerCase(Locale.ROOT);
        } catch (Exception e2) {
            return "";
        }
    }

    public static String getActiveNetworkName(Context context) {
        try {
            NetworkCapabilities activeNetworkCapabilities = getActiveNetworkCapabilities(context);
            if (activeNetworkCapabilities == null) {
                return "null";
            }
            if (activeNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return NETWORK_TYPE_WIFI;
            }
            if (activeNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                String activeCellularSubtypeName = getActiveCellularSubtypeName(context);
                return TextUtils.isEmpty(activeCellularSubtypeName) ? "mobile" : String.format(Locale.ROOT, "mobile-%s", activeCellularSubtypeName);
            }
            if (activeNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                return "ethernet";
            }
            return "unknown";
        } catch (Exception e2) {
            return "null";
        }
    }

    public static int getActiveNetworkType(Context context) {
        try {
            NetworkCapabilities activeNetworkCapabilities = getActiveNetworkCapabilities(context);
            if (activeNetworkCapabilities == null) {
                return -1;
            }
            if (activeNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return 1;
            }
            if (activeNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                return 0;
            }
            if (activeNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                return 9;
            }
            return -1;
        } catch (Exception e2) {
            return -1;
        }
    }

    public static String getCMWapUrl(URL url) {
        StringBuilder sb = new StringBuilder();
        sb.append(url.getProtocol());
        sb.append("://");
        sb.append(CMWAP_GATEWAY);
        sb.append(url.getPath());
        if (!TextUtils.isEmpty(url.getQuery())) {
            sb.append("?");
            sb.append(url.getQuery());
        }
        return sb.toString();
    }

    private static URL getDefaultStreamHandlerURL(String str) throws MalformedURLException {
        return new URL(str);
    }

    public static HttpHeaderInfo getHttpHeaderInfo(String str, String str2, String str3) {
        try {
            URL url = new URL(str);
            if (!url.getProtocol().equals("http") && !url.getProtocol().equals("https")) {
                return null;
            }
            HttpURLConnection.setFollowRedirects(false);
            HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
            if (str.indexOf("wap") == -1) {
                httpURLConnection.setConnectTimeout(5000);
                httpURLConnection.setReadTimeout(5000);
            } else {
                httpURLConnection.setConnectTimeout(15000);
                httpURLConnection.setReadTimeout(15000);
            }
            if (!TextUtils.isEmpty(str2)) {
                httpURLConnection.setRequestProperty(USER_AGENT, str2);
            }
            if (str3 != null) {
                httpURLConnection.setRequestProperty("Cookie", str3);
            }
            HttpHeaderInfo httpHeaderInfo = new HttpHeaderInfo();
            httpHeaderInfo.ResponseCode = httpURLConnection.getResponseCode();
            httpHeaderInfo.UserAgent = str2;
            int i = 0;
            while (true) {
                String headerFieldKey = httpURLConnection.getHeaderFieldKey(i);
                String headerField = httpURLConnection.getHeaderField(i);
                if (headerFieldKey == null && headerField == null) {
                    return httpHeaderInfo;
                }
                if (headerFieldKey != null && headerFieldKey.equals("content-type")) {
                    httpHeaderInfo.ContentType = headerField;
                }
                if (headerFieldKey != null && headerFieldKey.equals("location")) {
                    URI uri = new URI(headerField);
                    URI uriResolve = uri;
                    if (!uri.isAbsolute()) {
                        uriResolve = new URI(str).resolve(uri);
                    }
                    httpHeaderInfo.realUrl = uriResolve.toString();
                }
                i++;
            }
        } catch (MalformedURLException e) {
            Log.e(LogTag, "Failed to transform URL", e);
            return null;
        } catch (IOException e2) {
            Log.e(LogTag, "Failed to get mime type", e2);
            return null;
        } catch (URISyntaxException e3) {
            Log.e(LogTag, "Failed to parse URI", e3);
            return null;
        } catch (Throwable th) {
            Log.e(LogTag, "Failed to get HttpHeaderInfo", th);
            return null;
        }
    }

    /* JADX WARN: Multi-variable type inference failed */
    public static InputStream getHttpPostAsStream(URL url, String str, Map<String, String> map, String str2, String str3) throws IOException {
        if (url == null) {
            throw new IllegalArgumentException("url");
        }
        boolean z = false;
        boolean z2 = false;
        try {
            try {
                HttpURLConnection.setFollowRedirects(true);
                HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
                httpURLConnection.setConnectTimeout(5000);
                httpURLConnection.setReadTimeout(15000);
                httpURLConnection.setRequestMethod("POST");
                httpURLConnection.setDoOutput(true);
                if (!TextUtils.isEmpty(str2)) {
                    httpURLConnection.setRequestProperty(USER_AGENT, str2);
                }
                if (!TextUtils.isEmpty(str3)) {
                    httpURLConnection.setRequestProperty("Cookie", str3);
                }
                OutputStream outputStream = httpURLConnection.getOutputStream();
                outputStream.write(str.getBytes());
                outputStream.flush();
                outputStream.close();
                StringBuilder sb = new StringBuilder();
                sb.append(httpURLConnection.getResponseCode());
                sb.append("");
                map.put("ResponseCode", sb.toString());
                int i = 0;
                while (true) {
                    String headerFieldKey = httpURLConnection.getHeaderFieldKey(i);
                    String headerField = httpURLConnection.getHeaderField(i);
                    if (headerFieldKey == null && headerField == null) {
                        z = false;
                        z2 = false;
                        InputStream inputStream = httpURLConnection.getInputStream();
                        IOUtils.closeQuietly(null);
                        return inputStream;
                    }
                    map.put(headerFieldKey, headerField);
                    i++;
                }
            } catch (Throwable th) {
                throw th;
            }
        } catch (IOException e) {
            StringBuilder sb2 = new StringBuilder();
            sb2.append("IOException:");
            sb2.append(e.getClass().getSimpleName());
            IOException iOException = new IOException(sb2.toString());
            throw iOException;
        } catch (Throwable th2) {
            throw new IOException(th2.getMessage());
        }
    }

    public static HttpURLConnection getHttpUrlConnection(Context context, URL url) throws IOException {
        if ("http".equals(url.getProtocol()) && isCtwap(context)) {
            return (HttpURLConnection) url.openConnection(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("10.0.0.200", 80)));
        }
        return (HttpURLConnection) url.openConnection();
    }

    public static String getLocalNetworkType(Context context) {
        if (isWIFIConnected(context)) {
            return NETWORK_TYPE_WIFI;
        }
        if (TelephonyUtils.isChinaTelecom(context)) {
            return NETWORK_TYPE_CHINATELECOM;
        }
        String activeCellularSubtypeName = getActiveCellularSubtypeName(context);
        return TextUtils.isEmpty(activeCellularSubtypeName) ? "unknown" : activeCellularSubtypeName.toLowerCase(Locale.ROOT);
    }

    public static boolean hasNetwork(Context context) {
        return getActiveNetworkCapabilities(context) != null;
    }

    public static HttpResponse httpRequest(Context context, String str, String str2, Map<String, String> map, String str3) throws IOException {
        HttpResponse httpResponse = new HttpResponse();
        OutputStream outputStream = null;
        BufferedReader bufferedReader = null;
        try {
            HttpURLConnection httpUrlConnection = getHttpUrlConnection(context, getDefaultStreamHandlerURL(str));
            httpUrlConnection.setConnectTimeout(CONNECTION_TIMEOUT);
            httpUrlConnection.setReadTimeout(READ_TIMEOUT);
            httpUrlConnection.setRequestMethod(str2 == null ? "GET" : str2);
            if (map != null) {
                for (Map.Entry<String, String> entry : map.entrySet()) {
                    httpUrlConnection.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }
            if (!TextUtils.isEmpty(str3)) {
                byte[] bytes = str3.getBytes();
                httpUrlConnection.setDoOutput(true);
                outputStream = httpUrlConnection.getOutputStream();
                outputStream.write(bytes, 0, bytes.length);
                outputStream.flush();
            }
            httpResponse.responseCode = httpUrlConnection.getResponseCode();
            Log.d(LogTag, "Http POST Response Code: " + httpResponse.responseCode);
            for (int i = 0; ; i++) {
                String headerFieldKey = httpUrlConnection.getHeaderFieldKey(i);
                String headerField = httpUrlConnection.getHeaderField(i);
                if (headerFieldKey == null && headerField == null) {
                    break;
                }
                httpResponse.headers.put(headerFieldKey, headerField);
            }
            InputStream inputStream;
            try {
                inputStream = httpUrlConnection.getInputStream();
            } catch (IOException unused) {
                inputStream = httpUrlConnection.getErrorStream();
            }
            bufferedReader = new BufferedReader(new InputStreamReader(new DoneHandlerInputStream(inputStream)));
            StringBuffer stringBuffer = new StringBuffer();
            String property = System.getProperty("line.separator");
            String line = bufferedReader.readLine();
            while (line != null) {
                stringBuffer.append(line).append(property);
                line = bufferedReader.readLine();
            }
            httpResponse.responseString = stringBuffer.toString();
            return httpResponse;
        } catch (IOException e) {
            throw e;
        } catch (Throwable th) {
            throw new IOException(th.getMessage());
        } finally {
            IOUtils.closeQuietly(outputStream);
            IOUtils.closeQuietly(bufferedReader);
        }
    }

    public static boolean is2GConnected(Context context) {
        int activeCellularSubtype = getActiveCellularSubtype(context);
        if (activeCellularSubtype < 0) {
            return false;
        }
        switch (activeCellularSubtype) {
            case 1:
            case 2:
            case 4:
            case 7:
            case 11:
                return true;
        }
        return false;
    }

    public static boolean is3GConnected(Context context) {
        int activeCellularSubtype = getActiveCellularSubtype(context);
        if (activeCellularSubtype < 0) {
            return false;
        }
        String subtypeName = getActiveCellularSubtypeName(context);
        if (CHINA_3G_TD_SCDMA.equalsIgnoreCase(subtypeName) || CHINA_3G_CDMA2000.equalsIgnoreCase(subtypeName) || CHINA_3G_WCDMA.equalsIgnoreCase(subtypeName)) {
            return true;
        }
        switch (activeCellularSubtype) {
            case 3:
            case 5:
            case 6:
            case 8:
            case 9:
            case 10:
            case 12:
            case 14:
            case 15:
                return true;
        }
        return false;
    }

    public static boolean is4GConnected(Context context) {
        return getActiveCellularSubtype(context) == TelephonyManager.NETWORK_TYPE_LTE;
    }

    public static boolean is5GConnected(Context context) {
        return getActiveCellularSubtype(context) == TelephonyManager.NETWORK_TYPE_NR;
    }

    public static boolean isConnected(Context context) {
        try {
            NetworkCapabilities activeNetworkCapabilities = getActiveNetworkCapabilities(context);
            return activeNetworkCapabilities != null && activeNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isCtwap(Context context) {
        if (!"CN".equalsIgnoreCase(((TelephonyManager) context.getSystemService("phone")).getSimCountryIso())) {
            return false;
        }
        return false;
    }

    public static boolean isUsingMobileDataConnection(Context context) {
        return is5GConnected(context) || is4GConnected(context) || is3GConnected(context) || is2GConnected(context);
    }

    public static boolean isWIFIConnected(Context context) {
        try {
            NetworkCapabilities activeNetworkCapabilities = getActiveNetworkCapabilities(context);
            return activeNetworkCapabilities != null && activeNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } catch (Exception e2) {
            return false;
        }
    }

    private static ConnectivityManager getConnectivityManager(Context context) {
        return (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    private static NetworkCapabilities getActiveNetworkCapabilities(Context context) {
        ConnectivityManager connectivityManager = getConnectivityManager(context);
        if (connectivityManager == null) {
            return null;
        }
        android.net.Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            return null;
        }
        return connectivityManager.getNetworkCapabilities(activeNetwork);
    }

    private static int getActiveCellularSubtype(Context context) {
        NetworkCapabilities activeNetworkCapabilities = getActiveNetworkCapabilities(context);
        if (activeNetworkCapabilities == null || !activeNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return -1;
        }
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager == null) {
            return TelephonyManager.NETWORK_TYPE_UNKNOWN;
        }
        return telephonyManager.getDataNetworkType();
    }

    private static String getActiveCellularSubtypeName(Context context) {
        int activeCellularSubtype = getActiveCellularSubtype(context);
        if (activeCellularSubtype < 0) {
            return "";
        }
        String networkTypeName = getNetworkTypeName(activeCellularSubtype);
        return networkTypeName == null ? "" : networkTypeName;
    }

    private static String getNetworkTypeName(int networkType) {
        switch (networkType) {
            case TelephonyManager.NETWORK_TYPE_GPRS:
                return "GPRS";
            case TelephonyManager.NETWORK_TYPE_EDGE:
                return "EDGE";
            case TelephonyManager.NETWORK_TYPE_UMTS:
                return "UMTS";
            case 4:
                return "CDMA";
            case 5:
                return "EVDO_0";
            case 6:
                return "EVDO_A";
            case 7:
                return "1xRTT";
            case TelephonyManager.NETWORK_TYPE_HSDPA:
                return "HSDPA";
            case TelephonyManager.NETWORK_TYPE_HSUPA:
                return "HSUPA";
            case TelephonyManager.NETWORK_TYPE_HSPA:
                return "HSPA";
            case 11:
                return "IDEN";
            case 12:
                return "EVDO_B";
            case TelephonyManager.NETWORK_TYPE_LTE:
                return "LTE";
            case 14:
                return "EHRPD";
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                return "HSPAP";
            case TelephonyManager.NETWORK_TYPE_GSM:
                return "GSM";
            case TelephonyManager.NETWORK_TYPE_TD_SCDMA:
                return CHINA_3G_TD_SCDMA;
            case TelephonyManager.NETWORK_TYPE_IWLAN:
                return "IWLAN";
            case TelephonyManager.NETWORK_TYPE_NR:
                return "NR";
            default:
                return "UNKNOWN";
        }
    }

    private static String joinNetworkPoint(String... parts) {
        StringBuilder stringBuilder = new StringBuilder();
        for (String part : parts) {
            if (TextUtils.isEmpty(part)) {
                continue;
            }
            if (stringBuilder.length() > 0) {
                stringBuilder.append("-");
            }
            stringBuilder.append(part);
        }
        return stringBuilder.toString().toLowerCase(Locale.ROOT);
    }

    /* JADX WARN: Code restructure failed: missing block: B:54:0x01d5, code lost:
    
        r0 = com.xiaomi.channel.commonutils.network.Network.ContentTypePattern_XmlEncoding.matcher(r0);
     */
    /* JADX WARN: Code restructure failed: missing block: B:55:0x01df, code lost:
    
        r7 = r4;
     */
    /* JADX WARN: Code restructure failed: missing block: B:57:0x01f4, code lost:
    
        if (r0.matches() == false) goto L79;
     */
    /* JADX WARN: Code restructure failed: missing block: B:59:0x01f8, code lost:
    
        r7 = r4;
     */
    /* JADX WARN: Code restructure failed: missing block: B:61:0x020e, code lost:
    
        if (r0.groupCount() < 3) goto L79;
     */
    /* JADX WARN: Code restructure failed: missing block: B:64:0x0220, code lost:
    
        r0 = r0.group(2);
     */
    /* JADX WARN: Code restructure failed: missing block: B:65:0x0229, code lost:
    
        r7 = r4;
     */
    /* JADX WARN: Code restructure failed: missing block: B:67:0x023e, code lost:
    
        if (android.text.TextUtils.isEmpty(r0) != false) goto L79;
     */
    /* JADX WARN: Code restructure failed: missing block: B:69:0x0243, code lost:
    
        r7 = r0;
     */
    /* JADX WARN: Code restructure failed: missing block: B:72:0x0266, code lost:
    
        r0 = new java.lang.StringBuilder();
     */
    /* JADX WARN: Code restructure failed: missing block: B:74:0x0279, code lost:
    
        r0.append("XML charset detected is: ");
     */
    /* JADX WARN: Code restructure failed: missing block: B:76:0x0290, code lost:
    
        r0.append(r7);
     */
    /* JADX WARN: Code restructure failed: missing block: B:77:0x0298, code lost:
    
        r9 = r0;
        r5 = r0;
        r14 = r0;
        r8 = r0;
     */
    /* JADX WARN: Code restructure failed: missing block: B:78:0x02a5, code lost:
    
        android.util.Log.v(com.xiaomi.channel.commonutils.network.Network.LogTag, r0.toString());
     */
    public static String tryDetectCharsetEncoding(URL url, String str) throws IOException {
        String str2 = TextUtils.isEmpty(str) ? "UTF-8" : str;
        InputStream inputStream = null;
        try {
            HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
            httpURLConnection.setConnectTimeout(CONNECTION_TIMEOUT);
            httpURLConnection.setReadTimeout(READ_TIMEOUT);
            String contentType = httpURLConnection.getContentType();
            if (!TextUtils.isEmpty(contentType)) {
                Matcher matcher = ContentTypePattern_Charset.matcher(contentType);
                if (matcher.matches()) {
                    String group = matcher.group(2);
                    if (!TextUtils.isEmpty(group)) {
                        str2 = group;
                    }
                }
            }
            inputStream = httpURLConnection.getInputStream();
            byte[] bArr = new byte[1024];
            int read = inputStream.read(bArr);
            if (read > 0) {
                String str3;
                try {
                    str3 = new String(bArr, 0, read, str2);
                } catch (UnsupportedEncodingException e) {
                    str3 = new String(bArr, 0, read);
                }
                Matcher matcher2 = ContentTypePattern_XmlEncoding.matcher(str3);
                if (matcher2.find()) {
                    String group2 = matcher2.group(2);
                    if (!TextUtils.isEmpty(group2)) {
                        str2 = group2;
                    }
                }
            }
            Log.v(LogTag, "XML charset detected is: " + str2);
            return str2;
        } finally {
            IOUtils.closeQuietly(inputStream);
        }
    }

    public static String uploadFile(String str, File file, String str2) throws IOException {
        return uploadFile(str, null, file, str2);
    }

    public static String uploadFile(String str, Map<String, String> map, File file, String str2) throws IOException {
        if (!file.exists()) {
            return null;
        }
        HttpURLConnection httpURLConnection = null;
        DataOutputStream dataOutputStream = null;
        FileInputStream fileInputStream = null;
        BufferedReader bufferedReader = null;
        try {
            httpURLConnection = (HttpURLConnection) new URL(str).openConnection();
            httpURLConnection.setReadTimeout(READ_TIMEOUT);
            httpURLConnection.setConnectTimeout(CONNECTION_TIMEOUT);
            httpURLConnection.setDoInput(true);
            httpURLConnection.setDoOutput(true);
            httpURLConnection.setUseCaches(false);
            httpURLConnection.setRequestMethod("POST");
            httpURLConnection.setRequestProperty("Connection", "Keep-Alive");
            httpURLConnection.setRequestProperty("Content-Type", "multipart/form-data;boundary=*****");
            if (map != null) {
                for (Map.Entry<String, String> entry : map.entrySet()) {
                    httpURLConnection.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }
            httpURLConnection.setFixedLengthStreamingMode(file.getName().length() + 77 + ((int) file.length()) + str2.length());
            dataOutputStream = new DataOutputStream(httpURLConnection.getOutputStream());
            dataOutputStream.writeBytes("--*****\r\n");
            dataOutputStream.writeBytes("Content-Disposition: form-data; name=\"" + str2 + "\";filename=\"" + file.getName() + "\"\r\n");
            dataOutputStream.writeBytes("\r\n");
            fileInputStream = new FileInputStream(file);
            byte[] bArr = new byte[1024];
            while (true) {
                int read = fileInputStream.read(bArr);
                if (read == -1) {
                    break;
                }
                dataOutputStream.write(bArr, 0, read);
                dataOutputStream.flush();
            }
            dataOutputStream.writeBytes("\r\n");
            dataOutputStream.writeBytes("--");
            dataOutputStream.writeBytes("*****");
            dataOutputStream.writeBytes("--");
            dataOutputStream.writeBytes("\r\n");
            dataOutputStream.flush();
            StringBuffer stringBuffer = new StringBuffer();
            bufferedReader = new BufferedReader(new InputStreamReader(new DoneHandlerInputStream(httpURLConnection.getInputStream())));
            String line = bufferedReader.readLine();
            while (line != null) {
                stringBuffer.append(line);
                line = bufferedReader.readLine();
            }
            return stringBuffer.toString();
        } catch (IOException e) {
            throw new IOException("IOException:" + e.getClass().getSimpleName());
        } catch (Throwable th) {
            throw new IOException(th.getMessage());
        } finally {
            IOUtils.closeQuietly(fileInputStream);
            IOUtils.closeQuietly(bufferedReader);
            IOUtils.closeQuietly(dataOutputStream);
        }
    }
}
