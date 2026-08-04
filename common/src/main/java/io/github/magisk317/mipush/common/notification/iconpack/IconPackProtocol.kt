package io.github.magisk317.mipush.common.notification.iconpack

import android.graphics.Bitmap
import java.security.MessageDigest

/**
 * Version identifier supplied by a separately audited icon-pack protocol.
 * This type does not identify or imply an existing provider, Binder, or AIDL endpoint.
 */
data class ProtocolVersion(val value: String) {
    init {
        require(value.isNotBlank()) { "protocol version must not be blank" }
    }
}

/** Caller identity recorded for protocol permission audits. */
data class ProtocolCallerIdentity(
    val packageName: String,
    val uid: Int?,
    val processName: String?,
) {
    init {
        require(packageName.isNotBlank()) { "caller package must not be blank" }
    }
}

/** Read-only permission check evidence supplied by an audited adapter. */
data class ProtocolPermissionAudit(
    val requiredPermission: String?,
    val granted: Boolean,
    val checkedBy: String?,
) {
    init {
        require(requiredPermission == null || requiredPermission.isNotBlank()) {
            "permission name must not be blank"
        }
        require(checkedBy == null || checkedBy.isNotBlank()) {
            "permission auditor must not be blank"
        }
    }
}

/** Bounded request and observed duration for one protocol query. */
data class ProtocolTimeout(
    val timeoutMillis: Long,
    val elapsedMillis: Long? = null,
) {
    init {
        require(timeoutMillis > 0) { "protocol timeout must be positive" }
        require(elapsedMillis == null || elapsedMillis >= 0) {
            "elapsed protocol time must not be negative"
        }
    }
}

/** Version handshake evidence. Compatibility is explicit, never inferred from a path. */
data class ProtocolCompatibility(
    val requested: ProtocolVersion,
    val compatible: Boolean,
    val negotiated: ProtocolVersion?,
) {
    init {
        if (compatible) {
            require(negotiated != null) { "compatible result must have a negotiated version" }
        }
    }
}

/** A strictly target-package-scoped, read-only icon-pack query. */
data class IconPackQuery(
    val targetPackage: String,
    val userId: Int,
    val caller: ProtocolCallerIdentity,
    val permission: ProtocolPermissionAudit,
    val timeout: ProtocolTimeout,
    val compatibility: ProtocolCompatibility,
) {
    init {
        require(targetPackage.isNotBlank()) { "target package must not be blank" }
        require(userId >= 0) { "user id must not be negative" }
    }
}

/** Existing icon-pack payload shape; no independent monochrome bitmap is part of the contract. */
data class IconPackData(
    val packageName: String,
    val iconBitmap: Bitmap?,
    val iconColor: Int? = null,
    val revisionToken: String? = null,
) {
    init {
        require(packageName.isNotBlank()) { "icon package must not be blank" }
        require(revisionToken == null || revisionToken.isNotBlank()) {
            "revision token must not be blank"
        }
    }
}

enum class ProtocolState {
    AVAILABLE,
    EMPTY,
    INACCESSIBLE,
    ERROR,
    BLOCKED,
}

enum class ProtocolFailureReason {
    ENDPOINT_NOT_FOUND,
    ADAPTER_NOT_REGISTERED,
    PERMISSION_DENIED,
    UNSUPPORTED_VERSION,
    TIMEOUT,
    EXCEPTION,
    INVALID_REQUEST,
    EMPTY_RESPONSE,
    INACCESSIBLE,
}

/**
 * Result metadata is deliberately complete enough to audit a future protocol implementation.
 * An AVAILABLE result is only a protocol observation; bitmap validation belongs to the resolver.
 */
data class ProtocolResult(
    val state: ProtocolState,
    val targetPackage: String,
    val userId: Int,
    val iconData: IconPackData?,
    val protocolVersion: ProtocolVersion?,
    val sourceIdentity: String,
    val caller: ProtocolCallerIdentity,
    val permission: ProtocolPermissionAudit,
    val timeout: ProtocolTimeout,
    val compatibility: ProtocolCompatibility,
    val failureReason: ProtocolFailureReason?,
) {
    init {
        require(targetPackage.isNotBlank()) { "target package must not be blank" }
        require(userId >= 0) { "user id must not be negative" }
        require(sourceIdentity.isNotBlank()) { "source identity must not be blank" }
        if (state == ProtocolState.AVAILABLE) {
            require(iconData != null) { "available result must carry icon data" }
            require(protocolVersion != null) { "available result must carry a protocol version" }
            require(permission.granted) { "available result must have a granted permission audit" }
            require(compatibility.compatible) { "available result must be version compatible" }
            require(protocolVersion == compatibility.negotiated) {
                "available result version must match the negotiated version"
            }
            require(failureReason == null) { "available result must not carry a failure" }
        }
        if (state == ProtocolState.BLOCKED) {
            require(failureReason != null) { "blocked result must carry a failure reason" }
            require(iconData == null) { "blocked result must not carry icon data" }
        }
    }

    companion object {
        const val BLOCKED_SOURCE = "blocked-protocol"

        fun blocked(query: IconPackQuery, reason: ProtocolFailureReason): ProtocolResult =
            ProtocolResult(
                state = ProtocolState.BLOCKED,
                targetPackage = query.targetPackage,
                userId = query.userId,
                iconData = null,
                protocolVersion = null,
                sourceIdentity = BLOCKED_SOURCE,
                caller = query.caller,
                permission = query.permission,
                timeout = query.timeout,
                compatibility = query.compatibility.copy(compatible = false, negotiated = null),
                failureReason = reason,
            )
    }
}

/**
 * Read-only protocol boundary. Implementations must use a separately audited transport and must
 * not read another process's private storage or invent an implicit IPC endpoint.
 */
interface IconPackProtocolAdapter {
    fun query(query: IconPackQuery): ProtocolResult

    @Suppress("TooGenericExceptionCaught")
    fun querySafely(query: IconPackQuery): ProtocolResult {
        val startedAt = System.nanoTime()
        return try {
            val result = query(query)
            val elapsedMillis = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
            if (elapsedMillis > query.timeout.timeoutMillis) {
                ProtocolResult.blocked(query, ProtocolFailureReason.TIMEOUT)
            } else {
                result
            }
        } catch (_: Exception) {
            ProtocolResult.blocked(query, ProtocolFailureReason.EXCEPTION)
        }
    }
}

/**
 * Current product implementation: no endpoint is registered, so the feature is fail-closed.
 * This is not a fake provider and does not claim that any third-party protocol exists.
 */
object BlockedIconPackProtocolAdapter : IconPackProtocolAdapter {
    override fun query(query: IconPackQuery): ProtocolResult =
        ProtocolResult.blocked(query, ProtocolFailureReason.ADAPTER_NOT_REGISTERED)
}

sealed interface IconPackFallbackSource {
    val targetPackage: String

    data class App(override val targetPackage: String) : IconPackFallbackSource
    data class Unavailable(override val targetPackage: String) : IconPackFallbackSource
}

data class ProtocolFailureObservation(
    val state: ProtocolState,
    val reason: ProtocolFailureReason?,
    val targetPackageDigest: String,
    val userDigest: String,
    val sourceDigest: String,
    val errorType: String = reason?.name ?: state.name,
    val fallbackReason: String = reason?.name ?: state.name,
)

data class IconPackFallbackDecision(
    val source: IconPackFallbackSource,
    val observation: ProtocolFailureObservation,
)

/** Converts a blocked/unavailable protocol result into the safe application-icon branch. */
object IconPackProtocolGate {
    fun fallbackFor(result: ProtocolResult): IconPackFallbackDecision {
        val source = if (result.state == ProtocolState.AVAILABLE) {
            IconPackFallbackSource.Unavailable(result.targetPackage)
        } else {
            IconPackFallbackSource.App(result.targetPackage)
        }
        return IconPackFallbackDecision(source, result.toFailureObservation())
    }
}

private fun ProtocolResult.toFailureObservation(): ProtocolFailureObservation =
    ProtocolFailureObservation(
        state = state,
        reason = failureReason,
        targetPackageDigest = digestIdentity(targetPackage),
        userDigest = digestIdentity("user:$userId"),
        sourceDigest = digestIdentity(sourceIdentity),
        errorType = failureReason?.name ?: state.name,
        fallbackReason = if (state == ProtocolState.AVAILABLE) "UNAVAILABLE" else "APP",
    )

/** Stable, one-way identity for diagnostics; raw package/user/source values must not be logged. */
fun digestIdentity(value: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
}
