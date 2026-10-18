package io.github.magisk317.mipush.manager.xmsfdelivery

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

/**
 * Flavor-agnostic entry point for XMSF runtime delivery.
 *
 * github flavor: the full chain — download, sha256 check, same-signature
 * check, PackageInstaller with system confirmation UI, then post-install
 * verification (signature against the installed package, version contract,
 * Binder connectivity).
 *
 * play flavor: guidance only — a deep link to the GitHub release page; the app
 * never handles the APK. Once the user returns, [refresh] re-runs the shared
 * verification chain.
 */
interface XmsfDelivery {

    val status: StateFlow<XmsfStatus>

    /** Re-probe the installation state; also probes connectivity when READY. */
    suspend fun refresh(): XmsfStatus

    /** Whether this flavor can download and install the runtime in-process. */
    val canInstallDirectly: Boolean

    /**
     * Install or update the runtime. github: download, verify and install,
     * reporting progress 0..100 through [onProgress]. play: open the release
     * guidance page.
     *
     * Threading: [onProgress] may be invoked from a background dispatcher
     * (the downloader verifies on [kotlinx.coroutines.Dispatchers.IO]); hop to
     * the main dispatcher before touching UI state.
     */
    suspend fun ensureInstalled(onProgress: (Int) -> Unit = {}): XmsfInstallResult

    /** Open the manual guidance page (release listing) in the browser. */
    fun openGuidance(context: Context)
}
