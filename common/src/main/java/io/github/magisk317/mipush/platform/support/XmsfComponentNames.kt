package io.github.magisk317.mipush.platform.support

import io.github.magisk317.mipush.common.Constants

/** Component names required by the XMSF service and runtime compatibility contract. */
object XmsfComponentNames {
    const val SERVICE_PACKAGE = "com.xiaomi.xmsf"
    const val BRIDGE_SERVICE_CLASS = "com.xiaomi.xmsf.push.service.XMPushService"
    const val COMPAT_SERVICE_CLASS = "com.xiaomi.push.service.XMPushService"

    val manifestServices = setOf(
        BRIDGE_SERVICE_CLASS,
        COMPAT_SERVICE_CLASS,
        Constants.KEEPALIVE_ACCESSIBILITY_SERVICE_CLASS,
    )
}
