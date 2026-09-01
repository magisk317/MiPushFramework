package io.github.magisk317.mipush.manager

/**
 * Bounds shared by the manager wire contract and its Android Parcelable facade.
 *
 * Keeping the values here prevents validation policy from being owned accidentally by a DTO
 * implementation. The contract module may expose the same values as public compatibility constants.
 */
object ManagerContractLimits {
    const val MAX_PREFERENCE_ENTRY_COUNT = 256
    const val MAX_PREFERENCE_KEY_LENGTH = 128
    const val MAX_PREFERENCE_VALUE_LENGTH = 4_096
    const val MAX_CONFIGURATION_UPLOAD_BYTES = 512 * 1024
    const val MAX_WRITE_REQUEST_ID_LENGTH = 128
    const val MAX_WRITE_OPERATION_LENGTH = 64
    const val MAX_WRITE_ARGUMENT_LENGTH = 4_096
    const val MAX_WIRE_STRING_LENGTH = 4_096
    const val MAX_APPLICATION_QUERY_LENGTH = 512
    const val MAX_PACKAGE_NAME_LENGTH = 255
    const val MAX_PAGE_TOKEN_LENGTH = 1_024
    const val MAX_APPLICATION_PAGE_ITEM_COUNT = 1_000
    const val MAX_EVENT_PAGE_ITEM_COUNT = 1_000
    const val MAX_EVENT_PAYLOAD_BYTES = 256 * 1024
    const val MAX_EVENT_CONFIG_OPTION_COUNT = 64
    const val MAX_EVENT_CONFIG_OPTION_LENGTH = 128
    const val MAX_NOTIFICATION_CHANNEL_PAGE_ITEM_COUNT = 1_000
    const val MAX_NOTIFICATION_CHANNEL_GROUP_COUNT = 1_000
    const val MAX_CONFIGURATION_CATALOG_ITEM_COUNT = 2_000
    const val MAX_CONFIGURATION_PATH_LENGTH = 512
    const val MAX_CONFIGURATION_NAME_LENGTH = 255
    const val MAX_CONFIGURATION_SHA_LENGTH = 128
    const val MAX_LOG_EXPORT_DETAILS_LENGTH = 4_096
    const val MAX_CAPABILITY_COUNT = 64
    const val MAX_CAPABILITY_LENGTH = 128
    const val MAX_RUNTIME_VERSION_NAME_LENGTH = 128
    const val MAX_COMPATIBILITY_REASON_LENGTH = 128
    const val DEFAULT_MAX_PAGE_SIZE = 50
    const val DEFAULT_MAX_PAYLOAD_BYTES = 512 * 1024
    const val MAX_WIRE_FRAME_BYTES = DEFAULT_MAX_PAYLOAD_BYTES
    const val MAX_NEGOTIATED_PAGE_SIZE = 1_000
    const val MAX_NEGOTIATED_PAYLOAD_BYTES = DEFAULT_MAX_PAYLOAD_BYTES
}
