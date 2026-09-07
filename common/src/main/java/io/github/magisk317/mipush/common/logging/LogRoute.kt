package io.github.magisk317.mipush.common.logging

/** Stable log routes shared by hook and XMSF processes. */
enum class LogRoute(val id: String) {
    HOOK("hook"),
    XMSF_HOOK("xmsf_hook"),
    NMS_HOOK("nms_hook"),
    NOTIFICATION("notification"),
}
