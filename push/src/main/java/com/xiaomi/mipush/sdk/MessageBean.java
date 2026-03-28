package com.xiaomi.mipush.sdk;

import android.text.TextUtils;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/MessageBean.class */
class MessageBean {
    int count = 0;
    String messageId = "";

    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof MessageBean)) {
            return false;
        }
        MessageBean messageBean = (MessageBean) obj;
        return !TextUtils.isEmpty(messageBean.messageId) && messageBean.messageId.equals(this.messageId);
    }
}
