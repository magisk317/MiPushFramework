package io.github.magisk317.mipush.manager.runtime.read

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import io.github.aakira.napier.Napier
import io.github.magisk317.mipush.common.compat.PackageManagerCompatBridge
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.compat.RegistrationStateCompat
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.platform.support.MiPushManifestChecker
import io.github.magisk317.mipush.runtime.store.DatabaseUtils
import io.github.magisk317.mipush.runtime.store.db.EventDb
import io.github.magisk317.mipush.runtime.store.entities.Event
import io.github.magisk317.mipush.utils.RegistrationHelper
import kotlinx.coroutines.runBlocking

/** Android-backed source whose methods are reads only. */
class AndroidManagerApplicationReadSource(context: Context) : ManagerApplicationReadSource {
    private val appContext = context.applicationContext ?: context
    private val packageManager = appContext.packageManager
    private val database by lazy { DatabaseUtils.getDatabase(appContext) }
    private val registeredApplicationDao by lazy { database.registeredApplicationDao() }
    private val eventDao by lazy { database.eventDao() }

    override fun readStoredApplications(): List<StoredApplicationSnapshot> = runBlocking {
        registeredApplicationDao.getAll().map { it.toStoredApplicationSnapshot() }
    }

    override fun readInstalledApplications(includeSystemApps: Boolean): ApplicationCatalogSnapshot {
        val candidates = loadPackagesOnDevice()
            .filter { isListCandidate(it, includeSystemApps) }
        val checker = createManifestChecker()
        return ApplicationCatalogSnapshot(
            totalCandidatePackages = candidates.size,
            applications = candidates
                .asSequence()
                .filter { hasMiPushServices(checker, it) }
                .map { it.toInstalledSnapshot(hasMiPushServices = true) }
                .toList(),
        )
    }

    override fun readInstalledApplication(packageName: String): InstalledApplicationSnapshot? {
        val packageInfo = runCatching {
            PackageManagerCompatBridge.getPackageInfo(packageManager, packageName, PACKAGE_INFO_FLAGS)
        }.getOrNull() ?: return null
        if (!isListCandidate(packageInfo, includeSystemApps = true)) return null
        return packageInfo.toInstalledSnapshot(
            hasMiPushServices = hasMiPushServices(createManifestChecker(), packageInfo),
        )
    }

    override fun readLastReceiveTime(packageName: String): Long =
        Utils.getLastReceiveTime(packageName) ?: 0L

    override fun readLastReceiveTimes(packageNames: Collection<String>): Map<String, Long> {
        val databaseTimes = runBlocking {
            eventDao.getAllLastReceiveTimes().associate { it.pkg to it.date }
        }
        return packageNames.associateWith { packageName ->
            maxOf(
                databaseTimes[packageName] ?: 0L,
                Utils.getLastReceiveTime(packageName) ?: 0L,
            )
        }
    }

    override fun readLocallyRegisteredPackages(packageNames: Collection<String>): Set<String> =
        RegistrationStateCompat.findPackagesWithValidLocalRegistration(packageNames)

    override fun hasLocalRegistration(packageName: String): Boolean =
        RegistrationStateCompat.hasValidLocalRegistration(packageName)

    override fun readRegSecCount(packageName: String): Int = Utils.getRegSecs(packageName).size

    override fun readLatestRegistrationEvent(packageName: String): RegistrationEventSnapshot? = runBlocking {
        EventDb.queryAsync(
            skip = 0,
            limit = 1,
            types = REGISTRATION_EVENT_TYPES,
            pkg = packageName,
            text = null,
        ).firstOrNull()
            ?.let { RegistrationEventSnapshot(type = it.type, result = it.result) }
    }

    private fun loadPackagesOnDevice(): List<PackageInfo> = try {
        PackageManagerCompatBridge.getInstalledPackages(packageManager, 0).map { info ->
            runCatching {
                PackageManagerCompatBridge.getPackageInfo(packageManager, info.packageName, PACKAGE_INFO_FLAGS)
            }.getOrElse { info }
        }
    } catch (error: RuntimeException) {
        Napier.e("Failed to load installed packages for manager runtime", error, tag = TAG)
        emptyList()
    }

    private fun isListCandidate(info: PackageInfo, includeSystemApps: Boolean): Boolean {
        val applicationInfo = info.applicationInfo ?: return false
        if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_INSTALLED) == 0) {
            return false
        }
        return includeSystemApps || Utils.isUserApplication(applicationInfo)
    }

    private fun hasMiPushServices(checker: MiPushManifestChecker?, info: PackageInfo): Boolean {
        val serviceNames = info.services?.mapNotNull(ServiceInfo::name)?.toSet().orEmpty()
        val receiverNames = info.receivers?.mapNotNull { it.name }?.toSet().orEmpty()
        val plan = RegistrationHelper.classifyForceRegisterPlan(
            packageName = info.packageName,
            serviceNames = serviceNames,
            receiverNames = receiverNames,
        )
        if (plan.serviceCandidates.isEmpty() && plan.receiverCandidates.isEmpty() && plan.bridgeCandidates.isEmpty()) {
            return false
        }
        if (plan.serviceCandidates.isNotEmpty()) {
            checker?.checkServices(info)
        }
        return true
    }

    private fun createManifestChecker(): MiPushManifestChecker? =
        runCatching { MiPushManifestChecker.create(appContext) }.getOrNull()

    private fun PackageInfo.toInstalledSnapshot(hasMiPushServices: Boolean): InstalledApplicationSnapshot {
        val label = Global.applicationNameCache().getAppName(appContext, packageName).toString()
        return InstalledApplicationSnapshot(
            packageName = packageName,
            appName = label,
            hasMiPushServices = hasMiPushServices,
        )
    }

    companion object {
        private const val TAG = "ManagerApplicationReadSource"
        private const val PACKAGE_INFO_FLAGS =
            PackageManager.MATCH_DISABLED_COMPONENTS or
                PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS
        private val REGISTRATION_EVENT_TYPES = setOf(
            Event.Type.Registration,
            Event.Type.RegistrationResult,
            Event.Type.UnRegistration,
        )
    }
}
