package io.github.magisk317.mipush.manager.runtime.read

import io.github.magisk317.mipush.common.manager.ManagerApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlinx.coroutines.runBlocking

private fun ManagerApplicationRuntimeReader.readPageBlocking(query: ManagerApplicationReadQuery) =
    runBlocking { readPage(query) }

private fun ManagerApplicationRuntimeReader.readDetailBlocking(
    packageName: String,
    ignoreNotRegistered: Boolean,
) = runBlocking { readDetail(packageName, ignoreNotRegistered) }

private fun ManagerApplicationRuntimeReader.readDiagnosticsBlocking(
    packageName: String,
    registeredType: Int,
) = runBlocking { readDiagnostics(packageName, registeredType) }

class ManagerApplicationRuntimeReaderTest {
    @Test
    fun `list preserves legacy order and continues with an opaque keyset token`() {
        val source = FakeSource(
            stored = listOf(
                stored("registered", ManagerApplication.RegisteredType.REGISTERED),
                stored("observed", ManagerApplication.RegisteredType.NOT_REGISTERED),
                stored("unregistered", ManagerApplication.RegisteredType.UNREGISTERED),
            ),
            installed = listOf(
                installed("registered", "A"),
                installed("observed", "B"),
                installed("unregistered", "C"),
                installed("transient", "D"),
            ),
            receiveTimes = mapOf("observed" to 20L),
        )
        val reader = ManagerApplicationRuntimeReader(source, maxPageSize = 2)
        val query = ManagerApplicationReadQuery(pageSize = 2)

        val first = reader.readPageBlocking(query)
        assertEquals(listOf("registered", "observed"), first.items.map { it.packageName })
        assertEquals(4, first.stats.total)
        assertEquals(4, first.stats.usingMiPush)
        assertEquals(1, first.stats.registered)
        assertNotNull(first.nextPageToken)
        assertFalse(first.nextPageToken!!.contains("observed"))

        val second = reader.readPageBlocking(query.copy(pageToken = first.nextPageToken))
        assertEquals(listOf("unregistered", "transient"), second.items.map { it.packageName })
        assertNull(second.nextPageToken)
    }

    @Test
    fun `list projects local registration without changing the source row`() {
        val row = stored("target", ManagerApplication.RegisteredType.NOT_REGISTERED)
        val source = FakeSource(
            stored = listOf(row),
            installed = listOf(installed("target", "Target")),
            locallyRegistered = setOf("target"),
        )

        val result = ManagerApplicationRuntimeReader(source).readPageBlocking(ManagerApplicationReadQuery())

        assertEquals(ManagerApplication.RegisteredType.REGISTERED, result.items.single().registeredType)
        assertEquals(ManagerApplication.RegisteredType.NOT_REGISTERED, row.registeredType)
        assertEquals(1, source.storedReadCount)
    }

    @Test
    fun `filters run before pagination and keep the legacy inactive predicates`() {
        val source = FakeSource(
            stored = listOf(
                stored("never", ManagerApplication.RegisteredType.NOT_REGISTERED),
                stored("observed", ManagerApplication.RegisteredType.NOT_REGISTERED),
                stored("unregistered", ManagerApplication.RegisteredType.UNREGISTERED),
            ),
            installed = listOf(
                installed("never", "Never"),
                installed("observed", "Observed"),
                installed("unregistered", "Unregistered"),
            ),
            receiveTimes = mapOf("observed" to 1L),
        )
        val reader = ManagerApplicationRuntimeReader(source)

        val notRegistered = reader.readPageBlocking(
            ManagerApplicationReadQuery(filterMode = ManagerApplicationReadQuery.FILTER_NOT_REGISTERED),
        )
        val unregistered = reader.readPageBlocking(
            ManagerApplicationReadQuery(filterMode = ManagerApplicationReadQuery.FILTER_UNREGISTERED),
        )

        assertEquals(listOf("never"), notRegistered.items.map { it.packageName })
        assertEquals(listOf("unregistered"), unregistered.items.map { it.packageName })
    }

    @Test
    fun `token cannot be replayed with a different query`() {
        val source = FakeSource(
            installed = listOf(installed("one", "One"), installed("two", "Two")),
        )
        val reader = ManagerApplicationRuntimeReader(source, maxPageSize = 1)
        val firstQuery = ManagerApplicationReadQuery(pageSize = 1)
        val token = reader.readPageBlocking(firstQuery).nextPageToken

        assertThrows(IllegalArgumentException::class.java) {
            reader.readPageBlocking(firstQuery.copy(query = "two", pageToken = token))
        }
    }

    @Test
    fun `rejects a query for another runtime user`() {
        val reader = ManagerApplicationRuntimeReader(FakeSource(userId = 0))

        assertThrows(IllegalArgumentException::class.java) {
            reader.readPageBlocking(ManagerApplicationReadQuery(userId = 999))
        }
    }

    @Test
    fun `page item count shrinks before the negotiated payload bound`() {
        val longLabel = "x".repeat(4_096)
        val source = FakeSource(
            installed = (1..100).map { index ->
                installed("package$index", longLabel)
            },
        )
        val page = ManagerApplicationRuntimeReader(source).readPageBlocking(
            ManagerApplicationReadQuery(pageSize = 100),
        )

        assertTrue(page.items.isNotEmpty())
        assertTrue(page.items.size < 100)
        assertNotNull(page.nextPageToken)
        assertEquals(100, page.stats.usingMiPush)
    }

    @Test
    fun `detail can synthesize a transient row without persisting it`() {
        val source = FakeSource(
            installed = listOf(installed("target", "Target")),
        )
        val reader = ManagerApplicationRuntimeReader(source)

        assertNull(reader.readDetailBlocking("target", ignoreNotRegistered = false))
        val detail = reader.readDetailBlocking("target", ignoreNotRegistered = true)

        assertEquals("target", detail?.packageName)
        assertEquals("Target", detail?.appName)
        assertEquals(ManagerApplication.RegisteredType.NOT_REGISTERED, detail?.registeredType)
        assertFalse(detail!!.notificationOnRegister)
        assertEquals("", detail.appNamePinYin)
        assertTrue(source.stored.isEmpty())
    }

    @Test
    fun `transient list and detail rows use the captured runtime user`() {
        val source = FakeSource(
            installed = listOf(installed("target", "Target")),
            userId = 999,
        )
        val reader = ManagerApplicationRuntimeReader(source)

        val listItem = reader.readPageBlocking(ManagerApplicationReadQuery(userId = 999)).items.single()
        val detail = reader.readDetailBlocking("target", ignoreNotRegistered = true)
        val diagnostics = reader.readDiagnosticsBlocking(
            packageName = "target",
            registeredType = ManagerApplication.RegisteredType.NOT_REGISTERED,
        )

        assertEquals(999, listItem.userId)
        assertEquals(999, detail?.userId)
        assertEquals(999, diagnostics.userId)
    }

    @Test
    fun `stored rows from another user do not leak into list or detail`() {
        val source = FakeSource(
            stored = listOf(stored("target", ManagerApplication.RegisteredType.REGISTERED, userId = 999)),
            installed = listOf(installed("target", "Target")),
        )
        val reader = ManagerApplicationRuntimeReader(source)

        val listItem = reader.readPageBlocking(ManagerApplicationReadQuery()).items.single()
        val detail = reader.readDetailBlocking("target", ignoreNotRegistered = false)

        assertEquals(ManagerApplication.RegisteredType.NOT_REGISTERED, listItem.registeredType)
        assertEquals(0, listItem.userId)
        assertNull(detail)
    }

    @Test
    fun `list and detail preserve their legacy name and receive-time projections`() {
        val source = FakeSource(
            stored = listOf(stored("target", ManagerApplication.RegisteredType.NOT_REGISTERED, appName = "")),
            installed = listOf(installed("target", "Target")),
            receiveTimes = mapOf("target" to 20L),
            detailReceiveTimes = mapOf("target" to 5L),
        )
        val reader = ManagerApplicationRuntimeReader(source)

        val listItem = reader.readPageBlocking(ManagerApplicationReadQuery()).items.single()
        val detail = reader.readDetailBlocking("target", ignoreNotRegistered = false)

        assertEquals("Target", listItem.appName)
        assertEquals("target", listItem.appNamePinYin)
        assertEquals(20L, listItem.lastReceiveTimeMs)
        assertEquals("", detail?.appName)
        assertEquals("", detail?.appNamePinYin)
        assertEquals(5L, detail?.lastReceiveTimeMs)
    }

    @Test
    fun `list probes local state only for not-registered rows`() {
        val source = FakeSource(
            stored = listOf(
                stored("registered", ManagerApplication.RegisteredType.REGISTERED),
                stored("unregistered", ManagerApplication.RegisteredType.UNREGISTERED),
                stored("pending", ManagerApplication.RegisteredType.NOT_REGISTERED),
            ),
            installed = listOf(
                installed("registered", "Registered"),
                installed("unregistered", "Unregistered"),
                installed("pending", "Pending"),
                installed("transient", "Transient"),
            ),
        )

        ManagerApplicationRuntimeReader(source).readPageBlocking(ManagerApplicationReadQuery())

        assertEquals(setOf("pending", "transient"), source.lastLocalProbePackages)
    }

    @Test
    fun `diagnostics reads each source once and keeps legacy inference`() {
        val source = FakeSource(
            localRegistration = true,
            regSecCount = 2,
            latestEvent = RegistrationEventSnapshot(type = 21, result = 1),
        )
        val diagnostics = ManagerApplicationRuntimeReader(source).readDiagnosticsBlocking(
            packageName = "target",
            registeredType = ManagerApplication.RegisteredType.NOT_REGISTERED,
        )

        assertTrue(diagnostics.hasLocalRegistration)
        assertEquals(2, diagnostics.regSecCount)
        assertEquals(1, diagnostics.latestRegistrationEventResult)
        assertEquals("registration_result_failed", diagnostics.inferenceReason)
        assertEquals(1, source.regSecReadCount)
    }

    private class FakeSource(
        val stored: List<StoredApplicationSnapshot> = emptyList(),
        private val installed: List<InstalledApplicationSnapshot> = emptyList(),
        private val receiveTimes: Map<String, Long> = emptyMap(),
        private val detailReceiveTimes: Map<String, Long> = receiveTimes,
        private val locallyRegistered: Set<String> = emptySet(),
        private val localRegistration: Boolean = false,
        private val regSecCount: Int = 0,
        private val latestEvent: RegistrationEventSnapshot? = null,
        private val userId: Int = 0,
    ) : ManagerApplicationReadSource {
        var storedReadCount = 0
        var regSecReadCount = 0
        var lastLocalProbePackages: Set<String> = emptySet()

        override suspend fun currentUserId(): Int = userId

        override suspend fun readStoredApplications(): List<StoredApplicationSnapshot> {
            storedReadCount++
            return stored
        }

        override suspend fun readInstalledApplications(includeSystemApps: Boolean) =
            ApplicationCatalogSnapshot(
                totalCandidatePackages = installed.size,
                applications = installed.filter(InstalledApplicationSnapshot::hasMiPushServices),
            )

        override suspend fun readInstalledApplication(packageName: String): InstalledApplicationSnapshot? =
            installed.firstOrNull { it.packageName == packageName }

        override suspend fun readLastReceiveTime(packageName: String): Long = detailReceiveTimes[packageName] ?: 0L

        override suspend fun readLastReceiveTimes(packageNames: Collection<String>): Map<String, Long> =
            packageNames.associateWith { receiveTimes[it] ?: 0L }

        override suspend fun readLocallyRegisteredPackages(packageNames: Collection<String>): Set<String> {
            lastLocalProbePackages = packageNames.toSet()
            return locallyRegistered.intersect(lastLocalProbePackages)
        }

        override suspend fun hasLocalRegistration(packageName: String): Boolean =
            localRegistration || packageName in locallyRegistered

        override suspend fun readRegSecCount(packageName: String): Int {
            regSecReadCount++
            return regSecCount
        }

        override suspend fun readLatestRegistrationEvent(packageName: String): RegistrationEventSnapshot? = latestEvent
    }

    companion object {
        private fun stored(
            packageName: String,
            registeredType: Int,
            appName: String = packageName,
            userId: Int = 0,
        ) =
            StoredApplicationSnapshot(
                id = packageName.hashCode().toLong(),
                userId = userId,
                packageName = packageName,
                type = ManagerApplication.Type.ASK,
                notificationOnRegister = true,
                blocked = false,
                islandEnabled = true,
                islandFocusNotification = false,
                registeredType = registeredType,
                appName = appName,
            )

        private fun installed(packageName: String, appName: String) =
            InstalledApplicationSnapshot(
                packageName = packageName,
                appName = appName,
                hasMiPushServices = true,
            )
    }
}
