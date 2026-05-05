package com.xiaomi.smack.packet

import android.os.Bundle
import android.os.Parcelable
import android.text.TextUtils
import androidx.core.os.BundleCompat
import com.xiaomi.push.service.PushConstants
import com.xiaomi.smack.util.StringUtils

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/smack/packet/CommonPacketExtension.java
 * No stock 7.4.67-C same-path source was found in the split source tree.
 * This runtime keeps legacy constructors and child lookup helpers that are absent from the current override.
 */
class CommonPacketExtension : PacketExtension {
    private var mAttributeNames: Array<String>? = null
    private var mAttributeValues: Array<String>? = null
    private var mChildrenEles: MutableList<CommonPacketExtension>? = null
    private var mExtensionElementName: String? = null
    private var mNamespace: String? = null
    private var mText: String? = null

    var text: String
        get() = (if (!TextUtils.isEmpty(mText)) StringUtils.unescapeFromXML(mText) else mText).orEmpty()
        set(str) {
            mText = if (TextUtils.isEmpty(str)) {
                str
            } else {
                StringUtils.escapeForXML(str)
            }
        }

    constructor(str: String?, str2: String?, str3: String?, str4: String?) {
        mExtensionElementName = str
        mNamespace = str2
        mAttributeNames = arrayOf(str3 ?: "")
        mAttributeValues = arrayOf(str4 ?: "")
    }

    constructor(str: String?, str2: String?, list: List<String>, list2: List<String>) {
        mExtensionElementName = str
        mNamespace = str2
        mAttributeNames = list.toTypedArray()
        mAttributeValues = list2.toTypedArray()
    }

    constructor(
        str: String?,
        str2: String?,
        list: List<String>,
        list2: List<String>,
        str3: String?,
        list3: List<CommonPacketExtension>?
    ) {
        mExtensionElementName = str
        mNamespace = str2
        mAttributeNames = list.toTypedArray()
        mAttributeValues = list2.toTypedArray()
        mText = str3
        mChildrenEles = list3?.toMutableList()
    }

    constructor(str: String?, str2: String?, strArr: Array<String>?, strArr2: Array<String>?) {
        mExtensionElementName = str
        mNamespace = str2
        mAttributeNames = strArr
        mAttributeValues = strArr2
    }

    constructor(
        str: String?,
        str2: String?,
        strArr: Array<String>?,
        strArr2: Array<String>?,
        str3: String?,
        list: List<CommonPacketExtension>?
    ) {
        mExtensionElementName = str
        mNamespace = str2
        mAttributeNames = strArr
        mAttributeValues = strArr2
        mText = str3
        mChildrenEles = list?.toMutableList()
    }

    fun appendChild(commonPacketExtension: CommonPacketExtension) {
        if (mChildrenEles == null) {
            mChildrenEles = ArrayList()
        }
        if (mChildrenEles!!.contains(commonPacketExtension)) {
            return
        }
        mChildrenEles!!.add(commonPacketExtension)
    }

    fun getAttributeValue(str: String?): String? {
        if (str == null) {
            throw IllegalArgumentException()
        }
        val names = mAttributeNames ?: return null
        val values = mAttributeValues
        for (i in names.indices) {
            if (str == names[i]) {
                return values?.get(i)
            }
        }
        return null
    }

    fun getChildByName(str: String?): CommonPacketExtension? {
        val children = mChildrenEles
        if (TextUtils.isEmpty(str) || children == null) {
            return null
        }
        for (commonPacketExtension in children) {
            if (commonPacketExtension.mExtensionElementName == str) {
                return commonPacketExtension
            }
        }
        return null
    }

    fun getChildrenByName(str: String?): List<CommonPacketExtension>? {
        val children = mChildrenEles
        if (TextUtils.isEmpty(str) || children == null) {
            return null
        }
        val arrayList = ArrayList<CommonPacketExtension>()
        for (commonPacketExtension in children) {
            if (commonPacketExtension.mExtensionElementName == str) {
                arrayList.add(commonPacketExtension)
            }
        }
        return arrayList
    }

    fun getChildrenExt(): List<CommonPacketExtension>? = mChildrenEles

    override fun getElementName(): String = mExtensionElementName ?: ""

    override fun getNamespace(): String = mNamespace ?: ""

    fun toBundle(): Bundle {
        val bundle = Bundle()
        bundle.putString(PushConstants.EXTRA_EXTENSION_ELEMENT_NAME, mExtensionElementName)
        bundle.putString(PushConstants.EXTRA_EXTENSION_NAMESPACE, mNamespace)
        bundle.putString(PushConstants.EXTRA_EXTENSION_TEXT, mText)
        val bundle2 = Bundle()
        val names = mAttributeNames
        val values = mAttributeValues
        if (names != null && names.isNotEmpty()) {
            for (i in names.indices) {
                bundle2.putString(names[i], values?.get(i))
            }
        }
        bundle.putBundle(ATTRIBUTE_NAME, bundle2)
        val children = mChildrenEles
        if (children != null && children.isNotEmpty()) {
            bundle.putParcelableArray(CHILDREN_NAME, toParcelableArray(children))
        }
        return bundle
    }

    fun toParcelable(): Parcelable = toBundle()

    override fun toString(): String = toXML()

    override fun toXML(): String {
        val sb = StringBuilder()
        sb.append("<")
        sb.append(mExtensionElementName)
        if (!TextUtils.isEmpty(mNamespace)) {
            sb.append(" ")
            sb.append("xmlns=")
            sb.append("\"")
            sb.append(mNamespace)
            sb.append("\"")
        }
        val names = mAttributeNames
        val values = mAttributeValues
        if (names != null && names.isNotEmpty()) {
            for (i in names.indices) {
                if (!TextUtils.isEmpty(values?.get(i))) {
                    sb.append(" ")
                    sb.append(names[i])
                    sb.append("=\"")
                    sb.append(StringUtils.escapeForXML(values?.get(i)))
                    sb.append("\"")
                }
            }
        }
        if (TextUtils.isEmpty(mText)) {
            val children = mChildrenEles
            if (children == null || children.isEmpty()) {
                sb.append("/>")
            } else {
                sb.append(">")
                for (child in children) {
                    sb.append(child.toXML())
                }
                sb.append("</")
                sb.append(mExtensionElementName)
                sb.append(">")
            }
        } else {
            sb.append(">")
            sb.append(mText)
            sb.append("</")
            sb.append(mExtensionElementName)
            sb.append(">")
        }
        return sb.toString()
    }

    companion object {
        const val ATTRIBUTE_NAME = "attributes"
        const val CHILDREN_NAME = "children"

        @JvmStatic
        fun getArray(parcelableArr: Array<Parcelable>?): Array<CommonPacketExtension?> {
            val commonPacketExtensionArr = arrayOfNulls<CommonPacketExtension>(parcelableArr?.size ?: 0)
            if (parcelableArr != null) {
                for (i in parcelableArr.indices) {
                    commonPacketExtensionArr[i] = parseFromBundle(parcelableArr[i] as Bundle)
                }
            }
            return commonPacketExtensionArr
        }

        @JvmStatic
        fun parseFromBundle(bundle: Bundle): CommonPacketExtension? {
            val string = bundle.getString(PushConstants.EXTRA_EXTENSION_ELEMENT_NAME)
            val string2 = bundle.getString(PushConstants.EXTRA_EXTENSION_NAMESPACE)
            val string3 = bundle.getString(PushConstants.EXTRA_EXTENSION_TEXT)
            val bundle2 = bundle.getBundle(ATTRIBUTE_NAME)
            val keySet = bundle2?.keySet().orEmpty()
            val strArr = arrayOfNulls<String>(keySet.size)
            val strArr2 = arrayOfNulls<String>(keySet.size)
            var i = 0
            for (str in keySet) {
                strArr[i] = str
                strArr2[i] = bundle2?.getString(str)
                i++
            }
            val arrayList: ArrayList<CommonPacketExtension>? =
                if (bundle.containsKey(CHILDREN_NAME)) {
                    val parcelableArray = BundleCompat.getParcelableArray(bundle, CHILDREN_NAME, Parcelable::class.java)
                    val children = ArrayList<CommonPacketExtension>(parcelableArray?.size ?: 0)
                    if (parcelableArray != null) {
                        for (parcelable in parcelableArray) {
                            parseFromBundle(parcelable as Bundle)?.let { children.add(it) }
                        }
                    }
                    children
                } else {
                    null
                }
            return CommonPacketExtension(string, string2, strArr.filterNotNull().toTypedArray(), strArr2.filterNotNull().toTypedArray(), string3, arrayList)
        }

        @JvmStatic
        fun toParcelableArray(list: List<CommonPacketExtension>): Array<Parcelable?> {
            return toParcelableArray(list.toTypedArray()) ?: arrayOfNulls(0)
        }

        @JvmStatic
        fun toParcelableArray(commonPacketExtensionArr: Array<CommonPacketExtension>?): Array<Parcelable?>? {
            if (commonPacketExtensionArr == null) {
                return null
            }
            val parcelableArr = arrayOfNulls<Parcelable>(commonPacketExtensionArr.size)
            for (i in commonPacketExtensionArr.indices) {
                parcelableArr[i] = commonPacketExtensionArr[i].toParcelable()
            }
            return parcelableArr
        }
    }
}
