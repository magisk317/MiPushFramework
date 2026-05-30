package com.xiaomi.channel.commonutils.msa

import android.content.Context

object MsaIdFactory {
    private const val TYPE_HUAWEI = 2
    private const val TYPE_MIUI = 1
    private const val TYPE_MSA = 3
    private const val TYPE_OTHER = 0

    @JvmField
    var sType: Int = 0

    @JvmStatic
    fun instance(context: Context): IdManager {
        if (MiuiIdManager.isMiuiPhone(context)) {
            sType = TYPE_MIUI
            return MiuiIdManager(context)
        }
        if (HuaweiIdManager.isHuaweiPhone(context)) {
            sType = TYPE_HUAWEI
            return HuaweiIdManager(context)
        }
        if (MdidJLibrary.checkAndLoadMdidSdk(context)) {
            sType = TYPE_MSA
            return MdidIdManager(context)
        }
        sType = TYPE_OTHER
        return OtherIdManager()
    }
}
