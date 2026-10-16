package io.github.magisk317.mipush.manager.xmsfdelivery

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

/** Where a candidate set of APK signing certificates was read from. */
sealed interface XmsfSignatureSource {

    /** Certificate hashes, one per signer, in platform order. */
    val signerHashes: List<String>

    /** An APK file that has not been installed yet (download cache). */
    data class ApkFile(
        val path: String,
        override val signerHashes: List<String>,
    ) : XmsfSignatureSource

    /** An already installed package (post-install verification). */
    data class InstalledPackage(
        val packageName: String,
        override val signerHashes: List<String>,
    ) : XmsfSignatureSource
}

/**
 * Same-signature enforcement between the host manager app and the XMSF
 * runtime, modelled on the xinyi E2EE plugin loader: the runtime must carry
 * exactly the host's release certificate, checked before any runtime code is
 * trusted (github flavor, on the downloaded APK) and after installation
 * (both flavors, on the installed package).
 *
 * A stock or third-party XMSF signed by Xiaomi's platform certificate is a
 * [XmsfRuntimeState.SIGNATURE_MISMATCH], never silently accepted.
 */
object XmsfSignatureVerifier {

    /**
     * Pure comparison. An empty side is never a match: the host must always
     * be able to produce its own certificates, and an unreadable candidate is
     * a failure, not a pass.
     */
    fun matches(host: List<String>, candidate: List<String>): Boolean {
        if (host.isEmpty() || candidate.isEmpty()) return false
        return host.sorted() == candidate.sorted()
    }

    /**
     * Verify [source] against the host certificates. Throws
     * [IllegalStateException] with a cause chain on mismatch; callers turn
     * that into a user-visible message via [describeFailure].
     */
    fun verify(context: Context, source: XmsfSignatureSource) {
        val hostHashes = hostSignerHashes(context)
        check(hostHashes.isNotEmpty()) { "host signatures unavailable" }
        check(matches(hostHashes, source.signerHashes)) {
            "runtime signature does not match host app (${source.describe()})"
        }
    }

    /** Read the signing certificates of an APK file on disk. */
    @Suppress("DEPRECATION")
    fun apkSignerHashes(packageManager: PackageManager, apk: File): List<String> {
        val path = apk.absolutePath
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info: PackageInfo? = packageManager.getPackageArchiveInfo(
                path,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
            info?.signingInfo?.apkContentsSigners?.toList().orEmpty().map { it.toCharsString() }
        } else {
            val info: PackageInfo? = packageManager.getPackageArchiveInfo(
                path,
                PackageManager.GET_SIGNATURES,
            )
            info?.signatures?.toList().orEmpty().map { it.toCharsString() }
        }
    }

    /** Read the signing certificates of an installed package. */
    @Suppress("DEPRECATION")
    fun installedSignerHashes(packageManager: PackageManager, packageName: String): List<String> {
        val info: PackageInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            return emptyList()
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.toList().orEmpty().map { it.toCharsString() }
        } else {
            info.signatures?.toList().orEmpty().map { it.toCharsString() }
        }
    }

    private fun hostSignerHashes(context: Context): List<String> =
        installedSignerHashes(context.packageManager, context.packageName)

    private fun XmsfSignatureSource.describe(): String = when (this) {
        is XmsfSignatureSource.ApkFile -> "apk=$path"
        is XmsfSignatureSource.InstalledPackage -> "package=$packageName"
    }
}

/**
 * Walk the cause chain to surface the real error. Package archive parsing and
 * reflection wrap whatever actually broke in exceptions whose own message is
 * null, so a plain `error.message` would produce an empty dialog.
 */
internal fun Throwable.describeFailure(): String = buildString {
    var current: Throwable? = this@describeFailure
    var depth = 0
    while (current != null && depth < MAX_CAUSE_DEPTH) {
        if (isNotEmpty()) append(" <- ")
        append(current.javaClass.name)
        current.message?.let { append(": ").append(it) }
        current = current.cause
        depth++
    }
    if (current != null) append(" <- ...")
}

private const val MAX_CAUSE_DEPTH = 8
