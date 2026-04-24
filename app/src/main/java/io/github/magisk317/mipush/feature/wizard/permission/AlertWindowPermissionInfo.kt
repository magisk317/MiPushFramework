package io.github.magisk317.mipush.feature.wizard.permission

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import io.github.magisk317.mipush.runtime.R

@RequiresApi(api = Build.VERSION_CODES.M)
class AlertWindowPermissionInfo(private val context: Context) : PermissionInfo {
    override val permissionOperator: PermissionOperator
        get() = AlertWindowPermissionOperator(context)

    override val permissionTitle: String
        get() = context.getString(R.string.wizard_title_alert_window_permission)

    override val permissionDescription: String
        get() = context.getString(R.string.wizard_title_alert_window_text)
}
