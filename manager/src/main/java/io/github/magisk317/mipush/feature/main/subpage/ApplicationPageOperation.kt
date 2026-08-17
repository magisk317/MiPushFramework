package io.github.magisk317.mipush.feature.main.subpage

import io.github.magisk317.mipush.common.manager.ManagerApplication
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.mipush.manager.application.ApplicationListRequest
import io.github.magisk317.mipush.manager.application.ApplicationListSnapshot
import io.github.magisk317.mipush.manager.application.ApplicationListStats
import io.github.magisk317.mipush.manager.application.ApplicationReadResult
import io.github.magisk317.mipush.manager.application.ApplicationReadStatus
import io.github.magisk317.mipush.manager.application.RemoteApplicationListSource
import io.github.magisk317.mipush.manager.client.RemoteCallBudget
import io.github.magisk317.mipush.manager.remote.PageRemoteCallPolicy

class ApplicationPageOperation(
    private val applicationSource: RemoteApplicationListSource,
) {
    suspend fun getMiPushApplications(
        includeSystemApps: Boolean = false,
        budget: RemoteCallBudget = PageRemoteCallPolicy.visiblePage,
    ): ApplicationListLoadOutcome {
        return getMiPushApplications(query = "", filterMode = 0, includeSystemApps = includeSystemApps, budget = budget)
    }

    suspend fun getMiPushApplicationsThatQueryMatched(
        query: String,
        filterMode: Int = 0,
        includeSystemApps: Boolean = false,
        budget: RemoteCallBudget = PageRemoteCallPolicy.visiblePage,
    ): ApplicationListLoadOutcome {
        return getMiPushApplications(query, filterMode, includeSystemApps, budget)
    }

    fun getNotSupportHint(context: android.content.Context, notUseMiPushCount: Int): String =
        context.getString(R.string.footer_app_ignored_not_registered, notUseMiPushCount.toString())

    private suspend fun getMiPushApplications(
        query: String,
        filterMode: Int,
        includeSystemApps: Boolean,
        budget: RemoteCallBudget,
    ): ApplicationListLoadOutcome {
        val result = applicationSource.load(ApplicationListRequest(query, filterMode, includeSystemApps), budget)
        return when (result) {
            is ApplicationReadResult.Available -> {
                val snapshot = result.value
                ApplicationListLoadOutcome.Ready(
                    applications = snapshot.toMiPushApplications(),
                    stats = snapshot.stats,
                )
            }
            is ApplicationReadResult.Unavailable ->
                ApplicationListLoadOutcome.Unavailable(result.status)
        }
    }

    private fun ApplicationListSnapshot.toMiPushApplications(): MiPushApplications =
        MiPushApplications().apply {
            registeredPkgs.putAll(applications.registeredPkgs)
            res = applications.items.toMutableList()
            totalPkg = applications.totalPkg
        }

    class MiPushApplications {
        @JvmField
        var registeredPkgs: MutableMap<String, ManagerApplication> = mutableMapOf()

        @JvmField
        var res: MutableList<ManagerApplication> = mutableListOf()

        @JvmField
        var totalPkg: Int = 0
    }
}

sealed interface ApplicationListLoadOutcome {
    data class Ready(
        val applications: ApplicationPageOperation.MiPushApplications,
        val stats: ApplicationListStats,
    ) : ApplicationListLoadOutcome

    data class Unavailable(
        val status: ApplicationReadStatus,
    ) : ApplicationListLoadOutcome
}
