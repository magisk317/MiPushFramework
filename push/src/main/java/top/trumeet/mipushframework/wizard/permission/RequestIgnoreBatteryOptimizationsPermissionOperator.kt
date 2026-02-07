package top.trumeet.mipushframework.wizard.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import top.trumeet.common.push.PushServiceAccessibility

@RequiresApi(api = Build.VERSION_CODES.M)
class RequestIgnoreBatteryOptimizationsPermissionOperator(
    private val context: Context
) : PermissionOperator {
    override fun isPermissionGranted(): Boolean {
        return PushServiceAccessibility.isInDozeWhiteList(context)
    }

    override fun requestPermissionSilently() {
    }

    override fun requestPermission() {
        val intent = Intent()
        intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        intent.data = Uri.parse("package:${context.packageName}")
        context.startActivity(intent)
    }
}
