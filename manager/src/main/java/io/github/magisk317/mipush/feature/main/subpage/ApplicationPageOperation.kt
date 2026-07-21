package io.github.magisk317.mipush.feature.main.subpage

import io.github.magisk317.mipush.common.manager.ManagerApplication
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.mipush.manager.application.ApplicationListComparison
import io.github.magisk317.mipush.manager.application.ApplicationListRequest
import io.github.magisk317.mipush.manager.application.ApplicationListSnapshot
import io.github.magisk317.mipush.manager.application.ComparingApplicationListSource

class ApplicationPageOperation(
    private val applicationSource: ComparingApplicationListSource,
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

    suspend fun compareRemote(
        query: String,
        filterMode: Int,
        includeSystemApps: Boolean,
        primary: MiPushApplications,
    ): ApplicationListComparison = applicationSource.compareRemote(
        request = ApplicationListRequest(query, filterMode, includeSystemApps),
        primary = primary.toSnapshot(),
    )

    fun getNotSupportHint(context: android.content.Context, notUseMiPushCount: Int): String =
        context.getString(R.string.footer_app_ignored_not_registered, notUseMiPushCount.toString())

    private fun getMiPushApplications(
        query: String,
        filterMode: Int,
        includeSystemApps: Boolean = false,
    ): MiPushApplications {
        val snapshot = applicationSource.loadPrimary(
            ApplicationListRequest(query, filterMode, includeSystemApps),
        ).applications
        return MiPushApplications().apply {
            registeredPkgs.putAll(snapshot.registeredPkgs)
            res = snapshot.items.toMutableList()
            totalPkg = snapshot.totalPkg
        }
    }

    private fun MiPushApplications.toSnapshot(): ApplicationListSnapshot {
        val usingMiPush = res.size
        val registered = res.count {
            it.registeredType == ManagerApplication.RegisteredType.REGISTERED
        }
        return ApplicationListSnapshot(
            applications = io.github.magisk317.mipush.common.manager.ManagerApplications(
                registeredPkgs = registeredPkgs,
                items = res,
                totalPkg = totalPkg,
            ),
            stats = io.github.magisk317.mipush.manager.application.ApplicationListStats(
                total = totalPkg,
                usingMiPush = usingMiPush,
                notUsingMiPush = (totalPkg - usingMiPush).coerceAtLeast(0),
                registered = registered,
                notRegistered = (usingMiPush - registered).coerceAtLeast(0),
            ),
        )
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
