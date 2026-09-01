package io.github.magisk317.mipush.compat

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import android.os.SystemClock
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.platform.support.AppRootAccessFacade
import io.github.magisk317.mipush.platform.support.BoundedShellResult
import io.github.magisk317.mipush.platform.support.PermissionUtils
import java.io.StringReader
import java.util.concurrent.ConcurrentHashMap
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

object RegistrationStateCompat {
    private val diagnosticPackages = setOf("com.ss.android.ugc.aweme")
    private const val VALID_PATTERN = "name=\"valid\" value=\"true\""
    private const val REG_ID_TAG_PATTERN = "name=\"regId\">"
    private const val REG_ID_VALUE_PATTERN = "name=\"regId\" value=\""
    private const val REG_SEC_TAG_PATTERN = "name=\"regSec\">"
    private const val REG_SEC_VALUE_PATTERN = "name=\"regSec\" value=\""
    private const val KEVA_VALID_PATTERN = "valid"
    private const val KEVA_REG_ID_PATTERN = "regId"
    private const val KEVA_REG_SEC_PATTERN = "regSec"
    private const val KEVA_APP_TOKEN_PATTERN = "appToken"
    private const val ROOT_CAPABILITY_TTL_MS = 60_000L
    private const val PROBE_TIME_BUDGET_MS = 500L
    private const val REG_SEC_RECOVERY_TIMEOUT_MS = 1_000L
    private const val REG_SEC_RECOVERY_MISS_TTL_MS = 60_000L
    private const val MAX_REGISTRATION_XML_LENGTH = 256 * 1_024
    private const val MAX_REG_SEC_LENGTH = 1_024
    private val SAFE_PACKAGE_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)+")

    @Volatile
    private var rootCapabilityCache: RootCapability? = null
    private val regSecRecoveryMisses = ConcurrentHashMap<String, Long>()
    private val regSecRecoveryLock = Any()

    private data class RootCapability(
        val available: Boolean,
        val checkedAtElapsedMs: Long
    )

    private data class RegistrationMarkers(
        val hasXmlValid: Boolean,
        val hasXmlRegId: Boolean,
        val hasXmlRegSec: Boolean,
        val hasKevaValid: Boolean,
        val hasKevaRegId: Boolean,
        val hasKevaRegSec: Boolean,
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
        val paths = registrationArtifactPaths(packageName)
        val uid = if (PermissionUtils.hasCachedRootAccess()) "cached_root" else null
        logD { "check local registration, pkg=$packageName, shell uid=$uid" }
        for (path in paths) {
            if (probe(path, useSu = true)) {
                logI("local registration found via su: $path")
                logRegistrationMarkers(packageName, path, useSu = true)
                return true
            }
            if (probe(path, useSu = false)) {
                logI("local registration found via shell: $path")
                logRegistrationMarkers(packageName, path, useSu = false)
                return true
            }
            if (probeByRead(path, useSu = true)) {
                logI("local registration found via su read: $path")
                logRegistrationMarkers(packageName, path, useSu = true)
                return true
            }
            if (probeByRead(path, useSu = false)) {
                logI("local registration found via shell read: $path")
                logRegistrationMarkers(packageName, path, useSu = false)
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun hasLocalRegistrationArtifacts(packageName: String): Boolean {
        val uid = if (PermissionUtils.hasCachedRootAccess()) "cached_root" else null
        logD { "check local registration artifacts, pkg=$packageName, shell uid=$uid" }
        return registrationArtifactPaths(packageName).any { path ->
            fileExists(path, useSu = true) || fileExists(path, useSu = false)
        }
    }

    private fun probe(path: String, useSu: Boolean): Boolean {
        val cmd =
            "[ -f $path ] && " +
                "(" +
                "(grep -aq '$VALID_PATTERN' $path && " +
                "(grep -aq '$REG_ID_TAG_PATTERN' $path || grep -aq '$REG_ID_VALUE_PATTERN' $path))" +
                " || " +
                "(grep -aq '$KEVA_VALID_PATTERN' $path && grep -aq '$KEVA_REG_ID_PATTERN' $path && " +
                "grep -aq '$KEVA_APP_TOKEN_PATTERN' $path)" +
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

    private fun fileExists(path: String, useSu: Boolean): Boolean {
        val cmd = "[ -e $path ] && echo true || echo false"
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
        val userId = Utils.requireValidUserId(Utils.myUserId())
        logD { "find local registration start: queried=${packages.size} userId=$userId" }
        if (packages.isEmpty()) return emptySet()
        val result = linkedSetOf<String>()
        val uid = if (PermissionUtils.hasCachedRootAccess()) "cached_root" else null
        logD { "find local registration shell uid=$uid" }
        val capability = getRootCapability()
        if (!capability.available && !hasRootProbeAccess(uid)) {
            logI("skip local registration probe: root access unavailable")
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
                logW("find local registration stop by time budget: ${PROBE_TIME_BUDGET_MS}ms")
                break
            }
            chunkIdx++
            logD { "find local registration processing chunk=$chunkIdx" }
            val safePackages = chunk.filter { it.matches(SAFE_PACKAGE_NAME) }
            if (safePackages.isEmpty()) continue
            val script = buildBatchProbeScript(safePackages, userId)
            if (script.isBlank()) continue
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
            logW("find local registration fallback to sequential probe")
            for (pkg in packages) {
                if (SystemClock.elapsedRealtime() - startElapsed > PROBE_TIME_BUDGET_MS) {
                    logW("sequential local registration probe stopped by time budget")
                    break
                }
                if (hasValidLocalRegistration(pkg)) {
                    result += pkg
                }
            }
        }
        logD { "find local registration done: queried=${packages.size}, matched=${result.size}" }
        return result
    }

    internal fun buildBatchProbeScript(packages: Collection<String>, userId: Int): String {
        val safePackages = packages.filter { it.matches(SAFE_PACKAGE_NAME) }
        val bases = registrationBasePathsForUser(userId)
        if (safePackages.isEmpty() || bases.isEmpty()) return ""
        return buildString {
            append("for pkg in ")
            append(safePackages.joinToString(" "))
            append("; do ")
            append("for base in ")
            append(bases.joinToString(" "))
            append("; do ")
            append("f_xml=\"${'$'}base/${'$'}pkg/shared_prefs/mipush.xml\"; ")
            append("f_keva=\"${'$'}base/${'$'}pkg/files/keva/repo/mipush/mipush.blk\"; ")
            append("if [ -f \"${'$'}f_xml\" ] && ")
            append("grep -aq 'name=\"valid\" value=\"true\"' \"${'$'}f_xml\" && ")
            append("(grep -aq 'name=\"regId\">' \"${'$'}f_xml\" || grep -aq 'name=\"regId\" value=\"' \"${'$'}f_xml\"); then ")
            append("echo ${'$'}pkg; break; fi; ")
            append("if [ -f \"${'$'}f_keva\" ] && ")
            append("grep -aq 'valid' \"${'$'}f_keva\" && ")
            append("grep -aq 'regId' \"${'$'}f_keva\" && ")
            append("grep -aq 'appToken' \"${'$'}f_keva\"; then ")
            append("echo ${'$'}pkg; break; fi; ")
            append("done; ")
            append("done")
        }
    }

    @JvmStatic
    fun recoverLocalRegSec(packageName: String, userId: Int = Utils.requireValidUserId(Utils.myUserId())): String? {
        if (!packageName.matches(SAFE_PACKAGE_NAME)) return null
        val normalizedUserId = Utils.requireValidUserId(userId)
        val cacheKey = "$normalizedUserId:$packageName"
        Utils.getRegSec(packageName, normalizedUserId)?.let { return it }
        if (!getRootCapability().available) return null

        val now = SystemClock.elapsedRealtime()
        val lastMiss = regSecRecoveryMisses[cacheKey]
        if (lastMiss != null && now - lastMiss in 0 until REG_SEC_RECOVERY_MISS_TTL_MS) {
            return null
        }

        synchronized(regSecRecoveryLock) {
            Utils.getRegSec(packageName, normalizedUserId)?.let { return it }
            val currentMiss = regSecRecoveryMisses[cacheKey]
            if (currentMiss != null && now - currentMiss in 0 until REG_SEC_RECOVERY_MISS_TTL_MS) {
                return null
            }

            val secret = registrationArtifactPathsForUser(packageName, normalizedUserId)
                .asSequence()
                .filter { it.endsWith("/shared_prefs/mipush.xml") }
                .mapNotNull { path ->
                    AppRootAccessFacade.runRootCommand(
                        command = "[ -f $path ] && cat $path || true",
                        timeoutMs = REG_SEC_RECOVERY_TIMEOUT_MS,
                    ).takeIf { it.isSuccess }
                        ?.stdout
                        ?.joinToString("\n")
                        ?.let(::extractRegSecFromRegistrationXml)
                }
                .firstOrNull()

            if (secret != null) {
                Utils.setRegSec(packageName, secret, normalizedUserId)
                regSecRecoveryMisses.remove(cacheKey)
                logI { "recovered local regSec pkg=$packageName userId=$normalizedUserId" }
                return secret
            }

            regSecRecoveryMisses[cacheKey] = now
            logD { "local regSec unavailable pkg=$packageName userId=$normalizedUserId" }
            return null
        }
    }

    internal fun extractRegSecFromRegistrationXml(content: String): String? {
        val values = parseRegistrationXml(content) ?: return null
        return values["regSec"]
            ?.takeIf { value ->
                value.length in 8..MAX_REG_SEC_LENGTH && value.all { char ->
                    char.isLetterOrDigit() || char in "+/=_-"
                }
            }
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
        logI {
            "local registration details pkg=$packageName mode=${if (useSu) "su" else "shell"} path=$path " +
                "xmlValid=${markers.hasXmlValid} xmlRegId=${markers.hasXmlRegId} xmlRegSec=${markers.hasXmlRegSec} " +
                "kevaValid=${markers.hasKevaValid} kevaRegId=${markers.hasKevaRegId} " +
                "kevaRegSec=${markers.hasKevaRegSec} kevaAppToken=${markers.hasKevaAppToken} " +
                "regIdPresent=${markers.regId != null} appTokenPresent=${markers.appToken != null}"
        }
    }

    private fun parseRegistrationMarkers(content: String): RegistrationMarkers {
        val sanitized = content.toPrintableDiagnosticText()
        val hasXmlValid = content.contains(VALID_PATTERN)
        val hasXmlRegId = content.contains(REG_ID_TAG_PATTERN) || content.contains(REG_ID_VALUE_PATTERN)
        val hasXmlRegSec = content.contains(REG_SEC_TAG_PATTERN) || content.contains(REG_SEC_VALUE_PATTERN)
        val hasKevaValid = content.contains(KEVA_VALID_PATTERN)
        val hasKevaRegId = content.contains(KEVA_REG_ID_PATTERN)
        val hasKevaRegSec = content.contains(KEVA_REG_SEC_PATTERN)
        val hasKevaAppToken = content.contains(KEVA_APP_TOKEN_PATTERN)
        return RegistrationMarkers(
            hasXmlValid = hasXmlValid,
            hasXmlRegId = hasXmlRegId,
            hasXmlRegSec = hasXmlRegSec,
            hasKevaValid = hasKevaValid,
            hasKevaRegId = hasKevaRegId,
            hasKevaRegSec = hasKevaRegSec,
            hasKevaAppToken = hasKevaAppToken,
            regId = extractRegistrationValue(content, sanitized, "regId"),
            appToken = extractRegistrationValue(content, sanitized, "appToken")
        )
    }

    private fun extractRegistrationValue(content: String, sanitized: String, key: String): String? {
        extractXmlRegistrationValue(content, key)?.let { return it.truncateForDiagnostic() }
        Regex("""\b$key\b[^A-Za-z0-9]{0,24}([A-Za-z0-9._:-]{6,128})""")
            .find(sanitized)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it.truncateForDiagnostic() }
        return null
    }

    private fun extractXmlRegistrationValue(content: String, key: String): String? {
        val xmlPatterns = listOf(
            Regex("""name="$key"\s+value="([^"]+)""""),
            Regex("""name="$key"\s*>([^<\n\r]+)</string>""")
        )
        return xmlPatterns.firstNotNullOfOrNull { regex ->
            regex.find(content)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }
    }

    private fun parseRegistrationXml(content: String): Map<String, String>? {
        if (content.isBlank() || content.length > MAX_REGISTRATION_XML_LENGTH || content.contains("<!DOCTYPE", ignoreCase = true)) {
            return null
        }
        return runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isExpandEntityReferences = false
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            }
            val document = factory.newDocumentBuilder().parse(InputSource(StringReader(content)))
            val values = linkedMapOf<String, String>()
            val nodes = document.documentElement?.childNodes ?: return@runCatching emptyMap()
            for (index in 0 until nodes.length) {
                val node = nodes.item(index)
                val name = node.attributes?.getNamedItem("name")?.nodeValue ?: continue
                val value = node.attributes?.getNamedItem("value")?.nodeValue ?: node.textContent
                values[name] = value.trim()
            }
            values
        }.getOrNull()
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

    private fun registrationArtifactPaths(packageName: String): List<String> {
        return registrationArtifactPathsForUser(packageName, Utils.requireValidUserId(Utils.myUserId()))
    }

    internal fun registrationArtifactPathsForUser(packageName: String, userId: Int): List<String> {
        if (!packageName.matches(SAFE_PACKAGE_NAME)) return emptyList()
        return registrationBasePathsForUser(userId).flatMap { base ->
            listOf(
                "$base/$packageName/shared_prefs/mipush.xml",
                "$base/$packageName/files/keva/repo/mipush/mipush.blk",
            )
        }
    }

    private fun registrationBasePathsForUser(userId: Int): List<String> {
        if (userId < 0) return emptyList()
        return listOf(
            "/data/user/$userId",
            "/data_mirror/data_ce/null/$userId",
        )
    }
}
