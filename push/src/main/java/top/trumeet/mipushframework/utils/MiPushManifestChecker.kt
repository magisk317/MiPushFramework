package top.trumeet.mipushframework.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.text.TextUtils
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import com.magisk317.push.hook.HookTraceCompat
import com.xiaomi.mipush.sdk.ManifestChecker
import com.xiaomi.mipush.sdk.PushMessageHandler
import com.xiaomi.push.service.PushConstants
import top.trumeet.common.Constants
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

@Suppress("UNCHECKED_CAST")
class MiPushManifestChecker private constructor(
    private val manifestChecker: Class<*>,
    private val context: Context
) {
    private val TAG2 = "MiPushManifestChecker"
    private val logger = object {
        fun e(msg: String, t: Throwable? = null) = Napier.e(msg, t, tag = TAG2)
        fun w(msg: String) = Napier.w(msg, tag = TAG2)
    }

    private val checkServicesMethod: Method = manifestChecker.getDeclaredMethod("checkServices", Context::class.java, PackageInfo::class.java).apply {
        isAccessible = true
    }

    fun checkPermissions(packageInfo: PackageInfo): Boolean {
        return try {
            val method = manifestChecker.getDeclaredMethod("checkPermissions", Context::class.java, PackageInfo::class.java)
            method.isAccessible = true
            method.invoke(null, context, packageInfo)
            true
        } catch (e: Throwable) {
            if (!isIllegalManifestException(e)) {
                logger.e("checkPermissions", e)
            } else {
                logger.e("checkPermissions: " + packageInfo.packageName + "," + (e as InvocationTargetException).cause!!.message)
            }
            false
        }
    }

    fun checkReceivers(packageName: String): Boolean {
        return try {
            val appCtx = context.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY or Context.CONTEXT_INCLUDE_CODE)
            val method = manifestChecker.getDeclaredMethod("checkReceivers", Context::class.java)
            method.isAccessible = true
            method.invoke(null, appCtx)
            true
        } catch (e: Throwable) {
            if (!isIllegalManifestException(e)) {
                logger.e("checkReceivers", e)
            }
            false
        }
    }

    fun checkServices(pkgInfo: PackageInfo): Boolean {
        HookTraceCompat.onManifestCheckServices(pkgInfo)
        if (TextUtils.equals(pkgInfo.packageName, PushConstants.PUSH_SERVICE_PACKAGE_NAME)) {
            return true
        }
        return try {
            val configServiceProcessMap = HashMap<String, String?>()
            val requiredServicesMap = HashMap<String, ManifestChecker.ServiceCheckInfo>()
            val pushHandlerServiceName = PushMessageHandler::class.java.name
            requiredServicesMap[pushHandlerServiceName] =
                ManifestChecker.ServiceCheckInfo(pushHandlerServiceName, true, true, "")

            if (pkgInfo.services != null) {
                for (info: ServiceInfo in pkgInfo.services) {
                    if (!TextUtils.isEmpty(info.name) && requiredServicesMap.containsKey(info.name)) {
                        val checkInfo = requiredServicesMap.remove(info.name)!!
                        val enabled = checkInfo.enabled
                        val exported = checkInfo.exported
                        val permission = checkInfo.permission
                        if (enabled && !info.enabled) {
                            throw IllegalStateException("service ${info.name} has wrong enabled attribute")
                        }
                        if (exported && !info.exported) {
                            throw IllegalStateException("service ${info.name} has wrong exported attribute")
                        }
                        if (!TextUtils.isEmpty(permission) && !TextUtils.equals(permission, info.permission)) {
                            throw IllegalStateException("service ${info.name} has wrong permission attribute")
                        }
                        configServiceProcessMap[info.name] = info.processName
                        if (requiredServicesMap.isEmpty()) {
                            break
                        }
                    }
                }
            }

            if (requiredServicesMap.isNotEmpty()) {
                throw IllegalStateException("service missing or disabled: " + requiredServicesMap.keys.iterator().next())
            }

            if (configServiceProcessMap.containsKey(PushConstants.XM_SERVICE_CLASS_NAME_JAR)
                && configServiceProcessMap.containsKey(PushConstants.PUSH_SERVICE_CLASS_NAME_JAR)
                && !TextUtils.equals(
                    configServiceProcessMap[PushConstants.XM_SERVICE_CLASS_NAME_JAR],
                    configServiceProcessMap[PushConstants.PUSH_SERVICE_CLASS_NAME_JAR]
                )
            ) {
                throw IllegalStateException("XM_SERVICE and PUSH_SERVICE must be in same process")
            }
            true
        } catch (e: Throwable) {
            if (e is IllegalStateException) {
                logger.w("checkServices: " + pkgInfo.packageName + "," + e.message)
            } else if (!isIllegalManifestException(e)) {
                logger.e("checkServices", e)
            } else {
                logger.w("checkServices: " + pkgInfo.packageName + "," + e.message)
            }
            false
        }
    }

    companion object {
        private val TAG: String = MiPushManifestChecker::class.java.simpleName

        @JvmStatic
        @Throws(PackageManager.NameNotFoundException::class, ClassNotFoundException::class, NoSuchMethodException::class)
        fun create(context: Context): MiPushManifestChecker {
            val manifestChecker = context.createPackageContext(
                Constants.SERVICE_APP_NAME,
                Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
            ).classLoader.loadClass("com.xiaomi.mipush.sdk.ManifestChecker")
            return MiPushManifestChecker(manifestChecker, context)
        }

        private fun isIllegalManifestException(e0: Throwable): Boolean {
            var e = e0
            if (e is InvocationTargetException) {
                e = e.targetException
            }
            return e.javaClass.name == "com.xiaomi.mipush.sdk.ManifestChecker\$IllegalManifestException"
        }
    }
}
