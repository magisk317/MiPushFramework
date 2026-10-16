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
        val signerHashes = XmsfSignatureVerifier.installedSignerHashes(
            packageManager,
            ManagerProtocol.RUNTIME_PACKAGE,
        )
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
         * - version differs from the pinned runtime -> [XmsfRuntimeState.UPDATE_AVAILABLE]
         * - otherwise -> [XmsfRuntimeState.READY]
         *
         * The signature gate runs before the version gate on purpose: a
         * foreign-signed runtime is a hard failure no matter which version
         * it reports, and reporting UPDATE_AVAILABLE for it would invite
         * the user to "update" a package the manager must never touch.
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
            if (expectedVersionName != null && installed.versionName != expectedVersionName) {
                return XmsfRuntimeState.UPDATE_AVAILABLE
            }
            return XmsfRuntimeState.READY
        }
    }
}
