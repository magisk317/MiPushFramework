package com.xiaomi.push.service.awake;

import android.content.Context;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager;
import com.xiaomi.push.service.awake.module.AwakeManager;
import com.xiaomi.push.service.awake.module.IProcessData;
import java.util.HashMap;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/awake/AwakeUploadHelper.class */
public class AwakeUploadHelper {
    public static final int A_AWAKE_B_END = 1006;
    public static final int A_AWAKE_B_RESULT = 1005;
    public static final int A_GET_MESSAGE = 1001;
    public static final int A_WILL_AWAKE_B = 1004;
    public static final int B_MISS_COMPONENT = 1003;
    public static final int B_SATISFY_COMPONENT = 1002;
    public static final int B_UPLOAD_RESULT = 1007;
    public static final int ERROR = 1008;
    public static final String KEY_AWAKE_INFO = "awake_info";
    public static final String KEY_DESCRIPTION = "description";
    public static final String KEY_EVENT_TYPE = "event_type";
    public static final String KEY_PING_FREQUENCY = "ping_frequency";
    public static final String KEY_PING_SWITCH = "ping_switch";
    public static final int PING = 9999;
    public static final int SEND_BOTH_WAY = 3;
    public static final int SEND_BY_TINY_DATA = 2;
    public static final int SEND_DIRECTLY = 1;

    private static void doLast(Context context, HashMap<String, String> map) {
        IProcessData sendDataIml = AwakeManager.getInstance(context).getSendDataIml();
        if (sendDataIml != null) {
            sendDataIml.shouldDoLast(context, map);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void doUploadData(Context context, String str, int i, String str2) {
        if (context == null || TextUtils.isEmpty(str)) {
            return;
        }
        try {
            HashMap map = new HashMap();
            map.put(KEY_AWAKE_INFO, str);
            map.put(KEY_EVENT_TYPE, String.valueOf(i));
            map.put(KEY_DESCRIPTION, str2);
            switch (AwakeManager.getInstance(context).getOnLineCmd()) {
                case 1:
                    sendResponseDirectly(context, map);
                    break;
                case 2:
                    sendResultByTinyData(context, map);
                    break;
                case 3:
                    sendResponseDirectly(context, map);
                    sendResultByTinyData(context, map);
                    break;
            }
            doLast(context, map);
        } catch (Exception e) {
            MyLog.e(e);
        }
    }

    private static void sendResponseDirectly(Context context, HashMap<String, String> map) {
        IProcessData sendDataIml = AwakeManager.getInstance(context).getSendDataIml();
        if (sendDataIml != null) {
            sendDataIml.sendDirectly(context, map);
        }
    }

    private static void sendResultByTinyData(Context context, HashMap<String, String> map) {
        IProcessData sendDataIml = AwakeManager.getInstance(context).getSendDataIml();
        if (sendDataIml != null) {
            sendDataIml.sendByTinyData(context, map);
        }
    }

    public static void uploadData(final Context context, final String str, final int i, final String str2) {
        ScheduledJobManager.getInstance(context).addOneShootJob(new Runnable() { // from class: com.xiaomi.push.service.awake.AwakeUploadHelper.1
            @Override // java.lang.Runnable
            public void run() {
                AwakeUploadHelper.doUploadData(context, str, i, str2);
            }
        });
    }
}
