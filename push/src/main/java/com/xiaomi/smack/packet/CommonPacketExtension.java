package com.xiaomi.smack.packet;

import android.os.Bundle;
import android.os.Parcelable;
import android.text.TextUtils;
import com.xiaomi.push.service.PushConstants;
import com.xiaomi.smack.util.StringUtils;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/smack/packet/CommonPacketExtension.class */
public class CommonPacketExtension implements PacketExtension {
    public static final String ATTRIBUTE_NAME = "attributes";
    public static final String CHILDREN_NAME = "children";
    private String[] mAttributeNames;
    private String[] mAttributeValues;
    private List<CommonPacketExtension> mChildrenEles;
    private String mExtensionElementName;
    private String mNamespace;
    private String mText;

    public CommonPacketExtension(String str, String str2, String str3, String str4) {
        this.mAttributeNames = null;
        this.mAttributeValues = null;
        this.mChildrenEles = null;
        this.mExtensionElementName = str;
        this.mNamespace = str2;
        this.mAttributeNames = new String[]{str3};
        this.mAttributeValues = new String[]{str4};
    }

    public CommonPacketExtension(String str, String str2, List<String> list, List<String> list2) {
        this.mAttributeNames = null;
        this.mAttributeValues = null;
        this.mChildrenEles = null;
        this.mExtensionElementName = str;
        this.mNamespace = str2;
        this.mAttributeNames = (String[]) list.toArray(new String[list.size()]);
        this.mAttributeValues = (String[]) list2.toArray(new String[list2.size()]);
    }

    public CommonPacketExtension(String str, String str2, List<String> list, List<String> list2, String str3, List<CommonPacketExtension> list3) {
        this.mAttributeNames = null;
        this.mAttributeValues = null;
        this.mChildrenEles = null;
        this.mExtensionElementName = str;
        this.mNamespace = str2;
        this.mAttributeNames = (String[]) list.toArray(new String[list.size()]);
        this.mAttributeValues = (String[]) list2.toArray(new String[list2.size()]);
        this.mText = str3;
        this.mChildrenEles = list3;
    }

    public CommonPacketExtension(String str, String str2, String[] strArr, String[] strArr2) {
        this.mAttributeNames = null;
        this.mAttributeValues = null;
        this.mChildrenEles = null;
        this.mExtensionElementName = str;
        this.mNamespace = str2;
        this.mAttributeNames = strArr;
        this.mAttributeValues = strArr2;
    }

    public CommonPacketExtension(String str, String str2, String[] strArr, String[] strArr2, String str3, List<CommonPacketExtension> list) {
        this.mAttributeNames = null;
        this.mAttributeValues = null;
        this.mChildrenEles = null;
        this.mExtensionElementName = str;
        this.mNamespace = str2;
        this.mAttributeNames = strArr;
        this.mAttributeValues = strArr2;
        this.mText = str3;
        this.mChildrenEles = list;
    }

    public static CommonPacketExtension[] getArray(Parcelable[] parcelableArr) {
        CommonPacketExtension[] commonPacketExtensionArr = new CommonPacketExtension[parcelableArr == null ? 0 : parcelableArr.length];
        if (parcelableArr != null) {
            for (int i = 0; i < parcelableArr.length; i++) {
                commonPacketExtensionArr[i] = parseFromBundle((Bundle) parcelableArr[i]);
            }
        }
        return commonPacketExtensionArr;
    }

    public static CommonPacketExtension parseFromBundle(Bundle bundle) {
        ArrayList arrayList;
        String string = bundle.getString(PushConstants.EXTRA_EXTENSION_ELEMENT_NAME);
        String string2 = bundle.getString(PushConstants.EXTRA_EXTENSION_NAMESPACE);
        String string3 = bundle.getString(PushConstants.EXTRA_EXTENSION_TEXT);
        Bundle bundle2 = bundle.getBundle(ATTRIBUTE_NAME);
        Set<String> setKeySet = bundle2.keySet();
        String[] strArr = new String[setKeySet.size()];
        String[] strArr2 = new String[setKeySet.size()];
        int i = 0;
        for (String str : setKeySet) {
            strArr[i] = str;
            strArr2[i] = bundle2.getString(str);
            i++;
        }
        if (bundle.containsKey(CHILDREN_NAME)) {
            Parcelable[] parcelableArray = bundle.getParcelableArray(CHILDREN_NAME);
            arrayList = new ArrayList(parcelableArray.length);
            for (Parcelable parcelable : parcelableArray) {
                arrayList.add(parseFromBundle((Bundle) parcelable));
            }
        } else {
            arrayList = null;
        }
        return new CommonPacketExtension(string, string2, strArr, strArr2, string3, arrayList);
    }

    public static Parcelable[] toParcelableArray(List<CommonPacketExtension> list) {
        return toParcelableArray((CommonPacketExtension[]) list.toArray(new CommonPacketExtension[list.size()]));
    }

    public static Parcelable[] toParcelableArray(CommonPacketExtension[] commonPacketExtensionArr) {
        if (commonPacketExtensionArr == null) {
            return null;
        }
        Parcelable[] parcelableArr = new Parcelable[commonPacketExtensionArr.length];
        for (int i = 0; i < commonPacketExtensionArr.length; i++) {
            parcelableArr[i] = commonPacketExtensionArr[i].toParcelable();
        }
        return parcelableArr;
    }

    public void appendChild(CommonPacketExtension commonPacketExtension) {
        if (this.mChildrenEles == null) {
            this.mChildrenEles = new ArrayList();
        }
        if (this.mChildrenEles.contains(commonPacketExtension)) {
            return;
        }
        this.mChildrenEles.add(commonPacketExtension);
    }

    public String getAttributeValue(String str) {
        if (str == null) {
            throw new IllegalArgumentException();
        }
        if (this.mAttributeNames == null) {
            return null;
        }
        int i = 0;
        while (true) {
            String[] strArr = this.mAttributeNames;
            if (i >= strArr.length) {
                return null;
            }
            if (str.equals(strArr[i])) {
                return this.mAttributeValues[i];
            }
            i++;
        }
    }

    public CommonPacketExtension getChildByName(String str) {
        List<CommonPacketExtension> list;
        if (TextUtils.isEmpty(str) || (list = this.mChildrenEles) == null) {
            return null;
        }
        for (CommonPacketExtension commonPacketExtension : list) {
            if (commonPacketExtension.mExtensionElementName.equals(str)) {
                return commonPacketExtension;
            }
        }
        return null;
    }

    public List<CommonPacketExtension> getChildrenByName(String str) {
        if (TextUtils.isEmpty(str) || this.mChildrenEles == null) {
            return null;
        }
        ArrayList arrayList = new ArrayList();
        for (CommonPacketExtension commonPacketExtension : this.mChildrenEles) {
            if (commonPacketExtension.mExtensionElementName.equals(str)) {
                arrayList.add(commonPacketExtension);
            }
        }
        return arrayList;
    }

    public List<CommonPacketExtension> getChildrenExt() {
        return this.mChildrenEles;
    }

    @Override // com.xiaomi.smack.packet.PacketExtension
    public String getElementName() {
        return this.mExtensionElementName;
    }

    @Override // com.xiaomi.smack.packet.PacketExtension
    public String getNamespace() {
        return this.mNamespace;
    }

    public String getText() {
        return !TextUtils.isEmpty(this.mText) ? StringUtils.unescapeFromXML(this.mText) : this.mText;
    }

    public void setText(String str) {
        if (TextUtils.isEmpty(str)) {
            this.mText = str;
        } else {
            this.mText = StringUtils.escapeForXML(str);
        }
    }

    public Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putString(PushConstants.EXTRA_EXTENSION_ELEMENT_NAME, this.mExtensionElementName);
        bundle.putString(PushConstants.EXTRA_EXTENSION_NAMESPACE, this.mNamespace);
        bundle.putString(PushConstants.EXTRA_EXTENSION_TEXT, this.mText);
        Bundle bundle2 = new Bundle();
        String[] strArr = this.mAttributeNames;
        if (strArr != null && strArr.length > 0) {
            int i = 0;
            while (true) {
                String[] strArr2 = this.mAttributeNames;
                if (i >= strArr2.length) {
                    break;
                }
                bundle2.putString(strArr2[i], this.mAttributeValues[i]);
                i++;
            }
        }
        bundle.putBundle(ATTRIBUTE_NAME, bundle2);
        List<CommonPacketExtension> list = this.mChildrenEles;
        if (list != null && list.size() > 0) {
            bundle.putParcelableArray(CHILDREN_NAME, toParcelableArray(this.mChildrenEles));
        }
        return bundle;
    }

    public Parcelable toParcelable() {
        return toBundle();
    }

    public String toString() {
        return toXML();
    }

    @Override // com.xiaomi.smack.packet.PacketExtension
    public String toXML() {
        StringBuilder sb = new StringBuilder();
        sb.append("<");
        sb.append(this.mExtensionElementName);
        if (!TextUtils.isEmpty(this.mNamespace)) {
            sb.append(" ");
            sb.append("xmlns=");
            sb.append("\"");
            sb.append(this.mNamespace);
            sb.append("\"");
        }
        String[] strArr = this.mAttributeNames;
        if (strArr != null && strArr.length > 0) {
            for (int i = 0; i < this.mAttributeNames.length; i++) {
                if (!TextUtils.isEmpty(this.mAttributeValues[i])) {
                    sb.append(" ");
                    sb.append(this.mAttributeNames[i]);
                    sb.append("=\"");
                    sb.append(StringUtils.escapeForXML(this.mAttributeValues[i]));
                    sb.append("\"");
                }
            }
        }
        if (TextUtils.isEmpty(this.mText)) {
            List<CommonPacketExtension> list = this.mChildrenEles;
            if (list == null || list.size() <= 0) {
                sb.append("/>");
            } else {
                sb.append(">");
                Iterator<CommonPacketExtension> it = this.mChildrenEles.iterator();
                while (it.hasNext()) {
                    sb.append(it.next().toXML());
                }
                sb.append("</");
                sb.append(this.mExtensionElementName);
                sb.append(">");
            }
        } else {
            sb.append(">");
            sb.append(this.mText);
            sb.append("</");
            sb.append(this.mExtensionElementName);
            sb.append(">");
        }
        return sb.toString();
    }
}
