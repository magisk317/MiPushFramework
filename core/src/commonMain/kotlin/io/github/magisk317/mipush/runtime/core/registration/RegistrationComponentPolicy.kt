package io.github.magisk317.mipush.runtime.core.registration

data class RegistrationComponentInfo(
    val name: String,
    val enabled: Boolean,
    val exported: Boolean
)

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

/** Platform-neutral component recognition and cross-package dispatch policy. */
object RegistrationComponentPolicy {
    private const val PUSH_MESSAGE_HANDLER_CLASS = "com.xiaomi.mipush.sdk.PushMessageHandler"
    private const val MESSAGE_HANDLE_SERVICE_CLASS = "com.xiaomi.mipush.sdk.MessageHandleService"
    private const val XM_PUSH_SERVICE_CLASS = "com.xiaomi.push.service.XMPushService"
    private const val XM_JOB_SERVICE_CLASS = "com.xiaomi.push.service.XMJobService"
    private const val PUSH_SERVICE_RECEIVER_CLASS = "com.xiaomi.mipush.sdk.PushServiceReceiver"
    private const val PING_RECEIVER_CLASS = "com.xiaomi.push.service.receivers.PingReceiver"
    private const val PUSH_MESSAGE_RECEIVER_CLASS = "com.xiaomi.mipush.sdk.PushMessageReceiver"

    private val directServiceCandidates = linkedSetOf(
        PUSH_MESSAGE_HANDLER_CLASS,
        MESSAGE_HANDLE_SERVICE_CLASS,
        XM_PUSH_SERVICE_CLASS,
        XM_JOB_SERVICE_CLASS
    )
    private val externallyDispatchableServiceCandidates = linkedSetOf(PUSH_MESSAGE_HANDLER_CLASS)
    private val directRuntimeServiceCandidates = linkedSetOf(XM_PUSH_SERVICE_CLASS, XM_JOB_SERVICE_CLASS)
    private val directHandlerCandidates = linkedSetOf(PUSH_MESSAGE_HANDLER_CLASS, MESSAGE_HANDLE_SERVICE_CLASS)
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
        return when {
            matchedServices.isNotEmpty() -> ForceRegisterPlan(
                packageName, true, "direct_sdk", matchedServices, matchedReceivers, matchedBridges
            )
            matchedReceivers.isNotEmpty() -> ForceRegisterPlan(
                packageName, false, "receiver_only", emptySet(), matchedReceivers, matchedBridges
            )
            matchedBridges.isNotEmpty() -> ForceRegisterPlan(
                packageName, false, "bridge_wrapper", emptySet(), emptySet(), matchedBridges
            )
            else -> ForceRegisterPlan(
                packageName, false, "unsupported_components", emptySet(), emptySet(), emptySet()
            )
        }
    }

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
            matchedBridges -> "bridge_wrapper"
            matchedHandlers || matchedReceivers -> "receiver_only"
            else -> "unsupported_components"
        }
    }

    fun resolveForceRegisterPlan(
        packageName: String,
        serviceInfos: Set<RegistrationComponentInfo>,
        receiverInfos: Set<RegistrationComponentInfo>,
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
                packageName, true, "direct_sdk", matchedServices, matchedReceivers, matchedBridges,
                blockedServices, blockedReceivers
            )
            matchedReceivers.isNotEmpty() -> ForceRegisterPlan(
                packageName, false, "receiver_only", emptySet(), matchedReceivers, matchedBridges,
                blockedServices, blockedReceivers
            )
            blockedServices.isNotEmpty() || blockedReceivers.isNotEmpty() -> ForceRegisterPlan(
                packageName, false, "internal_only_components", emptySet(), emptySet(), matchedBridges,
                blockedServices, blockedReceivers
            )
            matchedBridges.isNotEmpty() -> ForceRegisterPlan(
                packageName, false, "bridge_wrapper", emptySet(), emptySet(), matchedBridges
            )
            else -> ForceRegisterPlan(
                packageName, false, "unsupported_components", emptySet(), emptySet(), emptySet()
            )
        }
    }

    private fun canDispatchAcrossPackages(
        sourcePackageName: String,
        targetPackageName: String,
        component: RegistrationComponentInfo
    ): Boolean = component.enabled && (component.exported || sourcePackageName == targetPackageName)
}
