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
     * Probe the registry for the newest runtime compatible with this host
     * and compare it against the installed one. Pure check: never installs,
     * never mutates the status flow.
     *
     * github: anonymous GitLab package listing plus manifest gating.
     * play: [XmsfLatestCheck.Unsupported] — the play distribution never
     * touches the registry.
     */
    suspend fun checkLatestRuntime(): XmsfLatestCheck

    /**
     * Install or update the runtime. github: download, verify and install,
     * reporting progress 0..100 through [onProgress]. play: open the release
     * guidance page.
     *
     * @param targetVersionName registry version to install; null installs the
     *   version this build was pinned against. Ignored by the play flavor,
     *   which routes every install through the guidance page.
     *
     * Threading: [onProgress] may be invoked from a background dispatcher
     * (the downloader verifies on [kotlinx.coroutines.Dispatchers.IO]); hop to
     * the main dispatcher before touching UI state.
     */
    suspend fun ensureInstalled(
        targetVersionName: String? = null,
        onProgress: (Int) -> Unit = {},
    ): XmsfInstallResult

    /** Open the manual guidance page (release listing) in the browser. */
    fun openGuidance(context: Context)
}
