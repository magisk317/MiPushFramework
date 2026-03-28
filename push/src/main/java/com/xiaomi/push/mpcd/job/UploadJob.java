package com.xiaomi.push.mpcd.job;

import android.content.Context;
import android.content.SharedPreferences;
import com.xiaomi.channel.commonutils.file.IOUtils;
import com.xiaomi.channel.commonutils.misc.CollectionUtils;
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager;
import com.xiaomi.channel.commonutils.network.Network;
import com.xiaomi.channel.commonutils.string.XMStringUtils;
import com.xiaomi.push.mpcd.CDActionProvider;
import com.xiaomi.push.mpcd.CDActionProviderHolder;
import com.xiaomi.push.mpcd.Constants;
import com.xiaomi.push.service.DefaultConfig;
import com.xiaomi.push.service.OnlineConfig;
import com.xiaomi.xmpush.thrift.ActionType;
import com.xiaomi.xmpush.thrift.ClientCollectionType;
import com.xiaomi.xmpush.thrift.ConfigKey;
import com.xiaomi.xmpush.thrift.DataCollectionItem;
import com.xiaomi.xmpush.thrift.NotificationType;
import com.xiaomi.xmpush.thrift.XmPushActionCollectData;
import com.xiaomi.xmpush.thrift.XmPushActionNotification;
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils;
import java.io.File;
import java.util.List;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/mpcd/job/UploadJob.class */
public class UploadJob extends ScheduledJobManager.Job {
    private static final int DEFAULT_LAST_UPDATE_DATA_TIMESTAMP = -1;
    public static final String LAST_UPLADTE_DATA_TIMESTAMP = "last_upload_data_timestamp";
    private Context context;
    private OnlineConfig mOnlineConfig;
    private SharedPreferences mSharedPreference;

    public UploadJob(Context context) {
        this.context = context;
        this.mSharedPreference = context.getSharedPreferences("mipush_extra", 0);
        this.mOnlineConfig = OnlineConfig.getInstance(context);
    }

    private boolean ignoreUploadDataAtCurrentNetwork() {
        if (Network.isWIFIConnected(this.context)) {
            return false;
        }
        if ((Network.is4GConnected(this.context) || Network.is5GConnected(this.context)) && !verify4GorFasterUploadData()) {
            return true;
        }
        return (Network.is3GConnected(this.context) && !verify3GUploadData()) || Network.is2GConnected(this.context);
    }

    private List<DataCollectionItem> readFromFile(File file) {
        return new java.util.ArrayList<>();
    }

    private void recordLastUploadFullData(DataCollectionItem dataCollectionItem) {
        if (dataCollectionItem.collectionType != ClientCollectionType.AppInstallList || dataCollectionItem.content.startsWith(CollectionJob.RESULT_SAME_PREFIX)) {
            return;
        }
        SharedPreferences.Editor editorEdit = this.mSharedPreference.edit();
        editorEdit.putLong("dc_job_result_time_4", dataCollectionItem.collectedAt);
        editorEdit.putString("dc_job_result_4", XMStringUtils.getMd5Digest(dataCollectionItem.content));
        editorEdit.commit();
    }

    private void updateUpdateTimeStamp() {
        SharedPreferences.Editor editorEdit = this.mSharedPreference.edit();
        editorEdit.putLong(LAST_UPLADTE_DATA_TIMESTAMP, System.currentTimeMillis() / 1000);
        editorEdit.commit();
    }

    private boolean verify3GUploadData() {
        boolean z = true;
        if (!this.mOnlineConfig.getBooleanValue(ConfigKey.Upload3GSwitch.getValue(), true)) {
            return false;
        }
        if (Math.abs((System.currentTimeMillis() / 1000) - this.mSharedPreference.getLong(LAST_UPLADTE_DATA_TIMESTAMP, -1L)) <= Math.max(86400, this.mOnlineConfig.getIntValue(ConfigKey.Upload3GFrequency.getValue(), DefaultConfig.DEFAULT_3G_UPLOAD_PERIOD))) {
            z = false;
        }
        return z;
    }

    private boolean verify4GorFasterUploadData() {
        boolean z = true;
        if (!this.mOnlineConfig.getBooleanValue(ConfigKey.Upload4GSwitch.getValue(), true)) {
            return false;
        }
        if (Math.abs((System.currentTimeMillis() / 1000) - this.mSharedPreference.getLong(LAST_UPLADTE_DATA_TIMESTAMP, -1L)) <= Math.max(86400, this.mOnlineConfig.getIntValue(ConfigKey.Upload4GFrequency.getValue(), DefaultConfig.DEFAULT_4G_UPLOAD_PERIOD))) {
            z = false;
        }
        return z;
    }

    @Override // com.xiaomi.channel.commonutils.misc.ScheduledJobManager.Job
    public String getJobId() {
        return "1";
    }

    @Override // java.lang.Runnable
    public void run() {
        File file = new File(this.context.getExternalFilesDir(null), Constants.COLLECTED_DATA_FILENAME);
        if (!Network.isConnected(this.context)) {
            if (file.length() > 1863680) {
                file.delete();
                return;
            }
            return;
        }
        if (!ignoreUploadDataAtCurrentNetwork() && file.exists()) {
            List<DataCollectionItem> fromFile = readFromFile(file);
            if (!CollectionUtils.isEmpty(fromFile)) {
                int size = fromFile.size();
                List<DataCollectionItem> listSubList = fromFile;
                if (size > 4000) {
                    listSubList = fromFile.subList(size - Constants.MAX_CDATA_ITEM_TO_UPLOAD, size);
                }
                XmPushActionCollectData xmPushActionCollectData = new XmPushActionCollectData();
                xmPushActionCollectData.setDataCollectionItems(listSubList);
                byte[] bArrGZip = IOUtils.gZip(XmPushThriftSerializeUtils.convertThriftObjectToBytes(xmPushActionCollectData));
                XmPushActionNotification xmPushActionNotification = new XmPushActionNotification("-1", false);
                xmPushActionNotification.setType(NotificationType.DataCollection.value);
                xmPushActionNotification.setBinaryExtra(bArrGZip);
                CDActionProvider cDActionProvider = CDActionProviderHolder.getInstance().getCDActionProvider();
                if (cDActionProvider != null) {
                    cDActionProvider.uploadNotification(xmPushActionNotification, ActionType.Notification, null);
                }
                updateUpdateTimeStamp();
            }
            file.delete();
        }
    }
}
