package io.github.magisk317.mipush.hook.keepalive

import io.github.magisk317.xposed.getHookIntField
import io.github.magisk317.xposed.getHookObjectField
import io.github.magisk317.xposed.setHookIntField

internal data class ActiveRecordMappings(
    val processName: String?,
    val nameCurrent: Boolean,
    val pidCurrent: Boolean,
)

internal class KeepAlivePlatformAdapter {
    fun adjustOomAdj(record: Any, flags: KeepAliveFlags): Boolean = runCatching {
        val processName = processName(record)
        val state = getHookObjectField(record, "mState") ?: return@runCatching false
        val currentAdj = getHookIntField(state, "mCurAdj")
        val desiredAdj = KeepAlivePolicy.desiredOomAdj(flags, processName, currentAdj) ?: return@runCatching false
        setHookIntField(state, "mCurAdj", desiredAdj)
        true
    }.getOrDefault(false)

    fun activeRecordMappings(record: Any): ActiveRecordMappings {
        return runCatching {
            val processName = processName(record)
            val uid = getHookIntField(record, "uid")
            val pid = getHookIntField(record, "mPid")
            val service = getHookObjectField(record, "mService")
            if (processName == null || pid <= 0 || service == null) {
                return@runCatching ActiveRecordMappings(processName, false, false)
            }
            val nameCurrent = currentRecordByName(service, processName, uid) === record
            val pidCurrent = currentRecordByPid(service, pid) === record
            ActiveRecordMappings(processName, nameCurrent, pidCurrent)
        }.getOrElse { ActiveRecordMappings(processName(record), false, false) }
    }

    fun processName(record: Any): String? = runCatching {
        getHookObjectField(record, "processName") as? String
    }.getOrNull()

    private fun currentRecordByName(service: Any, processName: String, uid: Int): Any? {
        val method = service.javaClass.declaredMethods.singleOrNull {
            it.name == "getProcessRecordLocked" &&
                it.parameterTypes.contentEquals(arrayOf(String::class.java, Int::class.javaPrimitiveType))
        } ?: return null
        return runCatching {
            method.isAccessible = true
            method.invoke(service, processName, uid)
        }.getOrNull()
    }

    private fun currentRecordByPid(service: Any, pid: Int): Any? {
        val pidMap = getHookObjectField(service, "mPidsSelfLocked") ?: return null
        val getMethod = pidMap.javaClass.declaredMethods.singleOrNull {
            it.name == "get" && it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
        } ?: return null
        return synchronized(pidMap) {
            runCatching {
                getMethod.isAccessible = true
                getMethod.invoke(pidMap, pid)
            }.getOrNull()
        }
    }
}
