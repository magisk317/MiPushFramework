package com.xiaomi.push.service;

import com.xiaomi.smack.packet.CommonPacketExtension;
import com.xiaomi.smack.provider.PacketExtensionProvider;
import com.xiaomi.smack.provider.ProviderManager;
import com.xiaomi.smack.util.StringUtils;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/CommonPacketExtensionProvider.class */
public class CommonPacketExtensionProvider implements PacketExtensionProvider {
    public static CommonPacketExtension parseExtensionFromStartTag(XmlPullParser xmlPullParser) throws XmlPullParserException, IOException {
        if (xmlPullParser.getEventType() != 2) {
            return null;
        }
        String[] strArr = null;
        String[] strArr2 = null;
        String name = xmlPullParser.getName();
        String namespace = xmlPullParser.getNamespace();
        String strTrim = null;
        ArrayList arrayList = null;
        if (xmlPullParser.getAttributeCount() > 0) {
            String[] strArr3 = new String[xmlPullParser.getAttributeCount()];
            String[] strArr4 = new String[xmlPullParser.getAttributeCount()];
            int i = 0;
            while (true) {
                strArr = strArr3;
                strArr2 = strArr4;
                strTrim = null;
                arrayList = null;
                if (i >= xmlPullParser.getAttributeCount()) {
                    break;
                }
                strArr3[i] = xmlPullParser.getAttributeName(i);
                strArr4[i] = StringUtils.unescapeFromXML(xmlPullParser.getAttributeValue(i));
                i++;
            }
        }
        while (true) {
            int next = xmlPullParser.next();
            if (next == 3) {
                return new CommonPacketExtension(name, namespace, strArr, strArr2, strTrim, arrayList);
            }
            if (next == 4) {
                strTrim = xmlPullParser.getText().trim();
            } else if (next == 2) {
                ArrayList arrayList2 = arrayList;
                if (arrayList == null) {
                    arrayList2 = new ArrayList();
                }
                CommonPacketExtension extensionFromStartTag = parseExtensionFromStartTag(xmlPullParser);
                if (extensionFromStartTag != null) {
                    arrayList2.add(extensionFromStartTag);
                }
                arrayList = arrayList2;
            }
        }
    }

    public static CommonPacketExtension parseExtensionFromXpp(String str) throws XmlPullParserException, IOException {
        int i;
        XmlPullParser xmlPullParserNewPullParser = XmlPullParserFactory.newInstance().newPullParser();
        xmlPullParserNewPullParser.setInput(new StringReader(str));
        int eventType = xmlPullParserNewPullParser.getEventType();
        while (true) {
            i = eventType;
            if (i == 1 || i == 2) {
                break;
            }
            eventType = xmlPullParserNewPullParser.next();
        }
        if (i == 2) {
            return parseExtensionFromStartTag(xmlPullParserNewPullParser);
        }
        return null;
    }

    @Override // com.xiaomi.smack.provider.PacketExtensionProvider
    public CommonPacketExtension parseExtension(XmlPullParser xmlPullParser) throws Exception {
        int i;
        int eventType = xmlPullParser.getEventType();
        while (true) {
            i = eventType;
            if (i == 1 || i == 2) {
                break;
            }
            eventType = xmlPullParser.next();
        }
        if (i == 2) {
            return parseExtensionFromStartTag(xmlPullParser);
        }
        return null;
    }

    public void register() {
        ProviderManager.getInstance().addExtensionProvider(PushServiceConstants.EXTENSION_ELE_NAME_ALL, PushServiceConstants.XM_CHAT_NAMESPACE, this);
    }
}
