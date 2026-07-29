package com.xiaomi.xmsf.push.service.receivers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager
import com.xiaomi.mipush.sdk.MiPushCommandMessage
import com.xiaomi.mipush.sdk.MiPushMessage
import com.xiaomi.mipush.sdk.OcVersionCheckJob
import com.xiaomi.mipush.sdk.PushMessageReceiver
import com.xiaomi.xmsf.stock.StockSurfaceSupport
import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.diagnostics.RateLimitedWarnLogger
import io.github.magisk317.xposed.logging.MagiskOtel
import org.json.JSONObject

/**
 * XMSF's own MiPush callback receiver.
 *
 * Stock 7.4.67-C moved beyond the 3.7.9 shared SDK contract: it routes every non-arrived message in
 * the deprecated all-message callback, handles pass-through `route_type`, refreshes online config,
 * and consumes registration `autoMarkPkgs`. The old product receiver overrode only the specialized
 * pass-through/click callbacks, which missed ordinary messages and made arrived-state correctness
 * impossible to observe.
 */
class MiuiPushMessageReceiver : PushMessageReceiver() {
    override fun onCommandResult(context: Context, miPushCommandMessage: MiPushCommandMessage) {
        logD("onCommandResult $miPushCommandMessage")
        if (miPushCommandMessage.resultCode != 0L) {
            logE(miPushCommandMessage.toString())
            return
        }
        // Stock 7.4.67-C MiuiPushMessageReceiver calls push.service.c after a successful register,
        // but that class's DEX resolves the desired account alias to null and performs no first-run
        // mutation. The old port queried AccountManager and registered the Xiaomi account name as a
        // server alias, creating account-dependent state that stock 7.4.67-C never sends.
    }

    override fun onReceiveMessage(context: Context, miPushMessage: MiPushMessage) {
        val route = appRouteFor(miPushMessage) ?: return
        val intent = Intent(route.action)
            .setPackage(route.packageName)
            .putExtras(miPushMessage.toBundle())
        runCatching {
            if (route.startService) context.startService(intent) else context.sendBroadcast(intent)
        }.onFailure {
            RateLimitedWarnLogger.warn(
                logTag = TAG,
                key = "route:${route.packageName}:${route.action}",
                message = "failed to route XMSF MiPush callback",
                throwable = it,
            )
        }
    }

    override fun onReceivePassThroughMessage(context: Context, miPushMessage: MiPushMessage) {
        when (miPushMessage.extra?.get(EXTRA_ROUTE_TYPE) ?: ROUTE_TASK) {
            ROUTE_TASK -> handleTaskMessage(context, miPushMessage)
            ROUTE_INNER -> context.sendBroadcast(
                Intent(ACTION_INNER_PUSH_MESSAGE)
                    .setPackage(context.packageName)
                    .putExtras(miPushMessage.toBundle()),
            )
        }
    }

    override fun onReceiveRegisterResult(context: Context, miPushCommandMessage: MiPushCommandMessage) {
        if (miPushCommandMessage.resultCode != 0L) return
        miPushCommandMessage.autoMarkPkgs.orEmpty().forEach { packageName ->
            if (packageName.isBlank() || isAlreadyMarked(context, packageName)) return@forEach
            if (!NotificationBadgeSettings.canShowBadge(context, packageName)) return@forEach
            runCatching {
                Settings.Global.putInt(
                    context.contentResolver,
                    "$packageName.superscript_count",
                    1,
                )
            }.onFailure {
                RateLimitedWarnLogger.warn(
                    logTag = TAG,
                    key = "badge:$packageName",
                    message = "failed to initialize stock superscript state",
                    throwable = it,
                )
            }
        }
    }

    private fun handleTaskMessage(context: Context, message: MiPushMessage) {
        when (taskCommandFor(message.content)) {
            TaskCommand.ONLINE_CONFIG_REFRESH -> ScheduledJobManager.getInstance(context)
                .addOneShootJob(OcVersionCheckJob(context.applicationContext))
            TaskCommand.LOG_FETCH -> {
                // Stock p9.l/f uploads a generated archive to Xiaomi's fetch endpoint. The
                // endpoint/auth response stack is not packaged here, so record the unsupported
                // command instead of leaking the product's local diagnostic bundle elsewhere.
                StockSurfaceSupport.recordStatEvent(context, "pass_through:log_fetch:unsupported")
            }
            TaskCommand.UNKNOWN -> Unit
        }
    }

    private fun isAlreadyMarked(context: Context, packageName: String): Boolean {
        return !context.getSharedPreferences(REGISTERED_PACKAGES_PREFS, Context.MODE_PRIVATE)
            .getString(packageName, null)
            .isNullOrEmpty()
    }

    internal data class AppRoute(
        val packageName: String,
        val action: String,
        val startService: Boolean,
    )

    internal enum class TaskCommand {
        ONLINE_CONFIG_REFRESH,
        LOG_FETCH,
        UNKNOWN,
    }

    companion object {
        private const val TAG = "MiuiPushMessageReceiver"
        private const val REGISTERED_PACKAGES_PREFS = "pref_registered_pkg_names"
        private const val EXTRA_MIUI_PACKAGE_NAME = "miui_package_name"
        private const val EXTRA_ROUTE_TYPE = "route_type"
        private const val ROUTE_TASK = "1"
        private const val ROUTE_INNER = "2"
        private const val ACTION_CLICK_MESSAGE = "com.xiaomi.mipush.miui.CLICK_MESSAGE"
        private const val ACTION_RECEIVE_MESSAGE = "com.xiaomi.mipush.miui.RECEIVE_MESSAGE"
        private const val ACTION_INNER_PUSH_MESSAGE = "com.xiaomi.xmsf.inner.PUSH_MESSAGE"

        internal fun appRouteFor(message: MiPushMessage): AppRoute? {
            val packageName = message.extra?.get(EXTRA_MIUI_PACKAGE_NAME)
                ?.takeIf(String::isNotBlank)
                ?: return null
            return AppRoute(
                packageName = packageName,
                action = if (message.isNotified) ACTION_CLICK_MESSAGE else ACTION_RECEIVE_MESSAGE,
                startService = message.isNotified,
            )
        }

        internal fun taskCommandFor(content: String?): TaskCommand {
            if (content.isNullOrBlank()) return TaskCommand.UNKNOWN
            return runCatching { JSONObject(content).optString("CMD") }
                .map { command ->
                    when (command) {
                        "cloud_control_update" -> TaskCommand.ONLINE_CONFIG_REFRESH
                        "log_fetch" -> TaskCommand.LOG_FETCH
                        else -> TaskCommand.UNKNOWN
                    }
                }
                .getOrDefault(TaskCommand.UNKNOWN)
        }
    }
}

private object NotificationBadgeSettings {
    private val SETTINGS_URI = Uri.parse("content://statusbar.notification")
    private const val METHOD_CAN_SHOW_BADGE = "canShowBadge"
    private const val EXTRA_PACKAGE = "package"

    fun canShowBadge(context: Context, packageName: String): Boolean {
        return runCatching {
            val extras = Bundle().apply { putString(EXTRA_PACKAGE, packageName) }
            context.contentResolver.call(
                SETTINGS_URI,
                METHOD_CAN_SHOW_BADGE,
                null,
                extras,
            )?.getBoolean(METHOD_CAN_SHOW_BADGE, false) == true
        }.getOrDefault(false)
    }
}
