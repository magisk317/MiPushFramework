package io.github.magisk317.mipush.hook.securitycore

import android.content.pm.PackageInfo
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Process
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.xposed.MethodHookParam
import io.github.magisk317.mipush.xposed.findClass
import io.github.magisk317.mipush.xposed.hook
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections

object SecurityCoreXSpacePackageInfoHook {
    private const val TAG = "SecurityCoreXSpacePackageInfoHook"
    private const val SECURITY_CORE_PACKAGE = "com.miui.securitycore"
    private const val PUSH_MESSAGE_HANDLER = "com.xiaomi.mipush.sdk.PushMessageHandler"
    private const val GET_SERVICES = 0x00000004L
    private const val GET_PERMISSIONS = 0x00001000L
    private const val OWNER_USER_ID = 0
    private const val XSPACE_USER_ID = 999

    private val packageManagerClasses = listOf(
        "com.android.server.pm.PackageManagerService\$IPackageManagerImpl",
        "com.android.server.pm.PackageManagerService",
    )
    private val loggedPackages: MutableSet<String> = Collections.synchronizedSet(HashSet())

    fun hook(classLoader: ClassLoader) {
        val installed = packageManagerClasses.sumOf { className ->
            runCatching {
                val targetClass = classLoader.findClass(className)
                targetClass.declaredMethods.count { method ->
                    if (!isPackageInfoMethod(method)) return@count false
                    hookPackageInfoMethod(method)
                    true
                }.also { count ->
                    if (count > 0) {
                        XLog.i(TAG, "installed SecurityCore PackageInfo hooks on $className count=$count")
                    }
                }
            }.getOrElse { throwable ->
                XLog.d(TAG, "skip PackageInfo hook target $className: ${throwable.message}")
                0
            }
        }
        if (installed == 0) {
            XLog.w(TAG, "no PackageInfo hook target installed for SecurityCore XSpace MiPush retention")
        }
    }

    private fun hookPackageInfoMethod(method: Method) {
        method.hook {
            doAfter {
                val packageInfo = result as? PackageInfo ?: return@doAfter
                val queryPackage = packageInfo.packageName.takeIf { it.isNotBlank() }
                    ?: firstStringArg()
                    ?: return@doAfter
                val flags = firstFlagsArg() ?: return@doAfter
                val userId = lastUserIdArg()
                val callerProcess = callerProcessName()
                val decision = decidePackageInfoPatch(
                    callerProcessName = callerProcess,
                    queryPackage = queryPackage,
                    flags = flags,
                    userId = userId,
                    alreadyRequired = hasMiPushRequiredSignal(queryPackage, packageInfo),
                )
                if (!decision.forceRequired) return@doAfter

                ensureMiPushRequiredSignal(queryPackage, packageInfo)
                result = packageInfo
                logPatch(queryPackage, callerProcess, flags, userId, decision)
            }
        }
    }

    internal fun decidePackageInfoPatch(
        callerProcessName: String?,
        queryPackage: String?,
        flags: Long,
        userId: Int?,
        alreadyRequired: Boolean,
    ): SecurityCoreXSpaceMiPushDecision {
        if (!isSecurityCoreCaller(callerProcessName)) {
            return SecurityCoreXSpaceMiPushDecision(forceRequired = false, reason = "caller_not_securitycore")
        }
        if (!requestsMiPushRequiredSignals(flags)) {
            return SecurityCoreXSpaceMiPushDecision(forceRequired = false, reason = "flags_without_mipush_signals")
        }
        if (userId != null && userId != XSPACE_USER_ID) {
            return SecurityCoreXSpaceMiPushDecision(forceRequired = false, reason = "unsupported_user")
        }
        return SecurityCoreXSpaceMiPushPolicy.decide(queryPackage, originalRequired = alreadyRequired)
    }

    internal fun hasMiPushRequiredSignal(packageName: String, packageInfo: PackageInfo): Boolean {
        val expectedPermission = "$packageName.permission.MIPUSH_RECEIVE"
        val hasPermission = packageInfo.requestedPermissions.orEmpty().any { it == expectedPermission }
        val hasHandler = packageInfo.services.orEmpty().any { it.name == PUSH_MESSAGE_HANDLER }
        return hasPermission && hasHandler
    }

    internal fun ensureMiPushRequiredSignal(packageName: String, packageInfo: PackageInfo) {
        val expectedPermission = "$packageName.permission.MIPUSH_RECEIVE"
        if (packageInfo.requestedPermissions.orEmpty().none { it == expectedPermission }) {
            packageInfo.requestedPermissions = buildList {
                addAll(packageInfo.requestedPermissions.orEmpty())
                add(expectedPermission)
            }.toTypedArray()
        }
        if (packageInfo.services.orEmpty().none { it.name == PUSH_MESSAGE_HANDLER }) {
            packageInfo.services = buildList {
                addAll(packageInfo.services.orEmpty())
                add(
                    ServiceInfo().apply {
                        this.packageName = packageName
                        name = PUSH_MESSAGE_HANDLER
                        enabled = true
                        exported = true
                        processName = packageName
                        applicationInfo = packageInfo.applicationInfo
                    }
                )
            }.toTypedArray()
        }
    }

    private fun isPackageInfoMethod(method: Method): Boolean {
        return !Modifier.isAbstract(method.modifiers) &&
            method.returnType == PackageInfo::class.java &&
            method.name.contains("getPackageInfo") &&
            method.parameterTypes.any { it == String::class.java } &&
            method.parameterTypes.any(::isFlagsType)
    }

    private fun MethodHookParam.firstStringArg(): String? {
        return args.firstOrNull { it is String } as? String
    }

    private fun MethodHookParam.firstFlagsArg(): Long? {
        return args.firstNotNullOfOrNull { arg ->
            when (arg) {
                is Long -> arg
                is Int -> arg.toLong()
                else -> null
            }
        }
    }

    private fun MethodHookParam.lastUserIdArg(): Int? {
        return args.lastOrNull { it is Int } as? Int
    }

    private fun callerProcessName(): String? {
        val pid = Binder.getCallingPid()
        if (pid <= 0 || pid == Process.myPid()) return null
        return runCatching {
            File("/proc/$pid/cmdline")
                .readText()
                .substringBefore('\u0000')
                .trim()
                .takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun isSecurityCoreCaller(processName: String?): Boolean {
        return processName == SECURITY_CORE_PACKAGE ||
            processName?.startsWith("$SECURITY_CORE_PACKAGE:") == true
    }

    private fun requestsMiPushRequiredSignals(flags: Long): Boolean {
        return flags and GET_SERVICES != 0L && flags and GET_PERMISSIONS != 0L
    }

    private fun isFlagsType(type: Class<*>): Boolean {
        return type == Long::class.javaPrimitiveType ||
            type == Long::class.javaObjectType ||
            type == Int::class.javaPrimitiveType ||
            type == Int::class.javaObjectType
    }

    private fun logPatch(
        packageName: String,
        callerProcess: String?,
        flags: Long,
        userId: Int?,
        decision: SecurityCoreXSpaceMiPushDecision,
    ) {
        if (!loggedPackages.add(packageName)) return
        XLog.i(
            TAG,
            "patched PackageInfo for SecurityCore XSpace MiPush retention " +
                "pkg=$packageName caller=${callerProcess.orEmpty()} flags=$flags userId=${userId ?: -1} reason=${decision.reason}"
        )
    }
}
