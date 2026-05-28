package io.github.magisk317.mipush.feature.wizard.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import com.xiaomi.xmsf.R
import io.github.magisk317.mipush.platform.override.AppOpsManagerOverride
import io.github.magisk317.mipush.platform.support.PermissionUtils

class AlertWindowPermissionOperator(private val context: Context) : PermissionOperator {
    override fun isPermissionGranted(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    override fun requestPermissionSilently(): Boolean {
        return PermissionUtils.lunchAppOps(
            context,
            AppOpsManagerOverride.OPSTR_SYSTEM_ALERT_WINDOW,
            context.getString(R.string.wizard_title_alert_window_text)
        )
    }

    override fun requestPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        context.startActivity(intent)
    }
}
