package com.magisk317.compat

import android.os.SystemClock
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import com.topjohnwu.superuser.Shell

object RegistrationStateCompat {
    private val logger = object {
        fun d(msg: String) = Napier.d(msg, tag = "RegistrationStateCompat")
        fun i(msg: String) = Napier.i(msg, tag = "RegistrationStateCompat")
        fun w(msg: String) = Napier.w(msg, tag = "RegistrationStateCompat")
    }
    private const val VALID_PATTERN = "name=\"valid\" value=\"true\""
    private const val REG_ID_TAG_PATTERN = "name=\"regId\">"
    private const val REG_ID_VALUE_PATTERN = "name=\"regId\" value=\""
    private const val KEVA_VALID_PATTERN = "valid"
    private const val KEVA_REG_ID_PATTERN = "regId"
    private const val KEVA_APP_TOKEN_PATTERN = "appToken"
    private const val ROOT_CAPABILITY_TTL_MS = 60_000L
    private const val PROBE_TIME_BUDGET_MS = 500L

    @Volatile
    private var rootCapabilityCache: RootCapability? = null

    private data class RootCapability(
        val available: Boolean,
        val mountMasterAvailable: Boolean,
        val checkedAtElapsedMs: Long
    )

    private fun runCommand(command: String): Shell.Result = Shell.cmd(command).exec()

    private fun isRootResult(result: Shell.Result?): Boolean {
        if (result == null || !result.isSuccess) return false
        return result.out.firstOrNull()?.trim() == "0"
    }

    private fun getRootCapability(): RootCapability {
        val now = SystemClock.elapsedRealtime()
        val cached = rootCapabilityCache
        if (cached != null && now - cached.checkedAtElapsedMs <= ROOT_CAPABILITY_TTL_MS) {
            return cached
        }
        val uid = runCatching { runCommand("id -u").out.firstOrNull()?.trim() }.getOrNull()
        if (uid == "0") {
            return RootCapability(
                available = true,
                mountMasterAvailable = true,
                checkedAtElapsedMs = now
            ).also { rootCapabilityCache = it }
        }
        val suMount = runCatching { runCommand("su --mount-master -c \"id -u\"") }.getOrNull()
        val suNormal = runCatching { runCommand("su -c \"id -u\"") }.getOrNull()
        return RootCapability(
            available = isRootResult(suMount) || isRootResult(suNormal),
            mountMasterAvailable = isRootResult(suMount),
            checkedAtElapsedMs = now
        ).also { rootCapabilityCache = it }
    }

    private fun runAsRoot(command: String, preferMountMaster: Boolean): Shell.Result? {
        val escaped = command.replace("\"", "\\\"")
        if (preferMountMaster) {
            val mountResult = runCatching { runCommand("su --mount-master -c \"$escaped\"") }.getOrNull()
            if (mountResult != null && mountResult.isSuccess) return mountResult
        }
        val suResult = runCatching { runCommand("su -c \"$escaped\"") }.getOrNull()
        if (suResult != null && suResult.isSuccess) return suResult
        if (!preferMountMaster) {
            return runCatching { runCommand("su --mount-master -c \"$escaped\"") }.getOrNull()
        }
        return suResult
    }

    private fun hasRootProbeAccess(currentUid: String?): Boolean {
        if (currentUid == "0") return true
        val suUid = runCatching {
            Shell.cmd("su -c 'id -u'").exec().out.firstOrNull()?.trim()
        }.getOrNull()
        return suUid == "0"
    }

    @JvmStatic
    fun hasValidLocalRegistration(packageName: String): Boolean {
        val paths = listOf(
            "/data/user/0/$packageName/shared_prefs/mipush.xml",
            "/data_mirror/data_ce/null/0/$packageName/shared_prefs/mipush.xml",
            "/data/user/0/$packageName/files/keva/repo/mipush/mipush.blk",
            "/data_mirror/data_ce/null/0/$packageName/files/keva/repo/mipush/mipush.blk"
        )
        val uid = runCatching { Shell.cmd("id -u").exec().out.firstOrNull()?.trim() }.getOrNull()
        logger.d("check local registration, pkg=$packageName, shell uid=$uid")
        for (path in paths) {
            if (probe(path, useSu = true)) {
                logger.i("local registration found via su: $path")
                return true
            }
            if (probe(path, useSu = false)) {
                logger.i("local registration found via shell: $path")
                return true
            }
            if (probeByRead(path, useSu = true)) {
                logger.i("local registration found via su read: $path")
                return true
            }
            if (probeByRead(path, useSu = false)) {
                logger.i("local registration found via shell read: $path")
                return true
            }
        }
        return false
    }

    private fun probe(path: String, useSu: Boolean): Boolean {
        val cmd =
            "[ -f $path ] && " +
                "(" +
                "(grep -aq '$VALID_PATTERN' $path && (grep -aq '$REG_ID_TAG_PATTERN' $path || grep -aq '$REG_ID_VALUE_PATTERN' $path))" +
                " || " +
                "(grep -aq '$KEVA_VALID_PATTERN' $path && grep -aq '$KEVA_REG_ID_PATTERN' $path && grep -aq '$KEVA_APP_TOKEN_PATTERN' $path)" +
                ") && " +
                "echo true || echo false"
        val result = if (useSu) {
            val capability = getRootCapability()
            runAsRoot(cmd, preferMountMaster = capability.mountMasterAvailable)
        } else {
            runCommand(cmd)
        }
        return result?.out?.firstOrNull()?.trim() == "true"
    }

    private fun probeByRead(path: String, useSu: Boolean): Boolean {
        val cmd = "[ -f $path ] && cat $path || true"
        val out = runCatching {
            if (useSu) {
                val capability = getRootCapability()
                runAsRoot(cmd, preferMountMaster = capability.mountMasterAvailable)?.out
            } else {
                runCommand(cmd).out
            }
        }.getOrNull() ?: return false
        return containsRegistrationMarkers(out.joinToString("\n"))
    }

    private fun containsRegistrationMarkers(content: String): Boolean {
        if (content.isBlank()) return false
        val hasValid = content.contains("name=\"valid\" value=\"true\"")
        val hasXmlRegId = content.contains("name=\"regId\">") || content.contains("name=\"regId\" value=\"")
        if (hasValid && hasXmlRegId) return true

        val hasKevaValid = content.contains(KEVA_VALID_PATTERN)
        val hasKevaRegId = content.contains(KEVA_REG_ID_PATTERN)
        val hasKevaAppToken = content.contains(KEVA_APP_TOKEN_PATTERN)
        return hasKevaValid && hasKevaRegId && hasKevaAppToken
    }

    @JvmStatic
    fun findPackagesWithValidLocalRegistration(packages: Collection<String>): Set<String> {
        logger.d("find local registration start: queried=${packages.size}")
        if (packages.isEmpty()) return emptySet()
        val result = linkedSetOf<String>()
        val uid = runCatching { Shell.cmd("id -u").exec().out.firstOrNull()?.trim() }.getOrNull()
        logger.d("find local registration shell uid=$uid")
        val capability = getRootCapability()
        if (!capability.available && !hasRootProbeAccess(uid)) {
            logger.i("skip local registration probe: root access unavailable")
            return emptySet()
        }
        val startElapsed = SystemClock.elapsedRealtime()
        var hasBatchExecutionFailure = false
        var hitTimeBudget = false
        val chunks = packages.chunked(60)
        var chunkIdx = 0
        for (chunk in chunks) {
            if (SystemClock.elapsedRealtime() - startElapsed > PROBE_TIME_BUDGET_MS) {
                hitTimeBudget = true
                logger.w("find local registration stop by time budget: ${PROBE_TIME_BUDGET_MS}ms")
                break
            }
            chunkIdx++
            logger.d("find local registration processing chunk=$chunkIdx")
            val safePackages = chunk.filter { it.matches(Regex("[A-Za-z0-9._]+")) }
            if (safePackages.isEmpty()) continue
            val script = buildString {
                append("for pkg in ")
                append(safePackages.joinToString(" "))
                append("; do ")
                append("for base in /data/user/0 /data_mirror/data_ce/null/0; do ")
                append("f_xml=\\\"${'$'}base/${'$'}pkg/shared_prefs/mipush.xml\\\"; ")
                append("f_keva=\\\"${'$'}base/${'$'}pkg/files/keva/repo/mipush/mipush.blk\\\"; ")
                append("if [ -f \\\"${'$'}f_xml\\\" ] && ")
                append("grep -aq 'name=\"valid\" value=\"true\"' \\\"${'$'}f_xml\\\" && ")
                append("(grep -aq 'name=\"regId\">' \\\"${'$'}f_xml\\\" || grep -aq 'name=\"regId\" value=\"' \\\"${'$'}f_xml\\\"); then ")
                append("echo ${'$'}pkg; break; fi; ")
                append("if [ -f \\\"${'$'}f_keva\\\" ] && ")
                append("grep -aq 'valid' \\\"${'$'}f_keva\\\" && ")
                append("grep -aq 'regId' \\\"${'$'}f_keva\\\" && ")
                append("grep -aq 'appToken' \\\"${'$'}f_keva\\\"; then ")
                append("echo ${'$'}pkg; break; fi; ")
                append("done; ")
                append("done")
            }
            val out = runAsRoot(script, preferMountMaster = capability.mountMasterAvailable)
            if (out != null && out.isSuccess) {
                result += out.out.map { it.trim() }.filter { it.isNotEmpty() }
                continue
            }
            val fallbackOut = runCatching { runCommand(script) }.getOrNull()
            if (fallbackOut != null && fallbackOut.isSuccess) {
                result += fallbackOut.out.map { it.trim() }.filter { it.isNotEmpty() }
                continue
            }
            hasBatchExecutionFailure = true
        }
        if (result.isEmpty() && hasBatchExecutionFailure && !hitTimeBudget) {
            logger.w("find local registration fallback to sequential probe")
            for (pkg in packages) {
                if (SystemClock.elapsedRealtime() - startElapsed > PROBE_TIME_BUDGET_MS) {
                    logger.w("sequential local registration probe stopped by time budget")
                    break
                }
                if (hasValidLocalRegistration(pkg)) {
                    result += pkg
                }
            }
        }
        logger.d("find local registration done: queried=${packages.size}, matched=${result.size}")
        return result
    }
}
