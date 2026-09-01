package io.github.magisk317.mipush.common.notification.iconpack

import android.content.Context
import android.graphics.Bitmap
import io.mockk.every
import io.mockk.mockk
import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Resolver property checks use a fake adapter seam only; they do not model or imply a real
 * provider/IPC implementation.
 */
class IconPackResolverPropertyTest {
    private val context = mockk<Context> {
        every { packageName } returns "io.github.magisk317.mipush"
    }

    /**
     * Property 1/2: every exact-package bitmap inside the inclusive bounds is available, and the
     * resolver keeps the third-party source after the injected notification-size strategy.
     *
     * **Validates: Requirements 2.1, 2.4, 2.7**
     */
    @Property(tries = 100)
    fun `valid dimensions remain available with third party source`(
        @ForAll("validDimensions") width: Int,
        @ForAll("validDimensions") height: Int,
        @ForAll iconColor: Int?,
    ) {
        val packageName = "com.example.target"
        val resolver = resolverFor(packageName, bitmap(width, height), iconColor)
        val result = resolver.resolve(packageName, 0, context)

        val available = result as? ResolveResult.Available
        assertTrue(available != null, "valid $width x $height bitmap must remain available")
        assertEquals("THIRD_PARTY_PACK($packageName)", available?.value?.sourceIdentity)
        assertEquals(iconColor, available?.value?.iconColor)
    }

    /**
     * Property 3: every dimension outside 1..4096 is rejected without an invalid bitmap result.
     *
     * **Validates: Requirements 1.6, 2.6**
     */
    @Property(tries = 100)
    fun `out of range dimensions are unavailable`(
        @ForAll("invalidDimensions") width: Int,
        @ForAll("invalidDimensions") height: Int,
    ) {
        val packageName = "com.example.target"
        val resolver = resolverFor(packageName, bitmap(width, height), null)
        val result = resolver.resolve(packageName, 0, context)

        assertEquals(
            ResolveResult.Unavailable(ResolveFailure.INVALID_SIZE),
            result,
        )
    }

    data class ResolverInput(
        val requestedPackage: String,
        val returnedPackage: String,
        val userId: Int,
        val width: Int,
        val height: Int,
        val recycled: Boolean,
        val state: ProtocolState,
    )

    /**
     * Property: only an exact package, AVAILABLE protocol result, decodable bitmap and inclusive
     * dimensions can cross the resolver boundary as Available.
     *
     * **Validates: Requirements 2.1, 2.6, 2.8**
     */
    @Property(tries = 120)
    fun `only exact decodable in range protocol data is available`(
        @ForAll("resolverInputs") input: ResolverInput,
    ) {
        val icon = bitmap(input.width, input.height, input.recycled)
        val resolver = IconPackResolver(
            adapter = object : IconPackProtocolAdapter {
                override fun query(query: IconPackQuery): ProtocolResult {
                    if (input.state == ProtocolState.BLOCKED) {
                        return ProtocolResult.blocked(query, ProtocolFailureReason.ADAPTER_NOT_REGISTERED)
                    }
                    val version = ProtocolVersion("icon-pack/1")
                    return ProtocolResult(
                        state = input.state,
                        targetPackage = query.targetPackage,
                        userId = query.userId,
                        iconData = if (input.state == ProtocolState.AVAILABLE) {
                            IconPackData(input.returnedPackage, icon, iconColor = null, revisionToken = "property")
                        } else {
                            null
                        },
                        protocolVersion = if (input.state == ProtocolState.AVAILABLE) version else null,
                        sourceIdentity = "property-test-protocol",
                        caller = query.caller,
                        permission = query.permission.copy(granted = input.state == ProtocolState.AVAILABLE),
                        timeout = query.timeout,
                        compatibility = query.compatibility.copy(
                            compatible = input.state == ProtocolState.AVAILABLE,
                            negotiated = if (input.state == ProtocolState.AVAILABLE) version else null,
                        ),
                        failureReason = if (input.state == ProtocolState.EMPTY) {
                            ProtocolFailureReason.EMPTY_RESPONSE
                        } else if (input.state == ProtocolState.INACCESSIBLE) {
                            ProtocolFailureReason.INACCESSIBLE
                        } else if (input.state == ProtocolState.ERROR) {
                            ProtocolFailureReason.EXCEPTION
                        } else {
                            null
                        },
                    )
                }
            },
            bitmapScaler = NotificationBitmapScaler { it },
        )

        val result = resolver.resolve(input.requestedPackage, input.userId, context)
        val legal = input.state == ProtocolState.AVAILABLE &&
            input.returnedPackage == input.requestedPackage &&
            !input.recycled &&
            input.width in 1..MAX_BITMAP_DIMENSION &&
            input.height in 1..MAX_BITMAP_DIMENSION
        assertEquals(legal, result is ResolveResult.Available, "input=$input result=$result")
        if (legal) {
            val available = result as ResolveResult.Available
            assertEquals(
                thirdPartyPackSourceIdentity(input.requestedPackage),
                available.value.sourceIdentity,
            )
            assertEquals(input.userId, available.value.userId)
        }
    }

    /**
     * Property: a caller scope that declines the path never queries the protocol adapter.
     *
     * **Validates: Requirements 2.8, 3.1, 3.6**
     */
    @Property(tries = 60)
    fun `non applicable scope never queries adapter`(
        @ForAll("packageNames") packageName: String,
        @ForAll userId: Int,
    ) {
        val nonNegativeUser = userId.takeIf { it >= 0 } ?: 0
        var queryCount = 0
        val resolver = IconPackResolver(
            adapter = object : IconPackProtocolAdapter {
                override fun query(query: IconPackQuery): ProtocolResult {
                    queryCount++
                    return ProtocolResult.blocked(query, ProtocolFailureReason.ENDPOINT_NOT_FOUND)
                }
            },
            callerScope = IconPackCallerScope { _, _, _ -> false },
        )
        val result = resolver.resolve(packageName, nonNegativeUser, context)
        assertEquals(0, queryCount, "non-applicable scope must not query $packageName/$nonNegativeUser")
        assertEquals(ResolveResult.Unavailable(ResolveFailure.BLOCKED), result)
    }

    data class CacheScenario(
        val firstPackage: String,
        val secondPackage: String,
        val firstUser: Int,
        val secondUser: Int,
        val firstVersion: String,
        val secondVersion: String,
        val firstRevision: String,
        val secondRevision: String,
    ) {
        val sameKey: Boolean
            get() = firstPackage == secondPackage &&
                firstUser == secondUser &&
                firstVersion == secondVersion &&
                firstRevision == secondRevision
    }

    /**
     * Property: successful cache entries are reusable only for the complete package/user/version/
     * revision key; a changed key must query and return the second source bitmap.
     *
     * **Validates: Requirements 2.6, 3.6**
     */
    @Property(tries = 80)
    fun `cache access stays isolated across composite identity`(
        @ForAll("cacheScenarios") scenario: CacheScenario,
    ) {
        val firstBitmap = bitmap(32, 32)
        val secondBitmap = bitmap(48, 48)
        var secondResponse = false
        var queryCount = 0
        val cache = InMemoryIconPackResultCache()
        val resolver = IconPackResolver(
            cache = cache,
            adapter = object : IconPackProtocolAdapter {
                override fun query(query: IconPackQuery): ProtocolResult {
                    queryCount++
                    val version = ProtocolVersion(if (secondResponse) scenario.secondVersion else scenario.firstVersion)
                    val revision = if (secondResponse) scenario.secondRevision else scenario.firstRevision
                    val data = IconPackData(
                        packageName = query.targetPackage,
                        iconBitmap = if (secondResponse) secondBitmap else firstBitmap,
                        revisionToken = revision,
                    )
                    return ProtocolResult(
                        state = ProtocolState.AVAILABLE,
                        targetPackage = query.targetPackage,
                        userId = query.userId,
                        iconData = data,
                        protocolVersion = version,
                        sourceIdentity = "cache-property-protocol",
                        caller = query.caller,
                        permission = query.permission.copy(granted = true),
                        timeout = query.timeout,
                        compatibility = query.compatibility.copy(compatible = true, negotiated = version),
                        failureReason = null,
                    )
                }
            },
            bitmapScaler = NotificationBitmapScaler { it },
        )

        val first = resolver.resolve(scenario.firstPackage, scenario.firstUser, context) as ResolveResult.Available
        secondResponse = true
        val second = resolver.resolve(scenario.secondPackage, scenario.secondUser, context) as ResolveResult.Available
        assertEquals(scenario.sameKey, second.value.bitmap === first.value.bitmap, "scenario=$scenario")
        // Version/revision are supplied by the protocol response, so the adapter is queried
        // before a composite cache key can be checked on both calls.
        assertEquals(2, queryCount, "scenario=$scenario")
    }

    @Provide
    fun packageNames(): Arbitrary<String> = Arbitraries.of(
        "com.example.chat",
        "com.example.mail",
        "org.example.target",
    )

    @Provide
    fun resolverInputs(): Arbitrary<ResolverInput> = net.jqwik.api.Combinators.combine(
        packageNames(),
        packageNames(),
        Arbitraries.integers().between(0, 999),
        Arbitraries.integers().between(-1, MAX_BITMAP_DIMENSION + 1),
        Arbitraries.integers().between(-1, MAX_BITMAP_DIMENSION + 1),
        Arbitraries.of(true, false),
        Arbitraries.of(
            ProtocolState.AVAILABLE,
            ProtocolState.EMPTY,
            ProtocolState.BLOCKED,
            ProtocolState.INACCESSIBLE,
            ProtocolState.ERROR,
        ),
    ).`as` { requested, returned, user, width, height, recycled, state ->
        ResolverInput(requested, returned, user, width, height, recycled, state)
    }

    @Provide
    fun cacheScenarios(): Arbitrary<CacheScenario> = net.jqwik.api.Combinators.combine(
        packageNames(),
        packageNames(),
        Arbitraries.integers().between(0, 3),
        Arbitraries.integers().between(0, 3),
        Arbitraries.of("icon-pack/1", "icon-pack/2"),
        Arbitraries.of("icon-pack/1", "icon-pack/2"),
        Arbitraries.of("revision-a", "revision-b"),
        Arbitraries.of("revision-a", "revision-b"),
    ).`as` { firstPackage, secondPackage, firstUser, secondUser, firstVersion, secondVersion, firstRevision, secondRevision ->
        CacheScenario(
            firstPackage,
            secondPackage,
            firstUser,
            secondUser,
            firstVersion,
            secondVersion,
            firstRevision,
            secondRevision,
        )
    }

    @Provide
    fun validDimensions(): Arbitrary<Int> = Arbitraries.integers().between(1, MAX_BITMAP_DIMENSION)

    @Provide
    fun invalidDimensions(): Arbitrary<Int> = Arbitraries.oneOf(
        Arbitraries.integers().between(-100, 0),
        Arbitraries.integers().between(MAX_BITMAP_DIMENSION + 1, MAX_BITMAP_DIMENSION + 100),
    )

    private fun resolverFor(
        packageName: String,
        bitmap: Bitmap,
        iconColor: Int?,
    ): IconPackResolver {
        return IconPackResolver(
            adapter = object : IconPackProtocolAdapter {
                override fun query(query: IconPackQuery): ProtocolResult {
                    val version = ProtocolVersion("icon-pack/1")
                    return ProtocolResult(
                        state = ProtocolState.AVAILABLE,
                        targetPackage = query.targetPackage,
                        userId = query.userId,
                        iconData = IconPackData(packageName, bitmap, iconColor),
                        protocolVersion = version,
                        sourceIdentity = "property-test-protocol",
                        caller = query.caller,
                        permission = query.permission.copy(granted = true),
                        timeout = query.timeout,
                        compatibility = query.compatibility.copy(
                            compatible = true,
                            negotiated = version,
                        ),
                        failureReason = null,
                    )
                }
            },
            bitmapScaler = NotificationBitmapScaler { it },
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
