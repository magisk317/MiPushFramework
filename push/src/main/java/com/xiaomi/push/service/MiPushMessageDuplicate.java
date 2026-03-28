package com.xiaomi.push.service;

import android.content.SharedPreferences;
import com.xiaomi.channel.commonutils.string.XMStringUtils;
import com.xiaomi.xmsf.runtime.PushRuntimeDuplicateStore;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/MiPushMessageDuplicate.class */
public class MiPushMessageDuplicate {
    private static final int MAX_MSG_CACHE_COUNT = 25;
    private static Object lock = new Object();
    private static Map<String, Queue<String>> mCachedMsgIds = new HashMap();

    public static boolean isDuplicateMessage(XMPushService xMPushService, String str, String str2) {
        return PushRuntimeDuplicateStore.isDuplicateMessage(xMPushService, str, str2);
    }
}
