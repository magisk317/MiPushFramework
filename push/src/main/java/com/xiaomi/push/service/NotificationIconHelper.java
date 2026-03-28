package com.xiaomi.push.service;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import com.xiaomi.channel.commonutils.file.FileUtils;
import com.xiaomi.channel.commonutils.file.IOUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.string.XMStringUtils;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/NotificationIconHelper.class */
public class NotificationIconHelper {
    private static final int BIG_PICTURE_MAX_SIZE = 2048000;
    private static final int CLEAN_ICON_EXIST_PERIOD = 1209600;
    private static final int CONNECT_TIMEOUT = 8000;
    private static final int MAX_SIZE = 102400;
    private static final String PIC_FILE_DIR_PATH = "mipush_icon";
    private static final int READ_TIMEOUT = 20000;
    private static final int READ_UNIT = 1024;
    private static final int SMALL_ICON_FILE_DIR_MAX_SIZE = 15728640;
    private static final int STANDARD_DENSITY = 160;
    private static final int STANDARD_ICON_SIZE = 48;
    private static long currentPicFileSize;

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/NotificationIconHelper$GetDataResult.class */
    public static class GetDataResult {
        byte[] data;
        int downloadSize;

        public GetDataResult(byte[] bArr, int i) {
            this.data = bArr;
            this.downloadSize = i;
        }
    }

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/NotificationIconHelper$GetIconResult.class */
    public static class GetIconResult {
        public Bitmap bitmap;
        public long downloadSize;

        public GetIconResult(Bitmap bitmap, long j) {
            this.bitmap = bitmap;
            this.downloadSize = j;
        }
    }

    private static void cleanCachedPic(Context context) {
        File file = new File(context.getCacheDir().getPath() + File.separator + PIC_FILE_DIR_PATH);
        if (file.exists()) {
            if (currentPicFileSize == 0) {
                currentPicFileSize = FileUtils.getFolderSize(file);
            }
            if (currentPicFileSize > 15728640) {
                try {
                    File[] fileArrListFiles = file.listFiles();
                    for (int i = 0; i < fileArrListFiles.length; i++) {
                        if (!fileArrListFiles[i].isDirectory() && Math.abs(System.currentTimeMillis() - fileArrListFiles[i].lastModified()) > 1209600) {
                            fileArrListFiles[i].delete();
                        }
                    }
                } catch (Exception e) {
                    MyLog.e(e);
                }
                currentPicFileSize = 0L;
            }
        }
    }

    private static Bitmap getBitmapFromFile(Context context, String str) {
        File file = new File(context.getCacheDir().getPath() + File.separator + PIC_FILE_DIR_PATH, XMStringUtils.getMd5Digest(str));
        if (!file.exists()) {
            return null;
        }
        FileInputStream fileInputStream = null;
        Bitmap bitmap = null;
        FileInputStream fileInputStream2 = null;
        try {
            try {
                FileInputStream fileInputStream3 = new FileInputStream(file);
                Bitmap bitmapDecodeStream = BitmapFactory.decodeStream(fileInputStream3);
                fileInputStream = fileInputStream3;
                bitmap = bitmapDecodeStream;
                fileInputStream2 = fileInputStream3;
                file.setLastModified(System.currentTimeMillis());
                bitmap = bitmapDecodeStream;
                fileInputStream2 = fileInputStream3;
            } catch (Exception e) {
                fileInputStream = fileInputStream2;
                MyLog.e(e);
            }
            IOUtils.closeQuietly(fileInputStream2);
            return bitmap;
        } catch (Throwable th) {
            IOUtils.closeQuietly(fileInputStream);
            throw th;
        }
    }

    private static GetDataResult getDataFromUrl(String str, boolean z) {
        HttpURLConnection httpURLConnection = null;
        HttpURLConnection httpURLConnection2 = null;
        try {
            try {
                HttpURLConnection httpURLConnection3 = (HttpURLConnection) new URL(str).openConnection();
                httpURLConnection3.setConnectTimeout(CONNECT_TIMEOUT);
                httpURLConnection3.setReadTimeout(READ_TIMEOUT);
                httpURLConnection3.setRequestProperty("User-agent", "Mozilla/5.0 (Linux; U;) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/71.0.3578.141 Mobile Safari/537.36 XiaoMi/MiuiBrowser");
                httpURLConnection3.connect();
                int contentLength = httpURLConnection3.getContentLength();
                if (z && contentLength > MAX_SIZE) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Bitmap size is too big, max size is 102400  contentLen size is ");
                    sb.append(contentLength);
                    sb.append(" from url ");
                    sb.append(str);
                    MyLog.w(sb.toString());
                    IOUtils.closeQuietly(null);
                    if (httpURLConnection3 == null) {
                        return null;
                    }
                    httpURLConnection3.disconnect();
                    return null;
                }
                int responseCode = httpURLConnection3.getResponseCode();
                if (responseCode != 200) {
                    StringBuilder sb2 = new StringBuilder();
                    sb2.append("Invalid Http Response Code ");
                    sb2.append(responseCode);
                    sb2.append(" received");
                    MyLog.w(sb2.toString());
                    IOUtils.closeQuietly(null);
                    if (httpURLConnection3 == null) {
                        return null;
                    }
                    httpURLConnection3.disconnect();
                    return null;
                }
                InputStream inputStream = httpURLConnection3.getInputStream();
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                int i = z ? MAX_SIZE : BIG_PICTURE_MAX_SIZE;
                byte[] bArr = new byte[READ_UNIT];
                while (i > 0) {
                    int i2 = inputStream.read(bArr, 0, READ_UNIT);
                    if (i2 == -1) {
                        break;
                    }
                    i -= i2;
                    byteArrayOutputStream.write(bArr, 0, i2);
                }
                if (i <= 0) {
                    MyLog.w("length 102400 exhausted.");
                    GetDataResult getDataResult = new GetDataResult(null, MAX_SIZE);
                    IOUtils.closeQuietly(inputStream);
                    if (httpURLConnection3 != null) {
                        httpURLConnection3.disconnect();
                    }
                    return getDataResult;
                }
                byte[] byteArray = byteArrayOutputStream.toByteArray();
                GetDataResult getDataResult2 = new GetDataResult(byteArray, byteArray.length);
                IOUtils.closeQuietly(inputStream);
                if (httpURLConnection3 != null) {
                    httpURLConnection3.disconnect();
                }
                return getDataResult2;
            } catch (SocketTimeoutException e) {
                StringBuilder sb3 = new StringBuilder();
                sb3.append("Connect timeout to ");
                sb3.append(str);
                MyLog.e(sb3.toString());
                IOUtils.closeQuietly(null);
                if (0 == 0) {
                    return null;
                }
                httpURLConnection2.disconnect();
                return null;
            } catch (IOException e2) {
                MyLog.e(e2);
                IOUtils.closeQuietly(null);
                if (0 == 0) {
                    return null;
                }
                httpURLConnection2 = null;
                httpURLConnection2.disconnect();
                return null;
            }
        } catch (Throwable th) {
            IOUtils.closeQuietly(null);
            if (0 != 0) {
                httpURLConnection.disconnect();
            }
            throw th;
        }
    }

    public static Bitmap getIconFromUri(Context context, String str) {
        Uri uri = Uri.parse(str);
        InputStream inputStream = null;
        InputStream inputStream2 = null;
        InputStream inputStream3 = null;
        InputStream inputStream4 = null;
        try {
            try {
                InputStream inputStreamOpenInputStream = context.getContentResolver().openInputStream(uri);
                int sampleSize = getSampleSize(context, inputStreamOpenInputStream);
                InputStream inputStreamOpenInputStream2 = context.getContentResolver().openInputStream(uri);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sampleSize;
                inputStream3 = inputStreamOpenInputStream2;
                inputStream2 = inputStreamOpenInputStream;
                inputStream4 = inputStreamOpenInputStream2;
                inputStream = inputStreamOpenInputStream;
                Bitmap bitmapDecodeStream = BitmapFactory.decodeStream(inputStreamOpenInputStream2, null, options);
                IOUtils.closeQuietly(inputStreamOpenInputStream2);
                IOUtils.closeQuietly(inputStreamOpenInputStream);
                return bitmapDecodeStream;
            } catch (IOException e) {
                MyLog.e(e);
                IOUtils.closeQuietly(inputStream4);
                IOUtils.closeQuietly(inputStream);
                return null;
            }
        } catch (Throwable th) {
            IOUtils.closeQuietly(inputStream3);
            IOUtils.closeQuietly(inputStream2);
            throw th;
        }
    }

    public static GetIconResult getIconFromUrl(Context context, String str, boolean z) {
        GetIconResult getIconResult = new GetIconResult(null, 0L);
        Bitmap bitmapFromFile = getBitmapFromFile(context, str);
        if (bitmapFromFile != null) {
            getIconResult.bitmap = bitmapFromFile;
            return getIconResult;
        }
        ByteArrayInputStream byteArrayInputStream = null;
        try {
            GetDataResult dataFromUrl = getDataFromUrl(str, z);
            if (dataFromUrl == null) {
                return getIconResult;
            }
            getIconResult.downloadSize = dataFromUrl.downloadSize;
            byte[] bArr = dataFromUrl.data;
            if (bArr == null) {
                return getIconResult;
            }
            if (z) {
                byteArrayInputStream = new ByteArrayInputStream(bArr);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = getSampleSize(context, byteArrayInputStream);
                getIconResult.bitmap = BitmapFactory.decodeByteArray(bArr, 0, bArr.length, options);
                savePic2File(context, bArr, str);
            } else {
                getIconResult.bitmap = BitmapFactory.decodeByteArray(bArr, 0, bArr.length);
            }
        } catch (Exception e) {
            MyLog.e(e);
        } finally {
            IOUtils.closeQuietly(byteArrayInputStream);
        }
        return getIconResult;
    }

    private static int getSampleSize(Context context, InputStream inputStream) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(inputStream, null, options);
        if (options.outWidth == -1 || options.outHeight == -1) {
            MyLog.w("decode dimension failed for bitmap.");
            return 1;
        }
        int iRound = Math.round((context.getResources().getDisplayMetrics().densityDpi / 160.0f) * 48.0f);
        if (options.outWidth <= iRound || options.outHeight <= iRound) {
            return 1;
        }
        return Math.min(options.outWidth / iRound, options.outHeight / iRound);
    }

    private static void savePic2File(Context context, byte[] bArr, String str) {
        if (bArr == null) {
            MyLog.w("cannot save small icon cause bitmap is null");
            return;
        }
        cleanCachedPic(context);
        File file = new File(context.getCacheDir().getPath() + File.separator + PIC_FILE_DIR_PATH);
        if (!file.exists()) {
            file.mkdirs();
        }
        File file2 = new File(file, XMStringUtils.getMd5Digest(str));
        BufferedOutputStream bufferedOutputStream = null;
        FileOutputStream fileOutputStream = null;
        BufferedOutputStream bufferedOutputStream2 = null;
        FileOutputStream fileOutputStream2 = null;
        try {
            try {
                if (!file2.exists()) {
                    file2.createNewFile();
                }
                FileOutputStream fileOutputStream3 = new FileOutputStream(file2);
                BufferedOutputStream bufferedOutputStream3 = new BufferedOutputStream(fileOutputStream3);
                bufferedOutputStream3.write(bArr);
                bufferedOutputStream = bufferedOutputStream3;
                fileOutputStream = fileOutputStream3;
                bufferedOutputStream2 = bufferedOutputStream3;
                fileOutputStream2 = fileOutputStream3;
                bufferedOutputStream3.flush();
                bufferedOutputStream2 = bufferedOutputStream3;
                fileOutputStream2 = fileOutputStream3;
            } catch (Exception e) {
                bufferedOutputStream = bufferedOutputStream2;
                fileOutputStream = fileOutputStream2;
                MyLog.e(e);
            }
            IOUtils.closeQuietly(bufferedOutputStream2);
            IOUtils.closeQuietly(fileOutputStream2);
            if (currentPicFileSize == 0) {
                currentPicFileSize = FileUtils.getFolderSize(new File(context.getCacheDir().getPath() + File.separator + PIC_FILE_DIR_PATH)) + file2.length();
            }
        } catch (Throwable th) {
            IOUtils.closeQuietly(bufferedOutputStream);
            IOUtils.closeQuietly(fileOutputStream);
            throw th;
        }
    }
}
