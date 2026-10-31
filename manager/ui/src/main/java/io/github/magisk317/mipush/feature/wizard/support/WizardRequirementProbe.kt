package io.github.magisk317.mipush.feature.wizard.support

import android.content.Context
import io.github.magisk317.mipush.feature.wizard.getPermissionInfos
import io.github.magisk317.mipush.feature.wizard.isPermissionRequirementSatisfied
import io.github.magisk317.mipush.main.viewmodel.evaluatePermissionStatesNow
import io.github.magisk317.mipush.manager.application.ManagerPermissionGateway
import io.github.magisk317.mipush.manager.application.ManagerRootAccessState

/**
 * Launch-time gate for the onboarding report: is any requirement still unmet?
 *
 * The gate reuses the report's own building blocks -- the same permission list, the same state
 * evaluation and [isPermissionRequirementSatisfied] -- so "7 / 7 已就绪" on the page and
 * "skip the report" here can never disagree. Two requirements are either/or groups
 * (usage stats vs accessibility, and the root subjects), and evaluating them item by item
 * used to report an outstanding requirement the page had already counted as satisfied.
 *
 * The root snapshot is queried live (`refresh = true`): the cached runtime state starts as
 * UNAVAILABLE in every cold process, so reading the cache made a fully granted device look
 * unmet and replayed the report on every upgrade. Only a definite
 * [ManagerRootAccessState.NOT_GRANTED] counts as unmet; UNAVAILABLE means the runtime bridge
 * has not answered yet, which is "cannot tell", not "missing".
 */
internal object WizardRequirementProbe {

    suspend fun hasOutstandingRequirement(
        context: Context,
        permissionGateway: ManagerPermissionGateway,
    ): Boolean {
        val snapshot = permissionGateway.getRootAccessSnapshot(refresh = true)
        if (snapshot.runtime.state == ManagerRootAccessState.NOT_GRANTED ||
            snapshot.manager.state == ManagerRootAccessState.NOT_GRANTED
        ) {
            return true
        }
        val permissionInfos = getPermissionInfos(context)
        val permissionStates = evaluatePermissionStatesNow(permissionInfos, permissionGateway)
        for (index in permissionInfos.indices) {
            if (!isPermissionRequirementSatisfied(index, permissionInfos, permissionStates)) {
                return true
            }
        }
        return false
    }
}
