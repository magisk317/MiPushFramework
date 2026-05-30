package io.github.magisk317.mipush.runtime

import android.content.Context
import android.content.Intent

object PushRuntimeComponents {
    const val SERVICE_PACKAGE = io.github.magisk317.mipush.runtime.core.PushRuntimeComponents.SERVICE_PACKAGE
    const val BRIDGE_SERVICE_CLASS = io.github.magisk317.mipush.runtime.core.PushRuntimeComponents.BRIDGE_SERVICE_CLASS
    const val LEGACY_MAIN_SERVICE_CLASS = io.github.magisk317.mipush.runtime.core.PushRuntimeComponents.LEGACY_MAIN_SERVICE_CLASS

    fun newLegacyMainServiceIntent(context: Context, action: String? = null): Intent {
        return io.github.magisk317.mipush.runtime.android.PushRuntimeAndroidComponents
            .newLegacyMainServiceIntent(context, action)
    }
}
