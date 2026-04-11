package com.xiaomi.channel.commonutils.misc

object DebugSwitch {
    const val DEBUG_CHECK_THREAD = false
    const val DEBUG_COLLECTION = false
    const val DEBUG_PUSH_ON_MINU = false

    @JvmField
    var sDebugServerHost = false

    @JvmField
    var sOneboxServerHost = DebugSwitchConstants.STAGING_SERVER_HOST
}
