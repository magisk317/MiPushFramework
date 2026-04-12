package com.xiaomi.channel.commonutils.android;

import android.content.Context;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.file.IOUtils;
import com.xiaomi.channel.commonutils.string.XMStringUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/android/AssetUtils.class */
public class AssetUtils {
    public static boolean extractAssetFile(Context context, String str, String str2) {
        InputStream inputStream = null;
        InputStream inputStream2 = null;
        FileInputStream fileInputStream = null;
        FileOutputStream fileOutputStream = null;
        FileInputStream fileInputStream2 = null;
        FileOutputStream fileOutputStream2 = null;
        try {
            try {
                InputStream inputStreamOpen = context.getAssets().open(str);
                byte[] inputStream3 = IOUtils.readInputStream(inputStreamOpen);
                File file = new File(str2);
                FileInputStream fileInputStream3 = null;
                if (file.exists()) {
                    FileInputStream fileInputStream4 = new FileInputStream(file);
                    String md5 = XMStringUtils.getMd5(IOUtils.readInputStream(fileInputStream4));
                    String md52 = XMStringUtils.getMd5(inputStream3);
                    fileInputStream3 = fileInputStream4;
                    if (!TextUtils.isEmpty(md5)) {
                        fileInputStream3 = fileInputStream4;
                        if (md5.equals(md52)) {
                            IOUtils.closeQuietly(inputStreamOpen);
                            IOUtils.closeQuietly(fileInputStream4);
                            IOUtils.closeQuietly(null);
                            return false;
                        }
                    }
                }
                FileOutputStream fileOutputStream3 = new FileOutputStream(file);
                fileOutputStream3.write(inputStream3);
                inputStream2 = inputStreamOpen;
                fileInputStream = fileInputStream3;
                fileOutputStream = fileOutputStream3;
                inputStream = inputStreamOpen;
                fileInputStream2 = fileInputStream3;
                fileOutputStream2 = fileOutputStream3;
                fileOutputStream3.flush();
                IOUtils.closeQuietly(inputStreamOpen);
                IOUtils.closeQuietly(fileInputStream3);
                IOUtils.closeQuietly(fileOutputStream3);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                IOUtils.closeQuietly(inputStream);
                IOUtils.closeQuietly(fileInputStream2);
                IOUtils.closeQuietly(fileOutputStream2);
                return false;
            }
        } catch (Throwable th) {
            IOUtils.closeQuietly(inputStream2);
            IOUtils.closeQuietly(fileInputStream);
            IOUtils.closeQuietly(fileOutputStream);
            throw th;
        }
    }
}
