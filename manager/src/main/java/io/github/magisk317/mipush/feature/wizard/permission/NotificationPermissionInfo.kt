package io.github.magisk317.mipush.feature.wizard.permission

import android.content.Context
import io.github.magisk317.mipush.manager.R

class NotificationPermissionInfo(context: Context) : PermissionInfo {
    override val permissionOperator = NotificationPermissionOperator(context)
    override val permissionTitle = context.getString(R.string.wizard_title_notification_permission)
    override val permissionDescription = context.getString(R.string.wizard_title_notification_text)
}
