package com.xiaomi.smack.provider;

import com.xiaomi.smack.packet.IQ;
import org.xmlpull.v1.XmlPullParser;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/smack/provider/IQProvider.class */
public interface IQProvider {
    IQ parseIQ(XmlPullParser xmlPullParser) throws Exception;
}
