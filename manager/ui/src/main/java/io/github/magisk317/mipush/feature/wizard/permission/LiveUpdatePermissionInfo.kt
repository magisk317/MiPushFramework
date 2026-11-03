package io.github.magisk317.mipush.feature.wizard.permission

import android.content.Context
import io.github.magisk317.mipush.manager.R

class LiveUpdatePermissionInfo(context: Context) : PermissionInfo {
    override val isRequired: Boolean = false
    override val permissionOperator = LiveUpdatePermissionOperator(context)
    override val permissionTitle = context.getString(R.string.wizard_title_live_update_permission)
    override val permissionDescription = context.getString(R.string.wizard_title_live_update_text)
}
