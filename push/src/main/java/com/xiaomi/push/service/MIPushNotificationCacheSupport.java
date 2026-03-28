package com.xiaomi.push.service;

import android.content.Context;
import android.text.TextUtils;
import android.util.Pair;
import com.xiaomi.xmpush.thrift.PushMetaInfo;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;
import java.util.LinkedList;

final class MIPushNotificationCacheSupport {
    private MIPushNotificationCacheSupport() {
    }

    static void clearNotification(Context context, String str, LinkedList<Pair<Integer, XmPushActionContainer>> linkedList) {
        clearNotification(context, str, -1, linkedList);
    }

    static void clearNotification(Context context, String str, int i, LinkedList<Pair<Integer, XmPushActionContainer>> linkedList) {
        NotificationManagerHelper notificationManagerHelperFrom = NotificationManagerHelper.from(context, str);
        int iHashCode = ((str.hashCode() / 10) * 10) + i;
        LinkedList<Pair<Integer, XmPushActionContainer>> linkedList2 = new LinkedList<>();
        if (i >= 0) {
            notificationManagerHelperFrom.cancel(iHashCode);
        }
        synchronized (linkedList) {
            for (Pair<Integer, XmPushActionContainer> pair : linkedList) {
                XmPushActionContainer xmPushActionContainer = pair.second;
                if (xmPushActionContainer != null) {
                    String targetPackage = MIPushNotificationHelper.getTargetPackage(xmPushActionContainer);
                    if (i >= 0) {
                        if (iHashCode == pair.first.intValue() && TextUtils.equals(targetPackage, str)) {
                            linkedList2.add(pair);
                        }
                    } else if (i == -1 && TextUtils.equals(targetPackage, str)) {
                        notificationManagerHelperFrom.cancel(pair.first.intValue());
                        linkedList2.add(pair);
                    }
                }
            }
            linkedList.removeAll(linkedList2);
        }
        uploadClearMessageData(context, linkedList2);
    }

    static void clearNotification(Context context, String str, String str2, String str3, LinkedList<Pair<Integer, XmPushActionContainer>> linkedList) {
        if (TextUtils.isEmpty(str2) && TextUtils.isEmpty(str3)) {
            return;
        }
        LinkedList<Pair<Integer, XmPushActionContainer>> linkedList2 = new LinkedList<>();
        synchronized (linkedList) {
            for (Pair<Integer, XmPushActionContainer> pair : linkedList) {
                XmPushActionContainer xmPushActionContainer = pair.second;
                if (xmPushActionContainer != null) {
                    String targetPackage = MIPushNotificationHelper.getTargetPackage(xmPushActionContainer);
                    PushMetaInfo metaInfo = xmPushActionContainer.getMetaInfo();
                    if (metaInfo != null && TextUtils.equals(targetPackage, str)) {
                        String title = metaInfo.getTitle();
                        String description = metaInfo.getDescription();
                        if (!TextUtils.isEmpty(title) && !TextUtils.isEmpty(description) && checkMatch(str2, title) && checkMatch(str3, description)) {
                            NotificationManagerHelper.from(context, str).cancel(pair.first.intValue());
                            linkedList2.add(pair);
                        }
                    }
                }
            }
            linkedList.removeAll(linkedList2);
        }
        uploadClearMessageData(context, linkedList2);
    }

    static void cacheNotification(Pair<Integer, XmPushActionContainer> pair, LinkedList<Pair<Integer, XmPushActionContainer>> linkedList) {
        synchronized (linkedList) {
            linkedList.add(pair);
            if (linkedList.size() > MIPushNotificationHelper.MAX_NOTIFY_ID_CACHE_SIZE) {
                linkedList.remove();
            }
        }
    }

    static void uploadClearMessageData(Context context, LinkedList<? extends Object> linkedList) {
        if (linkedList == null || linkedList.size() <= 0) {
            return;
        }
        TinyDataHelper.cacheTinyData(context, PushConstants.DOT_CATEGORY_CLEAR_NOTIFICATION, PushConstants.CLEAR_NOTIFICATION, linkedList.size(), "");
    }

    private static boolean checkMatch(String str, String str2) {
        return TextUtils.isEmpty(str) || str2.contains(str);
    }
}
