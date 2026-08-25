package io.github.magisk317.mipush.manager.remote

class RuntimeWriteUnavailableException(
    val status: String,
    val operation: String,
) : RuntimeException("ManagerRuntime write $operation unavailable: $status")

class RuntimeWriteRejectedException(
    val status: String,
    val operation: String,
    val details: String,
) : RuntimeException("ManagerRuntime write $operation rejected: $status ($details)")
