package io.github.magisk317.mipush.feature.wizard.permission

import android.content.Context
import io.github.magisk317.mipush.manager.R

class RootPermissionInfo(context: Context) : PermissionInfo {
    override val permissionOperator = RootPermissionOperator()
    override val permissionTitle = context.getString(R.string.wizard_title_root_permission)
    override val permissionDescription = context.getString(R.string.wizard_title_root_permission_text)
    override val isRequired: Boolean = false
}
