package io.github.magisk317.mipush.runtime

import android.content.Context
import android.content.Intent

object PushRuntimeComponents {
    const val SERVICE_PACKAGE = io.github.magisk317.mipush.runtime.core.PushRuntimeComponents.SERVICE_PACKAGE
    const val BRIDGE_SERVICE_CLASS = io.github.magisk317.mipush.runtime.core.PushRuntimeComponents.BRIDGE_SERVICE_CLASS
    const val LEGACY_COMPAT_SERVICE_CLASS = io.github.magisk317.mipush.runtime.core.PushRuntimeComponents.LEGACY_COMPAT_SERVICE_CLASS
    const val CORE_SERVICE_CLASS = io.github.magisk317.mipush.runtime.core.PushRuntimeComponents.CORE_SERVICE_CLASS

    fun newCoreServiceIntent(context: Context, action: String? = null): Intent {
        return io.github.magisk317.mipush.runtime.android.PushRuntimeAndroidComponents
            .newCoreServiceIntent(context, action)
    }
}
