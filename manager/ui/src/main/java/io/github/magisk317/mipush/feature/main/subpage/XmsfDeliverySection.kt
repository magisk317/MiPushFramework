@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.magisk317.mipush.feature.main.subpage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.mipush.manager.xmsfdelivery.XmsfConnectivity
import io.github.magisk317.mipush.manager.xmsfdelivery.XmsfDeliveryProvider
import io.github.magisk317.mipush.manager.xmsfdelivery.XmsfInstallResult
import io.github.magisk317.mipush.manager.xmsfdelivery.XmsfRuntimeState
import io.github.magisk317.uikit.common.AppSnackbarDuration
import io.github.magisk317.uikit.common.AppSnackbarHostState
import io.github.magisk317.uikit.preference.AppArrowItem
import kotlinx.coroutines.launch

/**
 * Settings block for the XMSF runtime delivery (module `manager:xmsfDelivery`).
 *
 * github flavor: the runtime can be downloaded, same-signature verified and
 * installed in-process, so the install action is offered and the manual
 * release-page guidance stays hidden. play flavor: the distribution never
 * handles an APK, so only the guidance and the post-install verification
 * state are shown.
 *
 * The delivery singleton is resolved through [XmsfDeliveryProvider], which
 * binds the flavor-specific implementation by reflection; this UI module stays
 * flavor-agnostic.
 */
@Composable
internal fun XmsfDeliveryBlock(snackbarHostState: AppSnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val delivery = remember(context) { XmsfDeliveryProvider.get(context) }
    val status by delivery.status.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }

    val installedMessage = stringResource(R.string.xmsf_delivery_installed_snackbar)
    val guidanceMessage = stringResource(R.string.xmsf_delivery_guidance_opened_snackbar)
    val failedMessageTemplate = stringResource(R.string.xmsf_delivery_failed_snackbar)

    // Re-probe whenever the settings screen comes back to the foreground: the
    // user may have installed or updated the runtime elsewhere meanwhile.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, delivery) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { delivery.refresh() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val stateText = when (status.state) {
        XmsfRuntimeState.NOT_INSTALLED -> stringResource(R.string.xmsf_delivery_state_not_installed)
        XmsfRuntimeState.UPDATE_AVAILABLE -> stringResource(R.string.xmsf_delivery_state_update_available)
        XmsfRuntimeState.SIGNATURE_MISMATCH -> stringResource(R.string.xmsf_delivery_state_signature_mismatch)
        XmsfRuntimeState.READY -> stringResource(R.string.xmsf_delivery_state_ready)
    }
    val stateSummary = buildString {
        append(stateText)
        val installed = status.installedVersionName
        if (installed != null) {
            append(" · ").append(installed)
        }
        val expected = status.expectedVersionName
        if (expected != null && installed != expected) {
            append(" → ").append(expected)
        }
        if (status.state == XmsfRuntimeState.READY) {
            append(" · ")
            append(
                when (status.connectivity) {
                    XmsfConnectivity.CONNECTED -> stringResource(R.string.xmsf_delivery_connectivity_connected)
                    else -> stringResource(R.string.xmsf_delivery_connectivity_unavailable)
                },
            )
        }
    }

    AppArrowItem(
        title = stringResource(R.string.xmsf_delivery_title),
        summary = stateSummary,
        enabled = !busy,
    ) {
        scope.launch { delivery.refresh() }
    }

    if (delivery.canInstallDirectly) {
        val installSummary = if (busy) {
            stringResource(R.string.xmsf_delivery_install_busy, progress)
        } else {
            stringResource(R.string.xmsf_delivery_install_summary)
        }
        AppArrowItem(
            title = stringResource(R.string.xmsf_delivery_install_action),
            summary = installSummary,
            enabled = !busy && !status.isReady,
        ) {
            scope.launch {
                busy = true
                progress = 0
                // The downloader reports progress on a background dispatcher;
                // hop back to the composition scope before touching state.
                val result = delivery.ensureInstalled { value ->
                    scope.launch { progress = value }
                }
                when (result) {
                    XmsfInstallResult.Installed ->
                        snackbarHostState.showSnackbar(installedMessage, duration = AppSnackbarDuration.Short)

                    XmsfInstallResult.GuidanceOpened ->
                        snackbarHostState.showSnackbar(guidanceMessage, duration = AppSnackbarDuration.Short)

                    is XmsfInstallResult.Failed ->
                        snackbarHostState.showSnackbar(
                            failedMessageTemplate.format(result.message),
                            duration = AppSnackbarDuration.Short,
                        )
                }
                busy = false
            }
        }
    }

    // Flavor split: github installs in-process (item above), so the manual
    // release-page guidance is play-only — showing both would offer two paths
    // to the same action on one build.
    if (!delivery.canInstallDirectly) {
        AppArrowItem(
            title = stringResource(R.string.xmsf_delivery_guidance_action),
            summary = stringResource(R.string.xmsf_delivery_guidance_summary),
            enabled = !busy,
        ) {
            delivery.openGuidance(context)
        }
    }
}
