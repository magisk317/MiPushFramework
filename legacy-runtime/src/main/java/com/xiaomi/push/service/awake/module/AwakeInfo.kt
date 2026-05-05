package com.xiaomi.push.service.awake.module

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/push/service/awake/module/AwakeInfo.java
 * No stock 7.4.67-C same-path source was found in the split source tree.
 */
class AwakeInfo {
    companion object {
        const val AWAKE_DEFAULT = 0
        const val AWAKE_JUST_FOREGROUND = 1
    }

    var action: String? = null
        private set
    var awakeForeground = AWAKE_DEFAULT
        private set
    var awakeInfo: String? = null
        private set
    var className: String? = null
        private set
    var targetPackageName: String? = null
        private set

    fun setAction(action: String?) {
        this.action = action
    }

    fun setAwakeForeground(awakeForeground: Int) {
        this.awakeForeground = awakeForeground
    }

    fun setAwakeInfo(awakeInfo: String?) {
        this.awakeInfo = awakeInfo
    }

    fun setClassName(className: String?) {
        this.className = className
    }

    fun setTargetPackageName(targetPackageName: String?) {
        this.targetPackageName = targetPackageName
    }
}
