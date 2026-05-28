package io.github.magisk317.mipush.feature.main.subpage

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.text.TextUtils
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import io.github.magisk317.mipush.common.compat.PackageManagerCompatBridge
import io.github.magisk317.mipush.utils.RegistrationHelper
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.compat.RegistrationStateCompat
import io.github.magisk317.mipush.compat.RegistrationStateStore
import com.xiaomi.xmsf.R
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.runBlocking
import io.github.magisk317.mipush.common.utils.ElapsedTimer
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.runtime.store.db.EventDb
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import io.github.magisk317.mipush.runtime.store.entities.RegisteredApplication
import io.github.magisk317.mipush.feature.main.RegistrationStateStyle
import io.github.magisk317.mipush.platform.support.MiPushManifestChecker

object ApplicationPageOperation {
    private val TAG = ApplicationPageOperation::class.java.simpleName

    @JvmStatic
    fun getMiPushApplications(): MiPushApplications {
        val miPushApplications = MiPushApplications()
        logD("[loadApp] start load app list")
        val timer = ElapsedTimer()
        
        val registeredPkgs = getRegisteredApplicationMap(miPushApplications)
        logD("[loadApp] get registeredPkgs ms: ${timer.restart()}")

        val packageInfos = getPackagesOnDevice().filter(::isUserApplication).toMutableList()
        miPushApplications.totalPkg = packageInfos.size
        logD("[loadApp] get package info ms: ${timer.restart()}")

        // Batch fetch all last receive times to avoid N+1 queries
        val lastReceiveTimes = runBlocking { EventDb.getAllLastReceiveTimesAsync() }
        logD("[loadApp] batch fetch lastReceiveTimes ms: ${timer.restart()}")

        removePackagesThatNotSupportMiPushServices(packageInfos, registeredPkgs)
        logD("[loadApp] filter not service package ms: ${timer.restart()}")

        val res = convertToRegisteredApplicationList(packageInfos, registeredPkgs)
        miPushApplications.res = res
        logD("[loadApp] convert to application list ms: ${timer.restart()}")

        addApplicationNameIfMissing(res)
        logD("[loadApp] query name ms: ${timer.restart()}")

        addApplicationPinYinName(res)
        logD("[loadApp] query pinyin ms: ${timer.restart()}")

        addLastReceiveTimeInfo(res, lastReceiveTimes)
        logD("[loadApp] query lastReceiveTime ms: ${timer.restart()}")
        return miPushApplications
    }

    @JvmStatic
    fun addLastReceiveTimeInfo(res: List<RegisteredApplication>, timesMap: Map<String, Long>) {
        for (application in res) {
            val timeFromDb = timesMap[application.packageName] ?: 0L
            // Also check the runtime cache in Utils (which might be fresher for some entries)
            val timeFromCache = Utils.getLastReceiveTime(application.packageName)
            application.lastReceiveTime = Date(maxOf(timeFromDb, timeFromCache ?: 0L))
        }
    }

    @JvmStatic
    fun addApplicationPinYinName(res: List<RegisteredApplication>) {
        for (application in res) {
            application.appNamePinYin = application.appName.lowercase(Locale.ROOT)
        }
    }

    @JvmStatic
    fun addApplicationNameIfMissing(res: List<RegisteredApplication>) {
        for (application in res) {
            if (!TextUtils.isEmpty(application.appName)) {
                continue
            }
            val context = Utils.getApplication() ?: continue
            application.appName = Global.applicationNameCache()
                .getAppName(context, application.packageName).toString()
        }
    }

    @JvmStatic
    fun convertToRegisteredApplicationList(
        packageInfos: List<PackageInfo>,
        registeredPkgs: Map<String, RegisteredApplication>
    ): MutableList<RegisteredApplication> {
        val checker = getMiPushManifestChecker()
        val res = mutableListOf<RegisteredApplication>()
        for (info in packageInfos) {
            val application = getRegisteredApplication(info, registeredPkgs, checker)
            res.add(application)
        }
        return res
    }

    @JvmStatic
    fun getRegisteredApplication(
        info: PackageInfo,
        registeredPkgs: Map<String, RegisteredApplication>,
        checker: MiPushManifestChecker?
    ): RegisteredApplication {
        val currentAppPkgName = info.packageName
        val application = registeredPkgs[currentAppPkgName] ?: RegisteredApplicationDb.registerApplication(currentAppPkgName)
        application.existServices = hasMiPushServices(checker, info)
        return application
    }

    @JvmStatic
    fun removePackagesThatNotSupportMiPushServices(
        packageInfos: MutableList<PackageInfo>,
        registeredPkgs: Map<String, RegisteredApplication>
    ) {
        val checker = getMiPushManifestChecker()
        val iterator = packageInfos.iterator()
        while (iterator.hasNext()) {
            val info = iterator.next()
            if (!shouldShowInList(info, registeredPkgs, checker)) {
                iterator.remove()
            }
        }
    }

    @JvmStatic
    fun shouldShowInList(
        info: PackageInfo,
        registeredPkgs: Map<String, RegisteredApplication>,
        checker: MiPushManifestChecker?
    ): Boolean {
        return isApplicationInstalled(info) &&
            isUserApplication(info) &&
            hasMiPushServices(checker, info)
    }

    @JvmStatic
    fun hasMiPushServices(checker: MiPushManifestChecker?, info: PackageInfo): Boolean {
        val serviceNames = info.services
            ?.mapNotNull(ServiceInfo::name)
            ?.toSet()
            ?: emptySet()
        val receiverNames = info.receivers
            ?.mapNotNull { it.name }
            ?.toSet()
            ?: emptySet()
        val plan = RegistrationHelper.classifyForceRegisterPlan(
            packageName = info.packageName,
            serviceNames = serviceNames,
            receiverNames = receiverNames
        )
        if (
            plan.serviceCandidates.isEmpty() &&
            plan.receiverCandidates.isEmpty() &&
            plan.bridgeCandidates.isEmpty()
        ) {
            return false
        }
        if (plan.serviceCandidates.isNotEmpty()) {
            checker?.checkServices(info)
        }
        return true
    }

    @JvmStatic
    fun isPackageStoredInDB(registeredPkgs: Map<String, RegisteredApplication>, info: PackageInfo): Boolean {
        val appInfo = info.applicationInfo ?: return false
        return registeredPkgs.containsKey(appInfo.packageName)
    }

    @JvmStatic
    fun isApplicationInstalled(info: PackageInfo): Boolean {
        val appInfo = info.applicationInfo ?: return false
        return (appInfo.flags and ApplicationInfo.FLAG_INSTALLED) != 0
    }

    @JvmStatic
    fun isUserApplication(info: PackageInfo): Boolean {
        val appInfo = info.applicationInfo ?: return false
        return Utils.isUserApplication(appInfo)
    }

    @JvmStatic
    fun getPackagesOnDevice(): MutableList<PackageInfo> {
        val app = Utils.getApplication() ?: return mutableListOf()
        val packageManager = app.packageManager
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS or
            PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS
        return loadPackagesOnDeviceIndividually(packageManager, flags)
    }

    private fun loadPackagesOnDeviceIndividually(
        packageManager: PackageManager,
        flags: Int,
    ): MutableList<PackageInfo> {
        val lightweightPackages = try {
            PackageManagerCompatBridge.getInstalledPackages(packageManager, 0)
        } catch (error: RuntimeException) {
            logE("Failed to load lightweight installed packages", error)
            return mutableListOf()
        }
        val packages = mutableListOf<PackageInfo>()
        lightweightPackages.forEach { info ->
            val packageName = info.packageName
            val detailedInfo = try {
                PackageManagerCompatBridge.getPackageInfo(packageManager, packageName, flags)
            } catch (_: PackageManager.NameNotFoundException) {
                null
            } catch (error: RuntimeException) {
                logE("Failed to load package details for $packageName", error)
                null
            }
            packages += detailedInfo ?: info
        }
        return packages
    }

    @JvmStatic
    fun getMiPushManifestChecker(): MiPushManifestChecker? {
        return try {
            MiPushManifestChecker.create(Utils.getApplication() ?: return null)
        } catch (e: PackageManager.NameNotFoundException) {
            logE("Create mi push checker", e)
            null
        } catch (e: ClassNotFoundException) {
            logE("Create mi push checker", e)
            null
        } catch (e: NoSuchMethodException) {
            logE("Create mi push checker", e)
            null
        }
    }

    @JvmStatic
    fun getRegisteredApplicationMap(miPushApplications: MiPushApplications): MutableMap<String, RegisteredApplication> {
        val registeredPkgs = miPushApplications.registeredPkgs
        for (application in RegisteredApplicationDb.getList(null)) {
            if (!Utils.isUserApplication(application.packageName)) {
                continue
            }
            registeredPkgs[application.packageName] = application
        }
        return registeredPkgs
    }

    @JvmStatic
    fun removeApplicationsThatQueryNotMatched(miPushApplications: MiPushApplications, query: String) {
        val iterator = miPushApplications.res.iterator()
        while (iterator.hasNext()) {
            val info = iterator.next()
            if (!isQueryMatched(info, query)) {
                iterator.remove()
            }
        }
    }

    @JvmStatic
    fun filterApplicationsByMode(miPushApplications: MiPushApplications, filterMode: Int) {
        if (filterMode == 0) return // All
        val iterator = miPushApplications.res.iterator()
        while (iterator.hasNext()) {
            val info = iterator.next()
            val matched = when (filterMode) {
                1 -> RegistrationStateStyle.isConfirmedRegistered(info)
                2 -> info.registeredType == RegisteredApplication.RegisteredType.NotRegistered && info.lastReceiveTime.time == 0L
                3 -> info.registeredType == RegisteredApplication.RegisteredType.Unregistered && info.lastReceiveTime.time == 0L
                else -> true
            }
            if (!matched) {
                iterator.remove()
            }
        }
    }

    @JvmStatic
    fun sortApplicationsForDisplay(miPushApplications: MiPushApplications) {
        miPushApplications.res.sortWith { o1, o2 ->
            val p1 = when {
                RegistrationStateStyle.isConfirmedRegistered(o1) -> 0
                RegistrationStateStyle.hasObservedActivity(o1) -> 1
                o1.registeredType == RegisteredApplication.RegisteredType.Unregistered -> 2
                else -> 3
            }
            val p2 = when {
                RegistrationStateStyle.isConfirmedRegistered(o2) -> 0
                RegistrationStateStyle.hasObservedActivity(o2) -> 1
                o2.registeredType == RegisteredApplication.RegisteredType.Unregistered -> 2
                else -> 3
            }

            if (p1 != p2) return@sortWith p1 - p2

            // Same priority, sort by push time desc
            val cmp = o2.lastReceiveTime.compareTo(o1.lastReceiveTime)
            if (cmp != 0) return@sortWith cmp

            // Same push time (usually 0), sort by name asc
            o1.appNamePinYin.compareTo(o2.appNamePinYin)
        }
    }

    private fun isQueryMatched(info: RegisteredApplication, query: String): Boolean {
        val q = query.lowercase()
        return info.packageName.lowercase().contains(q) ||
            info.appName.lowercase().contains(q) ||
            info.appNamePinYin.lowercase().contains(q)
    }

    @JvmStatic
    fun getMiPushApplicationsThatQueryMatched(query: String, filterMode: Int = 0): MiPushApplications {
        val totalTimer = ElapsedTimer()
        val miPushApplications = getMiPushApplications()

        val timer = ElapsedTimer()
        removeApplicationsThatQueryNotMatched(miPushApplications, query)
        logD("[loadApp] filter app search ms: ${timer.restart()}")

        filterApplicationsByMode(miPushApplications, filterMode)
        logD("[loadApp] filter app mode ms: ${timer.restart()}")

        sortApplicationsForDisplay(miPushApplications)
        logD("[loadApp] sort application list will show ms: ${timer.restart()}")
        logD("[loadApp] end load app list ms: ${totalTimer.elapsed()}")
        return miPushApplications
    }

    @JvmStatic
    fun updateRegisteredApplicationDb(context: Context, list: List<RegisteredApplication>) {
        val totalTimer = ElapsedTimer()
        val timer = ElapsedTimer()
        val notRegisteredPkgs = list
            .asSequence()
            .filter { it.registeredType == RegisteredApplication.RegisteredType.NotRegistered }
            .map { it.packageName }
            .toSet()
        
        val localRegisteredPkgs = RegistrationStateCompat.findPackagesWithValidLocalRegistration(notRegisteredPkgs)
        logD(
            "[updateApp] local registration probe ms: ${timer.restart()}, queried=${notRegisteredPkgs.size}, matched=${localRegisteredPkgs.size}"
        )

        for (application in list) {
            val pkg = application.packageName
            if (!Utils.isUserApplication(context, pkg)) {
                continue
            }
            application.appName = Global.applicationNameCache().getAppName(context, pkg).toString()
            if (
                application.registeredType == RegisteredApplication.RegisteredType.NotRegistered &&
                localRegisteredPkgs.contains(pkg)
            ) {
                RegistrationStateStore.updateIfChanged(
                    application = application,
                    nextType = RegisteredApplication.RegisteredType.Registered,
                    source = RegistrationStateStore.Source.LOCAL_PROBE
                )
            } else {
                RegisteredApplicationDb.update(application)
            }
        }
        logD("[updateApp] update app ms: ${timer.restart()}")
        logD("[updateApp] updated ms: ${totalTimer.elapsed()}")
    }

    @JvmStatic
    fun getNotSupportHint(context: Context, notUseMiPushCount: Int): String =
        context.getString(R.string.footer_app_ignored_not_registered, notUseMiPushCount.toString())

    class MiPushApplications {
        @JvmField
        var registeredPkgs: MutableMap<String, RegisteredApplication> = mutableMapOf()

        @JvmField
        var res: MutableList<RegisteredApplication> = mutableListOf()

        @JvmField
        var totalPkg: Int = 0
    }
}
