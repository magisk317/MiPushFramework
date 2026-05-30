package io.github.magisk317.mipush.service.runtime

import android.content.Context
import android.content.Intent
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.PushServiceConstants
import com.xiaomi.push.service.XMPushServiceMessenger
import com.xiaomi.smack.ConnectionConfiguration
import io.github.magisk317.mipush.app.ConfigCenter
import io.github.magisk317.mipush.app.di.AppDependencies
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.diagnostics.PushHealthSnapshotLogger
import io.github.magisk317.mipush.network.NetworkPolicyCompat
import io.github.magisk317.mipush.platform.support.InternalMessenger
import io.github.magisk317.mipush.platform.support.PermissionUtils
import io.github.magisk317.mipush.runtime.store.entities.RegisteredApplication
import io.github.magisk317.mipush.utils.RegistrationHelper
import kotlinx.coroutines.runBlocking

class RuntimeSettingsAdapter constructor(
    private val appContext: Context,
    private val configCenter: ConfigCenter,
) {
    constructor() : this(
        Utils.getApplication()!!,
        AppDependencies.get(ConfigCenter::class),
    )

    enum class ForceRegisterStage {
        ROOT_MISSING,
        ALL_FAILED,
        COMPLETED,
    }

    data class ForceRegisterOutcome(
        val stage: ForceRegisterStage,
        val successCount: Int,
        val failedCount: Int,
        val unsupportedCount: Int,
    ) {
        val nonSuccessCount: Int
            get() = failedCount + unsupportedCount
    }

    fun startMiPushServiceAsForegroundService(context: Context = appContext) {
        InternalMessenger(context).send(Intent(XMPushServiceMessenger.IntentStartForeground))
    }

    fun sendXmppReconnectRequest(context: Context = appContext) {
        InternalMessenger(context).send(Intent(PushConstants.ACTION_RESET_CONNECTION))
    }

    fun setXmppServer(context: Context = appContext, newHost: String) {
        runBlocking { configCenter.setXMPPServerAsync(newHost) }
        NetworkPolicyCompat.applyXmppHostOverride(context.applicationContext)
        sendXmppReconnectRequest(context)
    }

    fun getXmppServerHint(): String {
        return ConnectionConfiguration.getXmppServerHost() + ":" + PushServiceConstants.XMPP_SERVER_PORT
    }

    fun tryForceRegisterAllApplications(
        context: Context = appContext,
        applications: Collection<RegisteredApplication>,
    ): ForceRegisterOutcome {
        var successCount = 0
        var failedCount = 0
        var unsupportedCount = 0
        val unsupportedReasons = linkedMapOf<String, Int>()
        val unsupportedSamples = linkedMapOf<String, MutableList<String>>()
        val failureTypes = linkedMapOf<String, Int>()

        fun logSnapshot(stage: String) {
            val unsupportedSummary = unsupportedReasons.entries.joinToString(",") { "${it.key}:${it.value}" }
            val sampleSummary = unsupportedSamples.entries.joinToString(";") { "${it.key}=${it.value.joinToString(",")}" }
            val failureSummary = failureTypes.entries.joinToString(",") { "${it.key}:${it.value}" }
            PushHealthSnapshotLogger.log(
                context,
                "RuntimeSettingsAdapter.tryForceRegisterAllApplications",
                "stage=$stage success=$successCount failed=$failedCount unsupported=$unsupportedCount unsupportedReasons=$unsupportedSummary unsupportedSamples=$sampleSummary failureTypes=$failureSummary"
            )
        }

        if (!PermissionUtils.refreshRootAccessIfGranted()) {
            logSnapshot("root_missing")
            return ForceRegisterOutcome(
                stage = ForceRegisterStage.ROOT_MISSING,
                successCount = successCount,
                failedCount = failedCount,
                unsupportedCount = unsupportedCount,
            )
        }

        for (registeredApplication in applications) {
            val packageName = registeredApplication.packageName
            val plan = RegistrationHelper.inspectForceRegisterPlan(packageName)
            if (!plan.supportsServiceDispatch && !plan.supportsReceiverFallback) {
                unsupportedCount++
                unsupportedReasons[plan.reason] = (unsupportedReasons[plan.reason] ?: 0) + 1
                unsupportedSamples.getOrPut(plan.reason) { mutableListOf() }.apply {
                    if (size < 5) add(packageName)
                }
                continue
            }
            try {
                val success = if (plan.supportsServiceDispatch) {
                    RegistrationHelper.tryForceRegister(packageName) ||
                        (plan.supportsReceiverFallback && RegistrationHelper.tryForceRegisterFallback(packageName))
                } else {
                    RegistrationHelper.tryForceRegisterFallback(packageName)
                }
                if (success) {
                    successCount++
                } else {
                    failedCount++
                    failureTypes["FallbackDispatchFailed"] = (failureTypes["FallbackDispatchFailed"] ?: 0) + 1
                }
            } catch (e: UnsupportedOperationException) {
                unsupportedCount++
                unsupportedReasons[plan.reason] = (unsupportedReasons[plan.reason] ?: 0) + 1
                unsupportedSamples.getOrPut(plan.reason) { mutableListOf() }.apply {
                    if (size < 5) add(packageName)
                }
            } catch (_: NoClassDefFoundError) {
                failedCount++
                failureTypes["NoClassDefFoundError"] = (failureTypes["NoClassDefFoundError"] ?: 0) + 1
            } catch (_: ClassNotFoundException) {
                failedCount++
                failureTypes["ClassNotFoundException"] = (failureTypes["ClassNotFoundException"] ?: 0) + 1
            } catch (t: Throwable) {
                failedCount++
                val key = t::class.java.simpleName.ifBlank { "Throwable" }
                failureTypes[key] = (failureTypes[key] ?: 0) + 1
            }
        }

        val stage = if (successCount == 0 && (failedCount > 0 || unsupportedCount > 0)) {
            ForceRegisterStage.ALL_FAILED
        } else {
            ForceRegisterStage.COMPLETED
        }
        logSnapshot(if (stage == ForceRegisterStage.ALL_FAILED) "all_failed" else "completed")
        return ForceRegisterOutcome(
            stage = stage,
            successCount = successCount,
            failedCount = failedCount,
            unsupportedCount = unsupportedCount,
        )
    }
}
