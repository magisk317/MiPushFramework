package io.github.magisk317.mipush.hook.keepalive

import java.lang.reflect.Method

internal data class MethodShape(
    val owner: String,
    val name: String,
    val parameterTypes: List<String>,
    val returnType: String,
)

internal data class IndexedHookTarget(
    val capability: String,
    val shape: MethodShape,
    val packageIndex: Int? = null,
    val valueIndex: Int? = null,
    val secondaryValueIndex: Int? = null,
)

internal sealed class MethodResolution {
    data class Resolved(
        val target: IndexedHookTarget,
        val method: Method,
    ) : MethodResolution()

    data object Missing : MethodResolution()

    data class Ambiguous(
        val candidates: List<Resolved>,
    ) : MethodResolution()
}

internal object KeepAliveHookTargets {
    private const val LEGACY_OOM_ADJUSTER = "com.android.server.am.OomAdjuster"
    private const val MODERN_OOM_ADJUSTER = "com.android.server.am.psc.OomAdjuster"
    private const val MODERN_PROCESS_RECORD = "com.android.server.am.psc.ProcessRecordInternal"
    private const val PROCESS_RECORD = "com.android.server.am.ProcessRecord"
    private const val PROCESS_LIST = "com.android.server.am.ProcessList"
    private const val APP_STANDBY_CONTROLLER = "com.android.server.usage.AppStandbyController"

    val oomApplyOwners = listOf(MODERN_OOM_ADJUSTER, LEGACY_OOM_ADJUSTER)

    val oomApply = listOf(
        IndexedHookTarget(
            capability = "oom_apply",
            shape = MethodShape(
                owner = MODERN_OOM_ADJUSTER,
                name = "applyOomAdjLSP",
                parameterTypes = listOf(MODERN_PROCESS_RECORD, "boolean"),
                returnType = "void",
            ),
        ),
        IndexedHookTarget(
            capability = "oom_apply",
            shape = MethodShape(
                owner = LEGACY_OOM_ADJUSTER,
                name = "applyOomAdjLSP",
                parameterTypes = listOf(PROCESS_RECORD, "boolean", "long", "long"),
                returnType = "boolean",
            ),
        ),
        IndexedHookTarget(
            capability = "oom_apply",
            shape = MethodShape(
                owner = LEGACY_OOM_ADJUSTER,
                name = "applyOomAdjLSP",
                parameterTypes = listOf(PROCESS_RECORD, "boolean", "long", "long", "int", "boolean"),
                returnType = "boolean",
            ),
        ),
        IndexedHookTarget(
            capability = "oom_apply",
            shape = MethodShape(
                owner = LEGACY_OOM_ADJUSTER,
                name = "applyOomAdjLSP",
                parameterTypes = listOf(PROCESS_RECORD, "boolean", "long", "long", "int"),
                returnType = "boolean",
            ),
        ),
    )

    val killLocked = listOf(
        IndexedHookTarget(
            capability = "kill_guard",
            shape = MethodShape(
                owner = PROCESS_RECORD,
                name = "killLocked",
                parameterTypes = listOf("java.lang.String", "java.lang.String", "int", "int", "boolean"),
                returnType = "void",
            ),
            valueIndex = 2,
            secondaryValueIndex = 3,
        ),
        IndexedHookTarget(
            capability = "kill_guard",
            shape = MethodShape(
                owner = PROCESS_RECORD,
                name = "killLocked",
                parameterTypes = listOf(
                    "java.lang.String",
                    "java.lang.String",
                    "int",
                    "int",
                    "android.app.ApplicationExitInfo\$AnrInfo",
                    "boolean",
                    "boolean",
                ),
                returnType = "void",
            ),
            valueIndex = 2,
            secondaryValueIndex = 3,
        ),
        IndexedHookTarget(
            capability = "kill_guard",
            shape = MethodShape(
                owner = PROCESS_RECORD,
                name = "killLocked",
                parameterTypes = listOf(
                    "java.lang.String",
                    "java.lang.String",
                    "int",
                    "int",
                    "boolean",
                    "boolean",
                ),
                returnType = "void",
            ),
            valueIndex = 2,
            secondaryValueIndex = 3,
        ),
    )

    /** API 33 package-cleanup boundary, before MIUI sends SIGSTOP or removes process records. */
    val packageKill = listOf(
        IndexedHookTarget(
            capability = "package_kill_guard",
            shape = MethodShape(
                owner = PROCESS_LIST,
                name = "killPackageProcessesLSP",
                parameterTypes = listOf(
                    "java.lang.String",
                    "int",
                    "int",
                    "int",
                    "boolean",
                    "boolean",
                    "boolean",
                    "boolean",
                    "boolean",
                    "boolean",
                    "int",
                    "int",
                    "java.lang.String",
                ),
                returnType = "boolean",
            ),
            packageIndex = 0,
            valueIndex = 10,
            secondaryValueIndex = 11,
        ),
    )

    val standbyBucket = listOf(
        IndexedHookTarget(
            capability = "standby_bucket",
            shape = MethodShape(
                owner = APP_STANDBY_CONTROLLER,
                name = "setAppStandbyBucket",
                parameterTypes = listOf("java.lang.String", "int", "int", "int", "int"),
                returnType = "void",
            ),
            packageIndex = 0,
            valueIndex = 1,
        ),
        IndexedHookTarget(
            capability = "standby_bucket",
            shape = MethodShape(
                owner = APP_STANDBY_CONTROLLER,
                name = "setAppStandbyBucket",
                parameterTypes = listOf("java.lang.String", "int", "int", "int"),
                returnType = "void",
            ),
            packageIndex = 0,
            valueIndex = 2,
        ),
        IndexedHookTarget(
            capability = "standby_bucket",
            shape = MethodShape(
                owner = APP_STANDBY_CONTROLLER,
                name = "setAppStandbyBucket",
                parameterTypes = listOf("java.lang.String", "int", "int", "int", "long", "boolean"),
                returnType = "void",
            ),
            packageIndex = 0,
            valueIndex = 2,
        ),
    )

    val appIdle = listOf(
        IndexedHookTarget(
            capability = "standby_idle",
            shape = MethodShape(
                owner = APP_STANDBY_CONTROLLER,
                name = "setAppIdleAsync",
                parameterTypes = listOf("java.lang.String", "boolean", "int"),
                returnType = "void",
            ),
            packageIndex = 0,
            valueIndex = 1,
        ),
    )

    val forceIdle = listOf(
        IndexedHookTarget(
            capability = "standby_force_idle",
            shape = MethodShape(
                owner = APP_STANDBY_CONTROLLER,
                name = "forceIdleState",
                parameterTypes = listOf("java.lang.String", "int", "boolean"),
                returnType = "void",
            ),
            packageIndex = 0,
            valueIndex = 2,
        ),
    )

    fun resolve(owner: Class<*>, targets: List<IndexedHookTarget>): MethodResolution {
        val matches = targets.flatMap { target ->
            owner.declaredMethods
                .filter { method -> matches(method, target.shape) }
                .map { method -> MethodResolution.Resolved(target, method) }
        }
        return when (matches.size) {
            0 -> MethodResolution.Missing
            1 -> matches.single()
            else -> MethodResolution.Ambiguous(matches)
        }
    }

    fun describe(target: IndexedHookTarget): String = with(target.shape) {
        "$owner#$name(${parameterTypes.joinToString()})=$returnType"
    }

    private fun matches(method: Method, shape: MethodShape): Boolean {
        return method.declaringClass.name == shape.owner &&
            method.name == shape.name &&
            method.parameterTypes.map { it.name } == shape.parameterTypes &&
            method.returnType.name == shape.returnType
    }
}
