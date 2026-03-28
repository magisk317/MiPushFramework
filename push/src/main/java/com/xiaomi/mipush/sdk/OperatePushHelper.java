package com.xiaomi.mipush.sdk;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/OperatePushHelper.class */
public class OperatePushHelper {
    public static final int MAX_RETRY_COUNT = 10;
    public static final String SYNCED = "synced";
    public static final String SYNCING = "syncing";
    public static final int TIME_OUT = 5000;
    private static volatile OperatePushHelper sInstance = null;
    private Context appContext;
    private List<MessageBean> operateMessages = new ArrayList();

    private OperatePushHelper(Context context) {
        Context applicationContext = context.getApplicationContext();
        this.appContext = applicationContext;
        if (applicationContext == null) {
            this.appContext = context;
        }
    }

    public static OperatePushHelper getInstance(Context context) {
        if (sInstance == null) {
            synchronized (OperatePushHelper.class) {
                try {
                    if (sInstance == null) {
                        sInstance = new OperatePushHelper(context);
                    }
                } catch (Throwable th) {
                    throw th;
                }
            }
        }
        return sInstance;
    }

    public int getRetryCount(String str) {
        synchronized (this.operateMessages) {
            MessageBean messageBean = new MessageBean();
            messageBean.messageId = str;
            if (this.operateMessages.contains(messageBean)) {
                for (MessageBean messageBean2 : this.operateMessages) {
                    if (messageBean2.equals(messageBean)) {
                        return messageBean2.count;
                    }
                }
            }
            return 0;
        }
    }

    public String getSyncStatus(RetryType retryType) {
        String string;
        synchronized (this) {
            string = this.appContext.getSharedPreferences("mipush_extra", 0).getString(retryType.name(), "");
        }
        return string;
    }

    public void increaseRetryCount(String str) {
        synchronized (this.operateMessages) {
            MessageBean messageBean = new MessageBean();
            messageBean.messageId = str;
            MessageBean next = messageBean;
            if (this.operateMessages.contains(messageBean)) {
                Iterator<MessageBean> it = this.operateMessages.iterator();
                do {
                    next = messageBean;
                    if (!it.hasNext()) {
                        break;
                    } else {
                        next = it.next();
                    }
                } while (!messageBean.equals(next));
            }
            next.count++;
            this.operateMessages.remove(next);
            this.operateMessages.add(next);
        }
    }

    public boolean isMessageOperating(String str) {
        synchronized (this.operateMessages) {
            MessageBean messageBean = new MessageBean();
            messageBean.messageId = str;
            return this.operateMessages.contains(messageBean);
        }
    }

    public void putSyncStatus(RetryType retryType, String str) {
        synchronized (this) {
            SharedPreferences sharedPreferences = this.appContext.getSharedPreferences("mipush_extra", 0);
            sharedPreferences.edit().putString(retryType.name(), str).commit();
        }
    }

    public void removeOperateMessage(String str) {
        synchronized (this.operateMessages) {
            MessageBean messageBean = new MessageBean();
            messageBean.messageId = str;
            if (this.operateMessages.contains(messageBean)) {
                this.operateMessages.remove(messageBean);
            }
        }
    }

    public void resetOperateMessage(String str) {
        synchronized (this.operateMessages) {
            MessageBean messageBean = new MessageBean();
            messageBean.count = 0;
            messageBean.messageId = str;
            if (this.operateMessages.contains(messageBean)) {
                this.operateMessages.remove(messageBean);
            }
            this.operateMessages.add(messageBean);
        }
    }
}
