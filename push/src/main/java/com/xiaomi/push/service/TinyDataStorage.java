package com.xiaomi.push.service;

import android.content.Context;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.android.DataCryptUtils;
import com.xiaomi.channel.commonutils.android.SharedPreferenceManager;
import com.xiaomi.channel.commonutils.file.IOUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.misc.ByteUtils;
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager;
import com.xiaomi.channel.commonutils.string.Base64Coder;
import com.xiaomi.channel.commonutils.string.XMStringUtils;
import com.xiaomi.xmpush.thrift.ClientUploadDataItem;
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/TinyDataStorage.class */
public class TinyDataStorage {
    public static final int TINY_DATA_BYTE_MAX_SIZE = 10240;
    private static final String TINY_DATA_CACHE_DEBUG_FILE_NAME = "tiny_data_debug.txt";
    public static final String TINY_DATA_CACHE_FILE_LOCK = "tiny_data.lock";
    public static final String TINY_DATA_CACHE_FILE_NAME = "tiny_data.data";
    public static final Object mTinyDataLock4Thread = new Object();

    public static void cacheTinyData(final Context context, final ClientUploadDataItem clientUploadDataItem) {
        if (TinyDataHelper.shouldUpload(clientUploadDataItem.getPkgName())) {
            ScheduledJobManager.getInstance(context).addOneShootJob(new Runnable() { // from class: com.xiaomi.push.service.TinyDataStorage.1
                @Override // java.lang.Runnable
                public void run() {
                    synchronized (TinyDataStorage.mTinyDataLock4Thread) {
                        java.io.RandomAccessFile randomAccessFile = null;
                        java.nio.channels.FileLock fileLock = null;
                        try {
                            File file = new File(context.getFilesDir(), TinyDataStorage.TINY_DATA_CACHE_FILE_LOCK);
                            IOUtils.createFileQuietly(file);
                            randomAccessFile = new java.io.RandomAccessFile(file, "rw");
                            fileLock = randomAccessFile.getChannel().lock();
                            TinyDataStorage.writeTinyData2File(context, clientUploadDataItem);
                        } catch (Exception e) {
                            MyLog.e(e);
                        } finally {
                            if (fileLock != null) {
                                try {
                                    if (fileLock.isValid()) {
                                        fileLock.release();
                                    }
                                } catch (IOException e2) {
                                    MyLog.e(e2);
                                }
                            }
                            IOUtils.closeQuietly(randomAccessFile);
                        }
                    }
                }
            });
        }
    }

    public static byte[] getTinyDataKeyWithDefault(Context context) {
        String stringValue = SharedPreferenceManager.getInstance(context).getStringValue(PushConstants.SP_NAME_MIPUSH, PushConstants.SP_KEY_TINY_DATA_KEY, "");
        String strGenerateRandomString = stringValue;
        if (TextUtils.isEmpty(stringValue)) {
            strGenerateRandomString = XMStringUtils.generateRandomString(20);
            SharedPreferenceManager.getInstance(context).setStringnValue(PushConstants.SP_NAME_MIPUSH, PushConstants.SP_KEY_TINY_DATA_KEY, strGenerateRandomString);
        }
        return parseKey(strGenerateRandomString);
    }

    private static byte[] parseKey(String str) {
        byte[] bArrCopyOf = Arrays.copyOf(Base64Coder.decode(str), 16);
        bArrCopyOf[0] = (byte) 68;
        bArrCopyOf[15] = (byte) 84;
        return bArrCopyOf;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void writeTinyData2File(Context context, ClientUploadDataItem clientUploadDataItem) {
        BufferedOutputStream bufferedOutputStream = null;
        try {
            byte[] mipushEncrypt = DataCryptUtils.mipushEncrypt(getTinyDataKeyWithDefault(context), XmPushThriftSerializeUtils.convertThriftObjectToBytes(clientUploadDataItem));
            if (mipushEncrypt == null || mipushEncrypt.length < 1) {
                MyLog.w("TinyData write to cache file failed case encryption fail item:" + clientUploadDataItem.getId() + "   ts:" + System.currentTimeMillis());
                return;
            }
            if (mipushEncrypt.length > 10240) {
                MyLog.w("TinyData write to cache file failed case too much data content item:" + clientUploadDataItem.getId() + "   ts:" + System.currentTimeMillis());
                return;
            }
            File file = new File(context.getFilesDir(), TINY_DATA_CACHE_FILE_NAME);
            bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(file, true));
            bufferedOutputStream.write(ByteUtils.parseInt(mipushEncrypt.length));
            bufferedOutputStream.write(mipushEncrypt);
            bufferedOutputStream.flush();
        } catch (IOException e) {
            MyLog.e("TinyData write to cache file failed cause io exception item:" + clientUploadDataItem.getId(), e);
        } catch (Exception e2) {
            MyLog.e("TinyData write to cache file  failed item:" + clientUploadDataItem.getId(), e2);
        } finally {
            IOUtils.closeQuietly(bufferedOutputStream);
        }
    }
}
