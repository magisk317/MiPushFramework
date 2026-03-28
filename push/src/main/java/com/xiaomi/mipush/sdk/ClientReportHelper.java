package com.xiaomi.mipush.sdk;

import android.content.Context;
import android.content.Intent;
import com.xiaomi.clientreport.data.Config;
import com.xiaomi.push.service.PushConstants;
import org.apache.thrift.TBase;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/ClientReportHelper.class */
public class ClientReportHelper {
    public static <T extends TBase<T, ?>> void sendConfigInfo(Context context, Config config) {
        if (config == null) {
            return;
        }
        Intent intent = new Intent();
        intent.setAction(PushConstants.ACTION_CLIENT_REPORT_CONFIG);
        intent.putExtra(PushConstants.EXTRA_CR_EVENT_SWITCH, config.isEventUploadSwitchOpen());
        intent.putExtra(PushConstants.EXTRA_CR_EVENT_FREQUENCY, config.getEventUploadFrequency());
        intent.putExtra(PushConstants.EXTRA_CR_PREF_SWITCH, config.isPerfUploadSwitchOpen());
        intent.putExtra(PushConstants.EXTRA_CR_PREF_FREQUENCY, config.getPerfUploadFrequency());
        intent.putExtra(PushConstants.EXTRA_CR_EVENT_ENCRYPTED, config.isEventEncrypted());
        intent.putExtra(PushConstants.EXTRA_CR_MAX_FILE_SIZE, config.getMaxFileLength());
        PushServiceClient.getInstance(context).sendDataCommon(intent);
    }
}
