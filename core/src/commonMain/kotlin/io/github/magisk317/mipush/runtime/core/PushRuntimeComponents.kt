package io.github.magisk317.mipush.runtime.core

object PushRuntimeComponents {
    const val SERVICE_PACKAGE = "com.xiaomi.xmsf"
    const val BRIDGE_SERVICE_CLASS = "com.xiaomi.xmsf.push.service.XMPushService"
    /** Public ABI selected by MiPush SDKs from XMSF version 106 onward. */
    const val LEGACY_COMPAT_SERVICE_CLASS = "com.xiaomi.push.service.XMPushService"

    /** Private runtime service reached only after a compatibility ingress has validated a request. */
    const val CORE_SERVICE_CLASS = "com.xiaomi.push.service.XMPushServiceCore"
}
