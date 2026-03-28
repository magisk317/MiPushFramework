package com.xiaomi.smack.packet;

import android.os.Bundle;
import com.xiaomi.push.service.PushConstants;
import com.xiaomi.smack.util.StringUtils;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/smack/packet/Presence.class */
public class Presence extends Packet {
    private Mode mode;
    private int priority;
    private String status;
    private Type type;

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/smack/packet/Presence$Mode.class */
    public enum Mode {
        chat,
        available,
        away,
        xa,
        dnd
    }

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/smack/packet/Presence$Type.class */
    public enum Type {
        available,
        unavailable,
        subscribe,
        subscribed,
        unsubscribe,
        unsubscribed,
        error,
        probe
    }

    public Presence(Bundle bundle) {
        super(bundle);
        this.type = Type.available;
        this.status = null;
        this.priority = Integer.MIN_VALUE;
        this.mode = null;
        if (bundle.containsKey(PushConstants.EXTRA_PRES_TYPE)) {
            this.type = Type.valueOf(bundle.getString(PushConstants.EXTRA_PRES_TYPE));
        }
        if (bundle.containsKey(PushConstants.EXTRA_PRES_STATUS)) {
            this.status = bundle.getString(PushConstants.EXTRA_PRES_STATUS);
        }
        if (bundle.containsKey(PushConstants.EXTRA_PRES_PRIORITY)) {
            this.priority = bundle.getInt(PushConstants.EXTRA_PRES_PRIORITY);
        }
        if (bundle.containsKey(PushConstants.EXTRA_PRES_MODE)) {
            this.mode = Mode.valueOf(bundle.getString(PushConstants.EXTRA_PRES_MODE));
        }
    }

    public Presence(Type type) {
        this.type = Type.available;
        this.status = null;
        this.priority = Integer.MIN_VALUE;
        this.mode = null;
        setType(type);
    }

    public Presence(Type type, String str, int i, Mode mode) {
        this.type = Type.available;
        this.status = null;
        this.priority = Integer.MIN_VALUE;
        this.mode = null;
        setType(type);
        setStatus(str);
        setPriority(i);
        setMode(mode);
    }

    public Mode getMode() {
        return this.mode;
    }

    public int getPriority() {
        return this.priority;
    }

    public String getStatus() {
        return this.status;
    }

    public Type getType() {
        return this.type;
    }

    public boolean isAvailable() {
        return this.type == Type.available;
    }

    public boolean isAway() {
        return this.type == Type.available && (this.mode == Mode.away || this.mode == Mode.xa || this.mode == Mode.dnd);
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public void setPriority(int i) {
        if (i >= -128 && i <= 128) {
            this.priority = i;
            return;
        }
        throw new IllegalArgumentException("Priority value " + i + " is not valid. Valid range is -128 through 128.");
    }

    public void setStatus(String str) {
        this.status = str;
    }

    public void setType(Type type) {
        if (type == null) {
            throw new NullPointerException("Type cannot be null");
        }
        this.type = type;
    }

    @Override // com.xiaomi.smack.packet.Packet
    public Bundle toBundle() {
        Bundle bundle = super.toBundle();
        Type type = this.type;
        if (type != null) {
            bundle.putString(PushConstants.EXTRA_PRES_TYPE, type.toString());
        }
        String str = this.status;
        if (str != null) {
            bundle.putString(PushConstants.EXTRA_PRES_STATUS, str);
        }
        int i = this.priority;
        if (i != Integer.MIN_VALUE) {
            bundle.putInt(PushConstants.EXTRA_PRES_PRIORITY, i);
        }
        Mode mode = this.mode;
        if (mode != null && mode != Mode.available) {
            bundle.putString(PushConstants.EXTRA_PRES_MODE, this.mode.toString());
        }
        return bundle;
    }

    @Override // com.xiaomi.smack.packet.Packet
    public String toXML() {
        StringBuilder sb = new StringBuilder();
        sb.append("<presence");
        if (getXmlns() != null) {
            sb.append(" xmlns=\"");
            sb.append(getXmlns());
            sb.append("\"");
        }
        if (getPacketID() != null) {
            sb.append(" id=\"");
            sb.append(getPacketID());
            sb.append("\"");
        }
        if (getTo() != null) {
            sb.append(" to=\"");
            sb.append(StringUtils.escapeForXML(getTo()));
            sb.append("\"");
        }
        if (getFrom() != null) {
            sb.append(" from=\"");
            sb.append(StringUtils.escapeForXML(getFrom()));
            sb.append("\"");
        }
        if (getChannelId() != null) {
            sb.append(" chid=\"");
            sb.append(StringUtils.escapeForXML(getChannelId()));
            sb.append("\"");
        }
        if (this.type != null) {
            sb.append(" type=\"");
            sb.append(this.type);
            sb.append("\"");
        }
        sb.append(">");
        if (this.status != null) {
            sb.append("<status>");
            sb.append(StringUtils.escapeForXML(this.status));
            sb.append("</status>");
        }
        if (this.priority != Integer.MIN_VALUE) {
            sb.append("<priority>");
            sb.append(this.priority);
            sb.append("</priority>");
        }
        Mode mode = this.mode;
        if (mode != null && mode != Mode.available) {
            sb.append("<show>");
            sb.append(this.mode);
            sb.append("</show>");
        }
        sb.append(getExtensionsXML());
        XMPPError error = getError();
        if (error != null) {
            sb.append(error.toXML());
        }
        sb.append("</presence>");
        return sb.toString();
    }
}
