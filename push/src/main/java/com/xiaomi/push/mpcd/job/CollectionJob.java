package com.xiaomi.push.mpcd.job;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.file.IOUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.misc.ByteUtils;
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager;
import com.xiaomi.channel.commonutils.string.XMStringUtils;
import com.xiaomi.push.mpcd.CDActionProvider;
import com.xiaomi.push.mpcd.CDActionProviderHolder;
import com.xiaomi.push.mpcd.CDataHelper;
import com.xiaomi.push.mpcd.Constants;
import com.xiaomi.push.service.OnlineConfig;
import com.xiaomi.xmpush.thrift.ClientCollectionType;
import com.xiaomi.xmpush.thrift.ConfigKey;
import com.xiaomi.xmpush.thrift.DataCollectionItem;
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/mpcd/job/CollectionJob.class */
public abstract class CollectionJob extends ScheduledJobManager.Job {
    public static final String KEY_JOB_RESULT_PREFIX = "dc_job_result_";
    public static final String KEY_JOB_RESULT_TIME_PREFIX = "dc_job_result_time_";
    public static final String RESULT_SAME_PREFIX = "same_";
    protected Context context;
    protected int period;

    public CollectionJob(Context context, int i) {
        this.period = i;
        this.context = context;
    }

    private String getJobResultKey() {
        return KEY_JOB_RESULT_PREFIX + getJobId();
    }

    private String getJobResultTimeKey() {
        return KEY_JOB_RESULT_TIME_PREFIX + getJobId();
    }

    public static void writeItemToFile(Context context, DataCollectionItem dataCollectionItem) {
        CDActionProvider cDActionProvider = CDActionProviderHolder.getInstance().getCDActionProvider();
        String regSecret = cDActionProvider == null ? "" : cDActionProvider.getRegSecret();
        if (TextUtils.isEmpty(regSecret) || TextUtils.isEmpty(dataCollectionItem.getContent())) {
            return;
        }
        writeItemToFile(context, dataCollectionItem, regSecret);
    }

    private static void writeItemToFile(Context context, DataCollectionItem dataCollectionItem, String str) {
        byte[] bArrEncryptData = CDataHelper.encryptData(str, XmPushThriftSerializeUtils.convertThriftObjectToBytes(dataCollectionItem));
        if (bArrEncryptData == null || bArrEncryptData.length == 0) {
            return;
        }
        synchronized (Constants.cDataLock4Thread) {
            RandomAccessFile randomAccessFile = null;
            FileLock fileLock = null;
            BufferedOutputStream bufferedOutputStream = null;
            RandomAccessFile randomAccessFile2 = null;
            FileLock fileLock2 = null;
            BufferedOutputStream bufferedOutputStream2 = null;
            try {
                try {
                    File file = new File(context.getExternalFilesDir(null), Constants.COLLECTED_DATA_LOCK);
                    IOUtils.createFileQuietly(file);
                    RandomAccessFile randomAccessFile3 = new RandomAccessFile(file, "rw");
                    FileLock fileLockLock = randomAccessFile3.getChannel().lock();
                    BufferedOutputStream bufferedOutputStream3 = new BufferedOutputStream(new FileOutputStream(new File(context.getExternalFilesDir(null), Constants.COLLECTED_DATA_FILENAME), true));
                    bufferedOutputStream3.write(ByteUtils.parseInt(bArrEncryptData.length));
                    bufferedOutputStream3.write(bArrEncryptData);
                    randomAccessFile2 = randomAccessFile3;
                    fileLock2 = fileLockLock;
                    bufferedOutputStream2 = bufferedOutputStream3;
                    bufferedOutputStream3.flush();
                    if (fileLockLock != null && fileLockLock.isValid()) {
                        try {
                            fileLockLock.release();
                        } catch (IOException e) {
                        }
                    }
                    IOUtils.closeQuietly(bufferedOutputStream3);
                    randomAccessFile2 = randomAccessFile3;
                } catch (IOException e2) {
                    randomAccessFile = randomAccessFile2;
                    fileLock = fileLock2;
                    bufferedOutputStream = bufferedOutputStream2;
                    e2.printStackTrace();
                    if (fileLock2 != null && fileLock2.isValid()) {
                        try {
                            fileLock2.release();
                        } catch (IOException e3) {
                        }
                    }
                    IOUtils.closeQuietly(bufferedOutputStream2);
                }
                IOUtils.closeQuietly(randomAccessFile2);
            } catch (Throwable th) {
                if (fileLock != null && fileLock.isValid()) {
                    try {
                        fileLock.release();
                    } catch (IOException e4) {
                    }
                }
                IOUtils.closeQuietly(bufferedOutputStream);
                IOUtils.closeQuietly(randomAccessFile);
                throw th;
            }
        }
    }

    protected boolean checkDataCollectionJobMutual() {
        return CDataHelper.checkDataCollectionJobMutual(this.context, String.valueOf(getJobId()), this.period);
    }

    protected boolean checkPermission() {
        return true;
    }

    protected boolean checkRepeatedData() {
        return false;
    }

    public abstract String collectInfo();

    public abstract ClientCollectionType getCollectionType();

    @Override // java.lang.Runnable
    public void run() {
        String strCollectInfo = collectInfo();
        if (TextUtils.isEmpty(strCollectInfo)) {
            return;
        }
        if (checkDataCollectionJobMutual()) {
            MyLog.w("DC run job mutual: " + getJobId());
            return;
        }
        CDActionProvider cDActionProvider = CDActionProviderHolder.getInstance().getCDActionProvider();
        String regSecret = cDActionProvider == null ? "" : cDActionProvider.getRegSecret();
        if (!TextUtils.isEmpty(regSecret) && checkPermission()) {
            String str = strCollectInfo;
            if (checkRepeatedData()) {
                SharedPreferences sharedPreferences = this.context.getSharedPreferences("mipush_extra", 0);
                str = strCollectInfo;
                if (XMStringUtils.getMd5Digest(strCollectInfo).equals(sharedPreferences.getString(getJobResultKey(), null))) {
                    long j = sharedPreferences.getLong(getJobResultTimeKey(), 0L);
                    int intValue = OnlineConfig.getInstance(this.context).getIntValue(ConfigKey.DCJobUploadRepeatedInterval.getValue(), 604800);
                    if ((System.currentTimeMillis() - j) / 1000 < this.period) {
                        return;
                    }
                    str = strCollectInfo;
                    if ((System.currentTimeMillis() - j) / 1000 < intValue) {
                        str = RESULT_SAME_PREFIX + j;
                    }
                }
            }
            DataCollectionItem dataCollectionItem = new DataCollectionItem();
            dataCollectionItem.setContent(str);
            dataCollectionItem.setCollectedAt(System.currentTimeMillis());
            dataCollectionItem.setCollectionType(getCollectionType());
            writeItemToFile(this.context, dataCollectionItem, regSecret);
        }
    }
}
