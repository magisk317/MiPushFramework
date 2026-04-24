package io.github.magisk317.mipush.feature.wizard.permission

import android.content.Context
import io.github.magisk317.mipush.runtime.R

class UsageStatsPermissionInfo(private val context: Context) : PermissionInfo {
    override val permissionOperator: PermissionOperator
        get() = UsageStatsPermissionOperator(context)

    override val permissionTitle: String
        get() = context.getString(R.string.wizard_title_stats_permission)

    override val permissionDescription: String
        get() = context.getString(R.string.wizard_title_stats_permission_text)
}
