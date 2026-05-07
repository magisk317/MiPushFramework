package io.github.magisk317.mipush.feature.wizard.permission

import android.content.Context
import com.xiaomi.xmsf.R

class AccessibilityPermissionInfo(context: Context) : PermissionInfo {
    override val permissionOperator = AccessibilityPermissionOperator(context)
    override val permissionTitle = context.getString(R.string.wizard_title_accessibility_permission)
    override val permissionDescription = context.getString(R.string.wizard_title_accessibility_text)
}
