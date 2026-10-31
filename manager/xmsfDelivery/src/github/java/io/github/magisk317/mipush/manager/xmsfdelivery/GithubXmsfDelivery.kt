package io.github.magisk317.mipush.manager.xmsfdelivery

import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * github flavor delivery: the full chain.
 *
 * download (variant channel, target version, sha256 gated) -> signature check
 * on the APK file -> PackageInstaller with system confirmation -> post-install
 * verification (signature against the *installed* package, version contract,
 * Binder connectivity). Any failure leaves the previous state untouched and is
 * surfaced as a message; the manual guidance page remains the fallback.
 *
 * The runtime is signed by the same release key as this app, so a
 * signature-verified APK can only ever be one this project published.
 */
class GithubXmsfDelivery(
    context: Context,
    scope: CoroutineScope,
    /**
     * One downloader per (variant, version) pair: the channel is keyed by the
     * variant a device runs, which only the installed package can say, so no
     * downloader can be built once at construction time.
     */
    private val registryDownloaderFactory: (XmsfRuntimeFlavor, String) -> XmsfDownloader =
        { flavor, version -> XmsfDownloader(context, expectedVersionName = version, flavor = flavor) },
    private val ciDownloaderFactory: (XmsfRuntimeFlavor, String) -> XmsfDownloader =
        { flavor, version ->
            XmsfDownloader(
                context,
                expectedVersionName = version,
                flavor = flavor,
                baseUrl = XmsfCiArtifactProbe.payloadBaseUrlFor(flavor),
                requestQuery = XmsfCiArtifactProbe.CI_JOB_QUERY,
            )
        },
    private val registryProbeFactory: (XmsfRuntimeFlavor) -> XmsfRuntimeLatestProbe =
        { flavor -> XmsfRuntimeLatestProbe(flavor) },
    private val ciProbeFactory: (XmsfRuntimeFlavor) -> XmsfCiArtifactProbe =
        { flavor -> XmsfCiArtifactProbe(flavor) },
    /** Channel gate read on every probe; runs on background dispatchers. */
    private val trackCi: () -> Boolean = { false },
) : AbstractXmsfDelivery(context, scope) {

    private val installer = XmsfApkInstaller(appContext)
    private val mutex = Mutex()

    /**
     * The probe chain mirrors the manager's own OTA sources; the CI artifacts
     * track (freshest runtime, 14-day window) only joins when the channel gate
     * opens, otherwise the registry release track answers alone.
     */
    private fun probeChain(flavor: XmsfRuntimeFlavor): XmsfLatestProbeChain {
        val gate = trackCi()
        Log.i(TAG, "runtime channel: trackCi=$gate variant=${flavor.label}")
        return XmsfLatestProbeChain(
            ciArtifacts = if (gate) ciProbeFactory(flavor) else null,
            registry = registryProbeFactory(flavor),
        )
    }

    /**
     * Version name of the runtime the last [checkLatestRuntime] resolved from
     * the CI artifacts track, or null when the registry track answered. A
     * download of exactly that version must come from the CI artifacts base
     * URL: the registry 404s for CI version names -- which is also what a CI
     * build pins, so a pin target is only trustworthy once the probe answered.
     */
    @Volatile
    private var ciTrackVersionName: String? = null

    override val expectedVersionName: String get() = BuildConfig.XMSF_DELIVERY_VERSION

    override val canInstallDirectly: Boolean get() = true

    /**
     * Newest runtime this host may install from either track, compared against
     * the installed one. Read-only: the status flow keeps describing the pin,
     * and a runtime ahead of the pin still counts as READY there.
     */
    override suspend fun checkLatestRuntime(): XmsfLatestCheck = withContext(Dispatchers.IO) {
        val installed = statusResolver.queryInstalled()
        val latest = runCatching {
            probeChain(XmsfRuntimeFlavor.forVersionCode(installed?.versionCode))
                .fetchLatestCompatible(XmsfDownloader.hostVersionCode(appContext))
        }.getOrElse { error ->
            return@withContext XmsfLatestCheck.Failed(error.describeFailure())
        }
        // Remember which track answered so ensureInstalled picks the matching
        // base URL; a registry answer clears the CI target.
        ciTrackVersionName = latest.manifest.versionName.takeIf { latest.fromCiArtifacts }
        val hasNewer = installed == null ||
            XmsfVersionComparator.isNewer(latest.manifest.versionName, installed.versionName.orEmpty())
        if (hasNewer) XmsfLatestCheck.Available(latest.manifest) else XmsfLatestCheck.UpToDate
    }

    override suspend fun ensureInstalled(
        targetVersionName: String?,
        onProgress: (Int) -> Unit,
    ): XmsfInstallResult {
        // Without an explicit target, ask the probe rather than assuming the
        // build pin is downloadable: a CI pin names a version that only exists
        // in the CI staging area, and the probe is what resolves which track
        // actually holds a given version.
        val target = targetVersionName
            ?: (checkLatestRuntime() as? XmsfLatestCheck.Available)?.latest?.versionName
            ?: expectedVersionName
        // The variant decides the channel: a vc105 device stays on vc105, where
        // both tracks publish the same versionName and only the version code
        // tells them apart.
        val flavor = XmsfRuntimeFlavor.forVersionCode(statusResolver.queryInstalled()?.versionCode)
        val installedVersion = status.value.installedVersionName
        if (status.value.state == XmsfRuntimeState.READY &&
            installedVersion != null &&
            !XmsfVersionComparator.isNewer(target, installedVersion)
        ) {
            onProgress(PROGRESS_COMPLETE)
            return XmsfInstallResult.Installed()
        }
        if (!installer.canInstall) {
            // User turned off "install unknown apps" for this app: the only
            // honest move is the manual path, not a silent failure.
            XmsfGuidance.openReleasePage(appContext)
            return XmsfInstallResult.GuidanceOpened
        }
        return mutex.withLock {
            // A target ahead of the build pin installs a version the pin never
            // named, so it needs its own downloader: the pin is baked into the
            // manifest gate and the cache directory. CI-track targets come from
            // the staging area instead of the registry, which 404s for them.
            val effectiveDownloader = if (target == ciTrackVersionName) {
                ciDownloaderFactory(flavor, target)
            } else {
                registryDownloaderFactory(flavor, target)
            }
            var runtimeCommit: String? = null
            val failure = runCatching {
                val downloaded = effectiveDownloader.download(onProgress)
                runtimeCommit = downloaded.manifest.commit
                val apkHashes = XmsfSignatureVerifier.apkSignerHashes(
                    appContext.packageManager,
                    downloaded.apk,
                )
                XmsfSignatureVerifier.verify(
                    appContext,
                    XmsfSignatureSource.ApkFile(downloaded.apk.absolutePath, apkHashes),
                )
                installAndConfirm(downloaded.apk)
            }.exceptionOrNull()
            if (failure == null) {
                // Verify against the requested target, not the build pin:
                // updating to a newer track deliberately installs a version
                // ahead of what this build was pinned against.
                val verified = statusResolver.resolveState(target)
                if (verified == XmsfRuntimeState.READY) {
                    refresh()
                    XmsfInstallResult.Installed(commitNote(runtimeCommit))
                } else {
                    XmsfInstallResult.Failed("runtime installed but verification failed: $verified")
                }
            } else {
                val message = failure.describeFailure()
                publishStatus(detail = message)
                XmsfInstallResult.Failed(message)
            }
        }
    }

    /**
     * Note for a runtime built from another commit than this host, or null when
     * both sides agree or either side cannot say.
     *
     * Both commits of a CI track share one version name, so neither the pin nor
     * the comparator can see the difference; a user who reports a problem later
     * still needs to be able to say which source tree each side came from.
     */
    private fun commitNote(runtimeCommit: String?): String? {
        val host = BuildConfig.COMMIT_HASH.takeIf { it.isNotBlank() && it != "unknown" } ?: return null
        val runtime = runtimeCommit?.takeIf { it.isNotBlank() && it != "unknown" } ?: return null
        return if (runtime == host) null else "runtime commit $runtime != manager commit $host"
    }

    override fun openGuidance(context: Context) {
        XmsfGuidance.openReleasePage(context)
    }

    /**
     * Commit the verified APK and surface the system confirmation UI when the
     * installer asks for it. Suspends until a terminal installer event;
     * throws on failure so the caller reports a single message.
     */
    private suspend fun installAndConfirm(apk: File) = withContext(Dispatchers.Main) {
        when (val event = installer.install(apk) { confirmation -> startConfirmation(confirmation) }) {
            is XmsfInstallEvent.Success -> Unit
            is XmsfInstallEvent.Failed -> error(event.message)
        }
    }

    private fun startConfirmation(intent: Intent) {
        val confirmation = Intent(intent).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(confirmation)
    }

    companion object {
        private const val TAG = "XmsfDelivery"
        private const val PROGRESS_COMPLETE = 100
    }
}
