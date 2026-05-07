package com.xiaomi.push.service.awake.module

enum class HelpType(
    @JvmField val typeValue: String,
) {
    ACTIVITY("activity"),
    SERVICE_ACTION("service_action"),
    SERVICE_COMPONENT("service_component"),
    PROVIDER("provider"),
}
