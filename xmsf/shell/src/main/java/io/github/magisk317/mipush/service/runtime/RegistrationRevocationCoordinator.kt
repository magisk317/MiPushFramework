package io.github.magisk317.mipush.service

import android.content.Context
import com.xiaomi.xmsf.R
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.compat.RegistrationStateStore
import io.github.magisk317.mipush.manager.application.ManagerRegistrationResult
import io.github.magisk317.mipush.platform.support.PermissionUtils
import io.github.magisk317.mipush.push.bridge.PushShellBridgeHolder
import io.github.magisk317.mipush.push.pipeline.MiPushRuntimeBridge
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.runtime.PushRuntimePendingPacketStore
import io.github.magisk317.mipush.runtime.PushRuntimeRegistrationTaskStore
import io.github.magisk317.mipush.runtime.android.PackageRegistrationGuard
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import io.github.magisk317.mipush.runtime.store.kmp.RegisteredAppRegisteredType
import io.github.magisk317.mipush.runtime.store.kmp.RegisteredAppType
import io.github.magisk317.mipush.utils.RegistrationHelper
import io.github.magisk317.xposed.logging.XLog

/** Deny is the single entry for revoking an existing registration and blocking future requests. */
internal object RegistrationRevocationCoordinator {
    fun deny(context: Context, packageName: String): ManagerRegistrationResult {
        val userId = Utils.requireValidUserId(Utils.myUserId())
        return PackageRegistrationGuard.withPackage(packageName, userId) {
            val application = RegisteredApplicationDb.getRegisteredApplication(packageName)
                ?: RegisteredApplicationDb.registerApplication(packageName)
            if (!RegisteredApplicationDb.setRegistrationPolicy(packageName, RegisteredAppType.DENY, userId)) {
                return@withPackage result(context, false, R.string.registration_policy_failed)
            }
            // Revoke drained snapshots before any fallible report, prompt, or root cleanup.
            PushRuntimePendingPacketStore.discardPackage(packageName, userId)
            PushRuntimeRegistrationTaskStore.clear(packageName, userId)
            RegistrationApprovalCoordinator.dismissPrompt(context, packageName)
            val registration = PushShellBridgeHolder.registration()
            registration.forgetPendingRegistration(context, packageName)
            val appId = RegistrationRevocationJournal.pendingAppId(context, packageName, userId)
                ?: registration.rememberedAppId(context, packageName)
            val needsRevocation = application.registeredType == RegisteredAppRegisteredType.Registered ||
                RegistrationRevocationJournal.hasPending(context, packageName, userId)
            if (!needsRevocation) return@withPackage result(context, true, R.string.registration_denied)
            if (appId.isNullOrBlank()) return@withPackage result(context, false, R.string.unregister_app_id_missing)
            if (!RegistrationRevocationJournal.begin(context, packageName, userId, appId)) {
                return@withPackage result(context, false, R.string.unregister_journal_failed)
            }
            val accepted = runCatching {
                registration.reportAppAbsent(context, packageName, appId, "manager_deny")
            }.getOrDefault(false)
            if (!accepted) return@withPackage result(context, false, R.string.unregister_report_failed)
            if (!runCatching { PermissionUtils.requestRootAccess() }.getOrDefault(false)) {
                return@withPackage result(context, false, R.string.unregister_requires_root)
            }
            val wiped = runCatching { RegistrationHelper(context, packageName).removeMiPushData(userId) }
                .getOrDefault(false)
            if (!wiped) return@withPackage result(context, false, R.string.unregister_partial)
            val cleaned = runCatching {
                Utils.removeRegSec(packageName, userId)
                Utils.removeLastReceiveTime(packageName, userId)
                PushRuntime.clearPackageTransientState(packageName, userId)
                MiPushRuntimeBridge.clearPackageTransientState(packageName, userId)
                check(PushShellBridgeHolder.packageState().clearPackageDataShellState(context, packageName, userId) == 0)
                registration.markPackageUnregistered(context, packageName)
                RegistrationStateStore.updateIfChanged(
                    application = RegisteredApplicationDb.getRegisteredApplication(packageName) ?: application,
                    nextType = RegisteredAppRegisteredType.Unregistered,
                    source = RegistrationStateStore.Source.MANUAL,
                )
                registration.forgetRegisteredPackage(context, packageName)
                PushRuntime.observeUnregistration(
                    packageName = packageName,
                    source = "RegistrationRevocationCoordinator.deny",
                    reason = "manual_deny",
                    androidUserId = userId,
                )
                RegistrationRevocationJournal.complete(context, packageName, userId)
            }.onFailure { XLog.w("Registration revocation local cleanup incomplete", it) }.getOrDefault(false)
            if (!cleaned) return@withPackage result(context, false, R.string.unregister_local_cleanup_failed)
            result(context, true, if (registration.hasPendingAppAbsent(context, packageName)) R.string.unregister_queued else R.string.unregister_completed)
        }
    }

    private fun result(context: Context, succeeded: Boolean, message: Int) =
        ManagerRegistrationResult(succeeded, context.getString(message))
}
