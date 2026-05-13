package io.github.magisk317.mipush.utils

import android.content.Context
import android.content.Intent
import io.github.aakira.napier.Napier
import io.github.magisk317.mipush.platform.support.XMPushUtils
import io.github.magisk317.mipush.common.compat.PackageManagerCompatBridge
import com.xiaomi.push.sdk.MyPushMessageHandler
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.NotificationType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.common.Constants.PUSH_MESSAGE_HANDLER_CLASS
import io.github.magisk317.mipush.common.Constants.MESSAGE_HANDLE_SERVICE_CLASS
import io.github.magisk317.mipush.common.Constants.XM_PUSH_SERVICE_CLASS
import io.github.magisk317.mipush.common.Constants.XM_JOB_SERVICE_CLASS
import io.github.magisk317.mipush.common.Constants.PUSH_SERVICE_RECEIVER_CLASS
import io.github.magisk317.mipush.common.Constants.PING_RECEIVER_CLASS
import io.github.magisk317.mipush.common.Constants.PUSH_MESSAGE_RECEIVER_CLASS
import android.content.pm.PackageManager
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import io.github.magisk317.mipush.platform.support.AppRootAccessFacade
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.runtime.core.PushRuntime
import io.github.magisk317.mipush.runtime.store.db.EventDb
import io.github.magisk317.mipush.runtime.store.entities.Event
import io.github.magisk317.mipush.runtime.store.event.type.RegistrationType
import kotlinx.coroutines.runBlocking

class RegistrationHelper(
    private val context: Context,
    private val packageName: String
) {
    data class ComponentDispatchInfo(
        val name: String,
        val enabled: Boolean,
        val exported: Boolean
    )

    fun removeMiPushData(): Boolean {
        val result = AppRootAccessFacade.runRootCommand(
            String.format(
                "rm -rf $(ls -1" +
                    " /data/user/0/%s/shared_prefs/mipush*.xml" +
                    " /data_mirror/data_ce/null/0/%s/shared_prefs/mipush*.xml" +
                    " /data/user/0/%s/files/keva/repo/mipush" +
                    " /data/user/0/%s/files/keva/repo/mipush*" +
                    " /data_mirror/data_ce/null/0/%s/files/keva/repo/mipush" +
                    " /data_mirror/data_ce/null/0/%s/files/keva/repo/mipush*" +
                    " 2> /dev/null)",
                packageName,
                packageName,
                packageName,
                packageName,
                packageName,
                packageName
            )
        )
        logger.i("remove mipush data for $packageName success=${result.isSuccess}")
        return result.isSuccess
    }

    fun deleteRegistrationInfoAndRetryForceRegister() {
        removeMiPushData()
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
            val bridgeCandidates: Set<String>,
            val blockedServiceCandidates: Set<String> = emptySet(),
            val blockedReceiverCandidates: Set<String> = emptySet()
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
                if (blockedServiceCandidates.isNotEmpty()) {
                    parts += "blockedServices=${blockedServiceCandidates.joinToString(",")}"
                }
                if (blockedReceiverCandidates.isNotEmpty()) {
                    parts += "blockedReceivers=${blockedReceiverCandidates.joinToString(",")}"
                }
                return parts.joinToString(" ")
            }
        }

        private val logger = object {
            fun i(msg: String) = Napier.i(msg, tag = "RegistrationHelper")
            fun w(msg: String) = Napier.w(msg, tag = "RegistrationHelper")
        }

        private val directServiceCandidates = linkedSetOf(
            PUSH_MESSAGE_HANDLER_CLASS,
            MESSAGE_HANDLE_SERVICE_CLASS,
            XM_PUSH_SERVICE_CLASS,
            XM_JOB_SERVICE_CLASS
        )

        private val externallyDispatchableServiceCandidates = linkedSetOf(
            PUSH_MESSAGE_HANDLER_CLASS
        )

        private val directRuntimeServiceCandidates = linkedSetOf(
            XM_PUSH_SERVICE_CLASS,
            XM_JOB_SERVICE_CLASS
        )

        private val directHandlerCandidates = linkedSetOf(
            PUSH_MESSAGE_HANDLER_CLASS,
            MESSAGE_HANDLE_SERVICE_CLASS
        )

        private val directReceiverCandidates = linkedSetOf(
            PUSH_SERVICE_RECEIVER_CLASS,
            PING_RECEIVER_CLASS,
            PUSH_MESSAGE_RECEIVER_CLASS
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

        private fun canDispatchAcrossPackages(
            sourcePackageName: String,
            targetPackageName: String,
            component: ComponentDispatchInfo
        ): Boolean {
            return component.enabled && (component.exported || sourcePackageName == targetPackageName)
        }

        internal fun resolveForceRegisterPlan(
            packageName: String,
            serviceInfos: Set<ComponentDispatchInfo>,
            receiverInfos: Set<ComponentDispatchInfo>,
            sourcePackageName: String
        ): ForceRegisterPlan {
            val matchedServices = externallyDispatchableServiceCandidates.filterTo(linkedSetOf()) { candidate ->
                serviceInfos.any { it.name == candidate && canDispatchAcrossPackages(sourcePackageName, packageName, it) }
            }
            val blockedServices = externallyDispatchableServiceCandidates.filterTo(linkedSetOf()) { candidate ->
                serviceInfos.any { it.name == candidate } && candidate !in matchedServices
            }
            val matchedReceivers = directReceiverCandidates.filterTo(linkedSetOf()) { candidate ->
                receiverInfos.any { it.name == candidate && canDispatchAcrossPackages(sourcePackageName, packageName, it) }
            }
            val blockedReceivers = directReceiverCandidates.filterTo(linkedSetOf()) { candidate ->
                receiverInfos.any { it.name == candidate } && candidate !in matchedReceivers
            }
            val matchedBridges = bridgeReceiverHints.filterTo(linkedSetOf()) { hint ->
                serviceInfos.any { it.name.contains(hint, ignoreCase = true) } ||
                    receiverInfos.any { it.name.contains(hint, ignoreCase = true) }
            }
            return when {
                matchedServices.isNotEmpty() -> ForceRegisterPlan(
                    packageName = packageName,
                    available = true,
                    reason = "direct_sdk",
                    serviceCandidates = matchedServices,
                    receiverCandidates = matchedReceivers,
                    bridgeCandidates = matchedBridges,
                    blockedServiceCandidates = blockedServices,
                    blockedReceiverCandidates = blockedReceivers
                )

                matchedReceivers.isNotEmpty() -> ForceRegisterPlan(
                    packageName = packageName,
                    available = false,
                    reason = "receiver_only",
                    serviceCandidates = emptySet(),
                    receiverCandidates = matchedReceivers,
                    bridgeCandidates = matchedBridges,
                    blockedServiceCandidates = blockedServices,
                    blockedReceiverCandidates = blockedReceivers
                )

                blockedServices.isNotEmpty() || blockedReceivers.isNotEmpty() -> ForceRegisterPlan(
                    packageName = packageName,
                    available = false,
                    reason = "internal_only_components",
                    serviceCandidates = emptySet(),
                    receiverCandidates = emptySet(),
                    bridgeCandidates = matchedBridges,
                    blockedServiceCandidates = blockedServices,
                    blockedReceiverCandidates = blockedReceivers
                )

                matchedBridges.isNotEmpty() -> ForceRegisterPlan(
                    packageName = packageName,
                    available = false,
                    reason = "bridge_wrapper",
                    serviceCandidates = emptySet(),
                    receiverCandidates = emptySet(),
                    bridgeCandidates = matchedBridges
                )

                else -> ForceRegisterPlan(
                    packageName = packageName,
                    available = false,
                    reason = "unsupported_components",
                    serviceCandidates = emptySet(),
                    receiverCandidates = emptySet(),
                    bridgeCandidates = emptySet()
                )
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
            val serviceInfos = packageInfo.services
                ?.mapNotNull { info ->
                    info.name?.let {
                        ComponentDispatchInfo(
                            name = it,
                            enabled = info.enabled,
                            exported = info.exported
                        )
                    }
                }
                ?.toSet()
                ?: emptySet()
            val receiverInfos = packageInfo.receivers
                ?.mapNotNull { info ->
                    info.name?.let {
                        ComponentDispatchInfo(
                            name = it,
                            enabled = info.enabled,
                            exported = info.exported
                        )
                    }
                }
                ?.toSet()
                ?: emptySet()
            return resolveForceRegisterPlan(
                packageName = packageName,
                serviceInfos = serviceInfos,
                receiverInfos = receiverInfos,
                sourcePackageName = app.packageName
            )
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
            
            val dispatched = XMPushUtils.dispatchToApplication(Utils.getApplication() ?: return false, packageName, msgBytes)
            if (dispatched) {
                PushRuntime.observeRegistrationRequest(
                    packageName,
                    "RegistrationHelper.tryForceRegisterFallback",
                    "force_trigger_fallback"
                )
                runBlocking {
                    EventDb.insertEventAsync(Event.ResultType.OK, RegistrationType("force_trigger_fallback", packageName, null))
                }
                logger.i("force register fallback for $packageName dispatched")
            } else {
                logger.w("force register fallback for $packageName failed")
            }
            return dispatched
        }

        @JvmStatic
        fun tryForceRegister(packageName: String): Boolean {
            val app = Utils.getApplication() ?: return false
            val plan = inspectForceRegisterPlan(packageName)
            if (!plan.supportsServiceDispatch) {
                throw UnsupportedOperationException("force register unsupported for $packageName: ${plan.summary()}")
            }

            val container = createForceRegisterMessage(packageName)
            val msgBytes = XMPushUtils.packToBytes(container)
            
            val dispatched = XMPushUtils.dispatchToApplication(app, packageName, msgBytes)
            if (dispatched) {
                PushRuntime.observeRegistrationRequest(
                    packageName,
                    "RegistrationHelper.tryForceRegister",
                    "force_trigger"
                )
                runBlocking {
                    EventDb.insertEventAsync(Event.ResultType.OK, RegistrationType("force_trigger", packageName, null))
                }
                logger.i("force register for $packageName dispatched")
            } else {
                logger.w("force register for $packageName failed to dispatch")
            }
            return dispatched
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
