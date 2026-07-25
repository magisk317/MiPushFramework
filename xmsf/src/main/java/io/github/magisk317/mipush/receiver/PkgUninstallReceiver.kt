package io.github.magisk317.mipush.receiver

import io.github.magisk317.mipush.common.utils.logE

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xiaomi.push.service.PushServiceConstants
import io.github.magisk317.mipush.app.XSpaceXmsfInstallKeeper
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.push.pipeline.StalePackagePushGuard
import io.github.magisk317.mipush.service.PushServiceStarter
import io.github.magisk317.xposed.logging.MagiskOtel

class PkgUninstallReceiver : BroadcastReceiver() {
    private val tag = "PkgUninstallReceiver"

    override fun onReceive(context: Context, intent: Intent?) {
        val startedAt = System.nanoTime()
        fun emit(
            result: String,
            statusOk: Boolean = true,
            reason: String? = null,
            targetPackage: String? = null,
        ) {
            val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
            val attrs = mutableMapOf(
                "result" to result,
                "duration_ms" to durationMs.toString(),
                "process" to "xmsf",
                "stage" to "uninstall",
                "action" to (intent?.action.orEmpty()),
            )
            if (reason != null) attrs["reason"] = reason
            if (!targetPackage.isNullOrBlank()) attrs["target_package"] = targetPackage
            MagiskOtel.event(name = "push.package", attributes = attrs, statusOk = statusOk)
        }

        if (intent == null || intent.extras == null || !isPackageChangeAction(intent.action)) {
            emit(result = "skip", reason = "ignored")
            return
        }

        val replacing = intent.extras?.getBoolean("android.intent.extra.REPLACING") ?: false
        val data = intent.data
        if (data == null || replacing) {
            emit(result = "skip", reason = if (replacing) "replacing" else "missing_data")
            return
        }

        try {
            val packageName = data.encodedSchemeSpecificPart
            if (packageName == Constants.MANAGER_APP_NAME) {
                XSpaceXmsfInstallKeeper.schedule(context, "PkgUninstallReceiver.${intent.action}")
            }
            if (intent.action != Intent.ACTION_PACKAGE_REMOVED) {
                emit(result = "skip", reason = "non_remove", targetPackage = packageName)
                return
            }

            StalePackagePushGuard.onPackageRemoved(
                context,
                packageName,
                "PkgUninstallReceiver",
            )
            val serviceIntent = Intent(context, com.xiaomi.push.service.XMPushServiceCore::class.java)
            serviceIntent.action = PushServiceConstants.ACTION_UNINSTALL
            serviceIntent.putExtra(
                PushServiceConstants.EXTRA_UNINSTALL_PKG_NAME,
                packageName,
            )
            PushServiceStarter.start(context, serviceIntent)
            emit(result = "ok", reason = "uninstall_forwarded", targetPackage = packageName)
        } catch (e: Exception) {
            logE(e.message ?: "error", e)
            emit(result = "error", statusOk = false, reason = e.javaClass.simpleName)
        }
    }

    private fun isPackageChangeAction(action: String?): Boolean =
        action == Intent.ACTION_PACKAGE_ADDED || action == Intent.ACTION_PACKAGE_REMOVED
}
