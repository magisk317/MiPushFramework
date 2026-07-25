package io.github.magisk317.mipush.hook.securitycore
import io.github.magisk317.xposed.BaseHook
import io.github.magisk317.xposed.LoadParam

import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.hook.island.IslandPreferences
import io.github.magisk317.xposed.findClass
import io.github.magisk317.xposed.hook
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections

class SecurityCoreXSpaceMiPushHook : BaseHook() {
    override fun onLoadPackage(param: LoadParam) {
        if (param.packageName != SECURITY_CORE_PACKAGE_NAME) return
        val classLoader = param.classLoader
        runCatching {
            val xspaceUtilClass = classLoader.findClass(XSPACE_UTIL_CLASS)
            val targetMethod = findMiPushRequiredMethod(xspaceUtilClass)
            targetMethod.hook {
                doAfter {
                    val packageName = args.getOrNull(PACKAGE_NAME_ARG_INDEX) as? String
                    val originalRequired = result as? Boolean
                    val decision = SecurityCoreXSpaceMiPushPolicy.decide(
                        packageName,
                        originalRequired,
                        dualAppEnabled = IslandPreferences.current().dualAppEnabled,
                    )
                    if (!decision.forceRequired) return@doAfter

                    result = true
                    logDecision(packageName.orEmpty(), decision)
                }
            }
            XLog.i(TAG, "installed XSpace MiPush required hook: ${targetMethod.name}")
        }.onFailure {
            XLog.e(TAG, "install XSpace MiPush required hook failed", it)
        }
    }

    private fun findMiPushRequiredMethod(xspaceUtilClass: Class<*>): Method {
        val candidates = xspaceUtilClass.declaredMethods.filter(::isMiPushRequiredCandidate)
        return candidates.firstOrNull { it.name == DUMPED_METHOD_NAME }
            ?: candidates.singleOrNull()
            ?: throw NoSuchMethodException(
                "SecurityCore XSpace MiPush method not found in ${xspaceUtilClass.name}; " +
                    "candidates=${candidates.map { it.name }}"
            )
    }

    private fun logDecision(packageName: String, decision: SecurityCoreXSpaceMiPushDecision) {
        if (!loggedPackages.add(packageName)) return
        XLog.i(TAG, "force XSpace MiPush required for pkg=$packageName reason=${decision.reason}")
    }

    companion object {
        private const val TAG = "SecurityCoreXSpaceMiPushHook"
        private const val SECURITY_CORE_PACKAGE_NAME = "com.miui.securitycore"
        private const val XSPACE_UTIL_CLASS = "x6.f"
        private const val DUMPED_METHOD_NAME = "h"
        private const val PACKAGE_NAME_ARG_INDEX = 1
        private const val IPACKAGE_MANAGER_CLASS = "android.content.pm.IPackageManager"
        private val loggedPackages: MutableSet<String> = Collections.synchronizedSet(HashSet())

        internal fun isMiPushRequiredCandidate(method: Method): Boolean {
            val parameterTypes = method.parameterTypes
            return Modifier.isStatic(method.modifiers) &&
                method.returnType == Boolean::class.javaPrimitiveType &&
                parameterTypes.size == 2 &&
                parameterTypes[0].name == IPACKAGE_MANAGER_CLASS &&
                parameterTypes[1] == String::class.java
        }
    }
}
