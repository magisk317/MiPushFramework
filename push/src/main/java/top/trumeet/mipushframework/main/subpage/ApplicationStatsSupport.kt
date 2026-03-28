package top.trumeet.mipushframework.main.subpage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.trumeet.mipush.provider.entities.RegisteredApplication

data class ApplicationStats(
    val total: Int = 0,
    val usingMiPush: Int = 0,
    val notUsingMiPush: Int = 0,
    val registered: Int = 0,
    val notRegistered: Int = 0,
)

fun ApplicationPageOperation.MiPushApplications.toApplicationStats(): ApplicationStats {
    val usingMiPush = res.size
    val registered = res.count {
        it.registeredType == RegisteredApplication.RegisteredType.Registered ||
            it.lastReceiveTime.time > 0L
    }
    return ApplicationStats(
        total = totalPkg,
        usingMiPush = usingMiPush,
        notUsingMiPush = (totalPkg - usingMiPush).coerceAtLeast(0),
        registered = registered,
        notRegistered = (usingMiPush - registered).coerceAtLeast(0),
    )
}

suspend fun loadApplicationStats(context: Context): ApplicationStats = withContext(Dispatchers.IO) {
    val applications = ApplicationPageOperation.getMiPushApplicationsThatQueryMatched(query = "", filterMode = 0)
    ApplicationPageOperation.updateRegisteredApplicationDb(context, applications.res)
    applications.toApplicationStats()
}
