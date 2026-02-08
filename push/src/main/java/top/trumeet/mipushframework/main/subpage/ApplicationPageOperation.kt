@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
package top.trumeet.mipushframework.main.subpage

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.text.TextUtils
import com.elvishew.xlog.XLog
import com.magisk317.Global
import com.magisk317.compat.RegistrationStateCompat
import com.magisk317.compat.RegistrationStateStore
import com.xiaomi.xmsf.R
import java.util.Date
import java.util.Locale
import top.trumeet.common.utils.ElapsedTimer
import top.trumeet.common.utils.Utils
import top.trumeet.mipush.provider.db.EventDb
import top.trumeet.mipush.provider.db.RegisteredApplicationDb
import top.trumeet.mipush.provider.db.RegisteredApplicationDb.registerApplication
import top.trumeet.mipush.provider.entities.RegisteredApplication
import top.trumeet.mipushframework.utils.MiPushManifestChecker

object ApplicationPageOperation {
    private val logger = XLog.tag(ApplicationPageOperation::class.java.simpleName).build()

    @JvmStatic
    fun getMiPushApplications(): MiPushApplications {
        val miPushApplications = MiPushApplications()
        logger.d("[loadApp] start load app list")
        val timer = ElapsedTimer()
        val registeredPkgs = getRegisteredApplicationMap(miPushApplications)
        logger.d("[loadApp] get registeredPkgs ms: %d", timer.restart())

        val packageInfos = getPackagesOnDevice()
        miPushApplications.totalPkg = packageInfos.size
        logger.d("[loadApp] get package info ms: %d", timer.restart())

        removePackagesThatNotSupportMiPushServices(packageInfos, registeredPkgs)
        logger.d("[loadApp] filter not service package ms: %d", timer.restart())

        val res = convertToRegisteredApplicationList(packageInfos, registeredPkgs)
        miPushApplications.res = res
        logger.d("[loadApp] convert to application list ms: %d", timer.restart())

        addApplicationNameIfMissing(res)
        logger.d("[loadApp] query name ms: %d", timer.restart())

        addApplicationPinYinName(res)
        logger.d("[loadApp] query pinyin ms: %d", timer.restart())

        addLastReceiveTimeInfo(res)
        logger.d("[loadApp] query lastReceiveTime ms: %d", timer.restart())
        return miPushApplications
    }

    @JvmStatic
    fun addLastReceiveTimeInfo(res: List<RegisteredApplication>) {
        for (application in res) {
            application.lastReceiveTime = Date(EventDb.getLastReceiveTime(application.packageName))
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
            application.appName = Global.ApplicationNameCache()
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
        val application = registeredPkgs[currentAppPkgName] ?: registerApplication(currentAppPkgName)
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
            (isPackageStoredInDB(registeredPkgs, info) || hasMiPushServices(checker, info))
    }

    @JvmStatic
    fun hasMiPushServices(checker: MiPushManifestChecker?, info: PackageInfo): Boolean {
        if (checker != null && checker.checkServices(info)) return true
        return hasKnownMiPushComponents(info)
    }

    private fun hasKnownMiPushComponents(info: PackageInfo): Boolean {
        val serviceNames = info.services
            ?.mapNotNull(ServiceInfo::name)
            ?.toSet()
            ?: emptySet()
        if (serviceNames.contains("com.xiaomi.mipush.sdk.PushMessageHandler")) return true
        if (serviceNames.contains("com.xiaomi.mipush.sdk.MessageHandleService")) return true
        if (serviceNames.contains("com.xiaomi.push.service.XMJobService")) return true
        if (serviceNames.contains("com.xiaomi.push.service.XMPushService")) return true

        val receiverNames = info.receivers
            ?.mapNotNull { it.name }
            ?.toSet()
            ?: emptySet()
        if (receiverNames.contains("com.xiaomi.push.service.receivers.PingReceiver")) return true
        if (receiverNames.contains("com.xiaomi.mipush.sdk.PushMessageReceiver")) return true

        return false
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
    fun getPackagesOnDevice(): MutableList<PackageInfo> {
        val app = Utils.getApplication() ?: return mutableListOf()
        return app.packageManager.getInstalledPackages(
            PackageManager.GET_DISABLED_COMPONENTS or
                PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS
        )
    }

    @JvmStatic
    fun getMiPushManifestChecker(): MiPushManifestChecker? {
        return try {
            MiPushManifestChecker.create(Utils.getApplication() ?: return null)
        } catch (e: PackageManager.NameNotFoundException) {
            logger.e("Create mi push checker", e)
            null
        } catch (e: ClassNotFoundException) {
            logger.e("Create mi push checker", e)
            null
        } catch (e: NoSuchMethodException) {
            logger.e("Create mi push checker", e)
            null
        }
    }

    @JvmStatic
    fun getRegisteredApplicationMap(miPushApplications: MiPushApplications): MutableMap<String, RegisteredApplication> {
        val registeredPkgs = miPushApplications.registeredPkgs
        for (application in RegisteredApplicationDb.getList(null)) {
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
    fun sortApplicationsForDisplay(miPushApplications: MiPushApplications) {
        miPushApplications.res.sortWith { o1, o2 ->
            if ((o1.id == null && o2.id == null) ||
                (o1.registeredType == RegisteredApplication.RegisteredType.NotRegistered &&
                    o2.registeredType == RegisteredApplication.RegisteredType.NotRegistered)
            ) {
                return@sortWith o1.appNamePinYin.compareTo(o2.appNamePinYin)
            }
            if (o1.id == null) {
                return@sortWith 1
            }
            if (o2.id == null) {
                return@sortWith -1
            }
            if (o1.registeredType == RegisteredApplication.RegisteredType.NotRegistered) {
                return@sortWith 1
            }
            if (o2.registeredType == RegisteredApplication.RegisteredType.NotRegistered) {
                return@sortWith -1
            }
            if (o1.registeredType != o2.registeredType) {
                return@sortWith o1.registeredType - o2.registeredType
            }
            val cmp = o2.lastReceiveTime.compareTo(o1.lastReceiveTime)
            if (cmp != 0) {
                return@sortWith cmp
            }
            o1.appNamePinYin.compareTo(o2.appNamePinYin)
        }
    }

    private fun isQueryMatched(info: RegisteredApplication, query: String): Boolean {
        return info.packageName.lowercase().contains(query) ||
            info.appName.lowercase().contains(query) ||
            info.appNamePinYin.contains(query)
    }

    @JvmStatic
    fun getMiPushApplicationsThatQueryMatched(query: String): MiPushApplications {
        val totalTimer = ElapsedTimer()
        val miPushApplications = getMiPushApplications()

        val timer = ElapsedTimer()
        removeApplicationsThatQueryNotMatched(miPushApplications, query)
        logger.d("[loadApp] filter app search ms: %d", timer.restart())

        sortApplicationsForDisplay(miPushApplications)
        logger.d("[loadApp] sort application list will show ms: %d", timer.restart())
        logger.d("[loadApp] end load app list ms: %d", totalTimer.elapsed())
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
        logger.d(
            "[updateApp] local registration probe ms: %d, queried=%d, matched=%d",
            timer.restart(),
            notRegisteredPkgs.size,
            localRegisteredPkgs.size
        )

        for (application in list) {
            val pkg = application.packageName
            application.appName = Global.ApplicationNameCache().getAppName(context, pkg).toString()
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
        logger.d("[updateApp] update app ms: %d", timer.restart())
        logger.d("[updateApp] updated ms: %d", totalTimer.elapsed())
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
