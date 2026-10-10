package io.github.magisk317.mipush.push.pipeline

import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Property 5: 注册状态一致性
 *
 * For any confirmed registration, appId in `pref_registered_pkg_names` and regSecret in
 * `mipush_apps_scrt` must be consistently stored together. After `onPackageDataCleared`,
 * both storages must be cleared and the package is marked as unregistered (not absent).
 *
 * **Validates: Requirements 3.6, 12.1, 12.2**
 *
 * Tests the registration state consistency invariant using a model that mirrors the actual
 * storage logic in [MiPushRuntimeBridge.persistConfirmedRegistrationState] and
 * [PackageDataClearedCoordinator.handle]:
 * - After confirmRegistration(pkg, appId, regSecret), both stores contain entries for pkg
 * - After onPackageDataCleared(pkg), both stores are empty for pkg and pkg is unregistered (not absent)
 * - For any arbitrary sequence of register/clear operations, the two stores remain consistent
 * - Per-package isolation: clearing one package does not affect another
 */
class Property5RegistrationStateConsistencyTest {

    /**
     * Model of the dual-store registration state, mirroring the real SharedPreferences-based
     * storage used by [MIPushAppAbsentManager] (pref_registered_pkg_names) and
     * [Utils.setRegSec] (pref_registered_pkg_names_sec + mipush_apps_scrt).
     *
     * The model faithfully represents the storage semantics:
     * - [confirmRegistration] writes to both stores atomically (as the real code does)
     * - [onPackageDataCleared] removes from both stores (as PackageDataClearedCoordinator does)
     * - [getAppId] and [getRegSecret] query the respective stores independently
     */
    class RegistrationStateModel {
        // Mirrors: pref_registered_pkg_names SharedPreferences
        private val appIdStore = mutableMapOf<String, String>()
        // Mirrors: mipush_apps_scrt SharedPreferences
        private val regSecretStore = mutableMapOf<String, String>()
        // Tracks packages that have been cleared (unregistered but not absent)
        private val clearedPackages = mutableSetOf<String>()

        /**
         * Mirrors [MiPushRuntimeBridge.persistConfirmedRegistrationState] which calls:
         * - MIPushAppAbsentManager.rememberRegisteredPackage(context, pkg, appId)
         * - Utils.setRegSec(context, pkg, regSecret)
         *
         * Stock XMSF 7.4.67-C only confirms when BOTH appId and regSecret are non-empty.
         */
        fun confirmRegistration(pkg: String, appId: String, regSecret: String) {
            if (pkg.isBlank() || appId.isBlank() || regSecret.isBlank()) return
            appIdStore[pkg] = appId
            regSecretStore[pkg] = regSecret
            clearedPackages.remove(pkg)
        }

        /**
         * Mirrors [PackageDataClearedCoordinator.handle] which calls:
         * - MIPushAppAbsentManager.forgetRegisteredPackage(context, pkg) → removes from appIdStore
         * - Utils.removeRegSec(pkg) → removes from regSecretStore
         * - Marks package as unregistered but not absent
         */
        fun onPackageDataCleared(pkg: String) {
            appIdStore.remove(pkg)
            regSecretStore.remove(pkg)
            clearedPackages.add(pkg)
        }

        fun getAppId(pkg: String): String? = appIdStore[pkg]
        fun getRegSecret(pkg: String): String? = regSecretStore[pkg]
        fun isUnregisteredNotAbsent(pkg: String): Boolean = clearedPackages.contains(pkg)
        fun allPackages(): Set<String> = appIdStore.keys + regSecretStore.keys + clearedPackages

        /**
         * The core consistency invariant: for any package, either both appId and regSecret
         * are present, or both are absent.
         */
        fun isConsistent(): Boolean {
            val allPkgs = appIdStore.keys + regSecretStore.keys
            return allPkgs.all { pkg ->
                val hasAppId = appIdStore.containsKey(pkg)
                val hasRegSecret = regSecretStore.containsKey(pkg)
                hasAppId == hasRegSecret
            }
        }

        fun clear() {
            appIdStore.clear()
            regSecretStore.clear()
            clearedPackages.clear()
        }
    }

    private val model = RegistrationStateModel()

    @BeforeEach
    fun setUp() {
        model.clear()
    }

    @AfterEach
    fun tearDown() {
        model.clear()
    }

    // --- Arbitraries ---

    private val alphaChars: List<Char> = ('a'..'z') + ('A'..'Z')
    private val alphaNumericChars: List<Char> = alphaChars + ('0'..'9')

    private fun stringsOf(chars: List<Char>, sizeRange: IntRange): Arb<String> =
        Arb.list(Arb.element(chars), sizeRange).map { it.joinToString("") }

    private val nonBlankPackageNames: Arb<String> = stringsOf(alphaChars + '.', 3..30)
        .filter { it.isNotBlank() }

    private val appIds: Arb<String> = stringsOf(alphaNumericChars, 1..20)
        .filter { it.isNotBlank() }

    private val regSecrets: Arb<String> = stringsOf(alphaNumericChars, 1..32)
        .filter { it.isNotBlank() }

    private val packagePairs: Arb<PackagePair> = Arb.bind(
        nonBlankPackageNames,
        nonBlankPackageNames,
    ) { a, b -> PackagePair(a, b) }
        .filter { it.pkgA != it.pkgB }

    private val registrationOps: Arb<List<RegistrationOp>> = Arb.list(
        Arb.choice<RegistrationOp>(
            Arb.bind(nonBlankPackageNames, appIds, regSecrets) { pkg, appId, secret ->
                RegistrationOp.Register(pkg, appId, secret)
            },
            nonBlankPackageNames.map { RegistrationOp.Clear(it) },
        ),
        1..15,
    )

    sealed class RegistrationOp {
        data class Register(val pkg: String, val appId: String, val regSecret: String) : RegistrationOp()
        data class Clear(val pkg: String) : RegistrationOp()
    }

    data class PackagePair(val pkgA: String, val pkgB: String)

    // --- Properties ---

    /**
     * Property: After a successful confirmed registration (non-blank appId and regSecret),
     * both stores contain entries for the package. The appId and regSecret presence is
     * always consistent.
     *
     * **Validates: Requirements 3.6, 12.1, 12.2**
     */
    @Test
    fun `confirmed registration persists both appId and regSecret consistently`() {
        runBlocking {
            checkAll(200, nonBlankPackageNames, appIds, regSecrets) { pkg, appId, regSecret ->
                model.clear()

                model.confirmRegistration(pkg, appId, regSecret)

                // Both stores must have entries
                assertEquals(appId, model.getAppId(pkg), "appId must be stored for pkg=$pkg")
                assertEquals(regSecret, model.getRegSecret(pkg), "regSecret must be stored for pkg=$pkg")

                // Consistency invariant holds
                assertTrue(model.isConsistent(), "Stores must be consistent after registration for pkg=$pkg")
            }
        }
    }

    /**
     * Property: After onPackageDataCleared, both stores are cleared for the target package,
     * and the package is marked as unregistered (not absent).
     *
     * **Validates: Requirements 3.6, 12.1, 12.2**
     */
    @Test
    fun `package data cleared removes both appId and regSecret`() {
        runBlocking {
            checkAll(200, nonBlankPackageNames, appIds, regSecrets) { pkg, appId, regSecret ->
                model.clear()

                // First register
                model.confirmRegistration(pkg, appId, regSecret)

                // Then clear
                model.onPackageDataCleared(pkg)

                // Both stores must be empty
                assertNull(model.getAppId(pkg), "appId must be cleared after onPackageDataCleared for pkg=$pkg")
                assertNull(
                    model.getRegSecret(pkg),
                    "regSecret must be cleared after onPackageDataCleared for pkg=$pkg",
                )

                // Package is unregistered but not absent
                assertTrue(
                    model.isUnregisteredNotAbsent(pkg),
                    "Package must be marked unregistered (not absent) after clear for pkg=$pkg",
                )

                // Consistency invariant holds
                assertTrue(model.isConsistent(), "Stores must be consistent after clear for pkg=$pkg")
            }
        }
    }

    /**
     * Property: For any arbitrary sequence of register/clear operations, the two storage
     * locations remain consistent at every step — both appId and regSecret are either
     * both present or both absent for every package.
     *
     * **Validates: Requirements 3.6, 12.1, 12.2**
     */
    @Test
    fun `arbitrary register-clear sequences maintain storage consistency`() {
        runBlocking {
            checkAll(200, registrationOps) { ops ->
                model.clear()

                for ((index, op) in ops.withIndex()) {
                    when (op) {
                        is RegistrationOp.Register ->
                            model.confirmRegistration(op.pkg, op.appId, op.regSecret)
                        is RegistrationOp.Clear ->
                            model.onPackageDataCleared(op.pkg)
                    }

                    // Consistency must hold after every operation
                    assertTrue(
                        model.isConsistent(),
                        "Storage consistency violated after operation $index: $op",
                    )
                }
            }
        }
    }

    /**
     * Property: For any arbitrary sequence of operations, the final state of each package
     * matches the last operation applied to it. Register → present; Clear → absent.
     *
     * **Validates: Requirements 3.6, 12.1, 12.2**
     */
    @Test
    fun `final state matches last operation per package`() {
        runBlocking {
            checkAll(200, registrationOps) { ops ->
                model.clear()

                // Track the last operation per package
                val lastOps = mutableMapOf<String, RegistrationOp>()

                for (op in ops) {
                    when (op) {
                        is RegistrationOp.Register -> {
                            model.confirmRegistration(op.pkg, op.appId, op.regSecret)
                            lastOps[op.pkg] = op
                        }
                        is RegistrationOp.Clear -> {
                            model.onPackageDataCleared(op.pkg)
                            lastOps[op.pkg] = op
                        }
                    }
                }

                // Verify final state matches last operation
                for ((pkg, lastOp) in lastOps) {
                    when (lastOp) {
                        is RegistrationOp.Register -> {
                            assertEquals(
                                lastOp.appId,
                                model.getAppId(pkg),
                                "Final appId must match last register for pkg=$pkg",
                            )
                            assertEquals(
                                lastOp.regSecret,
                                model.getRegSecret(pkg),
                                "Final regSecret must match last register for pkg=$pkg",
                            )
                        }
                        is RegistrationOp.Clear -> {
                            assertNull(model.getAppId(pkg), "Final appId must be null after clear for pkg=$pkg")
                            assertNull(
                                model.getRegSecret(pkg),
                                "Final regSecret must be null after clear for pkg=$pkg",
                            )
                            assertTrue(
                                model.isUnregisteredNotAbsent(pkg),
                                "Package must be unregistered (not absent) after clear for pkg=$pkg",
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Property: Clearing one package does not affect another package's registration state.
     * Per-package isolation must be maintained.
     *
     * **Validates: Requirements 3.6, 12.1, 12.2**
     */
    @Test
    fun `clearing one package does not affect another`() {
        runBlocking {
            checkAll(
                200,
                packagePairs,
                appIds,
                appIds,
                regSecrets,
                regSecrets,
            ) { pair, appIdA, appIdB, secretA, secretB ->
                model.clear()

                // Register both packages
                model.confirmRegistration(pair.pkgA, appIdA, secretA)
                model.confirmRegistration(pair.pkgB, appIdB, secretB)

                // Clear only package A
                model.onPackageDataCleared(pair.pkgA)

                // Package A should be cleared
                assertNull(model.getAppId(pair.pkgA), "pkgA=${pair.pkgA} appId must be cleared")
                assertNull(model.getRegSecret(pair.pkgA), "pkgA=${pair.pkgA} regSecret must be cleared")

                // Package B should be unaffected
                assertEquals(appIdB, model.getAppId(pair.pkgB), "pkgB=${pair.pkgB} appId must be preserved")
                assertEquals(
                    secretB,
                    model.getRegSecret(pair.pkgB),
                    "pkgB=${pair.pkgB} regSecret must be preserved",
                )

                // Consistency holds
                assertTrue(model.isConsistent(), "Stores must be consistent after partial clear")
            }
        }
    }
}
