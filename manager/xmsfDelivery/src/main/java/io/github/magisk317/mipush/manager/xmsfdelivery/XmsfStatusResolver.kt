package io.github.magisk317.mipush.manager.xmsfdelivery

import android.content.Context
import android.content.pm.PackageManager
import io.github.magisk317.mipush.manager.api.ManagerProtocol

/**
 * Reads the installation state of the XMSF runtime package through the
 * platform [PackageManager] and turns it into an [XmsfRuntimeState].
 *
 * All state transitions live in the pure [decideStatus] function so the
 * decision table is unit-testable without a device; this class only supplies
 * the platform inputs.
 */
class XmsfStatusResolver(private val context: Context) {

    /** Installation snapshot of the runtime package, or null when absent. */
    data class Installed(
        val versionName: String?,
        val versionCode: Long,
        val signerHashes: List<String>,
    )

    @Suppress("DEPRECATION")
    fun queryInstalled(): Installed? {
        val packageManager = context.packageManager
        val info = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(
                    ManagerProtocol.RUNTIME_PACKAGE,
                    PackageManager.GET_SIGNING_CERTIFICATES,
                )
            } else {
                packageManager.getPackageInfo(
                    ManagerProtocol.RUNTIME_PACKAGE,
                    PackageManager.GET_SIGNATURES,
                )
            }
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
        // Extract signer hashes from the same PackageInfo we just read: a
        // second query would re-open a TOCTOU window where the package
        // disappears between the two calls and the signer set reads empty
        // (which the decision table would report as SIGNATURE_MISMATCH).
        val signerHashes = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.toList().orEmpty().map { it.toCharsString() }
        } else {
            info.signatures?.toList().orEmpty().map { it.toCharsString() }
        }
        return Installed(
            versionName = info.versionName,
            versionCode = versionCode,
            signerHashes = signerHashes,
        )
    }

    /** Resolve the coarse runtime state; never probes connectivity. */
    fun resolveState(expectedVersionName: String): XmsfRuntimeState {
        val installed = queryInstalled() ?: return XmsfRuntimeState.NOT_INSTALLED
        return decideStatus(
            installed = installed,
            hostHashes = XmsfSignatureVerifier.installedSignerHashes(
                context.packageManager,
                context.packageName,
            ),
            expectedVersionName = expectedVersionName,
        )
    }

    companion object {

        /**
         * Decision table:
         * - absent package -> [XmsfRuntimeState.NOT_INSTALLED]
         * - certificates differ from the host -> [XmsfRuntimeState.SIGNATURE_MISMATCH]
         * - installed runtime older than [expectedVersionName] (semver-ish,
         *   via [XmsfVersionComparator]) -> [XmsfRuntimeState.UPDATE_AVAILABLE]
         * - otherwise -> [XmsfRuntimeState.READY]
         *
         * The signature gate runs before the version gate on purpose: a
         * foreign-signed runtime is a hard failure no matter which version
         * it reports, and reporting UPDATE_AVAILABLE for it would invite
         * the user to "update" a package the manager must never touch.
         *
         * The version gate orders with [XmsfVersionComparator] instead of
         * exact string equality: a runtime ahead of the pin (the user
         * updated to the registry latest) is still READY, while a CI
         * pre-release below the pin ("1.0.9-20261016_120000" against pin
         * "1.0.9") correctly reports UPDATE_AVAILABLE.
         */
        fun decideStatus(
            installed: Installed?,
            hostHashes: List<String>,
            expectedVersionName: String?,
        ): XmsfRuntimeState {
            if (installed == null) return XmsfRuntimeState.NOT_INSTALLED
            if (!XmsfSignatureVerifier.matches(hostHashes, installed.signerHashes)) {
                return XmsfRuntimeState.SIGNATURE_MISMATCH
            }
            if (expectedVersionName != null &&
                XmsfVersionComparator.isNewer(expectedVersionName, installed.versionName.orEmpty())
            ) {
                return XmsfRuntimeState.UPDATE_AVAILABLE
            }
            return XmsfRuntimeState.READY
        }
    }
}
