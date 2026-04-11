package com.xiaomi.channel.commonutils.file;

import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/file/IOUtils.class */
public class IOUtils {
    private static final int BUFFER_SIZE = 1024;
    private static final int STREAM_BUFFER_SIZE = 4096;
    public static final String[] SUPPORTED_IMAGE_FORMATS = {"jpg", "png", "bmp", "gif", "webp"};

    public static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
            }
        }
    }

    public static void copyFile(File file, File file2) throws IOException {
        if (file.getAbsolutePath().equals(file2.getAbsolutePath())) {
            return;
        }
        FileInputStream fileInputStream = null;
        FileOutputStream fileOutputStream = null;
        try {
            FileInputStream fileInputStream2 = new FileInputStream(file);
            FileOutputStream fileOutputStream2 = new FileOutputStream(file2);
            byte[] bArr = new byte[BUFFER_SIZE];
            while (true) {
                fileInputStream = fileInputStream2;
                fileOutputStream = fileOutputStream2;
                int i = fileInputStream2.read(bArr);
                if (i < 0) {
                    fileInputStream2.close();
                    fileOutputStream2.close();
                    return;
                }
                fileOutputStream2.write(bArr, 0, i);
            }
        } catch (Throwable th) {
            if (fileInputStream != null) {
                fileInputStream.close();
            }
            if (fileOutputStream != null) {
                fileOutputStream.close();
            }
            throw th;
        }
    }

    public static boolean createFileQuietly(File file) {
        try {
            if (file.isDirectory()) {
                return false;
            }
            if (file.exists()) {
                return true;
            }
            File parentFile = file.getParentFile();
            if (parentFile.exists() || parentFile.mkdirs()) {
                return file.createNewFile();
            }
            return false;
        } catch (Throwable th) {
            th.printStackTrace();
            return false;
        }
    }

    public static void deleteDirs(File file) {
        MyLog.v("deleteDirs filePath = " + file.getAbsolutePath());
        if (file.isDirectory()) {
            File[] fileArrListFiles = file.listFiles();
            if (fileArrListFiles != null && fileArrListFiles.length > 0) {
                for (File file2 : fileArrListFiles) {
                    if (file2.isFile()) {
                        file2.delete();
                    } else {
                        deleteDirs(file2);
                    }
                }
            }
            file.delete();
        }
    }

    public static String fileToStr(File file) {
        StringWriter stringWriter = new StringWriter();
        InputStreamReader inputStreamReader = null;
        InputStreamReader inputStreamReader2 = null;
        try {
            try {
                InputStreamReader inputStreamReader3 = new InputStreamReader(new BufferedInputStream(new FileInputStream(file)));
                char[] cArr = new char[2048];
                while (true) {
                    int i = inputStreamReader3.read(cArr);
                    if (i == -1) {
                        inputStreamReader = inputStreamReader3;
                        inputStreamReader2 = inputStreamReader3;
                        String string = stringWriter.toString();
                        closeQuietly(inputStreamReader3);
                        closeQuietly(stringWriter);
                        return string;
                    }
                    stringWriter.write(cArr, 0, i);
                }
            } catch (IOException e) {
                StringBuilder sb = new StringBuilder();
                sb.append("read file :");
                sb.append(file.getAbsolutePath());
                sb.append(" failure :");
                sb.append(e.getMessage());
                MyLog.v(sb.toString());
                closeQuietly(inputStreamReader2);
                closeQuietly(stringWriter);
                return null;
            }
        } catch (Throwable th) {
            closeQuietly(inputStreamReader);
            closeQuietly(stringWriter);
            throw th;
        }
    }

    public static byte[] gZip(byte[] bArr) {
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            GZIPOutputStream gZIPOutputStream = new GZIPOutputStream(byteArrayOutputStream);
            gZIPOutputStream.write(bArr);
            gZIPOutputStream.finish();
            gZIPOutputStream.close();
            byte[] byteArray = byteArrayOutputStream.toByteArray();
            byteArrayOutputStream.close();
            bArr = byteArray;
        } catch (Exception e) {
        }
        return bArr;
    }

    public static byte[] getFileMD5Digest(String str) throws NoSuchAlgorithmException, IOException {
        MessageDigest messageDigest = MessageDigest.getInstance("MD5");
        FileInputStream fileInputStream = new FileInputStream(new File(str));
        byte[] bArr = new byte[STREAM_BUFFER_SIZE];
        while (true) {
            int i = fileInputStream.read(bArr);
            if (i != -1) {
                messageDigest.update(bArr, 0, i);
            } else {
                break;
            }
        }
        fileInputStream.close();
        return messageDigest.digest();
    }

    public static byte[] getFileSha1Digest(String str) throws NoSuchAlgorithmException, IOException {
        MessageDigest messageDigest = MessageDigest.getInstance("SHA1");
        FileInputStream fileInputStream = new FileInputStream(new File(str));
        byte[] bArr = new byte[STREAM_BUFFER_SIZE];
        while (true) {
            int i = fileInputStream.read(bArr);
            if (i != -1) {
                messageDigest.update(bArr, 0, i);
            } else {
                break;
            }
        }
        fileInputStream.close();
        return messageDigest.digest();
    }

    public static String getFileSuffix(String str) {
        int iLastIndexOf = str.lastIndexOf(46);
        return iLastIndexOf > 0 ? str.substring(iLastIndexOf + 1) : "";
    }

    public static void hideFromMediaScanner(File file) {
        File file2 = new File(file, ".nomedia");
        if (file2.exists() && file2.isFile()) {
            return;
        }
        try {
            file2.createNewFile();
        } catch (IOException e) {
            MyLog.e(e);
        }
    }

    public static boolean isSupportImageSuffix(String str) {
        if (TextUtils.isEmpty(str)) {
            return false;
        }
        for (String str2 : SUPPORTED_IMAGE_FORMATS) {
            if (str2.equalsIgnoreCase(str)) {
                return true;
            }
        }
        return false;
    }

    public static byte[] readInputStream(InputStream inputStream) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] bArr = new byte[8192];
        while (true) {
            try {
                try {
                    int i = inputStream.read(bArr, 0, 8192);
                    if (i <= 0) {
                        return byteArrayOutputStream.toByteArray();
                    }
                    byteArrayOutputStream.write(bArr, 0, i);
                } catch (Exception e) {
                    e.printStackTrace();
                    closeQuietly(inputStream);
                    closeQuietly(byteArrayOutputStream);
                    return null;
                }
            } finally {
                closeQuietly(inputStream);
                closeQuietly(byteArrayOutputStream);
            }
        }
    }

    public static void remove(File file) {
        if (!file.isDirectory()) {
            if (file.exists()) {
                file.delete();
            }
        } else {
            for (File file2 : file.listFiles()) {
                remove(file2);
            }
            file.delete();
        }
    }

    public static void strToFile(File file, String str) {
        if (!file.exists()) {
            MyLog.v("mkdir " + file.getAbsolutePath());
            file.getParentFile().mkdirs();
        }
        BufferedWriter bufferedWriter = null;
        BufferedWriter bufferedWriter2 = null;
        try {
            try {
                BufferedWriter bufferedWriter3 = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)));
                bufferedWriter = bufferedWriter3;
                bufferedWriter2 = bufferedWriter3;
                bufferedWriter3.write(str);
                bufferedWriter2 = bufferedWriter3;
            } catch (IOException e) {
                StringBuilder sb = new StringBuilder();
                sb.append("write file :");
                sb.append(file.getAbsolutePath());
                sb.append(" failure :");
                sb.append(e.getMessage());
                bufferedWriter = bufferedWriter2;
                MyLog.v(sb.toString());
            }
            closeQuietly(bufferedWriter2);
        } catch (Throwable th) {
            closeQuietly(bufferedWriter);
            throw th;
        }
    }

    public static boolean unZip(String str, String str2) {
        if (TextUtils.isEmpty(str2) || TextUtils.isEmpty(str)) {
            return false;
        }
        if (!str2.endsWith("/")) {
            str2 = str2 + "/";
        }
        try {
            ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(new FileInputStream(str)));
            byte[] bArr = new byte[STREAM_BUFFER_SIZE];
            while (true) {
                ZipEntry nextEntry = zipInputStream.getNextEntry();
                if (nextEntry == null) {
                    zipInputStream.close();
                    return true;
                }
                String name = nextEntry.getName();
                File file = new File(str2 + name);
                if (!name.endsWith("/")) {
                    File file2 = new File(file.getParent());
                    if (!file2.exists() || !file2.isDirectory()) {
                        file2.mkdirs();
                        hideFromMediaScanner(file2);
                    }
                    BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(file), STREAM_BUFFER_SIZE);
                    while (true) {
                        int i = zipInputStream.read(bArr, 0, STREAM_BUFFER_SIZE);
                        if (i == -1) {
                            break;
                        }
                        bufferedOutputStream.write(bArr, 0, i);
                    }
                    bufferedOutputStream.flush();
                    bufferedOutputStream.close();
                }
            }
        } catch (IOException e) {
            MyLog.e("unzip failed", e);
            return false;
        }
    }

    public static void zip(ZipOutputStream zipOutputStream, File file, String str, FileFilter fileFilter) throws IOException {
        String str2 = str == null ? "" : str;
        FileInputStream fileInputStream = null;
        try {
            if (file.isDirectory()) {
                File[] listFiles = fileFilter != null ? file.listFiles(fileFilter) : file.listFiles();
                zipOutputStream.putNextEntry(new ZipEntry(str2 + File.separator));
                String str3 = TextUtils.isEmpty(str2) ? "" : str2 + File.separator;
                if (listFiles != null) {
                    for (File file2 : listFiles) {
                        zip(zipOutputStream, file2, str3 + file2.getName(), null);
                    }
                }
                File[] listFiles2 = file.listFiles(new FileFilter() { // from class: com.xiaomi.channel.commonutils.file.IOUtils.1
                    @Override // java.io.FileFilter
                    public boolean accept(File file3) {
                        return file3.isDirectory();
                    }
                });
                if (listFiles2 != null) {
                    for (File file3 : listFiles2) {
                        zip(zipOutputStream, file3, str3 + File.separator + file3.getName(), fileFilter);
                    }
                }
                return;
            }
            if (TextUtils.isEmpty(str2)) {
                zipOutputStream.putNextEntry(new ZipEntry(String.valueOf(new Date().getTime()) + ".txt"));
            } else {
                zipOutputStream.putNextEntry(new ZipEntry(str2));
            }
            fileInputStream = new FileInputStream(file);
            byte[] bArr = new byte[BUFFER_SIZE];
            while (true) {
                int read = fileInputStream.read(bArr);
                if (read == -1) {
                    return;
                }
                zipOutputStream.write(bArr, 0, read);
            }
        } catch (IOException e) {
            MyLog.e("zipFiction failed with exception:" + e.toString());
        } finally {
            closeQuietly(fileInputStream);
        }
    }

    public static void zip(ZipOutputStream zipOutputStream, String str, InputStream inputStream) {
        try {
            if (TextUtils.isEmpty(str)) {
                zipOutputStream.putNextEntry(new ZipEntry(String.valueOf(new Date().getTime()) + ".txt"));
            } else {
                zipOutputStream.putNextEntry(new ZipEntry(str));
            }
            byte[] bArr = new byte[BUFFER_SIZE];
            while (true) {
                int i = inputStream.read(bArr);
                if (i == -1) {
                    return;
                } else {
                    zipOutputStream.write(bArr, 0, i);
                }
            }
        } catch (IOException e) {
            MyLog.e("zipFiction failed with exception:" + e.toString());
        }
    }

    public static void zip(File file, File file2) throws IOException {
        ZipOutputStream zipOutputStream = null;
        try {
            zipOutputStream = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(file2)));
            zipOutputStream.setLevel(Deflater.BEST_SPEED);
            zip(zipOutputStream, file, file.getName(), null);
        } finally {
            closeQuietly(zipOutputStream);
        }
    }
}
