package com.xiaomi.smack.packet;

import android.os.Bundle;
import android.text.TextUtils;
import com.xiaomi.push.service.PushConstants;
import com.xiaomi.smack.util.StringUtils;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/smack/packet/Message.class */
public class Message extends Packet {
    public static final String MSG_TYPE_CHAT = "chat";
    public static final String MSG_TYPE_ERROR = "error";
    public static final String MSG_TYPE_GROUPCHAT = "groupchat";
    public static final String MSG_TYPE_HEADLINE = "hearline";
    public static final String MSG_TYPE_NORMAL = "normal";
    public static final String MSG_TYPE_PPL = "ppl";
    private String fseq;
    private String language;
    private String mAppId;
    private String mBody;
    private String mBodyEncoding;
    private boolean mEncrypted;
    private String mSubject;
    private boolean mTransient;
    private String mseq;
    private String seq;
    private String status;
    private String thread;
    private String type;

    public Message() {
        this.type = null;
        this.thread = null;
        this.mTransient = false;
        this.seq = "";
        this.mseq = "";
        this.fseq = "";
        this.status = "";
        this.mEncrypted = false;
    }

    public Message(Bundle bundle) {
        super(bundle);
        this.type = null;
        this.thread = null;
        this.mTransient = false;
        this.seq = "";
        this.mseq = "";
        this.fseq = "";
        this.status = "";
        this.mEncrypted = false;
        this.type = bundle.getString(PushConstants.EXTRA_MESSAGE_TYPE);
        this.language = bundle.getString(PushConstants.EXTRA_MESSAGE_LANGUAGE);
        this.thread = bundle.getString(PushConstants.EXTRA_MESSAGE_THREAD);
        this.mSubject = bundle.getString(PushConstants.EXTRA_MESSAGE_SUBJECT);
        this.mBody = bundle.getString(PushConstants.EXTRA_MESSAGE_BODY);
        this.mBodyEncoding = bundle.getString(PushConstants.EXTRA_BODY_ENCODE);
        this.mAppId = bundle.getString(PushConstants.EXTRA_MESSAGE_APPID);
        this.mTransient = bundle.getBoolean(PushConstants.EXTRA_MESSAGE_TRANSIENT, false);
        this.mEncrypted = bundle.getBoolean(PushConstants.EXTRA_MESSAGE_ENCRYPT, false);
        this.seq = bundle.getString(PushConstants.EXTRA_MESSAGE_SEQ);
        this.mseq = bundle.getString(PushConstants.EXTRA_MESSAGE_MSEQ);
        this.fseq = bundle.getString(PushConstants.EXTRA_MESSAGE_FSEQ);
        this.status = bundle.getString(PushConstants.EXTRA_MESSAGE_STATUS);
    }

    public Message(String str) {
        this.type = null;
        this.thread = null;
        this.mTransient = false;
        this.seq = "";
        this.mseq = "";
        this.fseq = "";
        this.status = "";
        this.mEncrypted = false;
        setTo(str);
    }

    public Message(String str, String str2) {
        this.type = null;
        this.thread = null;
        this.mTransient = false;
        this.seq = "";
        this.mseq = "";
        this.fseq = "";
        this.status = "";
        this.mEncrypted = false;
        setTo(str);
        this.type = str2;
    }

    @Override // com.xiaomi.smack.packet.Packet
    public boolean equals(Object obj) {
        boolean z = true;
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Message message = (Message) obj;
        if (!super.equals(message)) {
            return false;
        }
        String str = this.mBody;
        if (str != null) {
            if (!str.equals(message.mBody)) {
                return false;
            }
        } else if (message.mBody != null) {
            return false;
        }
        String str2 = this.language;
        if (str2 != null) {
            if (!str2.equals(message.language)) {
                return false;
            }
        } else if (message.language != null) {
            return false;
        }
        String str3 = this.mSubject;
        if (str3 != null) {
            if (!str3.equals(message.mSubject)) {
                return false;
            }
        } else if (message.mSubject != null) {
            return false;
        }
        String str4 = this.thread;
        if (str4 != null) {
            if (!str4.equals(message.thread)) {
                return false;
            }
        } else if (message.thread != null) {
            return false;
        }
        if (this.type != message.type) {
            z = false;
        }
        return z;
    }

    public String getAppId() {
        return this.mAppId;
    }

    public String getBody() {
        return this.mBody;
    }

    public String getBodyEncoding() {
        return this.mBodyEncoding;
    }

    public boolean getEncrypted() {
        return this.mEncrypted;
    }

    public String getFSeq() {
        return this.fseq;
    }

    public String getLanguage() {
        return this.language;
    }

    public String getMSeq() {
        return this.mseq;
    }

    public String getSeq() {
        return this.seq;
    }

    public String getStatus() {
        return this.status;
    }

    public String getSubject() {
        return this.mSubject;
    }

    public String getThread() {
        return this.thread;
    }

    public String getType() {
        return this.type;
    }

    @Override // com.xiaomi.smack.packet.Packet
    public int hashCode() {
        String str = this.type;
        int iHashCode = 0;
        int iHashCode2 = str != null ? str.hashCode() : 0;
        String str2 = this.mBody;
        int iHashCode3 = str2 != null ? str2.hashCode() : 0;
        String str3 = this.thread;
        int iHashCode4 = str3 != null ? str3.hashCode() : 0;
        String str4 = this.language;
        int iHashCode5 = str4 != null ? str4.hashCode() : 0;
        String str5 = this.mSubject;
        if (str5 != null) {
            iHashCode = str5.hashCode();
        }
        return (((((((iHashCode2 * 31) + iHashCode3) * 31) + iHashCode4) * 31) + iHashCode5) * 31) + iHashCode;
    }

    public void setAppId(String str) {
        this.mAppId = str;
    }

    public void setBody(String str) {
        this.mBody = str;
    }

    public void setBody(String str, String str2) {
        this.mBody = str;
        this.mBodyEncoding = str2;
    }

    public void setEncrypted(boolean z) {
        this.mEncrypted = z;
    }

    public void setFSeq(String str) {
        this.fseq = str;
    }

    public void setIsTransient(boolean z) {
        this.mTransient = z;
    }

    public void setLanguage(String str) {
        this.language = str;
    }

    public void setMSeq(String str) {
        this.mseq = str;
    }

    public void setSeq(String str) {
        this.seq = str;
    }

    public void setStatus(String str) {
        this.status = str;
    }

    public void setSubject(String str) {
        this.mSubject = str;
    }

    public void setThread(String str) {
        this.thread = str;
    }

    public void setType(String str) {
        this.type = str;
    }

    @Override // com.xiaomi.smack.packet.Packet
    public Bundle toBundle() {
        Bundle bundle = super.toBundle();
        if (!TextUtils.isEmpty(this.type)) {
            bundle.putString(PushConstants.EXTRA_MESSAGE_TYPE, this.type);
        }
        String str = this.language;
        if (str != null) {
            bundle.putString(PushConstants.EXTRA_MESSAGE_LANGUAGE, str);
        }
        String str2 = this.mSubject;
        if (str2 != null) {
            bundle.putString(PushConstants.EXTRA_MESSAGE_SUBJECT, str2);
        }
        String str3 = this.mBody;
        if (str3 != null) {
            bundle.putString(PushConstants.EXTRA_MESSAGE_BODY, str3);
        }
        if (!TextUtils.isEmpty(this.mBodyEncoding)) {
            bundle.putString(PushConstants.EXTRA_BODY_ENCODE, this.mBodyEncoding);
        }
        String str4 = this.thread;
        if (str4 != null) {
            bundle.putString(PushConstants.EXTRA_MESSAGE_THREAD, str4);
        }
        String str5 = this.mAppId;
        if (str5 != null) {
            bundle.putString(PushConstants.EXTRA_MESSAGE_APPID, str5);
        }
        if (this.mTransient) {
            bundle.putBoolean(PushConstants.EXTRA_MESSAGE_TRANSIENT, true);
        }
        if (!TextUtils.isEmpty(this.seq)) {
            bundle.putString(PushConstants.EXTRA_MESSAGE_SEQ, this.seq);
        }
        if (!TextUtils.isEmpty(this.mseq)) {
            bundle.putString(PushConstants.EXTRA_MESSAGE_MSEQ, this.mseq);
        }
        if (!TextUtils.isEmpty(this.fseq)) {
            bundle.putString(PushConstants.EXTRA_MESSAGE_FSEQ, this.fseq);
        }
        if (this.mEncrypted) {
            bundle.putBoolean(PushConstants.EXTRA_MESSAGE_ENCRYPT, true);
        }
        if (!TextUtils.isEmpty(this.status)) {
            bundle.putString(PushConstants.EXTRA_MESSAGE_STATUS, this.status);
        }
        return bundle;
    }

    @Override // com.xiaomi.smack.packet.Packet
    public String toXML() {
        XMPPError error;
        StringBuilder sb = new StringBuilder();
        sb.append("<message");
        if (getXmlns() != null) {
            sb.append(" xmlns=\"");
            sb.append(getXmlns());
            sb.append("\"");
        }
        if (this.language != null) {
            sb.append(" xml:lang=\"");
            sb.append(getLanguage());
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
        if (!TextUtils.isEmpty(getSeq())) {
            sb.append(" seq=\"");
            sb.append(getSeq());
            sb.append("\"");
        }
        if (!TextUtils.isEmpty(getMSeq())) {
            sb.append(" mseq=\"");
            sb.append(getMSeq());
            sb.append("\"");
        }
        if (!TextUtils.isEmpty(getFSeq())) {
            sb.append(" fseq=\"");
            sb.append(getFSeq());
            sb.append("\"");
        }
        if (!TextUtils.isEmpty(getStatus())) {
            sb.append(" status=\"");
            sb.append(getStatus());
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
        if (this.mTransient) {
            sb.append(" transient=\"true\"");
        }
        if (!TextUtils.isEmpty(this.mAppId)) {
            sb.append(" appid=\"");
            sb.append(getAppId());
            sb.append("\"");
        }
        if (!TextUtils.isEmpty(this.type)) {
            sb.append(" type=\"");
            sb.append(this.type);
            sb.append("\"");
        }
        if (this.mEncrypted) {
            sb.append(" s=\"1\"");
        }
        sb.append(">");
        if (this.mSubject != null) {
            sb.append("<subject>");
            sb.append(StringUtils.escapeForXML(this.mSubject));
            sb.append("</subject>");
        }
        if (this.mBody != null) {
            sb.append("<body");
            if (!TextUtils.isEmpty(this.mBodyEncoding)) {
                sb.append(" encode=\"");
                sb.append(this.mBodyEncoding);
                sb.append("\"");
            }
            sb.append(">");
            sb.append(StringUtils.escapeForXML(this.mBody));
            sb.append("</body>");
        }
        if (this.thread != null) {
            sb.append("<thread>");
            sb.append(this.thread);
            sb.append("</thread>");
        }
        if (MSG_TYPE_ERROR.equalsIgnoreCase(this.type) && (error = getError()) != null) {
            sb.append(error.toXML());
        }
        sb.append(getExtensionsXML());
        sb.append("</message>");
        return sb.toString();
    }
}
