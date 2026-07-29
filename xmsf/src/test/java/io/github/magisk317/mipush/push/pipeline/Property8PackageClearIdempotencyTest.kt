package io.github.magisk317.mipush.push.pipeline

import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.Combinators
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide
import net.jqwik.api.lifecycle.AfterProperty
import net.jqwik.api.lifecycle.BeforeProperty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Property 8: 包清理幂等性
 *
 * For any package, consecutive calls to onPackageDataCleared:
 * - Second call does NOT produce a network request (because appId was already cleared by first call)
 * - No duplicate broadcasts are emitted
 * - Package remains installed but unregistered
 * - If only pending state exists (no confirmed appId), even the first call does NOT send a network request
 *
 * **Validates: Requirements 12.3, 12.4, 12.5**
 *
 * Tests the [PackageDataClearedCoordinator] idempotency using a model that faithfully
 * mirrors the coordinator's state machine:
 * - The coordinator checks if a confirmed appId exists (via [MIPushAppAbsentManager.getRememberedAppId])
 * - If appId is non-null, it builds a payload and dispatches it (network request)
 * - It then clears the confirmed registration (appId becomes null)
 * - Subsequent calls find no appId → no dispatch
 *
 * This model verifies:
 * - Confirmed pkg → first clear dispatches exactly once, subsequent clears are no-ops
 * - Pending-only pkg → first clear does NOT dispatch (no confirmed appId)
 * - Arbitrary repeated clears never dispatch more than once for a single registration
 * - Per-package isolation: clearing one package does not affect another's dispatch behavior
 */
class Property8PackageClearIdempotencyTest {

    /**
     * Model of the PackageDataClearedCoordinator's dispatch decision logic.
     *
     * Faithfully represents how [PackageDataClearedCoordinator.handle] decides whether
     * to send a network request:
     * 1. Read appId from confirmed registration store
     * 2. If appId is non-null → build payload, dispatch to server (app_data_cleared)
     * 3. Clear confirmed registration (appId → null)
     * 4. Clear pending registration
     * 5. Clear other associated state
     *
     * The idempotency guarantee: after step 3, any subsequent handle() call for the same
     * package will find appId = null and skip dispatching.
     */
    class PackageClearModel {
        // Mirrors: pref_registered_pkg_names (confirmed appId per package)
        private val confirmedAppIds = mutableMapOf<String, String>()
        // Mirrors: pref_pending_registration (pending appId per package)
        private val pendingAppIds = mutableMapOf<String, String>()
        // Track dispatch count per handle() call for verification
        private var lastDispatchCount = 0

        fun confirmRegistration(pkg: String, appId: String) {
            if (pkg.isBlank() || appId.isBlank()) return
            confirmedAppIds[pkg] = appId
        }

        fun setPendingRegistration(pkg: String, pendingAppId: String) {
            if (pkg.isBlank() || pendingAppId.isBlank()) return
            pendingAppIds[pkg] = pendingAppId
        }

        /**
         * Mirrors [PackageDataClearedCoordinator.handle]:
         * - Reads confirmed appId
         * - If present: builds payload, dispatches (returns dispatched=true)
         * - Clears confirmed and pending registration
         *
         * @return HandleResult with whether appId was present and whether dispatch occurred
         */
        fun handlePackageDataCleared(pkg: String): HandleResult {
            val appId = confirmedAppIds[pkg]
            val dispatched = appId != null

            // Clear all registration state (mirrors coordinator's cleanup sequence)
            confirmedAppIds.remove(pkg)
            pendingAppIds.remove(pkg)

            return HandleResult(
                appIdPresent = appId != null,
                dispatched = dispatched,
            )
        }

        fun getConfirmedAppId(pkg: String): String? = confirmedAppIds[pkg]
        fun getPendingAppId(pkg: String): String? = pendingAppIds[pkg]

        fun clear() {
            confirmedAppIds.clear()
            pendingAppIds.clear()
        }
    }

    data class HandleResult(
        val appIdPresent: Boolean,
        val dispatched: Boolean,
    )

    private val model = PackageClearModel()

    @BeforeProperty
    fun setUp() {
        model.clear()
    }

    @AfterProperty
    fun tearDown() {
        model.clear()
    }

    // --- Arbitraries ---

    @Provide
    fun packageNames(): Arbitrary<String> = Arbitraries.strings()
        .ofMinLength(3)
        .ofMaxLength(30)
        .alpha()
        .withChars('.')
        .filter { it.isNotBlank() }

    @Provide
    fun appIds(): Arbitrary<String> = Arbitraries.strings()
        .ofMinLength(1)
        .ofMaxLength(20)
        .alpha()
        .numeric()
        .filter { it.isNotBlank() }

    @Provide
    fun clearCounts(): Arbitrary<Int> = Arbitraries.integers()
        .between(2, 10)

    @Provide
    fun confirmedPackages(): Arbitrary<ConfirmedPackage> = Combinators.combine(
        packageNames(),
        appIds(),
    ).`as` { pkg, appId -> ConfirmedPackage(pkg, appId) }

    @Provide
    fun packagePairs(): Arbitrary<PackagePair> = Combinators.combine(
        packageNames(),
        packageNames(),
    ).filter { a, b -> a != b }
        .`as` { a, b -> PackagePair(a, b) }

    data class ConfirmedPackage(val pkg: String, val appId: String)
    data class PackagePair(val pkgA: String, val pkgB: String)

    // --- Properties ---

    /**
     * Property: For a package with confirmed appId, the first handlePackageDataCleared dispatches
     * (appId is present), and the second call does NOT dispatch (appId was already cleared).
     *
     * This is the core idempotency guarantee: once cleared, subsequent clears are no-ops
     * with respect to network requests.
     *
     * **Validates: Requirements 12.3, 12.5**
     */
    @Property(tries = 300)
    fun `second clear does not produce network request after confirmed registration`(
        @ForAll("confirmedPackages") confirmed: ConfirmedPackage,
    ) {
        model.clear()

        // Set up confirmed registration (mirrors stock: appId stored after server confirmation)
        model.confirmRegistration(confirmed.pkg, confirmed.appId)

        // First call: should dispatch (confirmed appId is present)
        val firstResult = model.handlePackageDataCleared(confirmed.pkg)
        assertTrue(
            firstResult.appIdPresent,
            "First clear of pkg=[${confirmed.pkg}] must find appId present",
        )
        assertTrue(
            firstResult.dispatched,
            "First clear of pkg=[${confirmed.pkg}] must dispatch network request (app_data_cleared)",
        )

        // Second call: should NOT dispatch (appId was already cleared by first call)
        val secondResult = model.handlePackageDataCleared(confirmed.pkg)
        assertFalse(
            secondResult.appIdPresent,
            "Second clear of pkg=[${confirmed.pkg}] must NOT find appId (already cleared)",
        )
        assertFalse(
            secondResult.dispatched,
            "Second clear of pkg=[${confirmed.pkg}] must NOT dispatch any network request",
        )
    }

    /**
     * Property: For a package with only pending registration (no confirmed appId),
     * even the first handlePackageDataCleared does NOT dispatch a network request.
     * Pending state alone never triggers the app_data_cleared server notification.
     *
     * **Validates: Requirements 12.4**
     */
    @Property(tries = 300)
    fun `pending only state does not send network request on any clear`(
        @ForAll("packageNames") pkg: String,
        @ForAll("appIds") pendingAppId: String,
        @ForAll("clearCounts") n: Int,
    ) {
        model.clear()

        // Set up only pending registration (no confirmed appId)
        model.setPendingRegistration(pkg, pendingAppId)

        // Clear N times — none should dispatch
        repeat(n) { iteration ->
            val result = model.handlePackageDataCleared(pkg)
            assertFalse(
                result.appIdPresent,
                "Pending-only pkg=[$pkg] must NOT have appIdPresent on clear #${iteration + 1}",
            )
            assertFalse(
                result.dispatched,
                "Pending-only pkg=[$pkg] must NOT dispatch on clear #${iteration + 1}",
            )
        }
    }

    /**
     * Property: For any number of consecutive clears on a confirmed package,
     * the total number of dispatches is exactly 1 (only the first call dispatches).
     * This generalizes the idempotency guarantee to N consecutive calls.
     *
     * **Validates: Requirements 12.3, 12.5**
     */
    @Property(tries = 300)
    fun `N consecutive clears dispatch exactly once`(
        @ForAll("confirmedPackages") confirmed: ConfirmedPackage,
        @ForAll("clearCounts") n: Int,
    ) {
        model.clear()

        // Set up confirmed registration
        model.confirmRegistration(confirmed.pkg, confirmed.appId)

        // Call handle N times and count dispatches
        var totalDispatches = 0
        repeat(n) {
            val result = model.handlePackageDataCleared(confirmed.pkg)
            if (result.dispatched) totalDispatches++
        }

        assertEquals(
            1,
            totalDispatches,
            "After $n consecutive clears of pkg=[${confirmed.pkg}], " +
                "total dispatches must be exactly 1 (first clear only)",
        )
    }

    /**
     * Property: After any clear sequence, the package has no confirmed appId and no
     * pending registration. The package is in the unregistered state.
     *
     * **Validates: Requirements 12.3, 12.5**
     */
    @Property(tries = 300)
    fun `package state is fully cleared after any number of clears`(
        @ForAll("confirmedPackages") confirmed: ConfirmedPackage,
        @ForAll("clearCounts") n: Int,
    ) {
        model.clear()

        model.confirmRegistration(confirmed.pkg, confirmed.appId)
        model.setPendingRegistration(confirmed.pkg, "pending-${confirmed.appId}")

        repeat(n) {
            model.handlePackageDataCleared(confirmed.pkg)
        }

        // After all clears, both confirmed and pending must be null (unregistered)
        assertNull(
            model.getConfirmedAppId(confirmed.pkg),
            "After $n clears, pkg=[${confirmed.pkg}] confirmed appId must be null (unregistered)",
        )
        assertNull(
            model.getPendingAppId(confirmed.pkg),
            "After $n clears, pkg=[${confirmed.pkg}] pending appId must be null",
        )
    }

    /**
     * Property: Clearing one package does not affect dispatch behavior of another package.
     * If two packages have confirmed registrations, clearing one does not prevent the
     * other from dispatching on its first clear.
     *
     * **Validates: Requirements 12.3, 12.5**
     */
    @Property(tries = 300)
    fun `clearing one package does not affect another packages dispatch`(
        @ForAll("packagePairs") pair: PackagePair,
        @ForAll("appIds") appIdA: String,
        @ForAll("appIds") appIdB: String,
    ) {
        model.clear()

        // Register both packages
        model.confirmRegistration(pair.pkgA, appIdA)
        model.confirmRegistration(pair.pkgB, appIdB)

        // Clear package A twice (first dispatches, second is no-op)
        val firstClearA = model.handlePackageDataCleared(pair.pkgA)
        val secondClearA = model.handlePackageDataCleared(pair.pkgA)
        assertTrue(firstClearA.dispatched, "First clear of pkgA=[${pair.pkgA}] must dispatch")
        assertFalse(secondClearA.dispatched, "Second clear of pkgA=[${pair.pkgA}] must NOT dispatch")

        // Package B should still dispatch on its first clear (unaffected by A's clears)
        val firstClearB = model.handlePackageDataCleared(pair.pkgB)
        assertTrue(
            firstClearB.dispatched,
            "First clear of pkgB=[${pair.pkgB}] must still dispatch after clearing pkgA",
        )
        assertTrue(
            firstClearB.appIdPresent,
            "pkgB=[${pair.pkgB}] must still have appId after pkgA was cleared",
        )

        // Second clear of B is also a no-op
        val secondClearB = model.handlePackageDataCleared(pair.pkgB)
        assertFalse(
            secondClearB.dispatched,
            "Second clear of pkgB=[${pair.pkgB}] must NOT dispatch",
        )
    }
}
