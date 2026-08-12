package io.github.magisk317.mipush.common.notification.iconpack

import android.content.Context
import android.graphics.Bitmap
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Resolver contract tests use only in-memory protocol seams. They never access HMSPush storage or
 * infer a provider/IPC endpoint.
 *
 * **Validates: Requirements 1.1, 1.6, 1.7, 1.8, 2.1, 2.4, 2.6, 2.7**
 */
class IconPackResolverTest {
    private val context = mockk<Context> {
        every { packageName } returns "io.github.magisk317.mipush"
    }

    @Test
    fun `scope and identity preflight happen before adapter query`() {
        val events = mutableListOf<String>()
        val adapter = object : IconPackProtocolAdapter {
            override fun query(query: IconPackQuery): ProtocolResult {
                events += "adapter"
                return ProtocolResult.blocked(query, ProtocolFailureReason.ENDPOINT_NOT_FOUND)
            }
        }
        val resolver = IconPackResolver(
            adapter = adapter,
            callerScope = IconPackCallerScope { _, packageName, userId ->
                events += "scope:$packageName:$userId"
                true
            },
            userNormalizer = IconPackUserNormalizer { _, requested ->
                events += "user"
                requested ?: 10
            },
            queryFactory = IconPackQueryFactory { _, packageName, userId ->
                events += "query:$packageName:$userId"
                testQuery(packageName, userId)
            },
        )

        val result = resolver.resolve("  com.example.target  ", null, context)

        assertEquals(ResolveResult.Unavailable(ResolveFailure.BLOCKED, ProtocolFailureReason.ENDPOINT_NOT_FOUND), result)
        assertEquals(
            listOf("user", "scope:com.example.target:10", "query:com.example.target:10", "adapter"),
            events,
        )
    }

    @Test
    fun `exact package and decodable bitmap produce third party source without icon color`() {
        val bitmap = bitmap(32, 32)
        val resolver = resolverFor(resultFactory = { query ->
            available(query, IconPackData(query.targetPackage, bitmap, iconColor = null))
        })

        val result = resolver.resolve("com.example.target", 0, context)

        val available = assertInstanceOf(ResolveResult.Available::class.java, result)
        assertEquals("com.example.target", available.value.targetPackage)
        assertEquals(0, available.value.userId)
        assertEquals(bitmap, available.value.bitmap)
        assertEquals("THIRD_PARTY_PACK(com.example.target)", available.value.sourceIdentity)
        assertEquals(null, available.value.iconColor)
    }

    @Test
    fun `package comparison is exact and rejects a near match`() {
        val resolver = resolverFor(resultFactory = { query ->
            available(query, IconPackData("com.example.target.other", bitmap(32, 32)))
        })

        val result = resolver.resolve("com.example.target", 0, context)

        assertEquals(ResolveResult.Unavailable(ResolveFailure.PACKAGE_MISMATCH), result)
    }

    @Test
    fun `null and recycled bitmaps are unavailable`() {
        val nullResolver = resolverFor(resultFactory = { query ->
            available(query, IconPackData(query.targetPackage, null))
        })
        assertEquals(
            ResolveResult.Unavailable(ResolveFailure.NULL_BITMAP),
            nullResolver.resolve("com.example.target", 0, context),
        )

        val recycledResolver = resolverFor(resultFactory = { query ->
            available(query, IconPackData(query.targetPackage, bitmap(32, 32, recycled = true)))
        })
        assertEquals(
            ResolveResult.Unavailable(ResolveFailure.BITMAP_DECODE_FAILED),
            recycledResolver.resolve("com.example.target", 0, context),
        )
    }

    @Test
    fun `small legal bitmap uses notification scaler and remains third party`() {
        val original = bitmap(12, 24)
        val scaled = bitmap(24, 48)
        var calls = 0
        val resolver = resolverFor(
            resultFactory = { query -> available(query, IconPackData(query.targetPackage, original)) },
            scaler = NotificationBitmapScaler {
                calls++
                scaled
            },
        )

        val result = resolver.resolve("com.example.target", 0, context)

        val available = assertInstanceOf(ResolveResult.Available::class.java, result)
        assertEquals(1, calls)
        assertEquals(scaled, available.value.bitmap)
        assertEquals("THIRD_PARTY_PACK(com.example.target)", available.value.sourceIdentity)
    }

    @Test
    fun `scaling failure and invalid scaled dimensions become unavailable`() {
        val original = bitmap(12, 24)
        val throwingResolver = resolverFor(
            resultFactory = { query -> available(query, IconPackData(query.targetPackage, original)) },
            scaler = NotificationBitmapScaler { error("scale failed") },
        )
        assertEquals(
            ResolveResult.Unavailable(ResolveFailure.LOAD_FAILED),
            throwingResolver.resolve("com.example.target", 0, context),
        )

        val invalidResolver = resolverFor(
            resultFactory = { query -> available(query, IconPackData(query.targetPackage, original)) },
            scaler = NotificationBitmapScaler { bitmap(0, 24) },
        )
        assertEquals(
            ResolveResult.Unavailable(ResolveFailure.INVALID_SIZE),
            invalidResolver.resolve("com.example.target", 0, context),
        )
    }

    @Test
    fun `blocked adapter is fail closed and never throws into notification flow`() {
        val resolver = IconPackResolver(adapter = BlockedIconPackProtocolAdapter)

        val result = resolver.resolve("com.example.target", null, context)

        val unavailable = assertInstanceOf(ResolveResult.Unavailable::class.java, result)
        assertEquals(ResolveFailure.BLOCKED, unavailable.reason)
        assertEquals(ProtocolFailureReason.ADAPTER_NOT_REGISTERED, unavailable.protocolReason)
    }

    @Test
    fun `adapter transport exceptions are normalized to blocked`() {
        val resolver = resolverFor(resultFactory = { error("provider, parcel, json or permission failure") })

        val result = resolver.resolve("com.example.target", 0, context)

        assertEquals(
            ResolveResult.Unavailable(ResolveFailure.BLOCKED, ProtocolFailureReason.EXCEPTION),
            result,
        )
    }

    @Test
    fun `invalid identity prevents protocol access`() {
        var queried = false
        val adapter = object : IconPackProtocolAdapter {
            override fun query(query: IconPackQuery): ProtocolResult {
                queried = true
                return ProtocolResult.blocked(query, ProtocolFailureReason.INVALID_REQUEST)
            }
        }
        val resolver = IconPackResolver(adapter = adapter)

        assertEquals(ResolveFailure.PARSE_ERROR, (resolver.resolve("  ", 0, context) as ResolveResult.Unavailable).reason)
        assertFalse(queried)

        val negativeUser = resolver.resolve("com.example.target", -1, context)
        assertEquals(ResolveFailure.PARSE_ERROR, (negativeUser as ResolveResult.Unavailable).reason)
        assertFalse(queried)
    }

    @Test
    fun `empty inaccessible and every invalid dimension become unavailable`() {
        val states = listOf(ProtocolState.EMPTY, ProtocolState.INACCESSIBLE)
        states.forEach { state ->
            val resolver = resolverFor(resultFactory = { query ->
                ProtocolResult(
                    state = state,
                    targetPackage = query.targetPackage,
                    userId = query.userId,
                    iconData = null,
                    protocolVersion = null,
                    sourceIdentity = "audited-test-protocol",
                    caller = query.caller,
                    permission = query.permission,
                    timeout = query.timeout,
                    compatibility = query.compatibility,
                    failureReason = ProtocolFailureReason.EMPTY_RESPONSE,
                )
            })
            assertEquals(
                ResolveResult.Unavailable(
                    if (state == ProtocolState.EMPTY) ResolveFailure.EMPTY else ResolveFailure.INACCESSIBLE,
                    ProtocolFailureReason.EMPTY_RESPONSE,
                ),
                resolver.resolve("com.example.target", 0, context),
            )
        }

        listOf(0, -1, MAX_BITMAP_DIMENSION + 1).forEach { invalidDimension ->
            val resolver = resolverFor(resultFactory = { query ->
                available(query, IconPackData(query.targetPackage, bitmap(invalidDimension, 32)))
            })
            assertEquals(
                ResolveResult.Unavailable(ResolveFailure.INVALID_SIZE),
                resolver.resolve("com.example.target", 0, context),
                "dimension=$invalidDimension",
            )
        }
    }
    @Test
    fun `dimension limits are inclusive`() {
        val resolver = resolverFor(resultFactory = { query ->
            available(query, IconPackData(query.targetPackage, bitmap(4096, 4096)))
        })
        val result = resolver.resolve("com.example.target", 0, context)
        assertTrue(result is ResolveResult.Available)
    }

    @Test
    fun `cache isolates scope and invalidates version or revision without caching failures`() {
        val cache = InMemoryIconPackResultCache()
        val first = bitmap(12, 24)
        val second = bitmap(48, 48)
        var revision = "rev-1"
        var version = "icon-pack/1"
        var scalerCalls = 0
        val resolver = IconPackResolver(
            cache = cache,
            adapter = object : IconPackProtocolAdapter {
                override fun query(query: IconPackQuery): ProtocolResult {
                    val protocolVersion = ProtocolVersion(version)
                    return ProtocolResult(
                        state = ProtocolState.AVAILABLE,
                        targetPackage = query.targetPackage,
                        userId = query.userId,
                        iconData = IconPackData(query.targetPackage, if (revision == "rev-1") first else second, revisionToken = revision),
                        protocolVersion = protocolVersion,
                        sourceIdentity = "audited-test-protocol",
                        caller = query.caller,
                        permission = query.permission.copy(granted = true),
                        timeout = query.timeout,
                        compatibility = query.compatibility.copy(compatible = true, negotiated = protocolVersion),
                        failureReason = null,
                    )
                }
            },
            bitmapScaler = NotificationBitmapScaler {
                scalerCalls++
                it
            },
        )

        val firstResult = resolver.resolve("com.example.target", 0, context) as ResolveResult.Available
        val cachedResult = resolver.resolve("com.example.target", 0, context) as ResolveResult.Available
        assertEquals(first, firstResult.value.bitmap)
        assertEquals(first, cachedResult.value.bitmap)
        assertEquals(1, cacheSize(cache))

        revision = "rev-2"
        val revisionResult = resolver.resolve("com.example.target", 0, context) as ResolveResult.Available
        assertEquals(second, revisionResult.value.bitmap)
        assertEquals("rev-2", revisionResult.value.revisionToken)
        assertEquals(1, cacheSize(cache))

        version = "icon-pack/2"
        val versionResult = resolver.resolve("com.example.target", 0, context) as ResolveResult.Available
        assertEquals("icon-pack/2", versionResult.value.protocolVersion)
        assertEquals(1, cacheSize(cache))

        val otherUser = resolver.resolve("com.example.target", 1, context) as ResolveResult.Available
        assertEquals(1, otherUser.value.userId)
        assertEquals(2, cacheSize(cache))
        assertEquals(1, scalerCalls)

        revision = "bad"
        val invalidResolver = IconPackResolver(
            cache = cache,
            adapter = object : IconPackProtocolAdapter {
                override fun query(query: IconPackQuery): ProtocolResult {
                    val protocolVersion = ProtocolVersion("icon-pack/2")
                    return ProtocolResult(
                        state = ProtocolState.AVAILABLE,
                        targetPackage = query.targetPackage,
                        userId = query.userId,
                        iconData = IconPackData(query.targetPackage, bitmap(0, 32), revisionToken = "bad"),
                        protocolVersion = protocolVersion,
                        sourceIdentity = "audited-test-protocol",
                        caller = query.caller,
                        permission = query.permission.copy(granted = true),
                        timeout = query.timeout,
                        compatibility = query.compatibility.copy(compatible = true, negotiated = protocolVersion),
                        failureReason = null,
                    )
                }
            },
        )
        assertEquals(
            ResolveResult.Unavailable(ResolveFailure.INVALID_SIZE),
            invalidResolver.resolve("com.example.target", 0, context),
        )
        assertEquals(2, cacheSize(cache))
    }

    @Test
    fun `failure observations contain only sanitized identities and fallback metadata`() {
        val observations = mutableListOf<IconPackResolutionObservation>()
        val resolver = IconPackResolver(
            observationLogger = IconPackObservationLogger { observations += it },
        )
        val result = resolver.resolve("com.example.secret", 7, context)

        assertEquals(ResolveResult.Unavailable(ResolveFailure.BLOCKED, ProtocolFailureReason.ADAPTER_NOT_REGISTERED), result)
        val observation = observations.single()
        assertEquals(ProtocolState.BLOCKED, observation.state)
        assertEquals(ProtocolFailureReason.ADAPTER_NOT_REGISTERED.name, observation.errorType)
        assertEquals("APP", observation.fallbackReason)
        assertFalse(observation.targetPackageDigest.contains("com.example.secret"))
        assertFalse(observation.userDigest.contains("user:7"))
        assertFalse(observation.sourceDigest.contains(ProtocolResult.BLOCKED_SOURCE))
        assertEquals(64, observation.targetPackageDigest.length)
        assertEquals(64, observation.userDigest.length)
        assertEquals(64, observation.sourceDigest.length)
    }

    private fun cacheSize(cache: IconPackResultCache): Int =
        (cache as InMemoryIconPackResultCache).size()

    private fun resolverFor(
        resultFactory: (IconPackQuery) -> ProtocolResult,
        scaler: NotificationBitmapScaler = NotificationBitmapScaler { it },
    ): IconPackResolver {
        return IconPackResolver(
            adapter = object : IconPackProtocolAdapter {
                override fun query(query: IconPackQuery): ProtocolResult = resultFactory(query)
            },
            queryFactory = IconPackQueryFactory { _, packageName, userId ->
                testQuery(packageName, userId)
            },
            bitmapScaler = scaler,
        )
    }

    private fun testQuery(targetPackage: String, userId: Int): IconPackQuery {
        val requested = ProtocolVersion("icon-pack/1")
        return IconPackQuery(
            targetPackage = targetPackage,
            userId = userId,
            caller = ProtocolCallerIdentity("io.github.magisk317.mipush", null, null),
            permission = ProtocolPermissionAudit(null, false, "test"),
            // This suite validates resolver semantics, not the production latency budget.
            timeout = ProtocolTimeout(5_000),
            compatibility = ProtocolCompatibility(requested, false, null),
        )
    }

    private fun available(query: IconPackQuery, data: IconPackData): ProtocolResult {
        val version = ProtocolVersion("icon-pack/1")
        return ProtocolResult(
            state = ProtocolState.AVAILABLE,
            targetPackage = query.targetPackage,
            userId = query.userId,
            iconData = data,
            protocolVersion = version,
            sourceIdentity = "audited-test-protocol",
            caller = query.caller,
            permission = query.permission.copy(granted = true),
            timeout = query.timeout,
            compatibility = query.compatibility.copy(compatible = true, negotiated = version),
            failureReason = null,
        )
    }

    private fun bitmap(width: Int, height: Int, recycled: Boolean = false): Bitmap {
        val bitmap = mockk<Bitmap>()
        every { bitmap.width } returns width
        every { bitmap.height } returns height
        every { bitmap.isRecycled } returns recycled
        return bitmap
    }
}
