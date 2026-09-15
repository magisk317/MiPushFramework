package io.github.magisk317.mipush.hook.fakedevice

import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.xposed.LoadParam
import io.github.magisk317.xposed.findClass
import io.github.magisk317.xposed.hookMethod
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

class Alipay : Common() {
    companion object {
        private const val TAG = "Alipay"
        private const val BEAN_CONSTANTS_CLASS =
            "com.alipay.mobile.common.netsdkextdependapi.BeanServiceConstants"
        private const val BEAN_INFO_MANAGER_FACTORY_CLASS =
            "com.alipay.mobile.common.netsdkextdependapi.beaninfo.BeanInfoManagerFactory"
        private const val BEAN_INFO_MANAGER_CLASS =
            "com.alipay.mobile.common.netsdkextdependapi.beaninfo.BeanInfoManager"

        /**
         * This is the mapping assembled by the newer DefaultBeanInfoManager. The idlefish
         * APK contains the manager classes but its older BeanServiceConstants class does not
         * contain all of the newer constant fields used by that manager's <clinit>.
         */
        private val FALLBACK_BEAN_CLASS_NAMES = mapOf(
            "LoggerManager" to "com.alipay.mobile.common.netsdkextdepend.logger.DefaultLoggerManager",
            "DeviceInfoManager" to "com.alipay.mobile.common.netsdkextdepend.deviceinfo.DefaultDeviceInfoManager",
            "AppInfoManager" to "com.alipay.mobile.common.netsdkextdepend.appinfo.DefaultAppInfoManager",
            "UserInfoManager" to "com.alipay.mobile.common.netsdkextdepend.userinfo.DefaultUserInfoManager",
            "MonitorManager" to "com.alipay.mobile.common.netsdkextdepend.monitorinfo.DefaultMonitorInfoManager",
            "SecurityManager" to "com.alipay.mobile.common.netsdkextdepend.security.DefaultSecurityManager",
            "LbsInfoManager" to "com.alipay.mobile.common.netsdkextdepend.lbs.DefaultLbsInfoManager",
            "StorageManager" to "com.alipay.mobile.common.netsdkextdepend.storager.DefaultStorageManager",
            "NwConfigServiceManager" to "com.alipay.mobile.common.netsdkextdepend.configservice.DefaultNwConfigServiceManager",
            "NwThreadManager" to "com.alipay.mobile.common.netsdkextdepend.thread.DefaultNwThreadManager",
            "ProcessInfoManager" to "com.alipay.mobile.common.netsdkextdepend.processinfo.DefaultProcessInfoManager",
            "FltracerManager" to "com.alipay.mobile.common.netsdkextdepend.fltracer.DefaultFltracerManager",
            "NwCacheManager" to "com.alipay.mobile.common.netsdkextdepend.nwcache.DefaultNwCacheManager",
            "SystemInfoManager" to "com.alipay.mobile.common.netsdkextdepend.system.DefaultSystemInfoManager",
            "SyncRpcManager" to "com.alipay.mobile.common.netsdkextdepend.rpc.DefaultSyncRpcManager",
            "RpcConfigManager" to "com.alipay.mobile.common.netsdkextdepend.rpc.DefaultRpcConfigManager",
            "APUIManager" to "com.alipay.mobile.common.netsdkextdepend.p162ui.DefaultAPUIManager",
        )
    }

    override fun fake(lpparam: LoadParam): Boolean {
        XLog.i(TAG, "fake() ENTER packageName=${lpparam.packageName} processName=${lpparam.processName}")
        super.fake(lpparam)
        XLog.i(TAG, "fake() calling fixBeanServiceConstants for ${lpparam.packageName}")
        fixBeanServiceConstants(lpparam)
        XLog.i(TAG, "fake() EXIT packageName=${lpparam.packageName}")
        return true
    }

    /**
     * The old idlefish SDK has a BeanServiceConstants/DefaultBeanInfoManager schema mismatch.
     * Do not add fields reflectively and do not catch <clinit>: once a static initializer fails,
     * ART permanently marks that class as erroneous for the process. Replace the factory method
     * before it can initialize DefaultBeanInfoManager and return an interface proxy backed by the
     * complete mapping instead.
     */
    private fun fixBeanServiceConstants(lpparam: LoadParam) {
        XLog.i(TAG, "fixBeanServiceConstants ENTER for ${lpparam.packageName}")
        runCatching {
            val beanConstantsClass = lpparam.classLoader.findClass(BEAN_CONSTANTS_CLASS)
            val requiredFields = FALLBACK_BEAN_CLASS_NAMES.keys
                .filter { key -> beanConstantsClass.declaredFields.none { it.name == keyToFieldName(key) } }
            if (requiredFields.isEmpty()) {
                XLog.i(TAG, "BeanServiceConstants is compatible for ${lpparam.packageName}")
            } else {
                XLog.i(
                    TAG,
                    "BeanServiceConstants schema mismatch for ${lpparam.packageName}, " +
                        "missing=${requiredFields.joinToString()}",
                )
                hookBeanInfoManagerFactory(lpparam)
            }
        }.onFailure {
            XLog.e(TAG, "Failed to install Alipay compatibility hook for ${lpparam.packageName}: ${it.message}", it)
        }
        XLog.i(TAG, "fixBeanServiceConstants EXIT for ${lpparam.packageName}")
    }

    /**
     * Hook the protected factory method that would otherwise execute
     * DefaultBeanInfoManager.$r8$clinit. The proxy implements the target app's interface class,
     * so the returned object is assignable to the app's BeanInfoManager type.
     */
    private fun hookBeanInfoManagerFactory(lpparam: LoadParam) {
        val factoryClass = lpparam.classLoader.findClass(BEAN_INFO_MANAGER_FACTORY_CLASS)
        val managerClass = lpparam.classLoader.findClass(BEAN_INFO_MANAGER_CLASS)
        val classLoader = managerClass.classLoader ?: lpparam.classLoader

        factoryClass.hookMethod("newDefaultBean") {
            replace {
                val fallback = Proxy.newProxyInstance(
                    classLoader,
                    arrayOf(managerClass),
                    InvocationHandler { _, method, args ->
                        when (method.name) {
                            "getBeanClassName" -> {
                                val key = args?.firstOrNull() as? String
                                FALLBACK_BEAN_CLASS_NAMES[key]
                            }
                            "toString" -> "MiPushAlipayBeanInfoManagerFallback"
                            "hashCode" -> System.identityHashCode(this)
                            "equals" -> args?.firstOrNull() === this
                            else -> null
                        }
                    },
                )
                XLog.i(TAG, "Returning BeanInfoManager fallback; DefaultBeanInfoManager initialization bypassed")
                fallback
            }
        }
        XLog.i(TAG, "BeanInfoManagerFactory.newDefaultBean hook installed")
    }

    /**
     * Convert the service key used by the fallback map into the corresponding constant field
     * name for compatibility detection. The five newer fields are deliberately included here;
     * the old APK is missing all of them, not only nwCacheManagerServiceName.
     */
    private fun keyToFieldName(key: String): String = when (key) {
        "LoggerManager" -> "loggerInfoManagerServiceName"
        "DeviceInfoManager" -> "deviceInfoManagerServiceName"
        "AppInfoManager" -> "appInfoManagerServiceName"
        "UserInfoManager" -> "userInfoManagerServiceName"
        "MonitorManager" -> "monitorInfoManagerServiceName"
        "SecurityManager" -> "securityManagerServiceName"
        "LbsInfoManager" -> "lbsInfoManagerServiceName"
        "StorageManager" -> "storageManagerServiceName"
        "NwConfigServiceManager" -> "nwConfigServiceName"
        "NwThreadManager" -> "nwThreadManagerServiceName"
        "ProcessInfoManager" -> "processInfoManagerServiceName"
        "FltracerManager" -> "fltracerManagerServiceName"
        "NwCacheManager" -> "nwCacheManagerServiceName"
        "SystemInfoManager" -> "systemInfoManagerServiceName"
        "SyncRpcManager" -> "syncRpcManagerServiceName"
        "RpcConfigManager" -> "rpcConfigManagerServiceName"
        "APUIManager" -> "apUIManagerServiceName"
        else -> key
    }
}
