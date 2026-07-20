package com.xiaomi.xmsf.security

import android.net.Uri
import android.os.Bundle
import io.github.magisk317.xposed.logging.PackageCallerGuard

internal object ExportedSurfacePolicy {
    const val PUSH_CONTROL_AUTHORITY = "com.xiaomi.xmsf.pushcontrol.PushControlProvider"
    const val PUSH_CONTROL_PATH = "control"

    private const val MAX_PACKAGE_NAME_LENGTH = 255
    private val pushControlColumns = setOf(
        "control_mode",
        "key_words",
        "special_pkg_names",
        "control_switch",
    )

    fun isPushControlQueryAllowed(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Boolean {
        return uri.scheme == "content" &&
            uri.authority == PUSH_CONTROL_AUTHORITY &&
            uri.pathSegments == listOf(PUSH_CONTROL_PATH) &&
            uri.query == null &&
            uri.fragment == null &&
            projection.orEmpty().all(pushControlColumns::contains) &&
            selection == null &&
            selectionArgs.isNullOrEmpty() &&
            sortOrder == null
    }

    fun isPushCommonCallAllowed(method: String, arg: String?, extras: Bundle?): Boolean {
        if (method != "is_push_support" || !arg.isNullOrEmpty()) return false
        if (extras == null) return true
        return runCatching {
            extras.keySet() == setOf("push_support_flag") &&
                extras.rawValue("push_support_flag") is Int
        }.getOrDefault(false)
    }

    fun resolveProfilePackageName(
        requestedPackage: String?,
        callingUid: Int,
        appUid: Int?,
        packagesForUid: Collection<String>,
    ): String? {
        val requested = requestedPackage?.takeIf(::isValidPackageName)
        if (isPrivilegedUid(callingUid, appUid)) return requested
        if (requested != null) return requested.takeIf(packagesForUid::contains)
        return packagesForUid.singleOrNull()?.takeIf(::isValidPackageName)
    }

    fun isPrivilegedUid(uid: Int, appUid: Int?): Boolean {
        return PackageCallerGuard.isPrivilegedUid(uid, appUid)
    }

    private fun isValidPackageName(packageName: String): Boolean {
        return packageName.isNotBlank() &&
            packageName.length <= MAX_PACKAGE_NAME_LENGTH &&
            packageName.none { it == '/' || it == '\u0000' || it.isWhitespace() }
    }

    @Suppress("DEPRECATION")
    private fun Bundle.rawValue(key: String): Any? = get(key)
}
