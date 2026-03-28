package com.xiaomi.smack;

import com.xiaomi.mipush.sdk.OperatePushHelper;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/smack/SmackConfiguration.class */
public final class SmackConfiguration {
    private static final String SMACK_VERSION = "3.1.0";
    private static int keepAliveInterval;
    private static int packetReplyTimeout;
    private static int pingInterval = 600000;
    private static int serverShutdownTimeout = 330000;
    private static Vector<String> defaultMechs = new Vector<>();

    static {
        int next;
        packetReplyTimeout = OperatePushHelper.TIME_OUT;
        keepAliveInterval = 330000;
        try {
            for (ClassLoader classLoader : getClassLoaders()) {
                Enumeration<URL> resources = classLoader.getResources("META-INF/smack-config.xml");
                while (resources.hasMoreElements()) {
                    URL urlNextElement = resources.nextElement();
                    InputStream inputStream = null;
                    InputStream inputStream2 = null;
                    try {
                        try {
                            InputStream inputStreamOpenStream = urlNextElement.openStream();
                            XmlPullParser xmlPullParserNewPullParser = XmlPullParserFactory.newInstance().newPullParser();
                            xmlPullParserNewPullParser.setFeature("http://xmlpull.org/v1/doc/features.html#process-namespaces", true);
                            xmlPullParserNewPullParser.setInput(inputStreamOpenStream, "UTF-8");
                            int eventType = xmlPullParserNewPullParser.getEventType();
                            do {
                                if (eventType == 2) {
                                    if (xmlPullParserNewPullParser.getName().equals("className")) {
                                        parseClassToLoad(xmlPullParserNewPullParser);
                                    } else if (xmlPullParserNewPullParser.getName().equals("packetReplyTimeout")) {
                                        packetReplyTimeout = parseIntProperty(xmlPullParserNewPullParser, packetReplyTimeout);
                                    } else if (xmlPullParserNewPullParser.getName().equals("keepAliveInterval")) {
                                        keepAliveInterval = parseIntProperty(xmlPullParserNewPullParser, keepAliveInterval);
                                    } else if (xmlPullParserNewPullParser.getName().equals("mechName")) {
                                        defaultMechs.add(xmlPullParserNewPullParser.nextText());
                                    }
                                }
                                next = xmlPullParserNewPullParser.next();
                                eventType = next;
                            } while (next != 1);
                            inputStreamOpenStream.close();
                        } catch (Throwable th) {
                            try {
                                inputStream2.close();
                            } catch (Exception e) {
                            }
                            throw th;
                        }
                    } catch (Exception e2) {
                        e2.printStackTrace();
                        inputStream.close();
                    }
                }
            }
        } catch (Exception e3) {
            e3.printStackTrace();
        }
    }

    private SmackConfiguration() {
    }

    public static void addSaslMech(String str) {
        if (defaultMechs.contains(str)) {
            return;
        }
        defaultMechs.add(str);
    }

    public static void addSaslMechs(Collection<String> collection) {
        Iterator<String> it = collection.iterator();
        while (it.hasNext()) {
            addSaslMech(it.next());
        }
    }

    public static int getCheckAliveInterval() {
        return keepAliveInterval;
    }

    private static ClassLoader[] getClassLoaders() {
        ClassLoader[] classLoaderArr = {SmackConfiguration.class.getClassLoader(), Thread.currentThread().getContextClassLoader()};
        ArrayList arrayList = new ArrayList();
        for (ClassLoader classLoader : classLoaderArr) {
            if (classLoader != null) {
                arrayList.add(classLoader);
            }
        }
        return (ClassLoader[]) arrayList.toArray(new ClassLoader[arrayList.size()]);
    }

    public static int getPacketReplyTimeout() {
        if (packetReplyTimeout <= 0) {
            packetReplyTimeout = OperatePushHelper.TIME_OUT;
        }
        return packetReplyTimeout;
    }

    public static int getPingInteval() {
        return pingInterval;
    }

    public static List<String> getSaslMechs() {
        return defaultMechs;
    }

    public static int getServerShutdownTimeOut() {
        return serverShutdownTimeout;
    }

    public static String getVersion() {
        return SMACK_VERSION;
    }

    private static void parseClassToLoad(XmlPullParser xmlPullParser) throws Exception {
        String strNextText = xmlPullParser.nextText();
        try {
            Class.forName(strNextText);
        } catch (ClassNotFoundException e) {
            System.err.println("Error! A startup class specified in smack-config.xml could not be loaded: " + strNextText);
        }
    }

    private static int parseIntProperty(XmlPullParser xmlPullParser, int i) throws Exception {
        try {
            return Integer.parseInt(xmlPullParser.nextText());
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return i;
        }
    }

    public static void removeSaslMech(String str) {
        if (defaultMechs.contains(str)) {
            defaultMechs.remove(str);
        }
    }

    public static void removeSaslMechs(Collection<String> collection) {
        Iterator<String> it = collection.iterator();
        while (it.hasNext()) {
            removeSaslMech(it.next());
        }
    }

    public static void setKeepAliveInterval(int i) {
        keepAliveInterval = i;
    }

    public static void setPacketReplyTimeout(int i) {
        if (i <= 0) {
            throw new IllegalArgumentException();
        }
        packetReplyTimeout = i;
    }
}
