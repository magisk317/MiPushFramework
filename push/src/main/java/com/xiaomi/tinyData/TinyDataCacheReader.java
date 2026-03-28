package com.xiaomi.tinyData;

import android.content.Context;
import android.content.SharedPreferences;
import com.xiaomi.channel.commonutils.android.DataCryptUtils;
import com.xiaomi.channel.commonutils.file.IOUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.misc.ByteUtils;
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager;
import com.xiaomi.push.service.TinyDataStorage;
import com.xiaomi.xmpush.thrift.ClientUploadDataItem;
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.List;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/tinyData/TinyDataCacheReader.class */
public class TinyDataCacheReader {
    private static final int TINY_DATA_MAX_UPLOAD_ITEM_COUNT = 8;
    private static final String TINY_DATA_READ_TEMP_FILE_DIR = "/tdReadTemp";
    private static boolean mTinyDataJobIsRunning = false;

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/tinyData/TinyDataCacheReader$TinyDataReadJob.class */
    static class TinyDataReadJob implements Runnable {
        private Context mContext;
        private TinyDataUploader mUploader;

        public TinyDataReadJob(Context context, TinyDataUploader tinyDataUploader) {
            this.mUploader = tinyDataUploader;
            this.mContext = context;
        }

        @Override // java.lang.Runnable
        public void run() {
            try {
                TinyDataCacheReader.extractTinyData(this.mContext, this.mUploader);
            } catch (Throwable th) {
                MyLog.e(th);
            }
        }
    }

    public static void addTinyDataCacheReadJob(Context context, TinyDataUploader tinyDataUploader) {
        ScheduledJobManager.getInstance(context).addOneShootJob(new TinyDataReadJob(context, tinyDataUploader));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void extractTinyData(Context context, TinyDataUploader tinyDataUploader) throws Throwable {
        if (mTinyDataJobIsRunning) {
            MyLog.w("TinyData extractTinyData is running");
            return;
        }
        mTinyDataJobIsRunning = true;
        File file = new File(context.getFilesDir(), TinyDataStorage.TINY_DATA_CACHE_FILE_NAME);
        if (!file.exists()) {
            MyLog.w("TinyData no ready file to get data.");
            return;
        }
        verifyFileDir(context);
        byte[] tinyDataKeyWithDefault = TinyDataStorage.getTinyDataKeyWithDefault(context);
        RandomAccessFile randomAccessFile = null;
        FileLock fileLock = null;
        try {
            File file2 = new File(context.getFilesDir(), TinyDataStorage.TINY_DATA_CACHE_FILE_LOCK);
            IOUtils.createFileQuietly(file2);
            randomAccessFile = new RandomAccessFile(file2, "rw");
            fileLock = randomAccessFile.getChannel().lock();
            File file3 = new File(context.getFilesDir() + TINY_DATA_READ_TEMP_FILE_DIR + "/" + TinyDataStorage.TINY_DATA_CACHE_FILE_NAME);
            file.renameTo(file3);
        } catch (Exception e) {
            MyLog.e(e);
        } finally {
            if (fileLock != null && fileLock.isValid()) {
                try {
                    fileLock.release();
                } catch (IOException e2) {
                    MyLog.e(e2);
                }
            }
            IOUtils.closeQuietly(randomAccessFile);
        }
        try {
            File file3 = new File(context.getFilesDir() + TINY_DATA_READ_TEMP_FILE_DIR + "/" + TinyDataStorage.TINY_DATA_CACHE_FILE_NAME);
            if (!file3.exists()) {
                MyLog.w("TinyData no ready file to get data.");
                return;
            }
            readTinyDataFromFile(context, tinyDataUploader, file3, tinyDataKeyWithDefault);
            updateTinyDataUploadTimeStamp(context);
        } catch (Throwable th) {
            throw th;
        } finally {
            TinyDataCacheProcessor.setIsTinyDataExtracting(false);
            mTinyDataJobIsRunning = false;
        }
    }

    private static void readTinyDataFromFile(Context context, TinyDataUploader tinyDataUploader, File file, byte[] bArr) throws Throwable {
        BufferedInputStream bufferedInputStream = null;
        List<ClientUploadDataItem> arrayList = new ArrayList<>(TINY_DATA_MAX_UPLOAD_ITEM_COUNT);
        try {
            bufferedInputStream = new BufferedInputStream(new FileInputStream(file));
            byte[] bArr2 = new byte[4];
            while (true) {
                int read = bufferedInputStream.read();
                if (read == -1) {
                    break;
                }
                bArr2[0] = (byte) read;
                if (bufferedInputStream.read(bArr2, 1, 3) != 3) {
                    MyLog.e("TinyData read from cache file failed cause lengthBuffer error.");
                    break;
                }
                int i = ByteUtils.toInt(bArr2);
                if (i < 1 || i > 10240) {
                    MyLog.e("TinyData read from cache file failed cause lengthBuffer < 1 || too big. length:" + i);
                    break;
                }
                byte[] bArr3 = new byte[i];
                int i2 = 0;
                while (i2 < i) {
                    int read2 = bufferedInputStream.read(bArr3, i2, i - i2);
                    if (read2 == -1) {
                        break;
                    }
                    i2 += read2;
                }
                if (i2 != i) {
                    MyLog.e("TinyData read from cache file failed cause buffer size not equal length. size:" + i2 + "__length:" + i);
                    break;
                }
                try {
                    byte[] mipushDecrypt = DataCryptUtils.mipushDecrypt(bArr, bArr3);
                    ClientUploadDataItem clientUploadDataItem = new ClientUploadDataItem();
                    XmPushThriftSerializeUtils.convertByteArrayToThriftObject(clientUploadDataItem, mipushDecrypt);
                    arrayList.add(clientUploadDataItem);
                    if (arrayList.size() >= TINY_DATA_MAX_UPLOAD_ITEM_COUNT) {
                        TinyDataCacheUploader.uploadTinyData(context, tinyDataUploader, arrayList);
                        arrayList = new ArrayList<>(TINY_DATA_MAX_UPLOAD_ITEM_COUNT);
                    }
                } catch (Exception e) {
                    MyLog.e(e);
                }
            }
            if (!arrayList.isEmpty()) {
                TinyDataCacheUploader.uploadTinyData(context, tinyDataUploader, arrayList);
            }
        } finally {
            IOUtils.closeQuietly(bufferedInputStream);
            if (file.exists() && !file.delete()) {
                MyLog.w("TinyData delete reading temp file failed");
            }
        }
    }

    private static void updateTinyDataUploadTimeStamp(Context context) {
        SharedPreferences.Editor editorEdit = context.getSharedPreferences("mipush_extra", 4).edit();
        editorEdit.putLong(TinyDataCacheProcessor.LAST_TINY_DATA_UPLOAD_TIMESTAMP, System.currentTimeMillis() / 1000);
        editorEdit.commit();
    }

    private static void verifyFileDir(Context context) {
        File file = new File(context.getFilesDir() + TINY_DATA_READ_TEMP_FILE_DIR);
        if (file.exists()) {
            return;
        }
        file.mkdirs();
    }
}
