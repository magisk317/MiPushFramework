package com.magisk317.compat

import com.elvishew.xlog.XLog
import com.topjohnwu.superuser.Shell

object RegistrationStateCompat {
    private val logger = XLog.tag("RegistrationStateCompat").build()
    private const val VALID_PATTERN = "name=\"valid\" value=\"true\""
    private const val REG_ID_TAG_PATTERN = "name=\"regId\">"
    private const val REG_ID_VALUE_PATTERN = "name=\"regId\" value=\""

    @JvmStatic
    fun hasValidLocalRegistration(packageName: String): Boolean {
        val paths = listOf(
            "/data/user/0/$packageName/shared_prefs/mipush.xml",
            "/data_mirror/data_ce/null/0/$packageName/shared_prefs/mipush.xml"
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
                "grep -q '$VALID_PATTERN' $path && " +
                "(grep -q '$REG_ID_TAG_PATTERN' $path || grep -q '$REG_ID_VALUE_PATTERN' $path) && " +
                "echo true || echo false"
        val result = if (useSu) {
            runCatching { Shell.cmd("su --mount-master -c '$cmd'").exec() }.getOrElse {
                Shell.cmd("su -c '$cmd'").exec()
            }
        } else {
            Shell.cmd(cmd).exec()
        }
        return result.out.firstOrNull()?.trim() == "true"
    }

    private fun probeByRead(path: String, useSu: Boolean): Boolean {
        val cmd = "[ -f $path ] && cat $path || true"
        val out = runCatching {
            if (useSu) {
                runCatching { Shell.cmd("su --mount-master -c '$cmd'").exec().out }
                    .getOrElse { Shell.cmd("su -c '$cmd'").exec().out }
            } else {
                Shell.cmd(cmd).exec().out
            }
        }.getOrNull() ?: return false
        return containsRegistrationMarkers(out.joinToString("\n"))
    }

    private fun containsRegistrationMarkers(content: String): Boolean {
        if (content.isBlank()) return false
        val hasValid = content.contains("name=\"valid\" value=\"true\"")
        if (!hasValid) return false
        return content.contains("name=\"regId\">") || content.contains("name=\"regId\" value=\"")
    }

    @JvmStatic
    fun findPackagesWithValidLocalRegistration(packages: Collection<String>): Set<String> {
        if (packages.isEmpty()) return emptySet()
        val result = linkedSetOf<String>()
        val uid = runCatching { Shell.cmd("id -u").exec().out.firstOrNull()?.trim() }.getOrNull()
        logger.d("find local registration start: queried=${packages.size}, shell uid=$uid")
        val chunks = packages.chunked(60)
        for (chunk in chunks) {
            val safePackages = chunk.filter { it.matches(Regex("[A-Za-z0-9._]+")) }
            if (safePackages.isEmpty()) continue
            val script = buildString {
                append("for pkg in ")
                append(safePackages.joinToString(" "))
                append("; do ")
                append("for base in /data/user/0 /data_mirror/data_ce/null/0; do ")
                append("f=\\\"${'$'}base/${'$'}pkg/shared_prefs/mipush.xml\\\"; ")
                append("if [ -f \\\"${'$'}f\\\" ] && ")
                append("grep -q 'name=\"valid\" value=\"true\"' \\\"${'$'}f\\\" && ")
                append("(grep -q 'name=\"regId\">' \\\"${'$'}f\\\" || grep -q 'name=\"regId\" value=\"' \\\"${'$'}f\\\"); then ")
                append("echo ${'$'}pkg; break 2; fi; ")
                append("done; ")
                append("done")
            }
            val out = runCatching {
                val escaped = script.replace("\"", "\\\"")
                Shell.cmd("su --mount-master -c \"$escaped\"").exec().out
            }.getOrNull()
            if (!out.isNullOrEmpty()) {
                result += out.map { it.trim() }.filter { it.isNotEmpty() }
                continue
            }
            val suOut = runCatching {
                val escaped = script.replace("\"", "\\\"")
                Shell.cmd("su -c \"$escaped\"").exec().out
            }.getOrNull()
            if (!suOut.isNullOrEmpty()) {
                result += suOut.map { it.trim() }.filter { it.isNotEmpty() }
                continue
            }
            val fallbackOut = runCatching { Shell.cmd(script).exec().out }.getOrNull()
            if (fallbackOut != null) {
                result += fallbackOut.map { it.trim() }.filter { it.isNotEmpty() }
            }
        }
        if (result.isEmpty()) {
            for (pkg in packages) {
                if (hasValidLocalRegistration(pkg)) {
                    result += pkg
                }
            }
        }
        logger.d("find local registration: queried=${packages.size}, matched=${result.size}")
        return result
    }
}
