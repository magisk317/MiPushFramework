package com.xiaomi.tinyData;

import android.content.Context;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.android.DeviceInfo;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.network.Network;
import com.xiaomi.push.service.DefaultConfig;
import com.xiaomi.push.service.MIPushAccountUtils;
import com.xiaomi.push.service.OnlineConfig;
import com.xiaomi.push.service.PushConstants;
import com.xiaomi.push.service.PushServiceConstants;
import com.xiaomi.push.service.TinyDataStorage;
import com.xiaomi.push.service.PingCallBack;
import com.xiaomi.push.service.XMPushService;
import com.xiaomi.xmpush.thrift.ConfigKey;
import java.io.File;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/tinyData/TinyDataCacheProcessor.class */
public class TinyDataCacheProcessor implements PingCallBack {
    private static final int DEFAULT_LAST_TINY_DATA_UPLOAD_TIMESTAMP = -1;
    public static final String LAST_TINY_DATA_UPLOAD_TIMESTAMP = "last_tiny_data_upload_timestamp";
    private static boolean mIsTinyDataExtracting = false;
    private Context mContext;
    private int mPeriod;
    private boolean mUploadSwitch;

    public TinyDataCacheProcessor(Context context) {
        this.mContext = context;
    }

    private boolean canUpload(TinyDataUploader tinyDataUploader) {
        if (!Network.hasNetwork(this.mContext) || tinyDataUploader == null || TextUtils.isEmpty(getAppId(this.mContext.getPackageName())) || !new File(this.mContext.getFilesDir(), TinyDataStorage.TINY_DATA_CACHE_FILE_NAME).exists() || mIsTinyDataExtracting) {
            return false;
        }
        return !OnlineConfig.getInstance(this.mContext).getBooleanValue(ConfigKey.ScreenOnOrChargingTinyDataUploadSwitch.getValue(), false) || DeviceInfo.isCharging(this.mContext) || DeviceInfo.isScreenOn(this.mContext);
    }

    private String getAppId(String str) {
        return PushConstants.PUSH_SERVICE_PACKAGE_NAME.equals(str) ? MIPushAccountUtils.MIPUSH_MIUI_APPID : this.mContext.getSharedPreferences(PushServiceConstants.PREF_KEY_REGISTERED_PKGS, 0).getString(str, null);
    }

    private void readOnlineConfig(Context context) {
        this.mUploadSwitch = OnlineConfig.getInstance(context).getBooleanValue(ConfigKey.TinyDataUploadSwitch.getValue(), true);
        int intValue = OnlineConfig.getInstance(context).getIntValue(ConfigKey.TinyDataUploadFrequency.getValue(), DefaultConfig.DEFAULT_TINY_DATA_UPLOAD_FREQUNECY);
        this.mPeriod = intValue;
        this.mPeriod = Math.max(60, intValue);
    }

    public static void setIsTinyDataExtracting(boolean z) {
        mIsTinyDataExtracting = z;
    }

    private boolean verifyUploadPeriod() {
        return Math.abs((System.currentTimeMillis() / 1000) - this.mContext.getSharedPreferences("mipush_extra", 4).getLong(LAST_TINY_DATA_UPLOAD_TIMESTAMP, -1L)) > ((long) this.mPeriod);
    }

    @Override // com.xiaomi.push.service.PingCallBack
    public void pingFollowUpAction() {
        readOnlineConfig(this.mContext);
        if (this.mUploadSwitch && verifyUploadPeriod()) {
            MyLog.w("TinyData TinyDataCacheProcessor.pingFollowUpAction ts:" + System.currentTimeMillis());
            TinyDataUploader uploader = TinyDataManager.getInstance(this.mContext).getUploader();
            if (canUpload(uploader)) {
                mIsTinyDataExtracting = true;
                TinyDataCacheReader.addTinyDataCacheReadJob(this.mContext, uploader);
            } else {
                MyLog.w("TinyData TinyDataCacheProcessor.pingFollowUpAction !canUpload(uploader) ts:" + System.currentTimeMillis());
            }
        }
    }
}
