package com.magisk317.utils

import android.content.Context
import android.content.Intent
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import com.magisk317.XMPushUtils
import com.magisk317.compat.PackageManagerCompatBridge
import com.topjohnwu.superuser.Shell
import com.xiaomi.push.sdk.MyPushMessageHandler
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.NotificationType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import android.content.pm.PackageManager
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import top.trumeet.common.utils.Utils

class RegistrationHelper(
    private val context: Context,
    private val packageName: String
) {
    fun removeMiPushXml(): Boolean {
        val result = Shell.cmd(
            String.format(
                "rm $(ls -1" +
                    " /data/user/0/%s/shared_prefs/mipush*.xml" +
                    " /data_mirror/data_ce/null/0/%s/shared_prefs/mipush*.xml" +
                    " 2> /dev/null)",
                packageName,
                packageName
            )
        ).exec()
        return result.isSuccess
    }

    fun deleteRegistrationInfoAndRetryForceRegister() {
        removeMiPushXml()
        MyPushMessageHandler.launchApp(context, createForceRegisterMessage(packageName))
        tryForceRegister(packageName)
    }

    companion object {
        data class ForceRegisterPlan(
            val packageName: String,
            val available: Boolean,
            val reason: String,
            val serviceCandidates: Set<String>,
            val receiverCandidates: Set<String>,
            val bridgeCandidates: Set<String>
        ) {
            val supportsServiceDispatch: Boolean
                get() = serviceCandidates.isNotEmpty()

            val supportsReceiverFallback: Boolean
                get() = receiverCandidates.isNotEmpty()

            fun summary(): String {
                val parts = mutableListOf("reason=$reason")
                if (serviceCandidates.isNotEmpty()) {
                    parts += "services=${serviceCandidates.joinToString(",")}"
                }
                if (receiverCandidates.isNotEmpty()) {
                    parts += "receivers=${receiverCandidates.joinToString(",")}"
                }
                if (bridgeCandidates.isNotEmpty()) {
                    parts += "bridges=${bridgeCandidates.joinToString(",")}"
                }
                return parts.joinToString(" ")
            }
        }

        private val logger = object {
            fun i(msg: String) = Napier.i(msg, tag = "RegistrationHelper")
            fun w(msg: String) = Napier.w(msg, tag = "RegistrationHelper")
        }

        private val directServiceCandidates = linkedSetOf(
            "com.xiaomi.mipush.sdk.PushMessageHandler",
            "com.xiaomi.mipush.sdk.MessageHandleService",
            "com.xiaomi.push.service.XMPushService",
            "com.xiaomi.push.service.XMJobService"
        )

        private val directRuntimeServiceCandidates = linkedSetOf(
            "com.xiaomi.push.service.XMPushService",
            "com.xiaomi.push.service.XMJobService"
        )

        private val directHandlerCandidates = linkedSetOf(
            "com.xiaomi.mipush.sdk.PushMessageHandler",
            "com.xiaomi.mipush.sdk.MessageHandleService"
        )

        private val directReceiverCandidates = linkedSetOf(
            "com.xiaomi.mipush.sdk.PushServiceReceiver",
            "com.xiaomi.push.service.receivers.PingReceiver",
            "com.xiaomi.mipush.sdk.PushMessageReceiver"
        )

        private val bridgeReceiverHints = linkedSetOf(
            "MiuiPushReceiver",
            "XiaoMiPushReceiver",
            "XiaomiPushReceiver",
            "com.igexin",
            "umeng",
            "HeytapPush",
            "HmsMessageService",
            "MzPush",
            "XGPush"
        )

        @JvmStatic
        fun classifyForceRegisterPlan(
            packageName: String,
            serviceNames: Set<String>,
            receiverNames: Set<String>
        ): ForceRegisterPlan {
            val matchedServices = directServiceCandidates.filterTo(linkedSetOf()) { it in serviceNames }
            val matchedReceivers = directReceiverCandidates.filterTo(linkedSetOf()) { it in receiverNames }
            val matchedBridges = bridgeReceiverHints.filterTo(linkedSetOf()) { hint ->
                receiverNames.any { it.contains(hint, ignoreCase = true) }
            }
            if (matchedServices.isNotEmpty()) {
                return ForceRegisterPlan(packageName, true, "direct_sdk", matchedServices, matchedReceivers, matchedBridges)
            }
            if (matchedReceivers.isNotEmpty()) {
                return ForceRegisterPlan(packageName, false, "receiver_only", emptySet(), matchedReceivers, matchedBridges)
            }
            if (matchedBridges.isNotEmpty()) {
                return ForceRegisterPlan(packageName, false, "bridge_wrapper", emptySet(), emptySet(), matchedBridges)
            }
            return ForceRegisterPlan(packageName, false, "unsupported_components", emptySet(), emptySet(), emptySet())
        }

        @JvmStatic
        fun classifyDisplayTypeReason(
            serviceNames: Set<String>,
            receiverNames: Set<String>
        ): String {
            val matchedRuntimeServices = directRuntimeServiceCandidates.any { it in serviceNames }
            val matchedHandlers = directHandlerCandidates.any { it in serviceNames }
            val matchedReceivers = directReceiverCandidates.any { it in receiverNames }
            val matchedBridges = bridgeReceiverHints.any { hint ->
                serviceNames.any { it.contains(hint, ignoreCase = true) } ||
                    receiverNames.any { it.contains(hint, ignoreCase = true) }
            }

            return when {
                matchedRuntimeServices -> "direct_sdk"
                matchedBridges && (matchedHandlers || matchedReceivers) -> "bridge_wrapper"
                matchedHandlers || matchedReceivers -> "receiver_only"
                else -> "unsupported_components"
            }
        }

        @JvmStatic
        fun inspectForceRegisterPlan(packageName: String): ForceRegisterPlan {
            val app = Utils.getApplication()
                ?: return ForceRegisterPlan(packageName, false, "application_unavailable", emptySet(), emptySet(), emptySet())
            val packageInfo = try {
                PackageManagerCompatBridge.getPackageInfo(
                    app.packageManager,
                    packageName,
                    PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS
                )
            } catch (_: PackageManager.NameNotFoundException) {
                null
            } ?: return ForceRegisterPlan(packageName, false, "package_not_found", emptySet(), emptySet(), emptySet())
            val serviceNames = packageInfo.services
                ?.mapNotNull { it.name }
                ?.toSet()
                ?: emptySet()
            val receiverNames = packageInfo.receivers
                ?.mapNotNull { it.name }
                ?.toSet()
                ?: emptySet()
            return classifyForceRegisterPlan(packageName, serviceNames, receiverNames)
        }

        @JvmStatic
        fun tryForceRegisterFallback(packageName: String): Boolean {
            val plan = inspectForceRegisterPlan(packageName)
            if (!plan.supportsReceiverFallback && !plan.supportsServiceDispatch) {
                logger.w("skip force register fallback for $packageName: ${plan.summary()}")
                return false
            }
            val msgBytes = runCatching {
                XMPushUtils.packToBytes(createForceRegisterMessage(packageName))
            }.getOrNull() ?: return false
            val intent = Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE).apply {
                `package` = packageName
                putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, msgBytes)
                putExtra(PushConstants.MESSAGE_RECEIVE_TIME, System.currentTimeMillis())
            }
            Utils.getApplication()?.sendBroadcast(intent, null)
            return true
        }

        @JvmStatic
        fun tryForceRegister(packageName: String) {
            val app = Utils.getApplication() ?: return
            val plan = inspectForceRegisterPlan(packageName)
            if (!plan.supportsServiceDispatch) {
                throw UnsupportedOperationException("force register unsupported for $packageName: ${plan.summary()}")
            }
            val container = createForceRegisterMessage(packageName)
            val msgBytes = XMPushUtils.packToBytes(container)
            // Prefer direct handler dispatch without launching/settings guidance side effects.
            val started = runCatching {
                MyPushMessageHandler.forwardToTargetApplication(app, msgBytes)
            }.getOrNull()
            if (started != null) {
                logger.i("force register via PushMessageHandler succeeded: $packageName")
                return
            }
            // Fallback to package-targeted broadcast for apps with nonstandard handlers.
            val intent = Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE).apply {
                `package` = packageName
                putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, msgBytes)
                putExtra(PushConstants.MESSAGE_RECEIVE_TIME, System.currentTimeMillis())
            }
            app.sendBroadcast(intent, null)
            logger.w("force register fell back to broadcast only: $packageName")
        }

        @JvmStatic
        fun createForceRegisterMessage(packageName: String): XmPushActionContainer {
            val id = "fake_expired_${packageName}_${System.currentTimeMillis()}"
            val regIdExpiredNotification = XmPushActionNotification().apply {
                type = NotificationType.RegIdExpired.value
                setId(id)
            }
            val metaInfo = PushMetaInfo().apply { setId(id) }
            val regIdExpiredContainer = XMPushUtils.packToContainer(regIdExpiredNotification, packageName)
            regIdExpiredContainer.metaInfo = metaInfo
            return regIdExpiredContainer
        }
    }
}
