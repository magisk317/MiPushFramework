package com.xiaomi.xmsf.security

import io.github.magisk317.xposed.logging.PackageCallerGuard

internal object ExportedSurfacePolicy {
    private const val MAX_PACKAGE_NAME_LENGTH = 255

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
}
