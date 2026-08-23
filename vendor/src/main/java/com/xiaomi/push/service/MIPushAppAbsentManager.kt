package com.xiaomi.push.service

import android.content.Context
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.smack.XMPPException

object MIPushAppAbsentManager {
    private const val PREF_PENDING_APP_ABSENT = "pref_pending_app_absent"
    private const val PREF_PENDING_REGISTRATION = "pref_pending_registration"

    @JvmStatic
    fun rememberRegisteredPackage(context: Context, packageName: String?, appId: String?) {
        val pkg = packageName?.takeIf { it.isNotBlank() } ?: return
        val resolvedAppId = appId?.takeIf { it.isNotBlank() } ?: return
        context.getSharedPreferences(PushServiceConstants.PREF_KEY_REGISTERED_PKGS, 0)
            .edit()
            .putString(pkg, resolvedAppId)
            .apply()
    }

    @JvmStatic
    fun forgetRegisteredPackage(context: Context, packageName: String) {
        context.getSharedPreferences(PushServiceConstants.PREF_KEY_REGISTERED_PKGS, 0)
            .edit()
            .remove(packageName)
            .apply()
    }

    @JvmStatic
    fun getRememberedAppId(context: Context, packageName: String): String? {
        return context.getSharedPreferences(PushServiceConstants.PREF_KEY_REGISTERED_PKGS, 0)
            .getString(packageName, null)
            ?.takeIf { it.isNotBlank() }
    }

    @JvmStatic
    fun rememberPendingRegistration(context: Context, packageName: String?, appId: String?) {
        val pkg = packageName?.takeIf { it.isNotBlank() } ?: return
        val resolvedAppId = appId?.takeIf { it.isNotBlank() } ?: return
        pendingRegistrationPrefs(context).edit().putString(pkg, resolvedAppId).apply()
    }

    @JvmStatic
    fun getPendingRegistrationAppId(context: Context, packageName: String): String? {
        return pendingRegistrationPrefs(context).getString(packageName, null)?.takeIf { it.isNotBlank() }
    }

    @JvmStatic
    fun forgetPendingRegistration(context: Context, packageName: String) {
        pendingRegistrationPrefs(context).edit().remove(packageName).apply()
    }

    @JvmStatic
    fun queuePendingAppAbsent(context: Context, packageName: String, appId: String) {
        enqueue(context, packageName, appId)
    }

    @JvmStatic
    fun sendOrQueue(pushAction: IPushServiceAction, context: Context, packageName: String, appId: String, source: String) {
        if (!pushAction.isConnected) {
            enqueue(context, packageName, appId)
            if (!pushAction.isConnecting) {
                pushAction.scheduleConnect(true)
            }
            MyLog.w("queue app absent for $packageName source=$source: push is not connected")
            return
        }
        if (!send(pushAction, context, packageName, appId, source)) {
            enqueue(context, packageName, appId)
        }
    }

    @JvmStatic
    fun flushPending(pushAction: IPushServiceAction, context: Context, source: String) {
        if (!pushAction.isConnected) {
            return
        }
        val pending = pendingPrefs(context).all
            .mapNotNull { (pkg, appId) -> pkg.takeIf { it.isNotBlank() }?.let { it to (appId as? String) } }
            .filter { (_, appId) -> !appId.isNullOrBlank() }
        if (pending.isEmpty()) {
            return
        }
        val editor = pendingPrefs(context).edit()
        for ((packageName, appId) in pending) {
            if (send(pushAction, context, packageName, appId!!, source)) {
                editor.remove(packageName)
            } else {
                break
            }
        }
        editor.apply()
    }

    private fun enqueue(context: Context, packageName: String, appId: String) {
        pendingPrefs(context)
            .edit()
            .putString(packageName, appId)
            .apply()
    }

    private fun send(
        pushAction: IPushServiceAction,
        context: Context,
        packageName: String,
        appId: String,
        source: String
    ): Boolean {
        return try {
            MIPushHelper.sendPacket(pushAction, context, MIPushHelper.contructAppAbsentMessage(packageName, appId))
            MyLog.w("uninstall $packageName msg sent source=$source")
            true
        } catch (e: XMPPException) {
            MyLog.e("Fail to send app absent message for $packageName: ${e.message}")
            pushAction.disconnect(10, e)
            false
        }
    }

    private fun pendingPrefs(context: Context) =
        context.getSharedPreferences(PREF_PENDING_APP_ABSENT, 0)

    private fun pendingRegistrationPrefs(context: Context) =
        context.getSharedPreferences(PREF_PENDING_REGISTRATION, 0)
}
