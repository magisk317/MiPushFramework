package io.github.magisk317.mipush.manager.application

import io.github.magisk317.mipush.manager.api.ManagerApplicationDetailDto
import io.github.magisk317.mipush.manager.api.ManagerApplicationPageDto
import io.github.magisk317.mipush.manager.api.ManagerApplicationQueryDto
import io.github.magisk317.mipush.manager.api.ManagerApplicationStatsDto
import io.github.magisk317.mipush.manager.api.ManagerApplicationSummaryDto
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.client.ManagerRuntimeResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ApplicationReadSourcesTest {
    @Test
    fun `list primary load does not invoke the remote source`() = runBlocking {
        val primary = ApplicationListSnapshot(
            applications = io.github.magisk317.mipush.manager.application.ManagerApplications(
                items = listOf(app("example.app")),
                totalPkg = 1,
            ),
            stats = ApplicationListStats(1, 1, 0, 0, 1),
        )
        var remoteLoads = 0
        val source = ComparingApplicationListSource(
            primaryLoader = { primary },
            remoteLoader = {
                remoteLoads += 1
                ApplicationReadResult.Unavailable(ApplicationReadStatus.UNSUPPORTED)
            },
            enableRemoteCompare = true,
        )

        assertEquals(primary, source.loadPrimary(ApplicationListRequest()))
        assertEquals(0, remoteLoads)
        assertEquals(
            ApplicationListComparison.Skipped(ApplicationReadStatus.UNSUPPORTED),
            source.compareRemote(ApplicationListRequest(), primary),
        )
        assertEquals(1, remoteLoads)
    }

    @Test
    fun `detail primary load does not invoke remote and unavailable is skippable`() = runBlocking {
        val primary = app("example.app")
        var detailLoads = 0
        var diagnosticsLoads = 0
        val source = ComparingApplicationDetailSource(
            primaryLoader = { _, _ -> primary },
            primaryDiagnosticsLoader = { _, registeredType ->
                io.github.magisk317.mipush.manager.application.ManagerApplicationDiagnostics(
                    registeredType = registeredType,
                    hasLocalRegistration = false,
                    regSecCount = 0,
                    latestRegistrationEventResult = null,
                    inferenceReason = "never_attempted",
                    userId = 0,
                )
            },
            remoteLoader = { _, _ ->
                detailLoads += 1
                ApplicationReadResult.Unavailable(ApplicationReadStatus.TIMED_OUT)
            },
            remoteDiagnosticsLoader = { _, _ ->
                diagnosticsLoads += 1
                ApplicationReadResult.Unavailable(ApplicationReadStatus.PERMISSION_DENIED)
            },
            enableRemoteCompare = true,
        )

        assertEquals(primary, source.loadPrimary("example.app", false))
        assertEquals(0, detailLoads)
        assertEquals(
            ApplicationDetailComparison.Skipped(ApplicationReadStatus.TIMED_OUT),
            source.compareRemote("example.app", false, primary),
        )
        assertEquals(1, detailLoads)
        val primaryDiagnostics = source.loadPrimaryDiagnostics("example.app", 0)
        assertEquals(0, diagnosticsLoads)
        assertEquals(
            ApplicationDiagnosticsComparison.Skipped(ApplicationReadStatus.PERMISSION_DENIED),
            source.compareRemoteDiagnostics("example.app", 0, primaryDiagnostics),
        )
        assertEquals(1, diagnosticsLoads)
    }

    @Test
    fun `detail remote runtime failure is reduced to a skippable result`() = runBlocking {
        val source = RemoteApplicationDetailSource(
            detailLoader = { _, _ -> error("remote detail failure") },
            diagnosticsLoader = { _, _ -> error("remote diagnostics failure") },
        )

        assertEquals(
            ApplicationReadResult.Unavailable(ApplicationReadStatus.FAILED),
            source.load("example.app", false),
        )
        assertEquals(
            ApplicationReadResult.Unavailable(ApplicationReadStatus.FAILED),
            source.loadDiagnostics("example.app", 0),
        )
    }

    @Test
    fun `remote list follows opaque pages and maps stats`() = runBlocking {
        val pages = listOf(
            ManagerApplicationPageDto(
                items = listOf(summary("one"), summary("two", registered = true)),
                stats = stats(total = 3, using = 3, registered = 1),
                nextPageToken = "next",
            ),
            ManagerApplicationPageDto(
                items = listOf(summary("three")),
                stats = stats(total = 3, using = 3, registered = 1),
            ),
        )
        val seenTokens = mutableListOf<String?>()
        val seenQueries = mutableListOf<String>()
        val source = RemoteApplicationListSource(
            pageLoader = { query: ManagerApplicationQueryDto ->
                seenTokens += query.pageToken
                seenQueries += query.query
                ManagerRuntimeResult.Success(pages[seenTokens.lastIndex])
            },
            pageSizeProvider = { 2 },
            userIdProvider = { 0 },
        )

        val result = source.load(ApplicationListRequest(query = "example"))

        assertEquals(
            ApplicationReadResult.Available(
                ApplicationListSnapshot(
                    applications = io.github.magisk317.mipush.manager.application.ManagerApplications(
                        items = listOf(
                            app("one"),
                            app("two", registered = true),
                            app("three"),
                        ),
                        totalPkg = 3,
                    ),
                    stats = ApplicationListStats(3, 3, 0, 1, 2),
                ),
            ),
            result,
        )
        assertEquals(listOf(null, "next"), seenTokens)
        assertEquals(listOf("example", "example"), seenQueries)
    }

    @Test
    fun `remote list rejects a page from another user`() = runBlocking {
        val source = RemoteApplicationListSource(
            pageLoader = {
                ManagerRuntimeResult.Success(
                    ManagerApplicationPageDto(
                        userId = 999,
                        stats = stats(total = 0, using = 0, registered = 0),
                    ),
                )
            },
            pageSizeProvider = { 1 },
            userIdProvider = { 0 },
        )

        assertEquals(
            ApplicationReadResult.Unavailable(ApplicationReadStatus.FAILED),
            source.load(ApplicationListRequest()),
        )
    }

    @Test
    fun `remote list rejects an item from another user`() = runBlocking {
        val source = RemoteApplicationListSource(
            pageLoader = {
                ManagerRuntimeResult.Success(
                    ManagerApplicationPageDto(
                        items = listOf(summary("foreign").copy(userId = 999)),
                        stats = stats(total = 1, using = 1, registered = 0),
                        userId = 0,
                    ),
                )
            },
            pageSizeProvider = { 1 },
            userIdProvider = { 0 },
        )

        assertEquals(
            ApplicationReadResult.Unavailable(ApplicationReadStatus.FAILED),
            source.load(ApplicationListRequest()),
        )
    }

    @Test
    fun `remote diagnostics rejects a response from another user`() = runBlocking {
        val source = RemoteApplicationDetailSource(
            detailLoader = { _, _ -> ManagerRuntimeResult.Success(null) },
            diagnosticsLoader = { _, _ ->
                ManagerRuntimeResult.Success(
                    io.github.magisk317.mipush.manager.api.ManagerApplicationDiagnosticsDto(userId = 999),
                )
            },
            userIdProvider = { 0 },
        )

        assertEquals(
            ApplicationReadResult.Unavailable(ApplicationReadStatus.FAILED),
            source.loadDiagnostics("example.app", 0),
        )
    }

    @Test
    fun `duplicate page token is skipped without looping`() = runBlocking {
        val source = RemoteApplicationListSource(
            pageLoader = {
                ManagerRuntimeResult.Success(
                    ManagerApplicationPageDto(
                        items = listOf(summary("one")),
                        stats = stats(total = 2, using = 2, registered = 0),
                        nextPageToken = "same",
                    ),
                )
            },
            pageSizeProvider = { 1 },
        )

        val result = source.load(ApplicationListRequest())

        assertEquals(
            ApplicationReadResult.Unavailable(ApplicationReadStatus.FAILED),
            result,
        )
    }

    @Test
    fun `unsupported list capability is reduced to a skippable status`() = runBlocking {
        val source = RemoteApplicationListSource(
            pageLoader = { ManagerRuntimeResult.Unsupported(ManagerProtocol.CAPABILITY_APPLICATION_LIST) },
            pageSizeProvider = { 1 },
            userIdProvider = { 0 },
        )

        val result = source.load(ApplicationListRequest())

        assertEquals(
            ApplicationReadResult.Unavailable(ApplicationReadStatus.UNSUPPORTED),
            result,
        )
    }

    @Test
    fun `remote detail maps every domain field and preserves not found`() = runBlocking {
        val detail = ManagerApplicationDetailDto(
            id = 7L,
            userId = 999,
            packageName = "example.app",
            type = 2,
            notificationOnRegister = true,
            blocked = true,
            islandEnabled = false,
            islandFocusNotification = false,
            clickFallbackEnabled = true,
            registeredType = 1,
            existServices = true,
            appName = "Example",
            appNamePinYin = "example",
            lastReceiveTimeMs = 9L,
        )
        val source = RemoteApplicationDetailSource(
            detailLoader = { _, _ -> ManagerRuntimeResult.Success(detail) },
            diagnosticsLoader = { _, _ -> ManagerRuntimeResult.Success(io.github.magisk317.mipush.manager.api.ManagerApplicationDiagnosticsDto()) },
            userIdProvider = { 999 },
        )

        val result = source.load("example.app", ignoreNotRegistered = false)

        assertTrue(result is ApplicationReadResult.Available)
        assertEquals(app("example.app", registered = true).copy(
            id = 7L,
            userId = 999,
            type = 2,
            notificationOnRegister = true,
            blocked = true,
            islandEnabled = false,
            clickFallbackEnabled = true,
            existServices = true,
            appName = "Example",
            appNamePinYin = "example",
            lastReceiveTimeMs = 9L,
        ), (result as ApplicationReadResult.Available).value)
    }

    private fun summary(packageName: String, registered: Boolean = false) = ManagerApplicationSummaryDto(
        id = packageName.hashCode().toLong(),
        packageName = packageName,
        registeredType = if (registered) 1 else 0,
        appName = packageName,
        appNamePinYin = packageName,
    )

    private fun app(packageName: String, registered: Boolean = false) =
        io.github.magisk317.mipush.manager.application.ManagerApplication(
            id = packageName.hashCode().toLong(),
            packageName = packageName,
            registeredType = if (registered) 1 else 0,
            appName = packageName,
            appNamePinYin = packageName,
        )

    private fun stats(total: Int, using: Int, registered: Int) = ManagerApplicationStatsDto(
        total = total,
        usingMiPush = using,
        notUsingMiPush = total - using,
        registered = registered,
        notRegistered = using - registered,
    )
}
