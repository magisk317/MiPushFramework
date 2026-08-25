package io.github.magisk317.mipush.feature.main.subpage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.magisk317.mipush.manager.application.RemoteApplicationListSource
import io.github.magisk317.mipush.feature.main.RegistrationStateStyle

import androidx.compose.runtime.Immutable

@Immutable
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
    applicationSource: RemoteApplicationListSource,
): ApplicationStats = withContext(Dispatchers.IO) {
    val operation = ApplicationPageOperation(applicationSource)
    when (val result = operation.getMiPushApplicationsThatQueryMatched(query = "", filterMode = 0)) {
        is ApplicationListLoadOutcome.Ready -> result.applications.toApplicationStats()
        is ApplicationListLoadOutcome.Unavailable -> ApplicationStats()
    }
}
