package com.xiaomi.push.service.awake.module
import io.github.magisk317.mipush.protocol.model.*

enum class HelpType(
    @JvmField val typeValue: String,
) {
    ACTIVITY("activity"),
    SERVICE_ACTION("service_action"),
    SERVICE_COMPONENT("service_component"),
    PROVIDER("provider"),
}
