package com.xiaomi.channel.commonutils.file;

import com.xiaomi.channel.commonutils.logger.MyLog;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/file/FileUtils.class */
public class FileUtils {
    private static final HashMap<String, String> mFileTypes;

    static {
        HashMap<String, String> map = new HashMap<>();
        mFileTypes = map;
        map.put("FFD8FF", "jpg");
        map.put("89504E47", "png");
        map.put("47494638", "gif");
        map.put("474946", "gif");
        map.put("424D", "bmp");
    }

    private static String bytesToHexString(byte[] bArr) {
        StringBuilder sb = new StringBuilder();
        if (bArr == null || bArr.length <= 0) {
            return null;
        }
        for (byte b : bArr) {
            String upperCase = Integer.toHexString(b & 255).toUpperCase();
            if (upperCase.length() < 2) {
                sb.append(0);
            }
            sb.append(upperCase);
        }
        return sb.toString();
    }

    private static String getFileHeader(String str) {
        FileInputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream(str);
            byte[] bArr = new byte[3];
            if (fileInputStream.read(bArr, 0, bArr.length) <= 0) {
                return null;
            }
            return bytesToHexString(bArr);
        } catch (Exception e) {
            return null;
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException e2) {
                }
            }
        }
    }

    public static String getFileType(String str) {
        return mFileTypes.get(getFileHeader(str));
    }

    public static long getFolderSize(File file) {
        long length = 0;
        long j = 0;
        try {
            File[] fileArrListFiles = file.listFiles();
            int i = 0;
            while (true) {
                j = length;
                if (i >= fileArrListFiles.length) {
                    break;
                }
                long j2 = length;
                if (fileArrListFiles[i].isDirectory()) {
                    long j3 = length;
                    length += getFolderSize(fileArrListFiles[i]);
                } else {
                    length += fileArrListFiles[i].length();
                }
                i++;
            }
        } catch (Exception e) {
            MyLog.e(e);
            length = j;
        }
        return length;
    }

    public static boolean isGif(String str) {
        return "gif".equals(getFileType(str));
    }
}
