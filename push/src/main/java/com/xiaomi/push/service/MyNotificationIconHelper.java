package com.xiaomi.push.service;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.xiaomi.channel.commonutils.file.IOUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/* loaded from: classes.dex */
public class MyNotificationIconHelper {
    private static final int CONNECT_TIMEOUT = 8000;
    private static final int MAX_SIZE = 200 * 1024; // 200 KiB
    private static final int READ_TIMEOUT = 20000;
    private static final int READ_UNIT = 1024;
    private static final int STANDARD_DENSITY = 160;
    private static final int STANDARD_ICON_SIZE = 48;

    /* loaded from: classes.dex */
    public static class GetIconResult {
        public Bitmap bitmap;
        public long downloadSize;

        public GetIconResult(Bitmap bitmap, long downloadSize) {
            this.bitmap = bitmap;
            this.downloadSize = downloadSize;
        }
    }

    public static GetIconResult getIconFromUrl(Context context, String urlStr) {
        InputStream isForBitmapSize = null;
        GetIconResult result = new GetIconResult(null, 0L);
        try {
            GetDataResult getDataResult = getDataFromUrl(urlStr);
            if (getDataResult != null) {
                result.downloadSize = getDataResult.downloadSize;
                byte[] data = getDataResult.data;
                if (data != null) {
                    InputStream isForBitmapSize2 = new ByteArrayInputStream(data);
                    try {
                        int sampleSize = getSampleSize(context, isForBitmapSize2);
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inSampleSize = sampleSize;
                        result.bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
                        isForBitmapSize = isForBitmapSize2;
                    } catch (Exception e) {
                        isForBitmapSize = isForBitmapSize2;
                        MyLog.e(e);
                        IOUtils.closeQuietly(isForBitmapSize);
                        return result;
                    } catch (Throwable th) {
                        isForBitmapSize = isForBitmapSize2;
                        IOUtils.closeQuietly(isForBitmapSize);
                        throw th;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        IOUtils.closeQuietly(isForBitmapSize);
        return result;
    }

    /* loaded from: classes.dex */
    public static class GetDataResult {
        byte[] data;
        int downloadSize;

        public GetDataResult(byte[] data, int downloadSize) {
            this.data = data;
            this.downloadSize = downloadSize;
        }
    }

    private static GetDataResult getDataFromUrl(String urlStr) {
        GetDataResult getDataResult;
        HttpURLConnection conn = null;
        try {
            try {
                URL url = new URL(urlStr);
                HttpURLConnection conn2 = (HttpURLConnection) url.openConnection();
                conn2.setConnectTimeout(CONNECT_TIMEOUT);
                conn2.setReadTimeout(READ_TIMEOUT);
                conn2.connect();
                int contentLen = conn2.getContentLength();
                if (contentLen > MAX_SIZE) {
                    MyLog.w("Bitmap size is too big, max size is " + MAX_SIZE + "  contentLen size is " + contentLen + " from url " + urlStr);
                    IOUtils.closeQuietly((InputStream) null);
                    if (conn2 != null) {
                        conn2.disconnect();
                    }
                    return null;
                }
                int responseCode = conn2.getResponseCode();
                if (responseCode != 200) {
                    MyLog.w("Invalid Http Response Code " + responseCode + " received");
                    IOUtils.closeQuietly((InputStream) null);
                    if (conn2 != null) {
                        conn2.disconnect();
                    }
                    return null;
                }
                InputStream inputStream = conn2.getInputStream();
                ByteArrayOutputStream tempOutStream = new ByteArrayOutputStream();
                int availableSpace = MAX_SIZE;
                byte[] dataUnit = new byte[READ_UNIT];
                while (availableSpace > 0) {
                    int readCount = inputStream.read(dataUnit, 0, READ_UNIT);
                    if (readCount == -1) {
                        break;
                    }
                    availableSpace -= readCount;
                    tempOutStream.write(dataUnit, 0, readCount);
                }
                if (availableSpace <= 0) {
                    MyLog.w("length " + MAX_SIZE + " exhausted.");
                    getDataResult = new GetDataResult(null, MAX_SIZE);
                    IOUtils.closeQuietly(inputStream);
                    if (conn2 == null) {
                        return getDataResult;
                    }
                } else {
                    byte[] data = tempOutStream.toByteArray();
                    getDataResult = new GetDataResult(data, data.length);
                    IOUtils.closeQuietly(inputStream);
                    if (conn2 == null) {
                        return getDataResult;
                    }
                }
                conn2.disconnect();
                return getDataResult;
            } catch (IOException e) {
                MyLog.e(e);
                IOUtils.closeQuietly((InputStream) null);
                if (0 != 0) {
                    conn.disconnect();
                }
                return null;
            }
        } catch (Throwable th) {
            IOUtils.closeQuietly((InputStream) null);
            if (0 != 0) {
                conn.disconnect();
            }
            throw th;
        }
    }

    public static Bitmap getIconFromUri(Context context, String uriStr) {
        Bitmap bitmap = null;
        Uri uri = Uri.parse(uriStr);
        InputStream is = null;
        InputStream isForBitmapSize = null;
        try {
            try {
                isForBitmapSize = context.getContentResolver().openInputStream(uri);
                int sampleSize = getSampleSize(context, isForBitmapSize);
                is = context.getContentResolver().openInputStream(uri);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sampleSize;
                bitmap = BitmapFactory.decodeStream(is, null, options);
                IOUtils.closeQuietly(is);
            } catch (IOException e) {
                MyLog.e(e);
                IOUtils.closeQuietly(is);
            }
            IOUtils.closeQuietly(isForBitmapSize);
            return bitmap;
        } catch (Throwable th) {
            IOUtils.closeQuietly(is);
            IOUtils.closeQuietly(isForBitmapSize);
            throw th;
        }
    }

    private static int getSampleSize(Context context, InputStream inputStream) {
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(inputStream, null, opt);
        if (opt.outWidth == -1 || opt.outHeight == -1) {
            MyLog.w("decode dimension failed for bitmap.");
            return 1;
        }
        int screenDensity = context.getResources().getDisplayMetrics().densityDpi;
        int targetWidth = Math.round((screenDensity / 160.0f) * 48.0f);
        if (opt.outWidth <= targetWidth || opt.outHeight <= targetWidth) {
            return 1;
        }
        return Math.min(opt.outWidth / targetWidth, opt.outHeight / targetWidth);
    }
}