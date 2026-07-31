package io.github.magisk317.mipush.platform.support

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.xiaomi.xmsf.R
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.platform.override.AppOpsManagerOverride
import io.github.magisk317.xposed.logging.MagiskOtel

/**
 * Root-backed silent permission grants for both [Constants.SERVICE_APP_NAME] (xmsf) and
 * [Constants.MANAGER_APP_NAME] (mipush manager), for primary user and dual-space (999).
 *
 * Special-access Settings UIs are almost always scoped to the current user, so dual-space
 * clones often cannot be toggled in the main-user overlay list — root appops is the reliable path.
 */
object PermissionUtils {
    const val USER_PRIMARY = 0
    const val USER_XSPACE = 999
    /** intArgument sentinel: grant for primary and dual-space (if installed). */
    const val USER_AUTO = -1

    private val FRAMEWORK_PACKAGES = listOf(
        Constants.SERVICE_APP_NAME,
        Constants.MANAGER_APP_NAME,
    )

    /** AppOps that Magisk/root can typically force-allow without interactive Settings. */
    private val SILENT_APPOPS = listOf(
        "SYSTEM_ALERT_WINDOW",
        AppOpsManagerOverride.OPSTR_SYSTEM_ALERT_WINDOW,
        "GET_USAGE_STATS",
        AppOpsManagerOverride.OPSTR_GET_USAGE_STATS,
        "POST_NOTIFICATION",
        "android:post_notification",
        "RUN_IN_BACKGROUND",
        "android:run_in_background",
        "RUN_ANY_IN_BACKGROUND",
        "android:run_any_in_background",
    )

    @JvmStatic
    private fun emitPermission(
        result: String,
        reason: String,
        statusOk: Boolean = true,
        extra: Map<String, String> = emptyMap(),
    ) {
        val attrs = linkedMapOf(
            "result" to result,
            "duration_ms" to "0",
            "process" to "app",
            "stage" to "permission_grant",
            "reason" to reason,
        )
        attrs.putAll(extra)
        MagiskOtel.event(name = "app.monitor", attributes = attrs, statusOk = statusOk)
    }

    fun hasRootAccess(): Boolean = AppRootAccessFacade.refreshRootAccessIfGranted()

    @JvmStatic
    fun hasCachedRootAccess(): Boolean = AppRootAccessFacade.hasCachedRootAccess()

    @JvmStatic
    fun refreshRootAccessIfGranted(): Boolean = AppRootAccessFacade.refreshRootAccessIfGranted()

    @JvmStatic
    fun requestRootAccess(): Boolean = AppRootAccessFacade.requestRootAccess()

    @JvmStatic
    fun canAssignPermissionViaAppOps(): Boolean {
        return Utils.isAppOpsInstalled() || hasExistingRootAccess()
    }

    /**
     * Grant one appop for [packageName] on [userId]. Tries both `appops set` and `cmd appops set`.
     */
    @JvmStatic
    fun allowPermission(
        permission: String,
        packageName: String = Constants.SERVICE_APP_NAME,
        userId: Int = Utils.myUserId(),
    ): Boolean {
        if (!hasExistingRootAccess()) return false
        val commands = listOf(
            "appops set --user $userId $packageName $permission allow",
            "cmd appops set --user $userId $packageName $permission allow",
        )
        val ok = commands.any { AppRootAccessFacade.runRootCommand(it).isSuccess }
        logI("allowPermission user=$userId pkg=$packageName op=$permission ok=$ok")
        emitPermission(
            result = if (ok) "ok" else "error",
            reason = if (ok) "appops_allow" else "appops_failed",
            statusOk = ok,
            extra = mapOf(
                "target_package" to packageName,
                "operation" to permission,
            ),
        )
        return ok
    }

    /**
     * Full silent grant suite for one package on one user (overlay, usage stats, notifications,
     * background, battery whitelist).
     */
    @JvmStatic
    fun grantSilentPermissions(
        packageName: String,
        userId: Int = USER_PRIMARY,
    ): Boolean {
        if (!hasExistingRootAccess()) {
            logI("grantSilentPermissions skip no-root pkg=$packageName user=$userId")
            emitPermission(
                result = "skip",
                reason = "no_root",
                statusOk = false,
                extra = mapOf("target_package" to packageName),
            )
            return false
        }
        var anySuccess = false
        for (op in SILENT_APPOPS) {
            if (allowPermission(op, packageName, userId)) {
                anySuccess = true
            }
        }
        val privilegedPmGrants = listOf(
            "android.permission.POST_NOTIFICATIONS",
            // Live Updates / ProgressStyle notifyAsPackage on Android 16+ checks this.
            "android.permission.UPDATE_APP_OPS_STATS",
            "android.permission.POST_PROMOTED_NOTIFICATIONS",
        )
        for (permission in privilegedPmGrants) {
            val grant = AppRootAccessFacade.runRootCommand(
                "pm grant --user $userId $packageName $permission",
            )
            if (grant.isSuccess) anySuccess = true
        }
        // deviceidle whitelist is process/global, not per-user, but still helps keep-alives.
        listOf(
            "cmd deviceidle whitelist +$packageName",
            "dumpsys deviceidle whitelist +$packageName",
        ).forEach { AppRootAccessFacade.runRootCommand(it) }
        logI("grantSilentPermissions done pkg=$packageName user=$userId anySuccess=$anySuccess")
        emitPermission(
            result = if (anySuccess) "ok" else "error",
            reason = if (anySuccess) "silent_suite" else "silent_suite_failed",
            statusOk = anySuccess,
            extra = mapOf("target_package" to packageName),
        )
        return anySuccess
    }

    /**
     * Grant silent permissions for xmsf + manager on the given user ids.
     * [USER_AUTO] expands to primary + dual-space when dual packages are present.
     */
    @JvmStatic
    fun grantSilentPermissionsForFramework(
        userId: Int = USER_AUTO,
        packages: Collection<String> = FRAMEWORK_PACKAGES,
    ): Boolean {
        if (!hasExistingRootAccess()) {
            emitPermission(result = "skip", reason = "no_root_framework", statusOk = false)
            return false
        }
        val users = resolveUsers(userId)
        var ok = false
        for (user in users) {
            for (pkg in packages) {
                if (user != USER_PRIMARY && !isPackageInstalledForUser(pkg, user)) {
                    logI("grantSilentPermissionsForFramework skip missing pkg=$pkg user=$user")
                    continue
                }
                if (grantSilentPermissions(pkg, user)) {
                    ok = true
                }
            }
        }
        return ok
    }

    @JvmStatic
    fun lunchAppOps(context: Context, permission: String, tips: CharSequence): Boolean {
        // Prefer root grant for both framework packages (main user + dual if present).
        if (requestRootForUserAction()) {
            val targetPackages = linkedSetOf(
                context.packageName,
                Constants.SERVICE_APP_NAME,
                Constants.MANAGER_APP_NAME,
            )
            var granted = false
            for (pkg in targetPackages) {
                if (allowPermission(permission, pkg, USER_PRIMARY)) {
                    granted = true
                }
                if (isPackageInstalledForUser(pkg, USER_XSPACE) &&
                    allowPermission(permission, pkg, USER_XSPACE)
                ) {
                    granted = true
                }
            }
            if (granted) return true
            Toast.makeText(context, R.string.fail, Toast.LENGTH_SHORT).show()
        }

        if (Utils.isAppOpsInstalled()) {
            val intent = Intent(Intent.ACTION_SHOW_APP_INFO)
                .setClassName("rikka.appops", "rikka.appops.appdetail.AppDetailActivity")
                .putExtra("rikka.appops.intent.extra.USER_HANDLE", Utils.myUserId())
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
    fun requestIgnoreBatteryOptimizations(context: Context): Boolean {
        if (!requestRootForUserAction()) {
            return false
        }
        val packages = linkedSetOf(context.packageName, Constants.SERVICE_APP_NAME, Constants.MANAGER_APP_NAME)
        packages.forEach { pkg ->
            listOf(
                "cmd deviceidle whitelist +$pkg",
                "dumpsys deviceidle whitelist +$pkg",
            ).forEach { AppRootAccessFacade.runRootCommand(it) }
        }
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true ||
            hasCachedRootAccess()
    }

    @JvmStatic
    fun grantNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            emitPermission(result = "ok", reason = "pre_tiramisu")
            return true
        }
        if (!requestRootForUserAction()) {
            emitPermission(result = "skip", reason = "no_root", statusOk = false)
            return false
        }
        val packages = linkedSetOf(context.packageName, Constants.SERVICE_APP_NAME, Constants.MANAGER_APP_NAME)
        for (pkg in packages) {
            for (user in resolveUsers(USER_AUTO)) {
                if (user != USER_PRIMARY && !isPackageInstalledForUser(pkg, user)) continue
                AppRootAccessFacade.runRootCommand(
                    "pm grant --user $user $pkg android.permission.POST_NOTIFICATIONS",
                )
                allowPermission("POST_NOTIFICATION", pkg, user)
                allowPermission("android:post_notification", pkg, user)
            }
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED || hasCachedRootAccess()
        emitPermission(
            result = if (granted) "ok" else "error",
            reason = if (granted) "notification_permission" else "notification_permission_failed",
            statusOk = granted,
            extra = mapOf("target_package" to context.packageName),
        )
        return granted
    }

    private fun resolveUsers(userId: Int): List<Int> {
        if (userId != USER_AUTO) return listOf(userId)
        val users = mutableListOf(USER_PRIMARY)
        if (FRAMEWORK_PACKAGES.any { isPackageInstalledForUser(it, USER_XSPACE) }) {
            users += USER_XSPACE
        }
        return users
    }

    private fun isPackageInstalledForUser(packageName: String, userId: Int): Boolean {
        val result = AppRootAccessFacade.runRootCommand(
            "cmd package list packages --user $userId $packageName",
            timeoutMs = 5_000L,
        )
        if (!result.isSuccess) return false
        val needle = "package:$packageName"
        return result.stdout.any { it.trim() == needle || it.contains(needle) }
    }

    private fun hasExistingRootAccess(): Boolean =
        hasCachedRootAccess() || refreshRootAccessIfGranted()

    private fun requestRootForUserAction(): Boolean =
        hasExistingRootAccess() || requestRootAccess()

    /**
     * Sync manager launcher activity-aliases for [userIds] (desktop icon per user).
     * PackageManager.setComponentEnabledSetting only affects the calling user; dual-space
     * (999) needs root `pm enable/disable --user`.
     *
     * Note: system App Info always uses the package [android:icon] on &lt;application&gt;,
     * which cannot be changed at runtime — only launcher/recents follow aliases.
     */
    @JvmStatic
    fun syncLauncherIconAliases(
        iconId: String,
        userIds: Collection<Int> = listOf(USER_PRIMARY, USER_XSPACE),
    ): Boolean {
        if (!hasExistingRootAccess()) {
            logI("syncLauncherIconAliases skip no-root iconId=$iconId")
            return false
        }
        val selected = when (iconId) {
            "legacy" -> "legacy"
            else -> "default"
        }
        val aliases = linkedMapOf(
            "default" to "io.github.magisk317.mipush.app.ManagerLauncherActivityDefault",
            "legacy" to "io.github.magisk317.mipush.app.ManagerLauncherActivityLegacy",
        )
        val retired = "io.github.magisk317.mipush.app.ManagerLauncherActivityXmsf"
        val pkg = Constants.MANAGER_APP_NAME
        var any = false
        for (user in userIds) {
            if (user != USER_PRIMARY && !isPackageInstalledForUser(pkg, user)) {
                logI("syncLauncherIconAliases skip missing pkg user=$user")
                continue
            }
            for ((id, className) in aliases) {
                val component = "$pkg/$className"
                val cmd = if (id == selected) {
                    "pm enable --user $user $component"
                } else {
                    "pm disable --user $user $component"
                }
                val result = AppRootAccessFacade.runRootCommand(cmd, timeoutMs = 5_000L)
                logI("syncLauncherIconAliases $cmd ok=${result.isSuccess} out=${result.stdoutText.trim()}")
                if (result.isSuccess) any = true
            }
            AppRootAccessFacade.runRootCommand(
                "pm disable --user $user $pkg/$retired",
                timeoutMs = 5_000L,
            )
        }
        return any
    }

}
