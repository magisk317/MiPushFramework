package com.xiaomi.smack.util;

import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.push.service.CommonPacketExtensionProvider;
import com.xiaomi.push.service.PushClientsManager;
import com.xiaomi.push.service.PushServiceConstants;
import com.xiaomi.push.service.RC4Cryption;
import com.xiaomi.smack.Connection;
import com.xiaomi.smack.XMPPException;
import com.xiaomi.smack.packet.CommonPacketExtension;
import com.xiaomi.smack.packet.IQ;
import com.xiaomi.smack.packet.Message;
import com.xiaomi.smack.packet.Packet;
import com.xiaomi.smack.packet.Presence;
import com.xiaomi.smack.packet.StreamError;
import com.xiaomi.smack.packet.XMPPError;
import com.xiaomi.smack.provider.ProviderManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/smack/util/PacketParserUtils.class */
public class PacketParserUtils {
    private static final String PROPERTIES_NAMESPACE = "http://www.jivesoftware.com/xmlns/xmpp/properties";
    private static XmlPullParser sDecryptedMsgParser = null;

    private static Object decode(Class cls, String str) throws Exception {
        if (cls.getName().equals("java.lang.String")) {
            return str;
        }
        if (cls.getName().equals("boolean")) {
            return Boolean.valueOf(str);
        }
        if (cls.getName().equals("int")) {
            return Integer.valueOf(str);
        }
        if (cls.getName().equals("long")) {
            return Long.valueOf(str);
        }
        if (cls.getName().equals("float")) {
            return Float.valueOf(str);
        }
        if (cls.getName().equals("double")) {
            return Double.valueOf(str);
        }
        if (cls.getName().equals("java.lang.Class")) {
            return Class.forName(str);
        }
        return null;
    }

    private static String getLanguageAttribute(XmlPullParser xmlPullParser) {
        for (int i = 0; i < xmlPullParser.getAttributeCount(); i++) {
            String attributeName = xmlPullParser.getAttributeName(i);
            if ("xml:lang".equals(attributeName) || ("lang".equals(attributeName) && "xml".equals(xmlPullParser.getAttributePrefix(i)))) {
                return xmlPullParser.getAttributeValue(i);
            }
        }
        return null;
    }

    private static String parseContent(XmlPullParser xmlPullParser) throws XmlPullParserException, IOException {
        String str = "";
        int depth = xmlPullParser.getDepth();
        while (true) {
            if (xmlPullParser.next() == 3 && xmlPullParser.getDepth() == depth) {
                return str;
            }
            str = str + xmlPullParser.getText();
        }
    }

    public static XMPPError parseError(XmlPullParser xmlPullParser) throws Exception {
        String text;
        String str;
        boolean z;
        String attributeValue = "-1";
        String attributeValue2 = null;
        String str2 = null;
        String str3 = null;
        String attributeValue3 = null;
        List<CommonPacketExtension> arrayList = new ArrayList<>();
        for (int i = 0; i < xmlPullParser.getAttributeCount(); i++) {
            if (xmlPullParser.getAttributeName(i).equals("code")) {
                attributeValue = xmlPullParser.getAttributeValue("", "code");
            }
            if (xmlPullParser.getAttributeName(i).equals("type")) {
                attributeValue2 = xmlPullParser.getAttributeValue("", "type");
            }
            if (xmlPullParser.getAttributeName(i).equals("reason")) {
                attributeValue3 = xmlPullParser.getAttributeValue("", "reason");
            }
        }
        boolean z2 = false;
        while (true) {
            boolean z3 = z2;
            if (z3) {
                break;
            }
            int next = xmlPullParser.next();
            if (next == 2) {
                if (xmlPullParser.getName().equals("text")) {
                    text = xmlPullParser.nextText();
                    str = str3;
                    z = z3;
                } else {
                    String name = xmlPullParser.getName();
                    String namespace = xmlPullParser.getNamespace();
                    if ("urn:ietf:params:xml:ns:xmpp-stanzas".equals(namespace)) {
                        str3 = name;
                    } else {
                        arrayList.add(parsePacketExtension(name, namespace, xmlPullParser));
                    }
                    text = str2;
                    str = str3;
                    z = z3;
                }
            } else if (next == 3) {
                text = str2;
                str = str3;
                z = z3;
                if (xmlPullParser.getName().equals(Message.MSG_TYPE_ERROR)) {
                    z = true;
                    text = str2;
                    str = str3;
                }
            } else {
                text = str2;
                str = str3;
                z = z3;
                if (next == 4) {
                    text = xmlPullParser.getText();
                    z = z3;
                    str = str3;
                }
            }
            str2 = text;
            str3 = str;
            z2 = z;
        }
        if (attributeValue2 == null) {
            attributeValue2 = "cancel";
        }
        return new XMPPError(Integer.parseInt(attributeValue), attributeValue2, attributeValue3, str3, str2, arrayList);
    }

    public static IQ parseIQ(XmlPullParser xmlPullParser, Connection connection) throws Exception {
        IQ iq;
        XMPPError error;
        IQ iq2 = null;
        String attributeValue = xmlPullParser.getAttributeValue("", "id");
        String attributeValue2 = xmlPullParser.getAttributeValue("", "to");
        String attributeValue3 = xmlPullParser.getAttributeValue("", "from");
        String attributeValue4 = xmlPullParser.getAttributeValue("", "chid");
        IQ.Type typeFromString = IQ.Type.fromString(xmlPullParser.getAttributeValue("", "type"));
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < xmlPullParser.getAttributeCount(); i++) {
            String attributeName = xmlPullParser.getAttributeName(i);
            map.put(attributeName, xmlPullParser.getAttributeValue("", attributeName));
        }
        XMPPError xMPPError = null;
        boolean z = false;
        while (!z) {
            int next = xmlPullParser.next();
            if (next == 2) {
                String name = xmlPullParser.getName();
                String namespace = xmlPullParser.getNamespace();
                if (name.equals(Message.MSG_TYPE_ERROR)) {
                    error = parseError(xmlPullParser);
                    iq = iq2;
                } else {
                    iq = new IQ();
                    iq.addExtension(parsePacketExtension(name, namespace, xmlPullParser));
                    error = xMPPError;
                }
            } else {
                iq = iq2;
                error = xMPPError;
                if (next == 3) {
                    iq = iq2;
                    error = xMPPError;
                    if (xmlPullParser.getName().equals("iq")) {
                        z = true;
                        error = xMPPError;
                        iq = iq2;
                    }
                }
            }
            iq2 = iq;
            xMPPError = error;
        }
        IQ iq3 = iq2;
        if (iq2 == null) {
            if (IQ.Type.GET == typeFromString || IQ.Type.SET == typeFromString) {
                IQ iq4 = new IQ() { // from class: com.xiaomi.smack.util.PacketParserUtils.1
                    @Override // com.xiaomi.smack.packet.IQ
                    public String getChildElementXML() {
                        return null;
                    }
                };
                iq4.setPacketID(attributeValue);
                iq4.setTo(attributeValue3);
                iq4.setFrom(attributeValue2);
                iq4.setType(IQ.Type.ERROR);
                iq4.setChannelId(attributeValue4);
                iq4.setError(new XMPPError(XMPPError.Condition.feature_not_implemented));
                connection.sendPacket(iq4);
                MyLog.e("iq usage error. send packet in packet parser.");
                return null;
            }
            iq3 = new IQ() { // from class: com.xiaomi.smack.util.PacketParserUtils.2
                @Override // com.xiaomi.smack.packet.IQ
                public String getChildElementXML() {
                    return null;
                }
            };
        }
        iq3.setPacketID(attributeValue);
        iq3.setTo(attributeValue2);
        iq3.setChannelId(attributeValue4);
        iq3.setFrom(attributeValue3);
        iq3.setType(typeFromString);
        iq3.setError(xMPPError);
        iq3.setAttributes(map);
        return iq3;
    }

    public static Packet parseMessage(XmlPullParser xmlPullParser) throws Exception {
        boolean z;
        String strNextText;
        Packet message;
        boolean z2;
        String text;
        if ("1".equals(xmlPullParser.getAttributeValue("", "s"))) {
            String attributeValue = xmlPullParser.getAttributeValue("", "chid");
            String attributeValue2 = xmlPullParser.getAttributeValue("", "id");
            String attributeValue3 = xmlPullParser.getAttributeValue("", "from");
            String attributeValue4 = xmlPullParser.getAttributeValue("", "to");
            String attributeValue5 = xmlPullParser.getAttributeValue("", "type");
            PushClientsManager.ClientLoginInfo clientLoginInfoByChidAndUserId = PushClientsManager.getInstance().getClientLoginInfoByChidAndUserId(attributeValue, attributeValue4);
            PushClientsManager.ClientLoginInfo clientLoginInfoByChidAndUserId2 = clientLoginInfoByChidAndUserId;
            if (clientLoginInfoByChidAndUserId == null) {
                clientLoginInfoByChidAndUserId2 = PushClientsManager.getInstance().getClientLoginInfoByChidAndUserId(attributeValue, attributeValue3);
            }
            Packet packet = null;
            if (clientLoginInfoByChidAndUserId2 == null) {
                throw new XMPPException("the channel id is wrong while receiving a encrypted message");
            }
            boolean z3 = false;
            while (true) {
                boolean z4 = z3;
                if (z4) {
                    if (packet != null) {
                        return packet;
                    }
                    throw new XMPPException("error while receiving a encrypted message with wrong format");
                }
                int next = xmlPullParser.next();
                if (next != 2) {
                    message = packet;
                    z2 = z4;
                    if (next == 3) {
                        message = packet;
                        z2 = z4;
                        if (xmlPullParser.getName().equals("message")) {
                            z2 = true;
                            message = packet;
                        }
                    }
                } else {
                    if (!"s".equals(xmlPullParser.getName())) {
                        throw new XMPPException("error while receiving a encrypted message with wrong format");
                    }
                    if (xmlPullParser.next() != 4) {
                        throw new XMPPException("error while receiving a encrypted message with wrong format");
                    }
                    text = xmlPullParser.getText();
                    if ("5".equals(attributeValue) || "6".equals(attributeValue)) {
                        break;
                    }
                    resetDecryptedMsgParser(RC4Cryption.decrypt(RC4Cryption.generateKeyForRC4(clientLoginInfoByChidAndUserId2.security, attributeValue2), text));
                    sDecryptedMsgParser.next();
                    message = parseMessage(sDecryptedMsgParser);
                    z2 = z4;
                }
                packet = message;
                z3 = z2;
            }
            Message message2 = new Message();
            message2.setChannelId(attributeValue);
            message2.setEncrypted(true);
            message2.setFrom(attributeValue3);
            message2.setTo(attributeValue4);
            message2.setPacketID(attributeValue2);
            message2.setType(attributeValue5);
            String[] strArr = (String[]) null;
            CommonPacketExtension commonPacketExtension = new CommonPacketExtension("s", (String) null, strArr, strArr);
            commonPacketExtension.setText(text);
            message2.addExtension(commonPacketExtension);
            return message2;
        }
        Message message3 = new Message();
        String attributeValue6 = xmlPullParser.getAttributeValue("", "id");
        if (attributeValue6 == null) {
            attributeValue6 = "ID_NOT_AVAILABLE";
        }
        message3.setPacketID(attributeValue6);
        message3.setTo(xmlPullParser.getAttributeValue("", "to"));
        message3.setFrom(xmlPullParser.getAttributeValue("", "from"));
        message3.setChannelId(xmlPullParser.getAttributeValue("", "chid"));
        message3.setAppId(xmlPullParser.getAttributeValue("", PushServiceConstants.EXTENSION_ATTRIBUTE_OPENPLATFORM_APPID));
        String attributeValue7 = null;
        try {
            attributeValue7 = xmlPullParser.getAttributeValue("", "transient");
        } catch (Exception e) {
        }
        try {
            String attributeValue8 = xmlPullParser.getAttributeValue("", "seq");
            if (!TextUtils.isEmpty(attributeValue8)) {
                message3.setSeq(attributeValue8);
            }
        } catch (Exception e2) {
        }
        try {
            String attributeValue9 = xmlPullParser.getAttributeValue("", "mseq");
            if (!TextUtils.isEmpty(attributeValue9)) {
                message3.setMSeq(attributeValue9);
            }
        } catch (Exception e3) {
        }
        try {
            String attributeValue10 = xmlPullParser.getAttributeValue("", "fseq");
            if (!TextUtils.isEmpty(attributeValue10)) {
                message3.setFSeq(attributeValue10);
            }
        } catch (Exception e4) {
        }
        try {
            String attributeValue11 = xmlPullParser.getAttributeValue("", "status");
            if (!TextUtils.isEmpty(attributeValue11)) {
                message3.setStatus(attributeValue11);
            }
        } catch (Exception e5) {
        }
        message3.setIsTransient(!TextUtils.isEmpty(attributeValue7) && attributeValue7.equalsIgnoreCase("true"));
        message3.setType(xmlPullParser.getAttributeValue("", "type"));
        String languageAttribute = getLanguageAttribute(xmlPullParser);
        if (languageAttribute == null || "".equals(languageAttribute.trim())) {
            Packet.getDefaultLanguage();
        } else {
            message3.setLanguage(languageAttribute);
        }
        boolean z5 = false;
        String str = null;
        while (true) {
            String str2 = str;
            if (z5) {
                message3.setThread(str2);
                return message3;
            }
            int next2 = xmlPullParser.next();
            if (next2 == 2) {
                String name = xmlPullParser.getName();
                String namespace = xmlPullParser.getNamespace();
                String str3 = namespace;
                if (TextUtils.isEmpty(namespace)) {
                    str3 = PushServiceConstants.XM_CHAT_GROUP_NAMESPACE;
                }
                if (name.equals("subject")) {
                    if (getLanguageAttribute(xmlPullParser) == null) {
                    }
                    message3.setSubject(parseContent(xmlPullParser));
                    strNextText = str2;
                } else if (name.equals("body")) {
                    String attributeValue12 = xmlPullParser.getAttributeValue("", "encode");
                    String content = parseContent(xmlPullParser);
                    if (TextUtils.isEmpty(attributeValue12)) {
                        message3.setBody(content);
                    } else {
                        message3.setBody(content, attributeValue12);
                    }
                    strNextText = str2;
                } else if (name.equals("thread")) {
                    strNextText = str2;
                    if (str2 == null) {
                        strNextText = xmlPullParser.nextText();
                    }
                } else if (name.equals(Message.MSG_TYPE_ERROR)) {
                    message3.setError(parseError(xmlPullParser));
                    strNextText = str2;
                } else {
                    message3.addExtension(parsePacketExtension(name, str3, xmlPullParser));
                    strNextText = str2;
                }
                z = z5;
            } else {
                z = z5;
                strNextText = str2;
                if (next2 == 3) {
                    z = z5;
                    strNextText = str2;
                    if (xmlPullParser.getName().equals("message")) {
                        z = true;
                        strNextText = str2;
                    }
                }
            }
            z5 = z;
            str = strNextText;
        }
    }

    public static CommonPacketExtension parsePacketExtension(String str, String str2, XmlPullParser xmlPullParser) throws Exception {
        Object extensionProvider = ProviderManager.getInstance().getExtensionProvider(PushServiceConstants.EXTENSION_ELE_NAME_ALL, PushServiceConstants.XM_CHAT_NAMESPACE);
        if (extensionProvider == null || !(extensionProvider instanceof CommonPacketExtensionProvider)) {
            return null;
        }
        return ((CommonPacketExtensionProvider) extensionProvider).parseExtension(xmlPullParser);
    }

    public static Presence parsePresence(XmlPullParser xmlPullParser) throws Exception {
        Presence.Type type = Presence.Type.available;
        String attributeValue = xmlPullParser.getAttributeValue("", "type");
        Presence.Type typeValueOf = type;
        if (attributeValue != null) {
            typeValueOf = type;
            if (!attributeValue.equals("")) {
                try {
                    typeValueOf = Presence.Type.valueOf(attributeValue);
                } catch (IllegalArgumentException e) {
                    System.err.println("Found invalid presence type " + attributeValue);
                    typeValueOf = type;
                }
            }
        }
        Presence presence = new Presence(typeValueOf);
        presence.setTo(xmlPullParser.getAttributeValue("", "to"));
        presence.setFrom(xmlPullParser.getAttributeValue("", "from"));
        presence.setChannelId(xmlPullParser.getAttributeValue("", "chid"));
        String attributeValue2 = xmlPullParser.getAttributeValue("", "id");
        if (attributeValue2 == null) {
            attributeValue2 = "ID_NOT_AVAILABLE";
        }
        presence.setPacketID(attributeValue2);
        boolean z = false;
        while (!z) {
            int next = xmlPullParser.next();
            if (next == 2) {
                String name = xmlPullParser.getName();
                String namespace = xmlPullParser.getNamespace();
                if (name.equals("status")) {
                    presence.setStatus(xmlPullParser.nextText());
                } else if (name.equals("priority")) {
                    try {
                        presence.setPriority(Integer.parseInt(xmlPullParser.nextText()));
                    } catch (NumberFormatException e2) {
                    } catch (IllegalArgumentException e3) {
                        presence.setPriority(0);
                    }
                } else if (name.equals("show")) {
                    String strNextText = xmlPullParser.nextText();
                    try {
                        presence.setMode(Presence.Mode.valueOf(strNextText));
                    } catch (IllegalArgumentException e4) {
                        System.err.println("Found invalid presence mode " + strNextText);
                    }
                } else if (name.equals(Message.MSG_TYPE_ERROR)) {
                    presence.setError(parseError(xmlPullParser));
                } else {
                    presence.addExtension(parsePacketExtension(name, namespace, xmlPullParser));
                }
            } else if (next == 3 && xmlPullParser.getName().equals("presence")) {
                z = true;
            }
        }
        return presence;
    }

    public static StreamError parseStreamError(XmlPullParser xmlPullParser) throws XmlPullParserException, IOException {
        StreamError streamError;
        boolean z;
        StreamError streamError2 = null;
        boolean z2 = false;
        while (true) {
            boolean z3 = z2;
            if (z3) {
                return streamError2;
            }
            int next = xmlPullParser.next();
            if (next == 2) {
                streamError = new StreamError(xmlPullParser.getName());
                z = z3;
            } else {
                streamError = streamError2;
                z = z3;
                if (next == 3) {
                    streamError = streamError2;
                    z = z3;
                    if (xmlPullParser.getName().equals(Message.MSG_TYPE_ERROR)) {
                        z = true;
                        streamError = streamError2;
                    }
                }
            }
            streamError2 = streamError;
            z2 = z;
        }
    }

    public static Object parseWithIntrospection(String str, Class<?> cls, XmlPullParser xmlPullParser) throws Exception {
        boolean z = false;
        Object objNewInstance = cls.getDeclaredConstructor().newInstance();
        while (!z) {
            int next = xmlPullParser.next();
            if (next == 2) {
                String name = xmlPullParser.getName();
                String strNextText = xmlPullParser.nextText();
                Class<?> returnType = objNewInstance.getClass().getMethod("get" + Character.toUpperCase(name.charAt(0)) + name.substring(1), new Class[0]).getReturnType();
                Object objDecode = decode(returnType, strNextText);
                objNewInstance.getClass().getMethod("set" + Character.toUpperCase(name.charAt(0)) + name.substring(1), returnType).invoke(objNewInstance, objDecode);
            } else if (next == 3 && xmlPullParser.getName().equals(str)) {
                z = true;
            }
        }
        return objNewInstance;
    }

    private static void resetDecryptedMsgParser(byte[] bArr) throws XmlPullParserException {
        if (sDecryptedMsgParser == null) {
            try {
                XmlPullParser xmlPullParserNewPullParser = XmlPullParserFactory.newInstance().newPullParser();
                sDecryptedMsgParser = xmlPullParserNewPullParser;
                xmlPullParserNewPullParser.setFeature("http://xmlpull.org/v1/doc/features.html#process-namespaces", true);
            } catch (XmlPullParserException e) {
                e.printStackTrace();
            }
        }
        sDecryptedMsgParser.setInput(new InputStreamReader(new ByteArrayInputStream(bArr)));
    }
}
