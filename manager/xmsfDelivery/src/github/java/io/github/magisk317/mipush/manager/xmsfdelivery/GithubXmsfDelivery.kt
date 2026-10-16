package io.github.magisk317.mipush.manager.xmsfdelivery

import android.content.Context
import android.content.Intent
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * github flavor delivery: the full chain.
 *
 * download (target version, sha256 gated) -> signature check on the APK file
 * -> PackageInstaller with system confirmation -> post-install verification
 * (signature against the *installed* package, version contract, Binder
 * connectivity). Any failure leaves the previous state untouched and is
 * surfaced as a message; the manual guidance page remains the fallback.
 *
 * The runtime is signed by the same release key as this app, so a
 * signature-verified APK can only ever be one this project published.
 */
class GithubXmsfDelivery(
    context: Context,
    scope: CoroutineScope,
    private val downloader: XmsfDownloader = XmsfDownloader(context),
    private val latestProbe: XmsfRuntimeLatestProbe = XmsfRuntimeLatestProbe(),
) : AbstractXmsfDelivery(context, scope) {

    private val installer = XmsfApkInstaller(appContext)
    private val mutex = Mutex()

    override val expectedVersionName: String get() = BuildConfig.XMSF_DELIVERY_VERSION

    override val canInstallDirectly: Boolean get() = true

    /**
     * Newest registry runtime this host may install, compared against the
     * installed one. Read-only: the status flow keeps describing the pin,
     * and a runtime ahead of the pin still counts as READY there.
     */
    override suspend fun checkLatestRuntime(): XmsfLatestCheck = withContext(Dispatchers.IO) {
        val latest = runCatching {
            latestProbe.fetchLatestCompatible(XmsfDownloader.hostVersionCode(appContext))
        }.getOrElse { error ->
            return@withContext XmsfLatestCheck.Failed(error.describeFailure())
        }
        val installed = statusResolver.queryInstalled()
        val hasNewer = installed == null ||
            XmsfVersionComparator.isNewer(latest.versionName, installed.versionName.orEmpty())
        if (hasNewer) XmsfLatestCheck.Available(latest) else XmsfLatestCheck.UpToDate
    }

    override suspend fun ensureInstalled(
        targetVersionName: String?,
        onProgress: (Int) -> Unit,
    ): XmsfInstallResult {
        val target = targetVersionName ?: expectedVersionName
        val installedVersion = status.value.installedVersionName
        if (status.value.state == XmsfRuntimeState.READY &&
            installedVersion != null &&
            !XmsfVersionComparator.isNewer(target, installedVersion)
        ) {
            onProgress(PROGRESS_COMPLETE)
            return XmsfInstallResult.Installed
        }
        if (!installer.canInstall) {
            // User turned off "install unknown apps" for this app: the only
            // honest move is the manual path, not a silent failure.
            XmsfGuidance.openReleasePage(appContext)
            return XmsfInstallResult.GuidanceOpened
        }
        return mutex.withLock {
            // A registry-latest target installs a version ahead of the build
            // pin, so it needs its own downloader: the pin is baked into the
            // manifest gate and the cache directory.
            val effectiveDownloader = if (target == expectedVersionName) {
                downloader
            } else {
                XmsfDownloader(appContext, expectedVersionName = target)
            }
            val failure = runCatching {
                val downloaded = effectiveDownloader.download(onProgress)
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
                // updating to the registry latest deliberately installs a
                // version ahead of what this build was pinned against.
                val verified = statusResolver.resolveState(target)
                if (verified == XmsfRuntimeState.READY) {
                    refresh()
                    XmsfInstallResult.Installed
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
        private const val PROGRESS_COMPLETE = 100
    }
}
