package com.xiaomi.push.service.profile;

import android.util.Pair;
import com.xiaomi.push.mpcd.Constants;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/profile/MessageProfiling.class */
public class MessageProfiling {
    private static Vector<Pair<String, Long>> sSentPerfDatas = new Vector<>();
    private static ConcurrentHashMap<String, Long> sSendingMessages = new ConcurrentHashMap<>();

    public static String getPrefString() {
        StringBuilder sb = new StringBuilder();
        synchronized (sSentPerfDatas) {
            for (int i = 0; i < sSentPerfDatas.size(); i++) {
                Pair<String, Long> pairElementAt = sSentPerfDatas.elementAt(i);
                sb.append((String) pairElementAt.first);
                sb.append(":");
                sb.append(pairElementAt.second);
                if (i < sSentPerfDatas.size() - 1) {
                    sb.append(Constants.ITEM_SEPARATOR);
                }
            }
            sSentPerfDatas.clear();
        }
        return sb.toString();
    }

    public static void onReceiveSentAck(String str, String str2) {
        if (sSendingMessages.containsKey(str)) {
            long jLongValue = sSendingMessages.get(str).longValue();
            sSentPerfDatas.add(new Pair<>(str2, Long.valueOf(System.currentTimeMillis() - jLongValue)));
            sSendingMessages.remove(str);
        }
    }

    public static void onSendingMessage(String str) {
        sSendingMessages.put(str, Long.valueOf(System.currentTimeMillis()));
    }
}
