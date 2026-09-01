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
                "stage" to "package_lifecycle",
                "action" to (intent?.action.orEmpty()),
            )
            if (reason != null) attrs["reason"] = reason
            if (!targetPackage.isNullOrBlank()) attrs["target_package"] = targetPackage
            MagiskOtel.event(name = "push.package", attributes = attrs, statusOk = statusOk)
        }

        if (intent == null || !isPackageChangeAction(intent.action)) {
            emit(result = "skip", reason = "ignored")
            return
        }

        val data = intent.data
        if (data == null) {
            emit(result = "skip", reason = "missing_data")
            return
        }

        try {
            val packageName = data.encodedSchemeSpecificPart
            if (packageName.isNullOrBlank()) {
                emit(result = "skip", reason = "missing_package")
                return
            }

            val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            if (intent.action == Intent.ACTION_PACKAGE_REMOVED && replacing) {
                emit(result = "skip", reason = "replacing", targetPackage = packageName)
                return
            }

            if (packageName == Constants.MANAGER_APP_NAME &&
                (intent.action == Intent.ACTION_PACKAGE_ADDED || intent.action == Intent.ACTION_PACKAGE_REMOVED)
            ) {
                XSpaceXmsfInstallKeeper.schedule(context, "PkgUninstallReceiver.${intent.action}")
            }

            if (intent.action == Intent.ACTION_PACKAGE_DATA_CLEARED) {
                // Stock 7.4.67-C PkgActionsReceiver forwards this exact action/key pair. The older
                // project receiver subscribed only to add/remove, so its existing service handler
                // was unreachable from the system broadcast.
                val serviceIntent = Intent(context, com.xiaomi.push.service.XMPushServiceCore::class.java)
                serviceIntent.action = PushServiceConstants.ACTION_PACKAGE_DATA_CLEARED
                serviceIntent.putExtra(PushServiceConstants.EXTRA_DATA_CLEARED_PKG_NAME, packageName)
                PushServiceStarter.start(context, serviceIntent)
                emit(result = "ok", reason = "data_clear_forwarded", targetPackage = packageName)
                return
            }

            if (intent.action != Intent.ACTION_PACKAGE_REMOVED) {
                emit(result = "skip", reason = "non_remove", targetPackage = packageName)
                return
            }

            StalePackagePushGuard.onPackageRemoved(
                context,
                packageName,
                "PkgUninstallReceiver",
                userId = resolveUserId(intent),
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
        action == Intent.ACTION_PACKAGE_ADDED ||
            action == Intent.ACTION_PACKAGE_REMOVED ||
            action == Intent.ACTION_PACKAGE_DATA_CLEARED

    private fun resolveUserId(intent: Intent): Int {
        val uid = intent.getIntExtra(Intent.EXTRA_UID, -1)
        return if (uid >= 0) {
            (uid.toLong() / 100_000L).toInt()
        } else {
            io.github.magisk317.mipush.common.utils.Utils.requireValidUserId(
                io.github.magisk317.mipush.common.utils.Utils.myUserId(),
            )
        }
    }
}
