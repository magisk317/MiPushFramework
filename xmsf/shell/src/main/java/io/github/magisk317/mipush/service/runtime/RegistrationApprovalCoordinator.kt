package io.github.magisk317.mipush.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.XMPushServiceCore
import com.xiaomi.xmsf.R
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.manager.application.ManagerRegistrationResult
import io.github.magisk317.mipush.platform.support.ManagerUiEntryPoints
import io.github.magisk317.mipush.push.bridge.PushShellBridgeHolder
import io.github.magisk317.mipush.runtime.PushRuntimePendingPacketStore
import io.github.magisk317.mipush.runtime.PushRuntimeRegistrationTaskStore
import io.github.magisk317.mipush.runtime.android.PackageRegistrationGuard
import io.github.magisk317.mipush.runtime.core.RegistrationThrottle
import io.github.magisk317.mipush.runtime.core.registration.RegistrationPolicyDecision
import io.github.magisk317.mipush.runtime.core.registration.RegistrationPolicyGate
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import io.github.magisk317.mipush.runtime.store.kmp.RegisteredAppType
import io.github.magisk317.mipush.runtime.store.kmp.RuntimePendingRegistrationRow
import io.github.magisk317.xposed.logging.XLog

/** Product admission point. Only genuine registration requests can create pending approval. */
object RegistrationApprovalCoordinator {
    private const val CHANNEL = "registration_approval"
    private const val NOTIFICATION_ID = 317

    fun intercept(context: Context, packageName: String?, payload: ByteArray?, envChanged: Boolean, envType: Int): Boolean {
        if (packageName.isNullOrBlank() || payload == null || payload.isEmpty()) return true
        val userId = Utils.requireValidUserId(Utils.myUserId())
        return PackageRegistrationGuard.withPackage(packageName, userId) {
            val row = RegisteredApplicationDb.getRegisteredApplication(packageName)
                ?: RegisteredApplicationDb.registerApplication(packageName)
            when (RegistrationPolicyGate.decide(row.type, row.blocked)) {
                RegistrationPolicyDecision.Allow -> {
                    RegisteredApplicationDb.clearPendingRegistration(packageName, userId)
                    false
                }
                RegistrationPolicyDecision.Deny -> true
                RegistrationPolicyDecision.Ask -> {
                    val first = RegisteredApplicationDb.getPendingRegistration(packageName, userId) == null
                    RegisteredApplicationDb.savePendingRegistration(
                        RuntimePendingRegistrationRow(userId, packageName, payload, envChanged, envType, System.currentTimeMillis()),
                    )
                    if (first) showPrompt(context, packageName)
                    true
                }
            }
        }
    }

    fun setPolicy(context: Context, packageName: String, type: Int): ManagerRegistrationResult {
        if (type == RegisteredAppType.DENY) return RegistrationRevocationCoordinator.deny(context, packageName)
        if (type !in setOf(RegisteredAppType.ASK, RegisteredAppType.ALLOW, RegisteredAppType.DENY)) {
            return ManagerRegistrationResult(false, context.getString(R.string.registration_policy_invalid))
        }
        val userId = Utils.requireValidUserId(Utils.myUserId())
        return PackageRegistrationGuard.withPackage(packageName, userId) {
            if (RegistrationRevocationJournal.hasPending(context, packageName, userId)) {
                return@withPackage ManagerRegistrationResult(false, context.getString(R.string.unregister_cleanup_pending))
            }
            RegisteredApplicationDb.getRegisteredApplication(packageName)
                ?: RegisteredApplicationDb.registerApplication(packageName)
            if (type == RegisteredAppType.ALLOW && RegisteredApplicationDb.getPendingRegistration(packageName, userId) != null) {
                return@withPackage approveLocked(context, packageName, userId)
            }
            if (type == RegisteredAppType.ALLOW &&
                !PushShellBridgeHolder.registration().flushAppAbsentBeforeRegister(context, packageName)) {
                return@withPackage ManagerRegistrationResult(false, context.getString(R.string.unregister_pending))
            }
            val updated = RegisteredApplicationDb.setRegistrationPolicy(packageName, type, userId)
            if (updated && type != RegisteredAppType.ALLOW) {
                PushRuntimePendingPacketStore.discardPackage(packageName, userId)
                PushRuntimeRegistrationTaskStore.clear(packageName, userId)
            }
            if (updated && type != RegisteredAppType.ASK) dismissPrompt(context, packageName)
            ManagerRegistrationResult(updated, context.getString(if (updated) R.string.registration_policy_saved else R.string.registration_policy_failed))
        }
    }

    fun approve(context: Context, packageName: String): ManagerRegistrationResult {
        val userId = Utils.requireValidUserId(Utils.myUserId())
        return PackageRegistrationGuard.withPackage(packageName, userId) { approveLocked(context, packageName, userId) }
    }

    private fun approveLocked(context: Context, packageName: String, userId: Int): ManagerRegistrationResult {
        if (RegistrationRevocationJournal.hasPending(context, packageName, userId)) {
            return ManagerRegistrationResult(false, context.getString(R.string.unregister_cleanup_pending))
        }
        val pending = RegisteredApplicationDb.getPendingRegistration(packageName, userId)
            ?: return ManagerRegistrationResult(false, context.getString(R.string.registration_request_missing))
        if (!PushShellBridgeHolder.registration().flushAppAbsentBeforeRegister(context, packageName)) {
            return ManagerRegistrationResult(false, context.getString(R.string.unregister_pending))
        }
        if (!RegisteredApplicationDb.setRegistrationPolicy(packageName, RegisteredAppType.ALLOW, userId, clearPending = false)) {
            return ManagerRegistrationResult(false, context.getString(R.string.registration_policy_failed))
        }
        RegistrationThrottle.reset(packageName)
        val accepted = runCatching {
            val intent = Intent(context, XMPushServiceCore::class.java).apply {
                action = PushConstants.MIPUSH_ACTION_REGISTER_APP
                putExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE, packageName)
                putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, pending.payload)
                putExtra(PushConstants.MIPUSH_EXTRA_ENV_CHANAGE, pending.envChanged)
                putExtra(PushConstants.MIPUSH_EXTRA_ENV_TYPE, pending.envType)
            }
            context.startService(intent) != null
        }.onFailure { XLog.w("RegistrationApproval: approved request dispatch failed", it) }.getOrDefault(false)
        if (!accepted) {
            RegisteredApplicationDb.setRegistrationPolicy(packageName, RegisteredAppType.ASK, userId)
            RegisteredApplicationDb.savePendingRegistration(pending)
            return ManagerRegistrationResult(false, context.getString(R.string.registration_dispatch_failed))
        }
        dismissPrompt(context, packageName)
        return ManagerRegistrationResult(true, context.getString(R.string.registration_request_approved))
    }

    private fun showPrompt(context: Context, packageName: String) {
        runCatching {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.registration_approval_channel), NotificationManager.IMPORTANCE_DEFAULT))
            val intent = ManagerUiEntryPoints.applicationInfoIntent(context, packageName, true)
                .setData(android.net.Uri.parse("mipush-manager://registration/$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pendingIntent = PendingIntent.getActivity(context, packageName.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val label = runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString() }.getOrDefault(packageName)
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(io.github.magisk317.mipush.common.R.drawable.ic_notifications_black_24dp)
                .setContentTitle(context.getString(R.string.registration_approval_title, label))
                .setContentText(context.getString(R.string.registration_approval_message))
                .setContentIntent(pendingIntent).setAutoCancel(true).setOnlyAlertOnce(true).build()
            manager.notify(packageName, NOTIFICATION_ID, notification)
        }.onFailure { XLog.w("RegistrationApproval: prompt unavailable; request remains in manager", it) }
    }

    internal fun dismissPrompt(context: Context, packageName: String) {
        runCatching { (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(packageName, NOTIFICATION_ID) }
            .onFailure { XLog.w("RegistrationApproval: dismiss prompt failed", it) }
    }
}
