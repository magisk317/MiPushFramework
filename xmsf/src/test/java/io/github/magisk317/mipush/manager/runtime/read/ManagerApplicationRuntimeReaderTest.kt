package io.github.magisk317.mipush.manager.runtime.read

import io.github.magisk317.mipush.common.manager.ManagerApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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

        val first = reader.readPage(query)
        assertEquals(listOf("registered", "observed"), first.items.map { it.packageName })
        assertEquals(4, first.stats.total)
        assertEquals(4, first.stats.usingMiPush)
        assertEquals(1, first.stats.registered)
        assertNotNull(first.nextPageToken)
        assertFalse(first.nextPageToken!!.contains("observed"))

        val second = reader.readPage(query.copy(pageToken = first.nextPageToken))
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

        val result = ManagerApplicationRuntimeReader(source).readPage(ManagerApplicationReadQuery())

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

        val notRegistered = reader.readPage(
            ManagerApplicationReadQuery(filterMode = ManagerApplicationReadQuery.FILTER_NOT_REGISTERED),
        )
        val unregistered = reader.readPage(
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
        val token = reader.readPage(firstQuery).nextPageToken

        assertThrows(IllegalArgumentException::class.java) {
            reader.readPage(firstQuery.copy(query = "two", pageToken = token))
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
        val page = ManagerApplicationRuntimeReader(source).readPage(
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

        assertNull(reader.readDetail("target", ignoreNotRegistered = false))
        val detail = reader.readDetail("target", ignoreNotRegistered = true)

        assertEquals("target", detail?.packageName)
        assertEquals("Target", detail?.appName)
        assertEquals(ManagerApplication.RegisteredType.NOT_REGISTERED, detail?.registeredType)
        assertFalse(detail!!.notificationOnRegister)
        assertEquals("", detail.appNamePinYin)
        assertTrue(source.stored.isEmpty())
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

        val listItem = reader.readPage(ManagerApplicationReadQuery()).items.single()
        val detail = reader.readDetail("target", ignoreNotRegistered = false)

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

        ManagerApplicationRuntimeReader(source).readPage(ManagerApplicationReadQuery())

        assertEquals(setOf("pending", "transient"), source.lastLocalProbePackages)
    }

    @Test
    fun `diagnostics reads each source once and keeps legacy inference`() {
        val source = FakeSource(
            localRegistration = true,
            regSecCount = 2,
            latestEvent = RegistrationEventSnapshot(type = 21, result = 1),
        )
        val diagnostics = ManagerApplicationRuntimeReader(source).readDiagnostics(
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
    ) : ManagerApplicationReadSource {
        var storedReadCount = 0
        var regSecReadCount = 0
        var lastLocalProbePackages: Set<String> = emptySet()

        override fun readStoredApplications(): List<StoredApplicationSnapshot> {
            storedReadCount++
            return stored
        }

        override fun readInstalledApplications(includeSystemApps: Boolean) =
            ApplicationCatalogSnapshot(
                totalCandidatePackages = installed.size,
                applications = installed.filter(InstalledApplicationSnapshot::hasMiPushServices),
            )

        override fun readInstalledApplication(packageName: String): InstalledApplicationSnapshot? =
            installed.firstOrNull { it.packageName == packageName }

        override fun readLastReceiveTime(packageName: String): Long = detailReceiveTimes[packageName] ?: 0L

        override fun readLastReceiveTimes(packageNames: Collection<String>): Map<String, Long> =
            packageNames.associateWith { receiveTimes[it] ?: 0L }

        override fun readLocallyRegisteredPackages(packageNames: Collection<String>): Set<String> {
            lastLocalProbePackages = packageNames.toSet()
            return locallyRegistered.intersect(lastLocalProbePackages)
        }

        override fun hasLocalRegistration(packageName: String): Boolean =
            localRegistration || packageName in locallyRegistered

        override fun readRegSecCount(packageName: String): Int {
            regSecReadCount++
            return regSecCount
        }

        override fun readLatestRegistrationEvent(packageName: String): RegistrationEventSnapshot? = latestEvent
    }

    companion object {
        private fun stored(
            packageName: String,
            registeredType: Int,
            appName: String = packageName,
        ) =
            StoredApplicationSnapshot(
                id = packageName.hashCode().toLong(),
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
