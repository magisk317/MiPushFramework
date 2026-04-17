package io.github.magisk317.mipush.runtime.core

data class PushSlimHandshakePlan(
    val valid: Boolean,
    val eventAction: String,
    val shouldEmitConfigBlob: Boolean = false,
    val failureReason: String? = null
)

data class PushSlimPayloadPlan(
    val action: PushSlimPayloadAction,
    val eventAction: String? = null,
    val shouldLogUnknownType: Boolean = false
)

enum class PushSlimPayloadAction {
    None,
    Normal,
    Ack,
    Error,
    DeliverBlob,
    ParseSecurePacket,
    ParsePacket,
    IgnoreUnknown
}

data class PushSlimWritePlan(
    val eventAction: String? = null,
    val shouldDrop: Boolean = false,
    val requiredCapacity: Int = 0,
    val shouldEncrypt: Boolean = false
)
