package io.github.magisk317.mipush.manager.api

/** Internal write-contract validation domain; public API remains on [ManagerProtocol]. */
internal object ManagerWriteContractValidation {
    fun validateWriteRequest(request: ManagerWriteRequestDto): String? = when {
        request.schemaVersion < 1 -> "invalid_write_request_schema"
        request.requestId.isBlank() || request.requestId.length > ManagerProtocol.MAX_WRITE_REQUEST_ID_LENGTH ->
            "invalid_write_request_id"
        request.operation.isBlank() || request.operation.length > ManagerProtocol.MAX_WRITE_OPERATION_LENGTH ->
            "invalid_write_operation"
        request.argument.length > ManagerProtocol.MAX_WRITE_ARGUMENT_LENGTH -> "write_argument_too_long"
        request.packageName.isNotEmpty() &&
            ManagerProtocol.validateApplicationPackageName(request.packageName) != null ->
            "invalid_write_package_name"
        request.operation in operationsRequiringPackage &&
            ManagerProtocol.validateApplicationPackageName(request.packageName) != null ->
            "write_package_name_required"
        request.operation in operationsRequiringEventId &&
            (request.eventId == null || request.eventId <= 0L) ->
            "invalid_write_event_id"
        request.operation in operationsRequiringUser && request.userId < 0 -> "invalid_write_user_id"
        request.eventId != null && request.eventId < 0L -> "invalid_write_event_id"
        else -> null
    }

    fun validateWriteResult(result: ManagerWriteResultDto): String? = when {
        result.schemaVersion < 1 -> "invalid_write_result_schema"
        result.requestId.isBlank() || result.requestId.length > ManagerProtocol.MAX_WRITE_REQUEST_ID_LENGTH ->
            "invalid_write_result_request_id"
        result.status !in setOf(
            ManagerProtocol.WRITE_STATUS_SUCCESS,
            ManagerProtocol.WRITE_STATUS_FAILED,
            ManagerProtocol.WRITE_STATUS_UNSUPPORTED,
            ManagerProtocol.WRITE_STATUS_DUPLICATE,
        ) -> "invalid_write_status"
        result.details.length > ManagerProtocol.MAX_LOG_EXPORT_DETAILS_LENGTH -> "write_details_too_long"
        else -> null
    }

    private val operationsRequiringPackage = setOf(
        ManagerProtocol.WRITE_OP_UPDATE_APPLICATION,
        ManagerProtocol.WRITE_OP_LAUNCH_TARGET_FORCE_REGISTER,
        ManagerProtocol.WRITE_OP_DELETE_EVENT,
        ManagerProtocol.WRITE_OP_RESTORE_EVENT,
        ManagerProtocol.WRITE_OP_MOCK_MESSAGE,
        ManagerProtocol.WRITE_OP_QUERY_USAGE_STATS,
        ManagerProtocol.WRITE_OP_DELETE_NOTIFICATION_CHANNEL,
        ManagerProtocol.WRITE_OP_ZYGISK_FORCE_STOP,
        ManagerProtocol.WRITE_OP_GET_EVENT_CONTENT,
        ManagerProtocol.WRITE_OP_GET_EVENT_JSON,
    )

    private val operationsRequiringEventId = setOf(
        ManagerProtocol.WRITE_OP_DELETE_EVENT,
        ManagerProtocol.WRITE_OP_RESTORE_EVENT,
        ManagerProtocol.WRITE_OP_MOCK_MESSAGE,
        ManagerProtocol.WRITE_OP_GET_EVENT_CONTENT,
        ManagerProtocol.WRITE_OP_GET_EVENT_JSON,
    )

    private val operationsRequiringUser = setOf(
        ManagerProtocol.WRITE_OP_DELETE_EVENT,
        ManagerProtocol.WRITE_OP_RESTORE_EVENT,
        ManagerProtocol.WRITE_OP_MOCK_MESSAGE,
        ManagerProtocol.WRITE_OP_GET_EVENT_CONTENT,
        ManagerProtocol.WRITE_OP_GET_EVENT_JSON,
    )
}
