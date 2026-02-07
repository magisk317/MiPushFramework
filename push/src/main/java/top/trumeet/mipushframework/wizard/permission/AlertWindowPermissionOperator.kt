package top.trumeet.mipushframework.wizard.permission

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import com.xiaomi.xmsf.R
import top.trumeet.mipushframework.utils.PermissionUtils

@RequiresApi(api = Build.VERSION_CODES.M)
class AlertWindowPermissionOperator(private val context: Context) : PermissionOperator {
    override fun isPermissionGranted(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    override fun requestPermissionSilently() {
        PermissionUtils.lunchAppOps(
            context,
            AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
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
