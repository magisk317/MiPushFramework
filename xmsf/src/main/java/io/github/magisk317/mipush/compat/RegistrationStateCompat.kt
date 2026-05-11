package io.github.magisk317.mipush.compat

import android.os.SystemClock
import io.github.aakira.napier.Napier
import io.github.magisk317.mipush.platform.support.AppRootAccessFacade
import io.github.magisk317.mipush.platform.support.BoundedShellResult
import io.github.magisk317.mipush.platform.support.PermissionUtils

object RegistrationStateCompat {
    private val diagnosticPackages = setOf("com.ss.android.ugc.aweme")
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
        val checkedAtElapsedMs: Long
    )

    private data class RegistrationMarkers(
        val hasXmlValid: Boolean,
        val hasXmlRegId: Boolean,
        val hasKevaValid: Boolean,
        val hasKevaRegId: Boolean,
        val hasKevaAppToken: Boolean,
        val regId: String?,
        val appToken: String?
    )

    private fun runCommand(command: String): BoundedShellResult = AppRootAccessFacade.runShellCommand(command)

    private fun getRootCapability(): RootCapability {
        val now = SystemClock.elapsedRealtime()
        val cached = rootCapabilityCache
        if (
            cached != null &&
            now - cached.checkedAtElapsedMs <= ROOT_CAPABILITY_TTL_MS &&
            (cached.available || !PermissionUtils.hasCachedRootAccess())
        ) {
            return cached
        }
        if (!PermissionUtils.hasCachedRootAccess()) {
            return RootCapability(
                available = false,
                checkedAtElapsedMs = now
            ).also { rootCapabilityCache = it }
        }
        return RootCapability(
            available = AppRootAccessFacade.refreshRootAccessIfGranted(),
            checkedAtElapsedMs = now
        ).also { rootCapabilityCache = it }
    }

    private fun runAsRoot(command: String): BoundedShellResult? {
        val result = AppRootAccessFacade.runRootCommand(command)
        return result.takeIf { it.isSuccess }
    }

    private fun hasRootProbeAccess(currentUid: String?): Boolean {
        if (currentUid == "0") return true
        return PermissionUtils.hasCachedRootAccess()
    }

    @JvmStatic
    fun hasValidLocalRegistration(packageName: String): Boolean {
        val paths = listOf(
            "/data/user/0/$packageName/shared_prefs/mipush.xml",
            "/data_mirror/data_ce/null/0/$packageName/shared_prefs/mipush.xml",
            "/data/user/0/$packageName/files/keva/repo/mipush/mipush.blk",
            "/data_mirror/data_ce/null/0/$packageName/files/keva/repo/mipush/mipush.blk"
        )
        val uid = if (PermissionUtils.hasCachedRootAccess()) "cached_root" else null
        logger.d("check local registration, pkg=$packageName, shell uid=$uid")
        for (path in paths) {
            if (probe(path, useSu = true)) {
                logger.i("local registration found via su: $path")
                logRegistrationMarkers(packageName, path, useSu = true)
                return true
            }
            if (probe(path, useSu = false)) {
                logger.i("local registration found via shell: $path")
                logRegistrationMarkers(packageName, path, useSu = false)
                return true
            }
            if (probeByRead(path, useSu = true)) {
                logger.i("local registration found via su read: $path")
                logRegistrationMarkers(packageName, path, useSu = true)
                return true
            }
            if (probeByRead(path, useSu = false)) {
                logger.i("local registration found via shell read: $path")
                logRegistrationMarkers(packageName, path, useSu = false)
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
            if (capability.available) {
                runAsRoot(cmd)
            } else {
                null
            }
        } else {
            runCommand(cmd)
        }
        return result?.stdout?.firstOrNull()?.trim() == "true"
    }

    private fun probeByRead(path: String, useSu: Boolean): Boolean {
        val cmd = "[ -f $path ] && cat $path || true"
        val out = runCatching {
            if (useSu) {
                val capability = getRootCapability()
                if (capability.available) {
                    runAsRoot(cmd)?.stdout
                } else {
                    null
                }
            } else {
                runCommand(cmd).stdout
            }
        }.getOrNull() ?: return false
        return containsRegistrationMarkers(out.joinToString("\n"))
    }

    private fun containsRegistrationMarkers(content: String): Boolean {
        if (content.isBlank()) return false
        val markers = parseRegistrationMarkers(content)
        if (markers.hasXmlValid && markers.hasXmlRegId) return true
        return markers.hasKevaValid && markers.hasKevaRegId && markers.hasKevaAppToken
    }

    @JvmStatic
    fun findPackagesWithValidLocalRegistration(packages: Collection<String>): Set<String> {
        logger.d("find local registration start: queried=${packages.size}")
        if (packages.isEmpty()) return emptySet()
        val result = linkedSetOf<String>()
        val uid = if (PermissionUtils.hasCachedRootAccess()) "cached_root" else null
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
            val out = runAsRoot(script)
            if (out != null && out.isSuccess) {
                result += out.stdout.map { it.trim() }.filter { it.isNotEmpty() }
                continue
            }
            val fallbackOut = runCatching { runCommand(script) }.getOrNull()
            if (fallbackOut != null && fallbackOut.isSuccess) {
                result += fallbackOut.stdout.map { it.trim() }.filter { it.isNotEmpty() }
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

    private fun logRegistrationMarkers(packageName: String, path: String, useSu: Boolean) {
        if (packageName !in diagnosticPackages) return
        val cmd = "[ -f $path ] && cat $path || true"
        val out = runCatching {
            if (useSu) {
                val capability = getRootCapability()
                runAsRoot(cmd)?.stdout
            } else {
                runCommand(cmd).stdout
            }
        }.getOrNull() ?: return
        val content = out.joinToString("\n")
        if (content.isBlank()) return
        val markers = parseRegistrationMarkers(content)
        logger.i(
            "local registration details pkg=$packageName mode=${if (useSu) "su" else "shell"} path=$path " +
                "xmlValid=${markers.hasXmlValid} xmlRegId=${markers.hasXmlRegId} " +
                "kevaValid=${markers.hasKevaValid} kevaRegId=${markers.hasKevaRegId} kevaAppToken=${markers.hasKevaAppToken} " +
                "regId=${markers.regId ?: "missing"} appToken=${markers.appToken ?: "missing"}"
        )
    }

    private fun parseRegistrationMarkers(content: String): RegistrationMarkers {
        val sanitized = content.toPrintableDiagnosticText()
        val hasXmlValid = content.contains(VALID_PATTERN)
        val hasXmlRegId = content.contains(REG_ID_TAG_PATTERN) || content.contains(REG_ID_VALUE_PATTERN)
        val hasKevaValid = content.contains(KEVA_VALID_PATTERN)
        val hasKevaRegId = content.contains(KEVA_REG_ID_PATTERN)
        val hasKevaAppToken = content.contains(KEVA_APP_TOKEN_PATTERN)
        return RegistrationMarkers(
            hasXmlValid = hasXmlValid,
            hasXmlRegId = hasXmlRegId,
            hasKevaValid = hasKevaValid,
            hasKevaRegId = hasKevaRegId,
            hasKevaAppToken = hasKevaAppToken,
            regId = extractRegistrationValue(content, sanitized, "regId"),
            appToken = extractRegistrationValue(content, sanitized, "appToken")
        )
    }

    private fun extractRegistrationValue(content: String, sanitized: String, key: String): String? {
        val xmlPatterns = listOf(
            Regex("""name="$key"\s+value="([^"]+)""""),
            Regex("""name="$key">([^<\n\r]+)""")
        )
        xmlPatterns.forEach { regex ->
            regex.find(content)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.let {
                return it.truncateForDiagnostic()
            }
        }
        Regex("""\b$key\b[^A-Za-z0-9]{0,24}([A-Za-z0-9._:-]{6,128})""")
            .find(sanitized)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it.truncateForDiagnostic() }
        return null
    }

    private fun String.toPrintableDiagnosticText(): String {
        return buildString(length) {
            this@toPrintableDiagnosticText.forEach { ch ->
                append(if (ch.code in 32..126) ch else ' ')
            }
        }
    }

    private fun String.truncateForDiagnostic(limit: Int = 32): String {
        return if (length <= limit) this else take(limit)
    }
}
