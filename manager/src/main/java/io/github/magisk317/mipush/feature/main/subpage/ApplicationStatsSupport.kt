package io.github.magisk317.mipush.feature.main.subpage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.magisk317.mipush.manager.application.ComparingApplicationListSource
import io.github.magisk317.mipush.feature.main.RegistrationStateStyle

data class ApplicationStats(
    val total: Int = 0,
    val usingMiPush: Int = 0,
    val notUsingMiPush: Int = 0,
    val registered: Int = 0,
    val notRegistered: Int = 0,
)

fun ApplicationPageOperation.MiPushApplications.toApplicationStats(): ApplicationStats {
    val usingMiPush = res.size
    val registered = res.count { RegistrationStateStyle.isConfirmedRegistered(it) }
    return ApplicationStats(
        total = totalPkg,
        usingMiPush = usingMiPush,
        notUsingMiPush = (totalPkg - usingMiPush).coerceAtLeast(0),
        registered = registered,
        notRegistered = (usingMiPush - registered).coerceAtLeast(0),
    )
}

suspend fun loadApplicationStats(
    applicationSource: ComparingApplicationListSource,
): ApplicationStats = withContext(Dispatchers.IO) {
    val operation = ApplicationPageOperation(applicationSource)
    val applications = operation.getMiPushApplicationsThatQueryMatched(query = "", filterMode = 0)
    applications.toApplicationStats()
}
