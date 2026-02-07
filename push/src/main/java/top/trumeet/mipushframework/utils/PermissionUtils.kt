package top.trumeet.mipushframework.utils

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.xiaomi.xmsf.R
import top.trumeet.common.Constants
import top.trumeet.common.utils.Utils

object PermissionUtils {
    @JvmStatic
    fun canAssignPermissionViaAppOps(): Boolean {
        return Utils.isAppOpsInstalled() || ShellUtils.isSuAvailable()
    }

    @JvmStatic
    fun lunchAppOps(context: Context, permission: String, tips: CharSequence): Boolean {
        if (ShellUtils.isSuAvailable()) {
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
            "appops set --user " + Utils.myUid() + " " + Constants.SERVICE_APP_NAME + " " + permission + " " + AppOpsManager.MODE_ALLOWED
        )
    }
}
