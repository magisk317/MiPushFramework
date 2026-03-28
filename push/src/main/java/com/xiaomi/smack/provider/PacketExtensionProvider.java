package com.xiaomi.smack.provider;

import com.xiaomi.smack.packet.PacketExtension;
import org.xmlpull.v1.XmlPullParser;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/smack/provider/PacketExtensionProvider.class */
public interface PacketExtensionProvider {
    PacketExtension parseExtension(XmlPullParser xmlPullParser) throws Exception;
}
