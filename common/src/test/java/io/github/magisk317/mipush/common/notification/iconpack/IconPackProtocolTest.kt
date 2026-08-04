package io.github.magisk317.mipush.common.notification.iconpack

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Protocol boundary tests. These tests use only in-memory contract values; they do not stand in
 * for an existing provider, Binder, AIDL endpoint, or private HMSPush storage.
 *
 * **Validates: Requirements 1.8, 2.8, 3.1, 3.2, 3.6**
 */
class IconPackProtocolTest {
    private val query = IconPackQuery(
        targetPackage = "com.example.target",
        userId = 10,
        caller = ProtocolCallerIdentity(
            packageName = "io.github.magisk317.mipush",
            uid = 10001,
            processName = "com.xiaomi.xmsf",
        ),
        permission = ProtocolPermissionAudit(
            requiredPermission = "com.example.ICON_PACK_READ",
            granted = false,
            checkedBy = "not-registered",
        ),
        timeout = ProtocolTimeout(timeoutMillis = 250),
        compatibility = ProtocolCompatibility(
            requested = ProtocolVersion("icon-pack/1"),
            compatible = false,
            negotiated = null,
        ),
    )

    @Test
    fun `unregistered adapter is blocked and falls back to exact target app`() {
        val result = BlockedIconPackProtocolAdapter.query(query)

        assertEquals(ProtocolState.BLOCKED, result.state)
        assertEquals(ProtocolFailureReason.ADAPTER_NOT_REGISTERED, result.failureReason)
        assertNull(result.iconData)
        assertEquals(ProtocolResult.BLOCKED_SOURCE, result.sourceIdentity)
        assertFalse(result.compatibility.compatible)

        val decision = IconPackProtocolGate.fallbackFor(result)
        assertEquals(IconPackFallbackSource.App(query.targetPackage), decision.source)
        assertEquals(ProtocolState.BLOCKED, decision.observation.state)
        assertEquals(ProtocolFailureReason.ADAPTER_NOT_REGISTERED, decision.observation.reason)
    }

    @Test
    fun `all unsupported access conditions are represented as blocked without payload`() {
        val reasons = listOf(
            ProtocolFailureReason.ENDPOINT_NOT_FOUND,
            ProtocolFailureReason.ADAPTER_NOT_REGISTERED,
            ProtocolFailureReason.PERMISSION_DENIED,
            ProtocolFailureReason.UNSUPPORTED_VERSION,
            ProtocolFailureReason.TIMEOUT,
            ProtocolFailureReason.EXCEPTION,
        )

        reasons.forEach { reason ->
            val result = ProtocolResult.blocked(query, reason)
            assertEquals(ProtocolState.BLOCKED, result.state, reason.name)
            assertEquals(reason, result.failureReason, reason.name)
            assertNull(result.iconData, reason.name)
            assertEquals(
                IconPackFallbackSource.App(query.targetPackage),
                IconPackProtocolGate.fallbackFor(result).source,
                reason.name,
            )
        }
    }

    @Test
    fun `query contract carries audit metadata and remains read only`() {
        val adapter = object : IconPackProtocolAdapter {
            override fun query(query: IconPackQuery): ProtocolResult =
                ProtocolResult.blocked(query, ProtocolFailureReason.ENDPOINT_NOT_FOUND)
        }
        val result = adapter.query(query)

        assertEquals(query.targetPackage, result.targetPackage)
        assertEquals(query.userId, result.userId)
        assertEquals(query.caller, result.caller)
        assertEquals(query.permission, result.permission)
        assertEquals(query.timeout, result.timeout)
        assertEquals(query.compatibility, result.compatibility.copy(compatible = false, negotiated = null))

        val queryMethod = IconPackProtocolAdapter::class.java.methods.single { it.name == "query" }
        assertTrue(queryMethod.parameterTypes.contentEquals(arrayOf(IconPackQuery::class.java)))
        val forbiddenTypes = setOf(
            "android.content.Context",
            "android.content.Intent",
            "android.net.Uri",
            "android.os.IBinder",
            "java.io.File",
        )
        assertTrue(queryMethod.parameterTypes.none { it.name in forbiddenTypes })
    }

    @Test
    fun `adapter exceptions are normalized to blocked`() {
        val throwingAdapter = object : IconPackProtocolAdapter {
            override fun query(query: IconPackQuery): ProtocolResult = error("transport failure")
        }

        val result = throwingAdapter.querySafely(query)

        assertEquals(ProtocolState.BLOCKED, result.state)
        assertEquals(ProtocolFailureReason.EXCEPTION, result.failureReason)
        assertEquals(IconPackFallbackSource.App(query.targetPackage), IconPackProtocolGate.fallbackFor(result).source)
    }

    @Test
    fun `failure observation is sanitized and does not expose package user or source`() {
        val result = BlockedIconPackProtocolAdapter.query(query)
        val observation = IconPackProtocolGate.fallbackFor(result).observation

        assertNotNull(observation.targetPackageDigest)
        assertNotNull(observation.userDigest)
        assertNotNull(observation.sourceDigest)
        assertNotEquals(query.targetPackage, observation.targetPackageDigest)
        assertNotEquals(query.userId.toString(), observation.userDigest)
        assertNotEquals(result.sourceIdentity, observation.sourceDigest)
        assertEquals(64, observation.targetPackageDigest.length)
        assertEquals(64, observation.userDigest.length)
        assertEquals(64, observation.sourceDigest.length)
    }

    @Test
    fun `available observation never receives the blocked app fallback`() {
        val available = ProtocolResult(
            state = ProtocolState.AVAILABLE,
            targetPackage = query.targetPackage,
            userId = query.userId,
            iconData = IconPackData(query.targetPackage, iconBitmap = null),
            protocolVersion = ProtocolVersion("icon-pack/1"),
            sourceIdentity = "audited-protocol-placeholder",
            caller = query.caller,
            permission = query.permission.copy(granted = true),
            timeout = query.timeout,
            compatibility = ProtocolCompatibility(
                requested = query.compatibility.requested,
                compatible = true,
                negotiated = ProtocolVersion("icon-pack/1"),
            ),
            failureReason = null,
        )

        assertEquals(
            IconPackFallbackSource.Unavailable(query.targetPackage),
            IconPackProtocolGate.fallbackFor(available).source,
        )
    }
}
