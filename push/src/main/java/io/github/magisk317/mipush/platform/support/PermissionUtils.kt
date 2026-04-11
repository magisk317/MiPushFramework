package io.github.magisk317.mipush.platform.support

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.magisk317.utils.PrivilegeElevator
import com.topjohnwu.superuser.Shell
import com.xiaomi.xmsf.R
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.platform.override.AppOpsManagerOverride

object PermissionUtils {
    @JvmStatic
    fun hasRootAccess(): Boolean {
        return runCatching {
            Shell.cmd("id -u").exec().out.firstOrNull()?.trim() == "0"
        }.getOrDefault(false)
    }

    @JvmStatic
    fun requestRootAccess(): Boolean {
        return runCatching {
            PrivilegeElevator.tryToElevate()
            hasRootAccess()
        }.getOrDefault(false)
    }

    @JvmStatic
    fun canAssignPermissionViaAppOps(): Boolean {
        return Utils.isAppOpsInstalled() || ShellUtils.isSuAvailable()
    }

    @JvmStatic
    fun lunchAppOps(context: Context, permission: String, tips: CharSequence): Boolean {
        if ((hasRootAccess() || requestRootAccess()) && ShellUtils.isSuAvailable()) {
            if (allowPermission(permission)) {
                return true
            }
            Toast.makeText(context, R.string.fail, Toast.LENGTH_SHORT).show()
        }

        if (Utils.isAppOpsInstalled()) {
            val intent = Intent(Intent.ACTION_SHOW_APP_INFO)
                .setClassName("rikka.appops", "rikka.appops.appdetail.AppDetailActivity")
                .putExtra("rikka.appops.intent.extra.USER_HANDLE", Utils.myUid())
                .putExtra("rikka.appops.intent.extra.PACKAGE_NAME", Constants.SERVICE_APP_NAME)
                .setData(Uri.parse("package:" + Constants.SERVICE_APP_NAME))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Toast.makeText(context, tips, Toast.LENGTH_LONG).show()
            return true
        }

        return false
    }

    @JvmStatic
    fun allowPermission(permission: String): Boolean {
        return ShellUtils.exec(
            "appops set --user " + Utils.myUid() + " " + Constants.SERVICE_APP_NAME + " " + permission + " " + AppOpsManagerOverride.MODE_ALLOWED
        )
    }

    @JvmStatic
    fun requestIgnoreBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }
        if (!(hasRootAccess() || requestRootAccess())) {
            return false
        }
        val commands = listOf(
            "cmd deviceidle whitelist +${context.packageName}",
            "dumpsys deviceidle whitelist +${context.packageName}"
        )
        commands.forEach { ShellUtils.exec(it) }
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    @JvmStatic
    fun grantNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        if (!(hasRootAccess() || requestRootAccess())) {
            return false
        }
        val packageName = context.packageName
        val commands = listOf(
            "pm grant $packageName android.permission.POST_NOTIFICATIONS",
            "appops set --user ${Utils.myUid()} $packageName POST_NOTIFICATION allow",
            "appops set --user ${Utils.myUid()} $packageName android:post_notification allow",
            "cmd appops set $packageName POST_NOTIFICATION allow"
        )
        commands.forEach { ShellUtils.exec(it) }
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
