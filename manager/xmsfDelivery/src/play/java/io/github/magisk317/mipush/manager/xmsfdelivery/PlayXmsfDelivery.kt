package io.github.magisk317.mipush.manager.xmsfdelivery

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * play flavor delivery: guidance plus post-install verification.
 *
 * The play distribution never handles an APK — the user is sent to the
 * GitHub release page and installs the runtime themselves. Once they return,
 * [refresh] re-runs the same shared verification chain the github flavor uses
 * after its in-process install, so both distributions agree on what "ready"
 * means.
 */
class PlayXmsfDelivery(context: Context) :
    AbstractXmsfDelivery(context, CoroutineScope(Dispatchers.Main.immediate)) {

    override val expectedVersionName: String get() = BuildConfig.XMSF_DELIVERY_VERSION

    override val canInstallDirectly: Boolean get() = false

    override suspend fun checkLatestRuntime(): XmsfLatestCheck = XmsfLatestCheck.Unsupported

    override suspend fun ensureInstalled(
        targetVersionName: String?,
        onProgress: (Int) -> Unit,
    ): XmsfInstallResult {
        // The play distribution never handles an APK, so the requested
        // target is irrelevant: every install routes through the release
        // guidance page.
        onProgress(PROGRESS_COMPLETE)
        XmsfGuidance.openReleasePage(appContext)
        return XmsfInstallResult.GuidanceOpened
    }

    override fun openGuidance(context: Context) {
        XmsfGuidance.openReleasePage(context)
    }

    companion object {
        private const val PROGRESS_COMPLETE = 100
    }
}
