package com.xiaomi.smack.packet;

import android.os.Bundle;
import android.os.Parcelable;
import android.text.TextUtils;
import com.xiaomi.mipush.sdk.Constants;
import com.xiaomi.push.service.PushConstants;
import com.xiaomi.smack.util.StringUtils;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CopyOnWriteArrayList;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/smack/packet/Packet.class */
public abstract class Packet {
    protected static final String DEFAULT_LANGUAGE = Locale.getDefault().getLanguage().toLowerCase();
    private static String DEFAULT_XML_NS = null;
    public static final String ID_NOT_AVAILABLE = "ID_NOT_AVAILABLE";
    public static final DateFormat XEP_0082_UTC_FORMAT;
    private static long id;
    private static String prefix;
    private String chId;
    private XMPPError error;
    private String from;
    private String packageName;
    private List<CommonPacketExtension> packetExtensions;
    private String packetID;
    private final Map<String, Object> properties;
    private String to;
    private String xmlns;

    static {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        XEP_0082_UTC_FORMAT = simpleDateFormat;
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        prefix = StringUtils.randomString(5) + Constants.ACCEPT_TIME_SEPARATOR_SERVER;
        id = 0L;
    }

    public Packet() {
        this.xmlns = DEFAULT_XML_NS;
        this.packetID = null;
        this.to = null;
        this.from = null;
        this.chId = null;
        this.packageName = null;
        this.packetExtensions = new CopyOnWriteArrayList();
        this.properties = new HashMap();
        this.error = null;
    }

    public Packet(Bundle bundle) {
        this.xmlns = DEFAULT_XML_NS;
        this.packetID = null;
        this.to = null;
        this.from = null;
        this.chId = null;
        this.packageName = null;
        this.packetExtensions = new CopyOnWriteArrayList();
        this.properties = new HashMap();
        this.error = null;
        this.to = bundle.getString(PushConstants.EXTRA_TO);
        this.from = bundle.getString(PushConstants.EXTRA_FROM);
        this.chId = bundle.getString(PushConstants.EXTRA_CHID);
        this.packetID = bundle.getString(PushConstants.EXTRA_PACKET_ID);
        Parcelable[] parcelableArray = bundle.getParcelableArray(PushConstants.EXTRA_EXTENSIONS);
        if (parcelableArray != null) {
            this.packetExtensions = new ArrayList(parcelableArray.length);
            for (Parcelable parcelable : parcelableArray) {
                CommonPacketExtension fromBundle = CommonPacketExtension.parseFromBundle((Bundle) parcelable);
                if (fromBundle != null) {
                    this.packetExtensions.add(fromBundle);
                }
            }
        }
        Bundle bundle2 = bundle.getBundle(PushConstants.EXTRA_ERROR);
        if (bundle2 != null) {
            this.error = new XMPPError(bundle2);
        }
    }

    public static String getDefaultLanguage() {
        return DEFAULT_LANGUAGE;
    }

    public static String nextID() {
        String string;
        synchronized (Packet.class) {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append(prefix);
                long j = id;
                id = 1 + j;
                sb.append(Long.toString(j));
                string = sb.toString();
            } catch (Throwable th) {
                throw th;
            }
        }
        return string;
    }

    public static void setDefaultXmlns(String str) {
        DEFAULT_XML_NS = str;
    }

    public void addExtension(CommonPacketExtension commonPacketExtension) {
        this.packetExtensions.add(commonPacketExtension);
    }

    public void deleteProperty(String str) {
        synchronized (this) {
            Map<String, Object> map = this.properties;
            if (map == null) {
                return;
            }
            map.remove(str);
        }
    }

    public boolean equals(Object obj) {
        boolean z = true;
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Packet packet = (Packet) obj;
        XMPPError xMPPError = this.error;
        if (xMPPError != null) {
            if (!xMPPError.equals(packet.error)) {
                return false;
            }
        } else if (packet.error != null) {
            return false;
        }
        String str = this.from;
        if (str != null) {
            if (!str.equals(packet.from)) {
                return false;
            }
        } else if (packet.from != null) {
            return false;
        }
        if (!this.packetExtensions.equals(packet.packetExtensions)) {
            return false;
        }
        String str2 = this.packetID;
        if (str2 != null) {
            if (!str2.equals(packet.packetID)) {
                return false;
            }
        } else if (packet.packetID != null) {
            return false;
        }
        String str3 = this.chId;
        if (str3 != null) {
            if (!str3.equals(packet.chId)) {
                return false;
            }
        } else if (packet.chId != null) {
            return false;
        }
        Map<String, Object> map = this.properties;
        if (map != null) {
            if (!map.equals(packet.properties)) {
                return false;
            }
        } else if (packet.properties != null) {
            return false;
        }
        String str4 = this.to;
        if (str4 != null) {
            if (!str4.equals(packet.to)) {
                return false;
            }
        } else if (packet.to != null) {
            return false;
        }
        String str5 = this.xmlns;
        if (str5 == null ? packet.xmlns != null : !str5.equals(packet.xmlns)) {
            z = false;
        }
        return z;
    }

    public String getChannelId() {
        return this.chId;
    }

    public XMPPError getError() {
        return this.error;
    }

    public CommonPacketExtension getExtension(String str) {
        return getExtension(str, null);
    }

    public CommonPacketExtension getExtension(String str, String str2) {
        for (CommonPacketExtension commonPacketExtension : this.packetExtensions) {
            if (str2 == null || str2.equals(commonPacketExtension.getNamespace())) {
                if (str.equals(commonPacketExtension.getElementName())) {
                    return commonPacketExtension;
                }
            }
        }
        return null;
    }

    public Collection<CommonPacketExtension> getExtensions() {
        synchronized (this) {
            if (this.packetExtensions == null) {
                return Collections.emptyList();
            }
            return Collections.unmodifiableList(new ArrayList(this.packetExtensions));
        }
    }

    protected String getExtensionsXML() {
        String string;
        synchronized (this) {
            StringBuilder sb = new StringBuilder();
            Iterator<CommonPacketExtension> it = getExtensions().iterator();
            while (it.hasNext()) {
                sb.append(it.next().toXML());
            }
            Map<String, Object> map = this.properties;
            if (map != null && !map.isEmpty()) {
                sb.append("<properties xmlns=\"http://www.jivesoftware.com/xmlns/xmpp/properties\">");
                for (String str : getPropertyNames()) {
                    Object property = getProperty(str);
                    sb.append("<property>");
                    sb.append("<name>");
                    sb.append(StringUtils.escapeForXML(str));
                    sb.append("</name>");
                    sb.append("<value type=\"");
                    if (property instanceof Integer) {
                        sb.append("integer\">");
                        sb.append(property);
                        sb.append("</value>");
                    } else if (property instanceof Long) {
                        sb.append("long\">");
                        sb.append(property);
                        sb.append("</value>");
                    } else if (property instanceof Float) {
                        sb.append("float\">");
                        sb.append(property);
                        sb.append("</value>");
                    } else if (property instanceof Double) {
                        sb.append("double\">");
                        sb.append(property);
                        sb.append("</value>");
                    } else if (property instanceof Boolean) {
                        sb.append("boolean\">");
                        sb.append(property);
                        sb.append("</value>");
                    } else if (property instanceof String) {
                        sb.append("string\">");
                        sb.append(StringUtils.escapeForXML((String) property));
                        sb.append("</value>");
                    } else {
                        ByteArrayOutputStream byteArrayOutputStream = null;
                        ByteArrayOutputStream byteArrayOutputStream2 = null;
                        ObjectOutputStream objectOutputStream = null;
                        ObjectOutputStream objectOutputStream2 = null;
                        try {
                            try {
                                ByteArrayOutputStream byteArrayOutputStream3 = new ByteArrayOutputStream();
                                ObjectOutputStream objectOutputStream3 = new ObjectOutputStream(byteArrayOutputStream3);
                                objectOutputStream3.writeObject(property);
                                sb.append("java-object\">");
                                sb.append(StringUtils.encodeBase64(byteArrayOutputStream3.toByteArray()));
                                sb.append("</value>");
                                try {
                                    objectOutputStream3.close();
                                } catch (Exception e) {
                                }
                                try {
                                    byteArrayOutputStream3.close();
                                } catch (Exception e2) {
                                }
                            } catch (Throwable th) {
                                if (0 != 0) {
                                    try {
                                        objectOutputStream2.close();
                                    } catch (Exception e3) {
                                    }
                                }
                                if (0 != 0) {
                                    try {
                                        byteArrayOutputStream2.close();
                                    } catch (Exception e4) {
                                    }
                                }
                                throw th;
                            }
                        } catch (Exception e5) {
                            e5.printStackTrace();
                            if (0 != 0) {
                                try {
                                    objectOutputStream.close();
                                } catch (Exception e6) {
                                }
                            }
                            sb.append("</property>");
                        }
                    }
                    sb.append("</property>");
                }
                sb.append("</properties>");
            }
            string = sb.toString();
        }
        return string;
    }

    public String getFrom() {
        return this.from;
    }

    public String getPackageName() {
        return this.packageName;
    }

    public String getPacketID() {
        if ("ID_NOT_AVAILABLE".equals(this.packetID)) {
            return null;
        }
        if (this.packetID == null) {
            this.packetID = nextID();
        }
        return this.packetID;
    }

    public Object getProperty(String str) {
        synchronized (this) {
            Map<String, Object> map = this.properties;
            if (map == null) {
                return null;
            }
            return map.get(str);
        }
    }

    public Collection<String> getPropertyNames() {
        synchronized (this) {
            if (this.properties == null) {
                return Collections.emptySet();
            }
            return Collections.unmodifiableSet(new HashSet(this.properties.keySet()));
        }
    }

    public String getTo() {
        return this.to;
    }

    public String getXmlns() {
        return this.xmlns;
    }

    public int hashCode() {
        String str = this.xmlns;
        int iHashCode = 0;
        int iHashCode2 = str != null ? str.hashCode() : 0;
        String str2 = this.packetID;
        int iHashCode3 = str2 != null ? str2.hashCode() : 0;
        String str3 = this.to;
        int iHashCode4 = str3 != null ? str3.hashCode() : 0;
        String str4 = this.from;
        int iHashCode5 = str4 != null ? str4.hashCode() : 0;
        String str5 = this.chId;
        int iHashCode6 = str5 != null ? str5.hashCode() : 0;
        int iHashCode7 = this.packetExtensions.hashCode();
        int iHashCode8 = this.properties.hashCode();
        XMPPError xMPPError = this.error;
        if (xMPPError != null) {
            iHashCode = xMPPError.hashCode();
        }
        return (((((((((((((iHashCode2 * 31) + iHashCode3) * 31) + iHashCode4) * 31) + iHashCode5) * 31) + iHashCode6) * 31) + iHashCode7) * 31) + iHashCode8) * 31) + iHashCode;
    }

    public void removeExtension(CommonPacketExtension commonPacketExtension) {
        this.packetExtensions.remove(commonPacketExtension);
    }

    public void setChannelId(String str) {
        this.chId = str;
    }

    public void setError(XMPPError xMPPError) {
        this.error = xMPPError;
    }

    public void setFrom(String str) {
        this.from = str;
    }

    public void setPackageName(String str) {
        this.packageName = str;
    }

    public void setPacketID(String str) {
        this.packetID = str;
    }

    public void setProperty(String str, Object obj) {
        synchronized (this) {
            if (!(obj instanceof Serializable)) {
                throw new IllegalArgumentException("Value must be serialiazble");
            }
            this.properties.put(str, obj);
        }
    }

    public void setTo(String str) {
        this.to = str;
    }

    public Bundle toBundle() {
        Bundle bundle = new Bundle();
        if (!TextUtils.isEmpty(this.xmlns)) {
            bundle.putString(PushConstants.EXTRA_EXTENSION_NAMESPACE, this.xmlns);
        }
        if (!TextUtils.isEmpty(this.from)) {
            bundle.putString(PushConstants.EXTRA_FROM, this.from);
        }
        if (!TextUtils.isEmpty(this.to)) {
            bundle.putString(PushConstants.EXTRA_TO, this.to);
        }
        if (!TextUtils.isEmpty(this.packetID)) {
            bundle.putString(PushConstants.EXTRA_PACKET_ID, this.packetID);
        }
        if (!TextUtils.isEmpty(this.chId)) {
            bundle.putString(PushConstants.EXTRA_CHID, this.chId);
        }
        XMPPError xMPPError = this.error;
        if (xMPPError != null) {
            bundle.putBundle(PushConstants.EXTRA_ERROR, xMPPError.toBundle());
        }
        List<CommonPacketExtension> list = this.packetExtensions;
        if (list != null) {
            Bundle[] bundleArr = new Bundle[list.size()];
            int i = 0;
            Iterator<CommonPacketExtension> it = this.packetExtensions.iterator();
            while (it.hasNext()) {
                Bundle bundle2 = it.next().toBundle();
                int i2 = i;
                if (bundle2 != null) {
                    bundleArr[i] = bundle2;
                    i2 = i + 1;
                }
                i = i2;
            }
            bundle.putParcelableArray(PushConstants.EXTRA_EXTENSIONS, bundleArr);
        }
        return bundle;
    }

    public abstract String toXML();
}
