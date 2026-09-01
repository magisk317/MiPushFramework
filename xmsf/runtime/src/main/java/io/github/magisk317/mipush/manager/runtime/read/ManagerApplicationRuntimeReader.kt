package io.github.magisk317.mipush.manager.runtime.read

import io.github.magisk317.mipush.manager.application.ManagerApplication

/** Read-only application list/detail projection used by the runtime Binder endpoint. */
class ManagerApplicationRuntimeReader(
    private val source: ManagerApplicationReadSource,
    private val maxPageSize: Int = ManagerApplicationReadQuery.DEFAULT_PAGE_SIZE,
    private val maxPayloadBytes: Int = DEFAULT_MAX_PAYLOAD_BYTES,
) {
    suspend fun readPage(query: ManagerApplicationReadQuery): ManagerApplicationReadPage {
        validateQuery(query)
        val userId = currentUserId()
        require(query.userId == userId) { "Application query user mismatch" }
        val stored = source.readStoredApplications()
            .asCurrentUser(userId)
            .associateBy(StoredApplicationSnapshot::packageName)
        val catalog = source.readInstalledApplications(query.includeSystemApps)
        val packageNames = catalog.applications.map(InstalledApplicationSnapshot::packageName)
        val receiveTimes = source.readLastReceiveTimes(packageNames)
        val localProbePackages = packageNames.filter { packageName ->
            stored[packageName]?.registeredType != ManagerApplication.RegisteredType.REGISTERED &&
                stored[packageName]?.registeredType != ManagerApplication.RegisteredType.UNREGISTERED
        }
        val locallyRegistered = source.readLocallyRegisteredPackages(localProbePackages)
        val all = catalog.applications.map { installed ->
            val storedApplication = stored[installed.packageName]
            storedApplication?.toManagerApplication(
                installed = installed,
                lastReceiveTimeMs = receiveTimes[installed.packageName] ?: 0L,
                locallyRegistered = installed.packageName in locallyRegistered,
            ) ?: installed.toTransientManagerApplication(
                locallyRegistered = installed.packageName in locallyRegistered,
                lastReceiveTimeMs = receiveTimes[installed.packageName] ?: 0L,
                userId = userId,
            )
        }
        val filtered = all
            .asSequence()
            .filter { ManagerApplicationReadPolicy.matchesQuery(it, query.query) }
            .filter { ManagerApplicationReadPolicy.matchesFilter(it, query.filterMode) }
            .sortedWith(ManagerApplicationReadPolicy.comparator)
            .toList()
        val stats = statsFor(filtered, catalog.totalCandidatePackages)
        val startIndex = query.pageToken?.let { token ->
            val cursorPackage = ManagerApplicationPageToken.decode(query, token, userId)
            filtered.indexOfFirst { it.packageName == cursorPackage }
                .takeIf { it >= 0 }
                ?.plus(1)
                ?: throw IllegalArgumentException("Application page token is stale")
        } ?: 0
        val pageItems = takeBoundedPage(filtered.drop(startIndex), query.pageSize)
        val nextToken = if (startIndex + pageItems.size < filtered.size) {
            ManagerApplicationPageToken.encode(query, pageItems.last().packageName, userId)
        } else {
            null
        }
        return ManagerApplicationReadPage(
            userId = userId,
            items = pageItems,
            stats = stats,
            nextPageToken = nextToken,
        )
    }

    suspend fun readDetail(packageName: String, ignoreNotRegistered: Boolean): ManagerApplication? {
        require(isValidPackageName(packageName)) { "Invalid application package name" }
        val userId = currentUserId()
        val stored = source.readStoredApplications()
            .asCurrentUser(userId)
            .firstOrNull { it.packageName == packageName }
        val installed = source.readInstalledApplication(packageName)
        if (stored == null && !ignoreNotRegistered) return null
        val lastReceiveTime = source.readLastReceiveTime(packageName)
        val locallyRegistered = if (
            stored == null || stored.registeredType == ManagerApplication.RegisteredType.NOT_REGISTERED
        ) {
            source.hasLocalRegistration(packageName)
        } else {
            false
        }
        return stored?.toManagerApplication(
            installed = installed,
            lastReceiveTimeMs = lastReceiveTime,
            locallyRegistered = locallyRegistered,
            fallbackToInstalledName = false,
            deriveAppNamePinYin = false,
        ) ?: (installed ?: InstalledApplicationSnapshot(packageName, packageName, false))
            .toTransientManagerApplication(
                locallyRegistered = locallyRegistered,
                lastReceiveTimeMs = lastReceiveTime,
                userId = userId,
                notificationOnRegister = false,
                deriveAppNamePinYin = false,
            )
    }

    suspend fun readDiagnostics(packageName: String, registeredType: Int): ManagerApplicationReadDiagnostics {
        require(isValidPackageName(packageName)) { "Invalid application package name" }
        require(registeredType in ManagerApplication.RegisteredType.NOT_REGISTERED..ManagerApplication.RegisteredType.UNREGISTERED) {
            "Invalid registered type"
        }
        val userId = currentUserId()
        val latestEvent = source.readLatestRegistrationEvent(packageName)
        val hasLocalRegistration = source.hasLocalRegistration(packageName)
        val regSecCount = source.readRegSecCount(packageName)
        val hasRegSec = regSecCount > 0
        return ManagerApplicationReadDiagnostics(
            hasLocalRegistration = hasLocalRegistration,
            regSecCount = regSecCount,
            latestRegistrationEventResult = latestEvent?.result,
            registeredType = registeredType,
            userId = userId,
            inferenceReason = ManagerApplicationReadPolicy.inferReason(
                registeredType = registeredType,
                latestEvent = latestEvent,
                hasLocalRegistration = hasLocalRegistration,
                hasRegSec = hasRegSec,
            ),
        )
    }

    private suspend fun currentUserId(): Int =
        source.currentUserId().also { userId ->
            require(userId >= 0) { "Unable to resolve a valid Android user id: $userId" }
        }

    private fun validateQuery(query: ManagerApplicationReadQuery) {
        require(query.schemaVersion >= 1) { "Invalid application query schema" }
        require(query.query.length <= MAX_QUERY_LENGTH) { "Application query is too long" }
        require(query.filterMode in ManagerApplicationReadQuery.FILTER_ALL..ManagerApplicationReadQuery.FILTER_UNREGISTERED) {
            "Invalid application filter mode"
        }
        require(maxPageSize in 1..MAX_MAX_PAGE_SIZE) { "Invalid maximum application page size" }
        require(maxPayloadBytes in MIN_PAYLOAD_BYTES..DEFAULT_MAX_PAYLOAD_BYTES) {
            "Invalid maximum application payload size"
        }
        require(query.pageSize in 1..maxPageSize) { "Invalid application page size" }
    }

    private fun takeBoundedPage(
        remaining: List<ManagerApplication>,
        requestedSize: Int,
    ): List<ManagerApplication> {
        val result = ArrayList<ManagerApplication>(minOf(requestedSize, remaining.size))
        var estimatedBytes = PAGE_FIXED_BYTES
        for (application in remaining.take(requestedSize)) {
            val itemBytes = estimateWireBytes(application)
            if (result.isNotEmpty() && estimatedBytes + itemBytes > maxPayloadBytes) break
            result += application
            estimatedBytes += itemBytes
        }
        return result
    }

    private fun estimateWireBytes(application: ManagerApplication): Int =
        ITEM_FIXED_BYTES +
            estimateWireStringBytes(application.packageName) +
            estimateWireStringBytes(application.appName) +
            estimateWireStringBytes(application.appNamePinYin)

    private fun estimateWireStringBytes(value: String): Int =
        STRING_LENGTH_PREFIX_BYTES + (value.length + STRING_TERMINATOR_CHARS) * UTF16_BYTES_PER_CHAR

    private fun statsFor(items: List<ManagerApplication>, totalCandidates: Int): ManagerApplicationReadStats {
        val usingMiPush = items.size
        val registered = items.count {
            it.registeredType == ManagerApplication.RegisteredType.REGISTERED
        }
        return ManagerApplicationReadStats(
            total = totalCandidates,
            usingMiPush = usingMiPush,
            notUsingMiPush = (totalCandidates - usingMiPush).coerceAtLeast(0),
            registered = registered,
            notRegistered = (usingMiPush - registered).coerceAtLeast(0),
        )
    }

    companion object {
        private const val MAX_QUERY_LENGTH = 512
        private const val MAX_MAX_PAGE_SIZE = 1_000
        private const val DEFAULT_MAX_PAYLOAD_BYTES = 512 * 1_024
        private const val MIN_PAYLOAD_BYTES = 32 * 1_024
        private const val PAGE_FIXED_BYTES = 1_024
        private const val ITEM_FIXED_BYTES = 128
        private const val STRING_LENGTH_PREFIX_BYTES = 4
        private const val STRING_TERMINATOR_CHARS = 1
        private const val UTF16_BYTES_PER_CHAR = 2
        private fun isValidPackageName(packageName: String): Boolean =
            packageName.length in 1..255 &&
                packageName.all { it.isLetterOrDigit() || it == '.' || it == '_' }
    }
}

private fun List<StoredApplicationSnapshot>.asCurrentUser(userId: Int): List<StoredApplicationSnapshot> =
    filter { it.userId == userId }

data class ManagerApplicationReadDiagnostics(
    val hasLocalRegistration: Boolean,
    val regSecCount: Int,
    val latestRegistrationEventResult: Int?,
    val registeredType: Int,
    val inferenceReason: String,
    val userId: Int,
)
