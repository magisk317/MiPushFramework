package io.github.magisk317.mipush.main.viewmodel

import io.github.magisk317.mipush.feature.main.subpage.ApplicationListLoadOutcome
import io.github.magisk317.mipush.feature.main.subpage.ApplicationPageOperation
import io.github.magisk317.mipush.manager.api.ManagerApplicationPageDto
import io.github.magisk317.mipush.manager.api.ManagerApplicationQueryDto
import io.github.magisk317.mipush.manager.api.ManagerApplicationStatsDto
import io.github.magisk317.mipush.manager.application.RemoteApplicationListSource
import io.github.magisk317.mipush.manager.client.ManagerRuntimeResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlinx.coroutines.runBlocking

class OverviewViewModelTest {
    @Test
    fun `overview forwards the system application preference to the runtime query`() = runBlocking {
        val requests = mutableListOf<ManagerApplicationQueryDto>()
        val source = RemoteApplicationListSource(
            pageLoader = { query ->
                requests += query
                ManagerRuntimeResult.Success(
                    ManagerApplicationPageDto(
                        stats = ManagerApplicationStatsDto(),
                    ),
                )
            },
            pageSizeProvider = { 100 },
            userIdProvider = { 0 },
        )

        val result = loadOverviewApplications(
            applicationPageOperation = ApplicationPageOperation(source),
            includeSystemApps = true,
        )

        assertTrue(result is ApplicationListLoadOutcome.Ready)
        assertEquals(listOf(true), requests.map { it.includeSystemApps })
    }
}
