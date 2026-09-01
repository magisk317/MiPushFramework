package io.github.magisk317.mipush.manager.api

import io.github.magisk317.mipush.manager.ManagerContractValidationCore
import io.github.magisk317.mipush.manager.WriteRequestValidationInput
import io.github.magisk317.mipush.manager.WriteResultValidationInput

/** Parcelable facade over the platform-neutral write contract validation. */
internal object ManagerWriteContractValidation {
    fun validateWriteRequest(request: ManagerWriteRequestDto): String? =
        ManagerContractValidationCore.validateWriteRequest(
            WriteRequestValidationInput(
                schemaVersion = request.schemaVersion,
                requestId = request.requestId,
                operation = request.operation,
                packageName = request.packageName,
                userId = request.userId,
                eventId = request.eventId,
                intArgument = request.intArgument,
                argumentLength = request.argument.length,
            ),
        )

    fun validateWriteResult(result: ManagerWriteResultDto): String? =
        ManagerContractValidationCore.validateWriteResult(
            WriteResultValidationInput(
                schemaVersion = result.schemaVersion,
                requestId = result.requestId,
                status = result.status,
                details = result.details,
            ),
        )
}
