package io.github.magisk317.mipush.hook.systemui

import io.github.magisk317.xposed.setHookObjectField

/** Keeps Android 17's descriptor icon and optional preloaded drawable in sync. */
internal fun replaceStatusBarDescriptorIcon(descriptor: Any, icon: Any) {
    setHookObjectField(descriptor, "icon", icon)
    try {
        setHookObjectField(descriptor, "preloadedIcon", null)
    } catch (_: ReflectiveOperationException) {
        // Older or vendor descriptors may not expose the optional cache field.
    } catch (_: IllegalArgumentException) {
        // A mismatched vendor field must not undo the icon replacement.
    }
}
