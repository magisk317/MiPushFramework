package io.github.magisk317.mipush.common.utils

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.text.Html
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.annotation.NonNull
import androidx.annotation.StringRes
import io.github.aakira.napier.Napier
import io.github.magisk317.mipush.common.compat.PackageManagerCompatBridge
import io.github.magisk317.mipush.platform.override.AppOpsManagerOverride
import java.util.*

object Utils {
    private const val PREF_REGISTERED_PKG_NAMES_SEC = "pref_registered_pkg_names_sec"
    private const val PREF_MIPUSH_APPS_SECRET = "mipush_apps_scrt"
    private const val PREF_MIPUSH = "mipush"
    private val REG_SEC_PREFS = listOf(
        PREF_REGISTERED_PKG_NAMES_SEC,
        PREF_MIPUSH_APPS_SECRET,
        PREF_MIPUSH
    )
    private val missingRegSecPackageWarnings = Collections.synchronizedSet(mutableSetOf<String>())

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
        val app = context ?: return false
        return isAppInstalled(app, packageName)
    }

    @JvmStatic
    fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            getPackageInfoCompat(context, packageName, 0) != null
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    @JvmStatic
    fun getPackageInfoCompat(context: Context, packageName: String, flags: Int): android.content.pm.PackageInfo? {
        return try {
            PackageManagerCompatBridge.getPackageInfo(context.packageManager, packageName, flags)
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
        return Html.fromHtml(str, Html.FROM_HTML_MODE_LEGACY)
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
            val appInfo = getApplicationInfoCompat(context!!, pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES)
            appInfo?.let { isUserApplication(it) } ?: false
        } catch (ignored: PackageManager.NameNotFoundException) {
            false
        }
    }

    @JvmStatic
    fun getApplicationInfoCompat(context: Context, packageName: String, flags: Int): ApplicationInfo? {
        return try {
            PackageManagerCompatBridge.getApplicationInfo(context.packageManager, packageName, flags)
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
        if (Looper.myLooper() == Looper.getMainLooper()) {
            try {
                Toast.makeText(context, usedString, duration).show()
            } catch (ignored: Throwable) {
                // Ignored: context may be from a dead service
            }
        } else {
            Handler(Looper.getMainLooper()).post {
                try {
                    Toast.makeText(context, usedString, duration).show()
                } catch (ignored: Throwable) {
                    // Ignored: context may be from a dead service
                }
            }
        }
    }

    @JvmStatic
    fun getRegSec(packageName: String): String? {
        return getRegSecs(packageName).firstOrNull()
    }

    @JvmStatic
    fun getRegSecs(packageName: String): List<String> {
        val app = getApplication() ?: return emptyList()
        val secrets = linkedSetOf<String>()
        var targetPackageMissing = false
        for (prefName in REG_SEC_PREFS) {
            val sec = app.getSharedPreferences(prefName, 0)?.getString(packageName, null)
            if (!sec.isNullOrEmpty()) {
                secrets += sec
                Napier.d("getRegSecs: found regSec in pref=$prefName pkg=$packageName", tag = "Utils")
            }
        }
        // Fallback: read regSec from the target app's own mipush SharedPreferences
        if (secrets.isEmpty()) {
            try {
                Napier.d("getRegSecs: trying fallback createPackageContext pkg=$packageName", tag = "Utils")
                val pkgContext = app.createPackageContext(packageName, 0)
                val regSec = pkgContext.getSharedPreferences(PREF_MIPUSH, 0)
                    ?.getString("regSec", null)
                if (!regSec.isNullOrEmpty()) {
                    secrets += regSec
                    Napier.d("getRegSecs: found regSec via fallback pkg=$packageName", tag = "Utils")
                } else {
                    Napier.w("getRegSecs: fallback found no regSec pkg=$packageName", tag = "Utils")
                }
            } catch (e: PackageManager.NameNotFoundException) {
                targetPackageMissing = true
                if (missingRegSecPackageWarnings.add(packageName)) {
                    Napier.w("getRegSecs: target package not installed pkg=$packageName", tag = "Utils")
                }
            } catch (e: Exception) {
                Napier.e("getRegSecs: fallback failed pkg=$packageName", e, tag = "Utils")
            }
        }
        if (secrets.isEmpty() && !targetPackageMissing) {
            Napier.w("getRegSecs: no regSec found for pkg=$packageName", tag = "Utils")
        }
        return secrets.toList()
    }

    @JvmStatic
    fun setRegSec(pkgName: String, regSec: String?) {
        if (regSec.isNullOrEmpty()) {
            return
        }
        val app = getApplication() ?: return
        for (prefName in listOf(PREF_REGISTERED_PKG_NAMES_SEC, PREF_MIPUSH_APPS_SECRET)) {
            val secEditor = app.getSharedPreferences(prefName, 0)?.edit()
            secEditor?.putString(pkgName, regSec)
            secEditor?.commit()
        }
    }

    @JvmStatic
    fun removeRegSec(pkgName: String) {
        val app = getApplication() ?: return
        for (prefName in REG_SEC_PREFS) {
            app.getSharedPreferences(prefName, 0)
                ?.edit()
                ?.remove(pkgName)
                ?.commit()
        }
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

    @JvmStatic
    fun removeLastReceiveTime(pkgName: String) {
        val secSp = getApplication()?.getSharedPreferences("last_receive_time", 0)
        secSp?.edit()?.remove(pkgName)?.commit()
    }
}
