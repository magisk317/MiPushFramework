package io.github.magisk317.mipush.feature.main.subpage

import android.content.Context
import io.github.magisk317.mipush.common.manager.ManagerApplication
import io.github.magisk317.mipush.common.manager.ManagerApplicationGateway
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.manager.R

class ApplicationPageOperation(
    private val applicationGateway: ManagerApplicationGateway,
) {
    fun getMiPushApplications(includeSystemApps: Boolean = false): MiPushApplications {
        val context = Utils.getApplication() ?: return MiPushApplications()
        return getMiPushApplications(context, query = "", filterMode = 0, includeSystemApps = includeSystemApps)
    }

    fun getMiPushApplicationsThatQueryMatched(
        query: String,
        filterMode: Int = 0,
        includeSystemApps: Boolean = false,
    ): MiPushApplications {
        val context = Utils.getApplication() ?: return MiPushApplications()
        return getMiPushApplications(context, query, filterMode, includeSystemApps)
    }

    fun updateRegisteredApplicationDb(context: Context, list: List<ManagerApplication>) {
        list.forEach(applicationGateway::updateApplication)
    }

    fun getNotSupportHint(context: Context, notUseMiPushCount: Int): String =
        context.getString(R.string.footer_app_ignored_not_registered, notUseMiPushCount.toString())

    private fun getMiPushApplications(
        context: Context,
        query: String,
        filterMode: Int,
        includeSystemApps: Boolean = false,
    ): MiPushApplications {
        val snapshot = applicationGateway.loadApplications(context, query, filterMode, includeSystemApps)
        return MiPushApplications().apply {
            registeredPkgs.putAll(snapshot.registeredPkgs)
            res = snapshot.items.toMutableList()
            totalPkg = snapshot.totalPkg
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
