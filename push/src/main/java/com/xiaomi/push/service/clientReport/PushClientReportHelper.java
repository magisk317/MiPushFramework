package com.xiaomi.push.service.clientReport;

import android.content.Context;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.clientreport.data.ClientReportConstants;
import com.xiaomi.clientreport.data.Config;
import com.xiaomi.clientreport.data.EventClientReport;
import com.xiaomi.clientreport.data.PerfClientReport;
import com.xiaomi.clientreport.manager.ClientReportClient;
import com.xiaomi.push.service.OnlineConfig;
import com.xiaomi.push.service.PushConstants;
import com.xiaomi.push.service.TinyDataHelper;
import com.xiaomi.push.service.TinyDataStorage;
import com.xiaomi.push.service.xmpush.Command;
import com.xiaomi.xmpush.thrift.ActionType;
import com.xiaomi.xmpush.thrift.ClientUploadDataItem;
import com.xiaomi.xmpush.thrift.ConfigKey;
import com.xiaomi.xmpush.thrift.NotificationType;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/clientReport/PushClientReportHelper.class */
public class PushClientReportHelper {
    private static Uploader mUploader;
    private static Map<String, NotificationType> notificationTypeMap = null;

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/clientReport/PushClientReportHelper$Uploader.class */
    public interface Uploader {
        void uploader(Context context, ClientUploadDataItem clientUploadDataItem);
    }

    public static int changeOrdinalToCode(Enum r3) {
        int iOrdinal = -1;
        if (r3 != null) {
            if (r3 instanceof ActionType) {
                iOrdinal = r3.ordinal() + 1001;
            } else if (r3 instanceof NotificationType) {
                iOrdinal = r3.ordinal() + 2001;
            } else {
                iOrdinal = -1;
                if (r3 instanceof Command) {
                    iOrdinal = r3.ordinal() + 3001;
                }
            }
        }
        return iOrdinal;
    }

    public static int changeValueToCode(int i) {
        int i2 = -1;
        if (i > 0) {
            i2 = i + 1000;
        }
        return i2;
    }

    public static NotificationType changeValueToNotificationType(String str) {
        if (notificationTypeMap == null) {
            synchronized (NotificationType.class) {
                try {
                    if (notificationTypeMap == null) {
                        notificationTypeMap = new HashMap<>();
                        for (NotificationType notificationType : NotificationType.values()) {
                            notificationTypeMap.put(notificationType.value.toLowerCase(), notificationType);
                        }
                    }
                } catch (Throwable th) {
                    throw th;
                }
            }
        }
        NotificationType notificationType2 = notificationTypeMap.get(str.toLowerCase());
        if (notificationType2 == null) {
            notificationType2 = NotificationType.Invalid;
        }
        return notificationType2;
    }

    public static void checkConfigChange(Context context) {
        ClientReportClient.updateConfig(context, getConfig(context));
    }

    public static Config getConfig(Context context) {
        boolean booleanValue = OnlineConfig.getInstance(context).getBooleanValue(ConfigKey.PerfUploadSwitch.getValue(), false);
        boolean booleanValue2 = OnlineConfig.getInstance(context).getBooleanValue(ConfigKey.EventUploadNewSwitch.getValue(), false);
        return Config.getBuilder().setEventUploadSwitchOpen(booleanValue2).setEventUploadFrequency(OnlineConfig.getInstance(context).getIntValue(ConfigKey.EventUploadFrequency.getValue(), 86400)).setPerfUploadSwitchOpen(booleanValue).setPerfUploadFrequency(OnlineConfig.getInstance(context).getIntValue(ConfigKey.PerfUploadFrequency.getValue(), 86400)).build(context);
    }

    public static String getInterfaceIdByType(int i) {
        return i == 1000 ? ReportConstants.NOTIFICATION_EVENT_CHAIN_INTERFACE_ID : i == 3000 ? ReportConstants.AWAKE_EVENT_CHAIN_INTERFACE_ID : i == 2000 ? ReportConstants.THROUGH_EVENT_CHAIN_INTERFACE_ID : i == 6000 ? ReportConstants.REGISTER_EVENT_CHAIN_INTERFACE_ID : "";
    }

    public static void initEventPerfLogic(Context context, Config config) {
        ClientReportClient.init(context, config, new MIPushEventDataProcessor(context), new MIPushPerfDataProcessor(context));
    }

    public static boolean isInXmsf(Context context) {
        return (context == null || TextUtils.isEmpty(context.getPackageName()) || !PushConstants.PUSH_SERVICE_PACKAGE_NAME.equals(context.getPackageName())) ? false : true;
    }

    public static EventClientReport newEvent(String str) {
        EventClientReport eventClientReport = new EventClientReport();
        eventClientReport.production = 1000;
        eventClientReport.reportType = 1001;
        eventClientReport.clientInterfaceId = str;
        return eventClientReport;
    }

    public static PerfClientReport newPerf() {
        PerfClientReport perfClientReport = new PerfClientReport();
        perfClientReport.production = 1000;
        perfClientReport.reportType = 1000;
        perfClientReport.clientInterfaceId = ReportConstants.DATA_FLOW_INTERFACE_ID;
        return perfClientReport;
    }

    public static EventClientReport reportEvent2Object(Context context, String str, String str2, int i, long j, String str3) {
        EventClientReport eventClientReportNewEvent = newEvent(str);
        eventClientReportNewEvent.eventId = str2;
        eventClientReportNewEvent.eventType = i;
        eventClientReportNewEvent.eventTime = j;
        eventClientReportNewEvent.eventContent = str3;
        return eventClientReportNewEvent;
    }

    public static PerfClientReport reportPerf2Object(Context context, int i, long j, long j2) {
        PerfClientReport perfClientReportNewPerf = newPerf();
        perfClientReportNewPerf.code = i;
        perfClientReportNewPerf.perfCounts = j;
        perfClientReportNewPerf.perfLatencies = j2;
        return perfClientReportNewPerf;
    }

    private static void sendByTinyData(Context context, ClientUploadDataItem clientUploadDataItem) {
        if (isInXmsf(context.getApplicationContext())) {
            TinyDataStorage.cacheTinyData(context.getApplicationContext(), clientUploadDataItem);
            return;
        }
        Uploader uploader = mUploader;
        if (uploader != null) {
            uploader.uploader(context, clientUploadDataItem);
        }
    }

    public static void sendData(Context context, List<String> list) {
        if (list == null) {
            return;
        }
        try {
            Iterator<String> it = list.iterator();
            while (it.hasNext()) {
                ClientUploadDataItem clientUploadDataItemWrapperData = wrapperData(context, it.next());
                if (!TinyDataHelper.verify(clientUploadDataItemWrapperData, false)) {
                    sendByTinyData(context, clientUploadDataItemWrapperData);
                }
            }
        } catch (Throwable th) {
            MyLog.e(th.getMessage());
        }
    }

    public static void setUploader(Uploader uploader) {
        mUploader = uploader;
    }

    public static ClientUploadDataItem wrapperData(Context context, String str) {
        if (TextUtils.isEmpty(str)) {
            return null;
        }
        ClientUploadDataItem clientUploadDataItem = new ClientUploadDataItem();
        clientUploadDataItem.setCategory("category_client_report_data");
        clientUploadDataItem.setChannel("push_sdk_channel");
        clientUploadDataItem.setCounter(1L);
        clientUploadDataItem.setData(str);
        clientUploadDataItem.setFromSdk(true);
        clientUploadDataItem.setTimestamp(System.currentTimeMillis());
        clientUploadDataItem.setPkgName(context.getPackageName());
        clientUploadDataItem.setSourcePackage(PushConstants.PUSH_SERVICE_PACKAGE_NAME);
        clientUploadDataItem.setId(TinyDataHelper.nextTinyDataItemId());
        clientUploadDataItem.setName(ClientReportConstants.NAME);
        return clientUploadDataItem;
    }
}
