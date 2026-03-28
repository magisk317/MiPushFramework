package com.xiaomi.smack.packet;

import android.os.Bundle;
import com.xiaomi.push.service.PushConstants;
import com.xiaomi.smack.util.StringUtils;
import java.util.HashMap;
import java.util.Map;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/smack/packet/IQ.class */
public class IQ extends Packet {
    private final Map<String, String> attributes;
    private Type type;

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/smack/packet/IQ$Type.class */
    public static class Type {
        private String value;
        public static final Type GET = new Type("get");
        public static final Type SET = new Type("set");
        public static final Type RESULT = new Type("result");
        public static final Type ERROR = new Type(Message.MSG_TYPE_ERROR);
        public static final Type COMMAND = new Type("command");

        private Type(String str) {
            this.value = str;
        }

        public static Type fromString(String str) {
            if (str == null) {
                return null;
            }
            String lowerCase = str.toLowerCase();
            Type type = GET;
            if (type.toString().equals(lowerCase)) {
                return type;
            }
            Type type2 = SET;
            if (type2.toString().equals(lowerCase)) {
                return type2;
            }
            Type type3 = ERROR;
            if (type3.toString().equals(lowerCase)) {
                return type3;
            }
            Type type4 = RESULT;
            if (type4.toString().equals(lowerCase)) {
                return type4;
            }
            Type type5 = COMMAND;
            if (type5.toString().equals(lowerCase)) {
                return type5;
            }
            return null;
        }

        public String toString() {
            return this.value;
        }
    }

    public IQ() {
        this.type = Type.GET;
        this.attributes = new HashMap();
    }

    public IQ(Bundle bundle) {
        super(bundle);
        this.type = Type.GET;
        this.attributes = new HashMap();
        if (bundle.containsKey(PushConstants.EXTRA_IQ_TYPE)) {
            this.type = Type.fromString(bundle.getString(PushConstants.EXTRA_IQ_TYPE));
        }
    }

    public static IQ createErrorResponse(IQ iq, XMPPError xMPPError) {
        if (iq.getType() != Type.GET && iq.getType() != Type.SET) {
            throw new IllegalArgumentException("IQ must be of type 'set' or 'get'. Original IQ: " + iq.toXML());
        }
        IQ iq2 = new IQ() { // from class: com.xiaomi.smack.packet.IQ.2
            @Override // com.xiaomi.smack.packet.IQ
            public String getChildElementXML() {
                return iq.getChildElementXML();
            }
        };
        iq2.setType(Type.ERROR);
        iq2.setPacketID(iq.getPacketID());
        iq2.setFrom(iq.getTo());
        iq2.setTo(iq.getFrom());
        iq2.setError(xMPPError);
        return iq2;
    }

    public static IQ createResultIQ(IQ iq) {
        if (iq.getType() != Type.GET && iq.getType() != Type.SET) {
            throw new IllegalArgumentException("IQ must be of type 'set' or 'get'. Original IQ: " + iq.toXML());
        }
        IQ iq2 = new IQ() { // from class: com.xiaomi.smack.packet.IQ.1
            @Override // com.xiaomi.smack.packet.IQ
            public String getChildElementXML() {
                return null;
            }
        };
        iq2.setType(Type.RESULT);
        iq2.setPacketID(iq.getPacketID());
        iq2.setFrom(iq.getTo());
        iq2.setTo(iq.getFrom());
        return iq2;
    }

    public String getAttribute(String str) {
        String str2;
        synchronized (this) {
            str2 = this.attributes.get(str);
        }
        return str2;
    }

    public String getChildElementXML() {
        return null;
    }

    public Type getType() {
        return this.type;
    }

    public void setAttribute(String str, String str2) {
        synchronized (this) {
            this.attributes.put(str, str2);
        }
    }

    public void setAttributes(Map<String, String> map) {
        synchronized (this) {
            this.attributes.putAll(map);
        }
    }

    public void setType(Type type) {
        if (type == null) {
            this.type = Type.GET;
        } else {
            this.type = type;
        }
    }

    @Override // com.xiaomi.smack.packet.Packet
    public Bundle toBundle() {
        Bundle bundle = super.toBundle();
        Type type = this.type;
        if (type != null) {
            bundle.putString(PushConstants.EXTRA_IQ_TYPE, type.toString());
        }
        return bundle;
    }

    @Override // com.xiaomi.smack.packet.Packet
    public String toXML() {
        StringBuilder sb = new StringBuilder();
        sb.append("<iq ");
        if (getPacketID() != null) {
            sb.append("id=\"" + getPacketID() + "\" ");
        }
        if (getTo() != null) {
            sb.append("to=\"");
            sb.append(StringUtils.escapeForXML(getTo()));
            sb.append("\" ");
        }
        if (getFrom() != null) {
            sb.append("from=\"");
            sb.append(StringUtils.escapeForXML(getFrom()));
            sb.append("\" ");
        }
        if (getChannelId() != null) {
            sb.append("chid=\"");
            sb.append(StringUtils.escapeForXML(getChannelId()));
            sb.append("\" ");
        }
        for (Map.Entry<String, String> entry : this.attributes.entrySet()) {
            sb.append(StringUtils.escapeForXML(entry.getKey()));
            sb.append("=\"");
            sb.append(StringUtils.escapeForXML(entry.getValue()));
            sb.append("\" ");
        }
        if (this.type == null) {
            sb.append("type=\"get\">");
        } else {
            sb.append("type=\"");
            sb.append(getType());
            sb.append("\">");
        }
        String childElementXML = getChildElementXML();
        if (childElementXML != null) {
            sb.append(childElementXML);
        }
        sb.append(getExtensionsXML());
        XMPPError error = getError();
        if (error != null) {
            sb.append(error.toXML());
        }
        sb.append("</iq>");
        return sb.toString();
    }
}
