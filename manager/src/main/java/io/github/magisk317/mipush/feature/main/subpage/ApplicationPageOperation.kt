package io.github.magisk317.mipush.feature.main.subpage

import io.github.magisk317.mipush.common.manager.ManagerApplication
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.mipush.manager.application.ApplicationListRequest
import io.github.magisk317.mipush.manager.application.ApplicationListSnapshot
import io.github.magisk317.mipush.manager.application.ApplicationReadResult
import io.github.magisk317.mipush.manager.application.RemoteApplicationListSource
import kotlinx.coroutines.runBlocking

class ApplicationPageOperation(
    private val applicationSource: RemoteApplicationListSource,
) {
    fun getMiPushApplications(includeSystemApps: Boolean = false): MiPushApplications {
        return getMiPushApplications(query = "", filterMode = 0, includeSystemApps = includeSystemApps)
    }

    fun getMiPushApplicationsThatQueryMatched(
        query: String,
        filterMode: Int = 0,
        includeSystemApps: Boolean = false,
    ): MiPushApplications {
        return getMiPushApplications(query, filterMode, includeSystemApps)
    }

    fun getNotSupportHint(context: android.content.Context, notUseMiPushCount: Int): String =
        context.getString(R.string.footer_app_ignored_not_registered, notUseMiPushCount.toString())

    private fun getMiPushApplications(
        query: String,
        filterMode: Int,
        includeSystemApps: Boolean = false,
    ): MiPushApplications {
        val result = runBlocking {
            applicationSource.load(ApplicationListRequest(query, filterMode, includeSystemApps))
        }
        val snapshot = when (result) {
            is ApplicationReadResult.Available -> result.value
            is ApplicationReadResult.Unavailable -> ApplicationListSnapshot(
                applications = io.github.magisk317.mipush.common.manager.ManagerApplications(
                    registeredPkgs = emptyMap(),
                    items = emptyList(),
                    totalPkg = 0,
                ),
                stats = io.github.magisk317.mipush.manager.application.ApplicationListStats(
                    total = 0,
                    usingMiPush = 0,
                    notUsingMiPush = 0,
                    registered = 0,
                    notRegistered = 0,
                ),
            )
        }
        return MiPushApplications().apply {
            registeredPkgs.putAll(snapshot.applications.registeredPkgs)
            res = snapshot.applications.items.toMutableList()
            totalPkg = snapshot.applications.totalPkg
        }
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
