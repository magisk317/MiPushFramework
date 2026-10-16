package io.github.magisk317.mipush.manager.xmsfdelivery

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Flavor-agnostic plumbing shared by both delivery implementations: the
 * installation-state snapshot consumed by the settings UI, plus the
 * post-install verification chain (state, then Binder connectivity once the
 * runtime is READY).
 *
 * The snapshot is computed eagerly at construction so the UI never renders a
 * blank state on first frame.
 */
abstract class AbstractXmsfDelivery(
    context: Context,
    private val scope: CoroutineScope,
) : XmsfDelivery {

    protected val appContext: Context = context.applicationContext ?: context
    protected val statusResolver: XmsfStatusResolver = XmsfStatusResolver(appContext)

    private val _status = MutableStateFlow(
        currentStatus(resolveState(), statusResolver.queryInstalled()),
    )

    override val status: StateFlow<XmsfStatus> = _status.asStateFlow()

    /**
     * Re-probe the installation state. PackageManager queries and the Binder
     * connectivity probe run on [Dispatchers.IO] so a settings screen can call
     * this from the main dispatcher without blocking it.
     */
    override suspend fun refresh(): XmsfStatus = withContext(Dispatchers.IO) {
        val state = resolveState()
        val installed = statusResolver.queryInstalled()
        val connectivity = if (state == XmsfRuntimeState.READY) {
            XmsfConnectivityProbe(appContext, scope).probe()
        } else {
            XmsfConnectivity.UNKNOWN
        }
        val next = currentStatus(state, installed, connectivity)
        _status.value = next
        next
    }

    protected fun resolveState(): XmsfRuntimeState =
        statusResolver.resolveState(expectedVersionName)

    /** The runtime version this host build was pinned against. */
    protected abstract val expectedVersionName: String

    protected fun publishStatus(
        state: XmsfRuntimeState = _status.value.state,
        connectivity: XmsfConnectivity = _status.value.connectivity,
        detail: String? = _status.value.detail,
    ): XmsfStatus {
        val installed = statusResolver.queryInstalled()
        val next = XmsfStatus(
            state = state,
            installedVersionName = installed?.versionName,
            installedVersionCode = installed?.versionCode,
            expectedVersionName = expectedVersionName,
            connectivity = connectivity,
            detail = detail,
        )
        _status.value = next
        return next
    }

    private fun currentStatus(
        state: XmsfRuntimeState,
        installed: XmsfStatusResolver.Installed?,
        connectivity: XmsfConnectivity = XmsfConnectivity.UNKNOWN,
    ): XmsfStatus = XmsfStatus(
        state = state,
        installedVersionName = installed?.versionName,
        installedVersionCode = installed?.versionCode,
        expectedVersionName = expectedVersionName,
        connectivity = connectivity,
    )
}
