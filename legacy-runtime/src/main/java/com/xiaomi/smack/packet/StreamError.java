package com.xiaomi.smack.packet;

import com.xiaomi.push.mpcd.Constants;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/smack/packet/StreamError.class */
public class StreamError {
    private String code;

    public StreamError(String str) {
        this.code = str;
    }

    public String getCode() {
        return this.code;
    }

    public String toString() {
        return "stream:error (" + this.code + Constants.SEPARATOR_RIGHT_PARENTESIS;
    }
}
