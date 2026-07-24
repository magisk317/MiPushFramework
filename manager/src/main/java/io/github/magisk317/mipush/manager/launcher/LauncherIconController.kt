package io.github.magisk317.mipush.manager.launcher

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import io.github.magisk317.mipush.feature.main.MainActivity
import io.github.magisk317.mipush.feature.navigation.AppDestinations

/**
 * Switches the visible desktop icon by enabling exactly one launcher activity-alias.
 * The real [io.github.magisk317.mipush.app.ManagerLauncherActivity] stays enabled for
 * cross-package redirects and LSPosed settings.
 *
 * ## Relaunch policy (icon switch)
 * Product requires **exit then auto-return** (usually Settings).
 *
 * Must NOT `startActivity` then `killProcess` in the same process — that race was confirmed in
 * logcat (`proc died without state saved` + BAL block on Alarm PendingIntent).
 *
 * Working sequence:
 * 1. Persist [pending resume route] so cold start can still land on Settings
 * 2. Ask runtime (xmsf) to schedule root `am start` after ~650ms (bypasses BAL)
 * 3. Also schedule AlarmManager PendingIntent with BAL creator allow (non-root fallback)
 * 4. finishAndRemoveTask only — no in-process startActivity
 * 5. killProcess shortly after
 */
object LauncherIconController {
    const val ICON_DEFAULT = "default"
    const val ICON_LEGACY = "legacy"
    /** @deprecated Kept for preference migration; maps to [ICON_DEFAULT]. */
    const val ICON_XMSF = "xmsf"

    private val selectableAliases = linkedMapOf(
        ICON_DEFAULT to "io.github.magisk317.mipush.app.ManagerLauncherActivityDefault",
        ICON_LEGACY to "io.github.magisk317.mipush.app.ManagerLauncherActivityLegacy",
    )

    private const val RETIRED_XMSF_ALIAS =
        "io.github.magisk317.mipush.app.ManagerLauncherActivityXmsf"

    private const val PREFS_NAME = "launcher_icon_controller"
    private const val KEY_PENDING_RESUME_ROUTE = "pending_resume_route"
    private const val KEY_PENDING_RESUME_TAB = "pending_resume_tab"
    private const val KEY_PENDING_RESUME_UNTIL = "pending_resume_until"
    private const val PENDING_RESUME_TTL_MS = 120_000L
    private const val RELAUNCH_ALARM_DELAY_MS = 800L
    private const val KILL_DELAY_MS = 100L
    private const val RELAUNCH_REQUEST_CODE = 0x4d495055

    fun normalize(iconId: String): String {
        return when (iconId) {
            ICON_LEGACY -> ICON_LEGACY
            ICON_XMSF, ICON_DEFAULT -> ICON_DEFAULT
            else -> ICON_DEFAULT
        }
    }

    fun apply(context: Context, iconId: String) {
        val selected = normalize(iconId)
        val pm = context.packageManager
        for ((id, className) in selectableAliases) {
            val state = if (id == selected) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            pm.setComponentEnabledSetting(
                ComponentName(context.packageName, className),
                state,
                PackageManager.DONT_KILL_APP,
            )
        }
        pm.setComponentEnabledSetting(
            ComponentName(context.packageName, RETIRED_XMSF_ALIAS),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    /**
     * Apply icon, drop tasks, kill process, then auto-relaunch onto [resumeRoute].
     *
     * @param scheduleExternalRelaunch optional hook invoked **before** kill (e.g. Binder → xmsf
     *   root `am start`). Preferred primary relaunch path on rooted devices.
     * @param crossUserSync optional dual-space alias sync; runs before kill.
     */
    fun applyAndRelaunch(
        context: Context,
        iconId: String,
        resumeRoute: String? = AppDestinations.Settings.ROUTE,
        crossUserSync: ((normalizedIconId: String) -> Unit)? = null,
        scheduleExternalRelaunch: ((route: String) -> Unit)? = null,
    ) {
        val selected = normalize(iconId)
        apply(context, selected)
        runCatching { crossUserSync?.invoke(selected) }
        relaunchTo(
            context = context,
            resumeRoute = resumeRoute,
            scheduleExternalRelaunch = scheduleExternalRelaunch,
        )
    }

    /**
     * Exit then auto-return to [resumeRoute] (default Settings).
     */
    fun relaunchTo(
        context: Context,
        resumeRoute: String? = AppDestinations.Settings.ROUTE,
        scheduleExternalRelaunch: ((route: String) -> Unit)? = null,
    ) {
        val appContext = context.applicationContext
        val route = resumeRoute?.takeIf { it.isNotBlank() } ?: AppDestinations.Settings.ROUTE
        persistPendingResume(appContext, route)
        val launchIntent = buildResumeIntent(appContext, route)
        runCatching { scheduleExternalRelaunch?.invoke(route) }
        scheduleRelaunchAlarm(appContext, launchIntent)

        runCatching {
            val am = appContext.getSystemService(ActivityManager::class.java)
            am?.appTasks?.forEach { task ->
                runCatching { task.setExcludeFromRecents(true) }
                runCatching { task.finishAndRemoveTask() }
            }
        }
        if (context is Activity) {
            runCatching { context.finishAndRemoveTask() }
        }
        // Do not startActivity here — same-process start-then-kill loses extras / races BAL.
        Handler(Looper.getMainLooper()).postDelayed({
            Process.killProcess(Process.myPid())
        }, KILL_DELAY_MS)
    }

    /** Drop tasks and kill without auto-return (rare; prefer [relaunchTo]). */
    fun exitOnly(context: Context) {
        val appContext = context.applicationContext
        runCatching {
            val am = appContext.getSystemService(ActivityManager::class.java)
            am?.appTasks?.forEach { task ->
                runCatching { task.setExcludeFromRecents(true) }
                runCatching { task.finishAndRemoveTask() }
            }
        }
        if (context is Activity) {
            runCatching { context.finishAndRemoveTask() }
        }
        Handler(Looper.getMainLooper()).postDelayed({
            Process.killProcess(Process.myPid())
        }, KILL_DELAY_MS)
    }

    fun consumePendingResumeRoute(context: Context): String? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val until = prefs.getLong(KEY_PENDING_RESUME_UNTIL, 0L)
        val route = prefs.getString(KEY_PENDING_RESUME_ROUTE, null)?.takeIf { it.isNotBlank() }
        if (route == null) return null
        if (until > 0L && System.currentTimeMillis() > until) {
            prefs.edit()
                .remove(KEY_PENDING_RESUME_ROUTE)
                .remove(KEY_PENDING_RESUME_TAB)
                .remove(KEY_PENDING_RESUME_UNTIL)
                .commit()
            return null
        }
        prefs.edit()
            .remove(KEY_PENDING_RESUME_ROUTE)
            .remove(KEY_PENDING_RESUME_TAB)
            .remove(KEY_PENDING_RESUME_UNTIL)
            .commit()
        return route
    }

    private fun persistPendingResume(appContext: Context, resumeRoute: String) {
        val tab = if (isSettingsFamilyRoute(resumeRoute)) {
            MainActivity.START_TAB_SETTINGS
        } else {
            null
        }
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_RESUME_ROUTE, resumeRoute)
            .putString(KEY_PENDING_RESUME_TAB, tab)
            .putLong(KEY_PENDING_RESUME_UNTIL, System.currentTimeMillis() + PENDING_RESUME_TTL_MS)
            .commit()
    }

    private fun scheduleRelaunchAlarm(appContext: Context, launchIntent: Intent): Boolean {
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return false
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            PendingIntent.FLAG_IMMUTABLE or
            PendingIntent.FLAG_ONE_SHOT
        val balOptions = buildBalAllowedOptions()
        val pendingIntent = if (balOptions != null) {
            PendingIntent.getActivity(
                appContext,
                RELAUNCH_REQUEST_CODE,
                launchIntent,
                pendingFlags,
                balOptions,
            )
        } else {
            PendingIntent.getActivity(
                appContext,
                RELAUNCH_REQUEST_CODE,
                launchIntent,
                pendingFlags,
            )
        }
        val triggerAt = SystemClock.elapsedRealtime() + RELAUNCH_ALARM_DELAY_MS
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)
                } else {
                    true
                }
                if (canExact) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAt,
                        pendingIntent,
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAt,
                        pendingIntent,
                    )
                }
            } else {
                @Suppress("DEPRECATION")
                alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            }
            true
        }.getOrDefault(false)
    }

    private fun buildBalAllowedOptions(): android.os.Bundle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        return runCatching {
            val options = ActivityOptions.makeBasic()
            options.setPendingIntentCreatorBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
            )
            options.setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
            )
            options.toBundle()
        }.getOrNull()
    }

    fun buildResumeIntent(appContext: Context, resumeRoute: String?): Intent {
        val intent = Intent(appContext, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
        )
        val route = resumeRoute?.takeIf { it.isNotBlank() }
        if (route != null) {
            intent.putExtra(MainActivity.EXTRA_START_ROUTE, route)
            if (isSettingsFamilyRoute(route)) {
                intent.putExtra(MainActivity.EXTRA_START_TAB, MainActivity.START_TAB_SETTINGS)
            }
        }
        return intent
    }

    private fun isSettingsFamilyRoute(route: String): Boolean =
        route.startsWith(AppDestinations.Settings.ROUTE) ||
            route.startsWith(AppDestinations.SettingsSection.ROUTE) ||
            route.startsWith(AppDestinations.StatusBarIconSettings.ROUTE) ||
            route.startsWith(AppDestinations.ConnectionStatus.ROUTE)
}
