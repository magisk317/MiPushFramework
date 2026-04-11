package com.xiaomi.smack.packet;

import android.os.Bundle;
import android.os.Parcelable;
import androidx.core.os.BundleCompat;
import com.xiaomi.push.mpcd.Constants;
import com.xiaomi.push.service.PushConstants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/smack/packet/XMPPError.class */
public class XMPPError {
    private List<CommonPacketExtension> applicationExtensions;
    private int code;
    private String condition;
    private String message;
    private String reason;
    private String type;

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/smack/packet/XMPPError$Condition.class */
    public static class Condition {
        private String value;
        public static final Condition interna_server_error = new Condition("internal-server-error");
        public static final Condition forbidden = new Condition("forbidden");
        public static final Condition bad_request = new Condition("bad-request");
        public static final Condition conflict = new Condition("conflict");
        public static final Condition feature_not_implemented = new Condition("feature-not-implemented");
        public static final Condition gone = new Condition("gone");
        public static final Condition item_not_found = new Condition("item-not-found");
        public static final Condition jid_malformed = new Condition("jid-malformed");
        public static final Condition no_acceptable = new Condition("not-acceptable");
        public static final Condition not_allowed = new Condition("not-allowed");
        public static final Condition not_authorized = new Condition("not-authorized");
        public static final Condition payment_required = new Condition("payment-required");
        public static final Condition recipient_unavailable = new Condition("recipient-unavailable");
        public static final Condition redirect = new Condition("redirect");
        public static final Condition registration_required = new Condition("registration-required");
        public static final Condition remote_server_error = new Condition("remote-server-error");
        public static final Condition remote_server_not_found = new Condition("remote-server-not-found");
        public static final Condition remote_server_timeout = new Condition("remote-server-timeout");
        public static final Condition resource_constraint = new Condition("resource-constraint");
        public static final Condition service_unavailable = new Condition("service-unavailable");
        public static final Condition subscription_required = new Condition("subscription-required");
        public static final Condition undefined_condition = new Condition("undefined-condition");
        public static final Condition unexpected_request = new Condition("unexpected-request");
        public static final Condition request_timeout = new Condition("request-timeout");

        public Condition(String str) {
            this.value = str;
        }

        public String toString() {
            return this.value;
        }
    }

    public XMPPError(int i) {
        this.applicationExtensions = null;
        this.code = i;
        this.message = null;
    }

    public XMPPError(int i, String str) {
        this.applicationExtensions = null;
        this.code = i;
        this.message = str;
    }

    public XMPPError(int i, String str, String str2, String str3, String str4, List<CommonPacketExtension> list) {
        this.applicationExtensions = null;
        this.code = i;
        this.type = str;
        this.reason = str2;
        this.condition = str3;
        this.message = str4;
        this.applicationExtensions = list;
    }

    public XMPPError(Bundle bundle) {
        this.applicationExtensions = null;
        this.code = bundle.getInt(PushConstants.EXTRA_ERROR_CODE);
        if (bundle.containsKey(PushConstants.EXTRA_ERROR_TYPE)) {
            this.type = bundle.getString(PushConstants.EXTRA_ERROR_TYPE);
        }
        this.condition = bundle.getString(PushConstants.EXTRA_ERROR_CONDITION);
        this.reason = bundle.getString(PushConstants.EXTRA_ERROR_REASON);
        this.message = bundle.getString(PushConstants.EXTRA_ERROR_MESSAGE);
        Parcelable[] parcelableArray = BundleCompat.getParcelableArray(bundle, PushConstants.EXTRA_EXTENSIONS, Parcelable.class);
        if (parcelableArray != null) {
            this.applicationExtensions = new ArrayList<>(parcelableArray.length);
            for (Parcelable parcelable : parcelableArray) {
                CommonPacketExtension fromBundle = CommonPacketExtension.parseFromBundle((Bundle) parcelable);
                if (fromBundle != null) {
                    this.applicationExtensions.add(fromBundle);
                }
            }
        }
    }

    public XMPPError(Condition condition) {
        this.applicationExtensions = null;
        init(condition);
        this.message = null;
    }

    public XMPPError(Condition condition, String str) {
        this.applicationExtensions = null;
        init(condition);
        this.message = str;
    }

    private void init(Condition condition) {
        this.condition = condition.value;
    }

    public void addExtension(CommonPacketExtension commonPacketExtension) {
        synchronized (this) {
            if (this.applicationExtensions == null) {
                this.applicationExtensions = new ArrayList<>();
            }
            this.applicationExtensions.add(commonPacketExtension);
        }
    }

    public int getCode() {
        return this.code;
    }

    public String getCondition() {
        return this.condition;
    }

    public PacketExtension getExtension(String str, String str2) {
        synchronized (this) {
            List<CommonPacketExtension> list = this.applicationExtensions;
            if (list == null || str == null || str2 == null) {
                return null;
            }
            for (CommonPacketExtension commonPacketExtension : list) {
                if (str.equals(commonPacketExtension.getElementName()) && str2.equals(commonPacketExtension.getNamespace())) {
                    return commonPacketExtension;
                }
            }
            return null;
        }
    }

    public List<CommonPacketExtension> getExtensions() {
        synchronized (this) {
            List<CommonPacketExtension> list = this.applicationExtensions;
            if (list == null) {
                return Collections.emptyList();
            }
            return Collections.unmodifiableList(list);
        }
    }

    public String getMessage() {
        return this.message;
    }

    public String getReason() {
        return this.reason;
    }

    public String getType() {
        return this.type;
    }

    public void setExtension(List<CommonPacketExtension> list) {
        synchronized (this) {
            this.applicationExtensions = list;
        }
    }

    public Bundle toBundle() {
        Bundle bundle = new Bundle();
        String str = this.type;
        if (str != null) {
            bundle.putString(PushConstants.EXTRA_ERROR_TYPE, str);
        }
        bundle.putInt(PushConstants.EXTRA_ERROR_CODE, this.code);
        String str2 = this.reason;
        if (str2 != null) {
            bundle.putString(PushConstants.EXTRA_ERROR_REASON, str2);
        }
        String str3 = this.condition;
        if (str3 != null) {
            bundle.putString(PushConstants.EXTRA_ERROR_CONDITION, str3);
        }
        String str4 = this.message;
        if (str4 != null) {
            bundle.putString(PushConstants.EXTRA_ERROR_MESSAGE, str4);
        }
        List<CommonPacketExtension> list = this.applicationExtensions;
        if (list != null) {
            Bundle[] bundleArr = new Bundle[list.size()];
            int i = 0;
            Iterator<CommonPacketExtension> it = this.applicationExtensions.iterator();
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

    public String toString() {
        StringBuilder sb = new StringBuilder();
        String str = this.condition;
        if (str != null) {
            sb.append(str);
        }
        sb.append(Constants.SEPARATOR_LEFT_PARENTESIS);
        sb.append(this.code);
        sb.append(Constants.SEPARATOR_RIGHT_PARENTESIS);
        if (this.message != null) {
            sb.append(" ");
            sb.append(this.message);
        }
        return sb.toString();
    }

    public String toXML() {
        StringBuilder sb = new StringBuilder();
        sb.append("<error code=\"");
        sb.append(this.code);
        sb.append("\"");
        if (this.type != null) {
            sb.append(" type=\"");
            sb.append(this.type);
            sb.append("\"");
        }
        if (this.reason != null) {
            sb.append(" reason=\"");
            sb.append(this.reason);
            sb.append("\"");
        }
        sb.append(">");
        if (this.condition != null) {
            sb.append("<");
            sb.append(this.condition);
            sb.append(" xmlns=\"urn:ietf:params:xml:ns:xmpp-stanzas\"/>");
        }
        if (this.message != null) {
            sb.append("<text xml:lang=\"en\" xmlns=\"urn:ietf:params:xml:ns:xmpp-stanzas\">");
            sb.append(this.message);
            sb.append("</text>");
        }
        Iterator<CommonPacketExtension> it = getExtensions().iterator();
        while (it.hasNext()) {
            sb.append(it.next().toXML());
        }
        sb.append("</error>");
        return sb.toString();
    }
}
