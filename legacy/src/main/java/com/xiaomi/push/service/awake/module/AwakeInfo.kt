package com.xiaomi.push.service.awake.module
import io.github.magisk317.mipush.protocol.model.*

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
