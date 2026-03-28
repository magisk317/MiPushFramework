package com.xiaomi.smack.provider;

import com.xiaomi.smack.packet.IQ;
import com.xiaomi.smack.packet.PacketExtension;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/smack/provider/ProviderManager.class */
public class ProviderManager {
    private static ProviderManager instance;
    private Map<String, Object> extensionProviders = new ConcurrentHashMap();
    private Map<String, Object> iqProviders = new ConcurrentHashMap();

    private ProviderManager() throws Throwable {
        initialize();
    }

    private ClassLoader[] getClassLoaders() {
        ClassLoader[] classLoaderArr = {ProviderManager.class.getClassLoader(), Thread.currentThread().getContextClassLoader()};
        ArrayList arrayList = new ArrayList();
        for (ClassLoader classLoader : classLoaderArr) {
            if (classLoader != null) {
                arrayList.add(classLoader);
            }
        }
        return (ClassLoader[]) arrayList.toArray(new ClassLoader[arrayList.size()]);
    }

    public static ProviderManager getInstance() {
        ProviderManager providerManager;
        synchronized (ProviderManager.class) {
            try {
                if (instance == null) {
                    instance = new ProviderManager();
                }
                providerManager = instance;
            } catch (Throwable th) {
                throw new RuntimeException(th);
            }
        }
        return providerManager;
    }

    private String getProviderKey(String str, String str2) {
        StringBuilder sb = new StringBuilder();
        sb.append("<");
        sb.append(str);
        sb.append("/>");
        if (str != null) {
            sb.append("<");
            sb.append(str2);
            sb.append("/>");
        }
        return sb.toString();
    }

    public static void setInstance(ProviderManager providerManager) {
        synchronized (ProviderManager.class) {
            try {
                if (instance != null) {
                    throw new IllegalStateException("ProviderManager singleton already set");
                }
                instance = providerManager;
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    public void addExtensionProvider(String str, String str2, Object obj) {
        if (!(obj instanceof PacketExtensionProvider) && !(obj instanceof Class)) {
            throw new IllegalArgumentException("Provider must be a PacketExtensionProvider or a Class instance.");
        }
        this.extensionProviders.put(getProviderKey(str, str2), obj);
    }

    public void addIQProvider(String str, String str2, Object obj) {
        if (!(obj instanceof IQProvider) && (!(obj instanceof Class) || !IQ.class.isAssignableFrom((Class) obj))) {
            throw new IllegalArgumentException("Provider must be an IQProvider or a Class instance.");
        }
        this.iqProviders.put(getProviderKey(str, str2), obj);
    }

    public Object getExtensionProvider(String str, String str2) {
        return this.extensionProviders.get(getProviderKey(str, str2));
    }

    public Collection<Object> getExtensionProviders() {
        return Collections.unmodifiableCollection(this.extensionProviders.values());
    }

    public Object getIQProvider(String str, String str2) {
        return this.iqProviders.get(getProviderKey(str, str2));
    }

    public Collection<Object> getIQProviders() {
        return Collections.unmodifiableCollection(this.iqProviders.values());
    }

    protected void initialize() throws Throwable {
        try {
            for (ClassLoader classLoader : getClassLoaders()) {
                Enumeration<URL> resources = classLoader.getResources("META-INF/smack.providers");
                while (resources.hasMoreElements()) {
                    InputStream inputStream = null;
                    try {
                        InputStream inputStreamOpenStream = resources.nextElement().openStream();
                        XmlPullParser xmlPullParserNewPullParser = XmlPullParserFactory.newInstance().newPullParser();
                        xmlPullParserNewPullParser.setFeature("http://xmlpull.org/v1/doc/features.html#process-namespaces", true);
                        xmlPullParserNewPullParser.setInput(inputStreamOpenStream, "UTF-8");
                        int eventType = xmlPullParserNewPullParser.getEventType();
                        do {
                            if (eventType == 2) {
                                if (xmlPullParserNewPullParser.getName().equals("iqProvider")) {
                                    xmlPullParserNewPullParser.next();
                                    xmlPullParserNewPullParser.next();
                                    String strNextText = xmlPullParserNewPullParser.nextText();
                                    xmlPullParserNewPullParser.next();
                                    xmlPullParserNewPullParser.next();
                                    String strNextText2 = xmlPullParserNewPullParser.nextText();
                                    xmlPullParserNewPullParser.next();
                                    xmlPullParserNewPullParser.next();
                                    String strNextText3 = xmlPullParserNewPullParser.nextText();
                                    String providerKey = getProviderKey(strNextText, strNextText2);
                                    if (!this.iqProviders.containsKey(providerKey)) {
                                        try {
                                            Class<?> cls = Class.forName(strNextText3);
                                            if (IQProvider.class.isAssignableFrom(cls)) {
                                                try {
                                                    try {
                                                        this.iqProviders.put(providerKey, cls.newInstance());
                                                    } catch (Throwable th) {
                                                        th = th;
                                                        inputStream = inputStreamOpenStream;
                                                        try {
                                                            inputStream.close();
                                                        } catch (Exception e) {
                                                        }
                                                        throw th;
                                                    }
                                                } catch (ClassNotFoundException e2) {
                                                    e2.printStackTrace();
                                                }
                                            } else if (IQ.class.isAssignableFrom(cls)) {
                                                this.iqProviders.put(providerKey, cls);
                                            }
                                        } catch (ClassNotFoundException e3) {
                                            e3.printStackTrace();
                                        }
                                    }
                                } else if (xmlPullParserNewPullParser.getName().equals("extensionProvider")) {
                                    xmlPullParserNewPullParser.next();
                                    xmlPullParserNewPullParser.next();
                                    String strNextText4 = xmlPullParserNewPullParser.nextText();
                                    xmlPullParserNewPullParser.next();
                                    xmlPullParserNewPullParser.next();
                                    String strNextText5 = xmlPullParserNewPullParser.nextText();
                                    xmlPullParserNewPullParser.next();
                                    xmlPullParserNewPullParser.next();
                                    String strNextText6 = xmlPullParserNewPullParser.nextText();
                                    String providerKey2 = getProviderKey(strNextText4, strNextText5);
                                    if (!this.extensionProviders.containsKey(providerKey2)) {
                                        try {
                                            Class<?> cls2 = Class.forName(strNextText6);
                                            if (PacketExtensionProvider.class.isAssignableFrom(cls2)) {
                                                this.extensionProviders.put(providerKey2, cls2.newInstance());
                                            } else if (PacketExtension.class.isAssignableFrom(cls2)) {
                                                this.extensionProviders.put(providerKey2, cls2);
                                            }
                                        } catch (ClassNotFoundException e4) {
                                            e4.printStackTrace();
                                        }
                                    }
                                }
                            }
                            eventType = xmlPullParserNewPullParser.next();
                        } while (eventType != 1);
                        inputStreamOpenStream.close();
                    } catch (Throwable th2) {
                        throw th2;
                    }
                }
            }
        } catch (Exception e5) {
            e5.printStackTrace();
        }
    }

    public void removeExtensionProvider(String str, String str2) {
        this.extensionProviders.remove(getProviderKey(str, str2));
    }

    public void removeIQProvider(String str, String str2) {
        this.iqProviders.remove(getProviderKey(str, str2));
    }
}
