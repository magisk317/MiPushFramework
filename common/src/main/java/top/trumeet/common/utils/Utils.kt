@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
package top.trumeet.common.utils

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.text.Html
import android.text.TextUtils
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.annotation.NonNull
import androidx.annotation.StringRes
import top.trumeet.common.override.AppOpsManagerOverride
import java.util.*

object Utils {
    @JvmStatic
    var context: Context? = null

    @JvmStatic
    fun setApplicationContext(context: Context) {
        Utils.context = context.applicationContext
    }

    @JvmStatic
    fun myUid(): Int {
        return Process.myUserHandle().hashCode()
    }

    @JvmStatic
    fun getApplication(): Context? {
        return context
    }

    @JvmStatic
    fun getPackageManager(): PackageManager? {
        return context?.packageManager
    }

    @JvmStatic
    fun isAppOpsInstalled(): Boolean {
        return isAppInstalled("rikka.appops")
    }

    @JvmStatic
    fun isAppInstalled(packageName: String): Boolean {
        return try {
            getPackageInfoCompat(context!!, packageName, 0) != null
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    @JvmStatic
    fun getPackageInfoCompat(context: Context, packageName: String, flags: Int): android.content.pm.PackageInfo? {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, flags)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    @JvmStatic
    @ColorInt
    fun getColorAttr(context: Context, attr: Int): Int {
        val ta = context.obtainStyledAttributes(intArrayOf(attr))
        @ColorInt val colorAccent = ta.getColor(0, 0)
        ta.recycle()
        return colorAccent
    }

    @JvmStatic
    fun getUTC(date: Date): Date {
        return date
    }

    @JvmStatic
    fun getUTC(): Date {
        return getUTC(Date())
    }

    @JvmStatic
    fun getString(
        @StringRes id: Int,
        @NonNull context: Context,
        vararg formatArgs: Any?
    ): CharSequence {
        return toHtml(context.getString(id, *formatArgs))
    }

    @JvmStatic
    fun toHtml(str: String): CharSequence {
        return Html.fromHtml(str)
    }

    @JvmStatic
    fun isUserApplication(applicationInfo: ApplicationInfo): Boolean {
        return (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0
    }

    @JvmStatic
    fun checkOp(context: Context, op: Int): Int {
        return AppOpsManagerOverride.checkOpNoThrow(
            op, Process.myUid(),
            context.packageName, context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        )
    }

    @JvmStatic
    fun isUserApplication(pkg: String): Boolean {
        return try {
            val appInfo = getApplicationInfoCompat(context!!, pkg, PackageManager.GET_UNINSTALLED_PACKAGES)
            appInfo?.let { isUserApplication(it) } ?: false
        } catch (ignored: PackageManager.NameNotFoundException) {
            false
        }
    }

    @JvmStatic
    fun getApplicationInfoCompat(context: Context, packageName: String, flags: Int): ApplicationInfo? {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getApplicationInfo(packageName, android.content.pm.PackageManager.ApplicationInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(packageName, flags)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    @JvmStatic
    fun makeText(str: CharSequence, duration: Int) {
        makeText(getApplication()!!, str, duration)
    }

    @JvmStatic
    fun makeText(context: Context, usedString: CharSequence, duration: Int) {
        Handler(Looper.getMainLooper()).post {
            try {
                Toast.makeText(context, usedString, duration).show()
            } catch (ignored: Throwable) {
                // TODO: It's a bad way to switch to main thread.
                // Ignored service death
            }
        }
    }

    @JvmStatic
    fun getRegSec(packageName: String): String? {
        var secSp = getApplication()?.getSharedPreferences("pref_registered_pkg_names_sec", 0)
        var sec = secSp?.getString(packageName, null)
        if (sec != null) {
            return sec
        }
        secSp = getApplication()?.getSharedPreferences("mipush", 0)
        return secSp?.getString(packageName, null)
    }

    @JvmStatic
    fun setRegSec(pkgName: String, regSec: String?) {
        if (TextUtils.isEmpty(regSec)) {
            return
        }
        val secSp = getApplication()?.getSharedPreferences("pref_registered_pkg_names_sec", 0)
        val secEditor = secSp?.edit()
        secEditor?.putString(pkgName, regSec)
        secEditor?.commit()
    }

    @JvmStatic
    fun getLastReceiveTime(packageName: String): Long? {
        val secSp = getApplication()?.getSharedPreferences("last_receive_time", 0)
        if (secSp?.contains(packageName) == false) {
            return null
        }
        return secSp?.getLong(packageName, 0)
    }

    @JvmStatic
    fun setLastReceiveTime(pkgName: String, time: Long) {
        val secSp = getApplication()?.getSharedPreferences("last_receive_time", 0)
        val secEditor = secSp?.edit()
        secEditor?.putLong(pkgName, time)
        secEditor?.commit()
    }
}
