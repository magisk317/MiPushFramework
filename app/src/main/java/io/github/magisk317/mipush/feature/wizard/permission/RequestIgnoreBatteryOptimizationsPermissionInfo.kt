package io.github.magisk317.mipush.feature.wizard.permission

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import io.github.magisk317.mipush.runtime.R

@RequiresApi(api = Build.VERSION_CODES.M)
class RequestIgnoreBatteryOptimizationsPermissionInfo(
    private val context: Context
) : PermissionInfo {
    override val permissionOperator: PermissionOperator
        get() = RequestIgnoreBatteryOptimizationsPermissionOperator(context)

    override val permissionTitle: String
        get() = context.getString(R.string.wizard_title_ignore_battery_optimizations_permission)

    override val permissionDescription: String
        get() = context.getString(R.string.wizard_title_ignore_battery_optimizations_text)
}
