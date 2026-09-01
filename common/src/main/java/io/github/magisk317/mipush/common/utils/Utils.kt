package io.github.magisk317.mipush.common.utils

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.text.Html
import android.widget.Toast
import androidx.core.content.edit
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import co.touchlab.kermit.Logger
import io.github.magisk317.mipush.common.compat.PackageManagerCompatBridge
import io.github.magisk317.mipush.platform.override.AppOpsManagerOverride
import java.io.File
import java.util.*

@SuppressLint("StaticFieldLeak")
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
    fun myUserId(): Int = runCatching {
        Process.myUid().takeIf { it >= 0 } ?: error("Invalid process uid")
    }
        .getOrNull()
        ?.div(PER_USER_RANGE)
        ?.takeIf { it >= 0 }
        ?: -1

    @JvmStatic
    fun requireValidUserId(userId: Int): Int {
        require(userId >= 0) { "Invalid Android user id: $userId" }
        return userId
    }

    @JvmStatic
    @Deprecated("Use myUserId()", ReplaceWith("myUserId()"))
    fun myUid(): Int = myUserId()

    @JvmStatic
    fun getApplication(): Context? {
        return context
    }

    private const val PER_USER_RANGE = 100_000

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
            getPackageInfoCompat(context, packageName, 0)
                ?.applicationInfo
                ?.let(::isAppInstalled) == true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Xiaomi XSpace can expose owner-user package metadata to user 999 even when the package is not
     * assigned to that user. FLAG_INSTALLED is the package-user state carried by that metadata.
     */
    @JvmStatic
    fun isAppInstalled(applicationInfo: ApplicationInfo): Boolean {
        return (applicationInfo.flags and ApplicationInfo.FLAG_INSTALLED) != 0
    }

    @JvmStatic
    fun getPackageInfoCompat(context: Context, packageName: String, flags: Int): android.content.pm.PackageInfo? {
        return try {
            PackageManagerCompatBridge.getPackageInfo(context.packageManager, packageName, flags)
        } catch (_: PackageManager.NameNotFoundException) {
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
    fun getString(
        @StringRes id: Int,
        context: Context,
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
        val systemFlags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        return (applicationInfo.flags and systemFlags) == 0
    }

    @JvmStatic
    fun isUserApplication(context: Context, pkg: String): Boolean {
        val appInfo = getApplicationInfoCompat(context, pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES)
        return appInfo?.let { isUserApplication(it) } ?: false
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
        val app = context ?: return false
        return isUserApplication(app, pkg)
    }

    @JvmStatic
    fun getApplicationInfoCompat(context: Context, packageName: String, flags: Int): ApplicationInfo? {
        return try {
            PackageManagerCompatBridge.getApplicationInfo(context.packageManager, packageName, flags)
        } catch (_: PackageManager.NameNotFoundException) {
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
    fun getRegSec(packageName: String, userId: Int = requireValidUserId(myUserId())): String? {
        return getRegSecs(packageName, userId).firstOrNull()
    }

    @JvmStatic
    fun getRegSecs(packageName: String, userId: Int = requireValidUserId(myUserId())): List<String> {
        val app = getApplication() ?: return emptyList()
        val normalizedUserId = requireValidUserId(userId)
        val secrets = linkedSetOf<String>()
        var targetPackageMissing = false
        for (prefName in REG_SEC_PREFS) {
            val preferences = app.getSharedPreferences(prefName, 0)
            val sec = preferences?.getString(regSecPreferenceKey(packageName, normalizedUserId), null)
            if (!sec.isNullOrEmpty()) {
                secrets += sec
            } else if (normalizedUserId == 0 && preferences?.contains(packageName) == true) {
                // Migrate the historical primary-user key without exposing it to other users.
                preferences.getString(packageName, null)?.takeIf { it.isNotEmpty() }?.let { legacySec ->
                    secrets += legacySec
                    preferences.edit { putString(regSecPreferenceKey(packageName, normalizedUserId), legacySec) }
                }
            }
        }
        // Fallback: read regSec from the target app's own mipush SharedPreferences
        if (secrets.isEmpty() && normalizedUserId == myUserId().takeIf { it >= 0 }) {
            try {
                Logger.withTag("Utils").d { "getRegSecs: trying fallback createPackageContext pkg=$packageName" }
                val pkgContext = app.createPackageContext(packageName, 0)
                val preferencePath = File(
                    pkgContext.applicationInfo.dataDir,
                    "shared_prefs/$PREF_MIPUSH.xml",
                )
                val regSec = if (preferencePath.isFile) {
                    pkgContext.getSharedPreferences(PREF_MIPUSH, 0)
                        ?.getString("regSec", null)
                } else {
                    null
                }
                if (!regSec.isNullOrEmpty()) {
                    secrets += regSec
                    Logger.withTag("Utils").d { "getRegSecs: found regSec via fallback pkg=$packageName" }
                } else {
                    Logger.withTag("Utils").d { "getRegSecs: fallback found no regSec pkg=$packageName" }
                }
            } catch (_: PackageManager.NameNotFoundException) {
                targetPackageMissing = true
                if (missingRegSecPackageWarnings.add(packageName)) {
                    Logger.withTag("Utils").w { "getRegSecs: target package not installed pkg=$packageName" }
                }
            } catch (e: Exception) {
                Logger.withTag("Utils").e(e) { "getRegSecs: fallback failed pkg=$packageName" }
            }
        }
        if (secrets.isEmpty() && !targetPackageMissing) {
            Logger.withTag("Utils").d { "getRegSecs: no regSec found for pkg=$packageName" }
        }
        return secrets.toList()
    }

    @JvmStatic
    fun setRegSec(pkgName: String, regSec: String?, userId: Int = requireValidUserId(myUserId())) {
        val app = getApplication() ?: return
        setRegSec(app, pkgName, regSec, userId)
    }

    @JvmStatic
    fun setRegSec(
        context: Context,
        pkgName: String,
        regSec: String?,
        userId: Int = requireValidUserId(myUserId()),
    ) {
        if (regSec.isNullOrEmpty()) {
            return
        }
        val normalizedUserId = requireValidUserId(userId)
        for (prefName in listOf(PREF_REGISTERED_PKG_NAMES_SEC, PREF_MIPUSH_APPS_SECRET)) {
            context.getSharedPreferences(prefName, 0).edit {
                putString(regSecPreferenceKey(pkgName, normalizedUserId), regSec)
                if (normalizedUserId == 0) putString(pkgName, regSec)
            }
        }
    }

    @JvmStatic
    fun removeRegSec(pkgName: String, userId: Int = requireValidUserId(myUserId())) {
        val app = getApplication() ?: return
        val normalizedUserId = requireValidUserId(userId)
        for (prefName in REG_SEC_PREFS) {
            app.getSharedPreferences(prefName, 0).edit {
                remove(regSecPreferenceKey(pkgName, normalizedUserId))
                if (normalizedUserId == 0) remove(pkgName)
            }
        }
    }

    internal fun regSecPreferenceKey(packageName: String, userId: Int): String =
        "${requireValidUserId(userId)}:$packageName"

    @JvmStatic
    fun getLastReceiveTime(packageName: String, userId: Int = requireValidUserId(myUserId())): Long? {
        val secSp = getApplication()?.getSharedPreferences("last_receive_time", 0)
        if (secSp == null) return null
        val normalizedUserId = requireValidUserId(userId)
        val scopedKey = lastReceiveTimePreferenceKey(packageName, normalizedUserId)
        if (secSp.contains(scopedKey)) {
            return secSp.getLong(scopedKey, 0)
        }
        // Migrate the legacy primary-user value without exposing it to cloned users.
        if (normalizedUserId != 0 || !secSp.contains(packageName)) return null
        return secSp.getLong(packageName, 0).also { legacyValue ->
            secSp.edit { putLong(scopedKey, legacyValue) }
        }
    }

    @JvmStatic
    fun setLastReceiveTime(pkgName: String, time: Long, userId: Int = requireValidUserId(myUserId())) {
        val secSp = getApplication()?.getSharedPreferences("last_receive_time", 0)
        val normalizedUserId = requireValidUserId(userId)
        val scopedKey = lastReceiveTimePreferenceKey(pkgName, normalizedUserId)
        secSp?.edit {
            putLong(scopedKey, time)
            if (normalizedUserId == 0) putLong(pkgName, time)
        }
    }

    @JvmStatic
    fun removeLastReceiveTime(pkgName: String, userId: Int = requireValidUserId(myUserId())) {
        val secSp = getApplication()?.getSharedPreferences("last_receive_time", 0)
        val normalizedUserId = requireValidUserId(userId)
        secSp?.edit {
            remove(lastReceiveTimePreferenceKey(pkgName, normalizedUserId))
            if (normalizedUserId == 0) remove(pkgName)
        }
    }

    internal fun lastReceiveTimePreferenceKey(packageName: String, userId: Int): String =
        "$userId:$packageName"
}
