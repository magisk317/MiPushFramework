package com.xiaomi.slim;

import com.xiaomi.smack.Connection;
import com.xiaomi.smack.XMPPException;
import com.xiaomi.smack.packet.Message;
import com.xiaomi.smack.packet.Packet;
import com.xiaomi.smack.util.PacketParserUtils;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/slim/PacketParser.class */
public class PacketParser {
    private XmlPullParser mParser;

    PacketParser() {
        try {
            XmlPullParser xmlPullParserNewPullParser = XmlPullParserFactory.newInstance().newPullParser();
            this.mParser = xmlPullParserNewPullParser;
            xmlPullParserNewPullParser.setFeature("http://xmlpull.org/v1/doc/features.html#process-namespaces", true);
        } catch (XmlPullParserException e) {
        }
    }

    Packet parse(byte[] bArr, Connection connection) throws Exception {
        this.mParser.setInput(new InputStreamReader(new ByteArrayInputStream(bArr)));
        this.mParser.next();
        int eventType = this.mParser.getEventType();
        String name = this.mParser.getName();
        if (eventType != 2) {
            return null;
        }
        if (name.equals("message")) {
            return PacketParserUtils.parseMessage(this.mParser);
        }
        if (name.equals("iq")) {
            return PacketParserUtils.parseIQ(this.mParser, connection);
        }
        if (name.equals("presence")) {
            return PacketParserUtils.parsePresence(this.mParser);
        }
        if (this.mParser.getName().equals("stream")) {
            return null;
        }
        if (this.mParser.getName().equals(Message.MSG_TYPE_ERROR)) {
            throw new XMPPException(PacketParserUtils.parseStreamError(this.mParser));
        }
        if (!this.mParser.getName().equals("warning")) {
            this.mParser.getName().equals("bind");
            return null;
        }
        this.mParser.next();
        this.mParser.getName().equals("multi-login");
        return null;
    }
}
