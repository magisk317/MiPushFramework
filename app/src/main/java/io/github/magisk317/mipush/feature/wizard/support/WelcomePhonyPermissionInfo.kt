package io.github.magisk317.mipush.feature.wizard.support

import android.content.Context
import io.github.magisk317.mipush.runtime.R

class WelcomePhonyPermissionInfo(context: Context) : DisplayOnlyPhonyPermissionInfo(context) {
    override val permissionTitle: String
        get() = context.getString(R.string.app_name)

    override val permissionDescription: String
        get() = context.getString(R.string.wizard_descr)
}
