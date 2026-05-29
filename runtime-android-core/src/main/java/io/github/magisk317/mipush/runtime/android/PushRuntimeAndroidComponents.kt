package io.github.magisk317.mipush.runtime.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import io.github.magisk317.mipush.runtime.core.PushRuntimeComponents

object PushRuntimeAndroidComponents {
    fun newLegacyMainServiceIntent(context: Context, action: String? = null): Intent {
        return Intent().apply {
            component = ComponentName(context.packageName, PushRuntimeComponents.LEGACY_MAIN_SERVICE_CLASS)
            this.action = action
        }
    }
}
