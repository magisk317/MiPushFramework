package io.github.magisk317.mipush.feature.main.subpage

import android.os.Build
import top.yukonga.miuix.kmp.theme.MiuixTheme
import io.github.magisk317.uikit.surface.uiKitSurfaceGlassSample
import io.github.magisk317.uikit.surface.rememberUiKitGlassTopBar
import io.github.magisk317.uikit.theme.LocalUiKitSurfaceBlur
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.uikit.common.AppSnackbarHost
import io.github.magisk317.uikit.common.AppSnackbarHostState
import io.github.magisk317.uikit.surface.AppCard
import io.github.magisk317.uikit.surface.chromeSurfaceColor
import io.github.magisk317.uikit.surface.surfaceBlurContainerColor
import io.github.magisk317.uikit.surface.uiKitSurfaceBlur
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import io.github.magisk317.uikit.R as UiKitR

/**
 * Miuix implementation of the Overview page. Follows the KernelSU `HomeMiuix`
 * composition model: page-owned miuix `Scaffold` + collapsing `SmallTopAppBar`
 * and grouped info cards built from miuix `BasicComponent` rows (the settings
 * rhythm of HyperOS), instead of the Material-shaped overview cards which
 * carried an over-heavy M3 look into this style.
 *
 * `overScrollVertical()` is deliberately omitted: the global
 * `MiuixOverscrollFactory` under `MagiskUiKitTheme` already provides the bounce.
 */
@Composable
internal fun OverviewMiuix(
    state: OverviewUiState,
    actions: OverviewActions,
    contentPadding: PaddingValues,
    snackbarHostState: AppSnackbarHostState,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val scrollState = rememberScrollState()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val unknown = stringResource(UiKitR.string.unknown)

    val topGlass = rememberUiKitGlassTopBar()
    val glassOn = LocalUiKitSurfaceBlur.current.usesBackdrop
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.uiKitSurfaceGlassSample(topGlass),
                                title = stringResource(R.string.app_name),
                // The status card below owns the connection readout now, so the old top-bar
                // dot/label indicator would only repeat it.
                scrollBehavior = scrollBehavior,
                color = if (glassOn) Color.Transparent else MiuixTheme.colorScheme.surface,
                defaultWindowInsetsPadding = true,
            )
        },
        contentWindowInsets = WindowInsets.systemBars
            .union(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (glassOn) topGlass.contentRecorder() else Modifier)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .scrollEndHaptic()
                    .padding(
                        PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = innerPadding.calculateTopPadding() + 12.dp,
                            bottom = maxOf(contentPadding.calculateBottomPadding(), bottomInset) +
                                innerPadding.calculateBottomPadding() + 16.dp,
                        ),
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MiuixStatusCard(
                    connectionState = state.connectionState,
                    onClick = actions.onStatusCardClick,
                )

                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        MiuixInfoRow(UiKitR.string.uikit_manager_version, state.appVersionName ?: unknown)
                        MiuixInfoRow(UiKitR.string.uikit_runtime_version, state.runtimeVersionName ?: unknown)
                        MiuixInfoRow(R.string.commit_info, state.appVersionCode)
                    }
                }

                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        MiuixInfoRow(UiKitR.string.uikit_manufacturer, Build.MANUFACTURER)
                        MiuixInfoRow(UiKitR.string.uikit_model, io.github.magisk317.uikit.platform.resolveAndroidDeviceDisplayName())
                        MiuixInfoRow(UiKitR.string.uikit_android_version, Build.VERSION.RELEASE)
                        MiuixInfoRow(UiKitR.string.uikit_api_level, Build.VERSION.SDK_INT.toString())
                        MiuixInfoRow(UiKitR.string.uikit_android_codename, Build.VERSION.CODENAME)
                    }
                }

                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        MiuixActionRow(
                            label = UiKitR.string.uikit_pref_join_telegram_group_title,
                            summary = UiKitR.string.uikit_pref_join_telegram_group_summary,
                            onClick = actions.onJoinTelegram,
                        )
                        MiuixActionRow(
                            label = UiKitR.string.uikit_pref_join_qq_channel_title,
                            summary = UiKitR.string.uikit_pref_join_qq_channel_summary,
                            onClick = actions.onJoinQqChannel,
                        )
                        MiuixActionRow(
                            label = UiKitR.string.uikit_pref_source_code_title,
                            summary = UiKitR.string.uikit_pref_source_code_summary,
                            onClick = actions.onSourceCode,
                        )
                        MiuixActionRow(
                            label = UiKitR.string.uikit_pref_donate_by_alipay_title,
                            summary = UiKitR.string.uikit_dialog_donate_summary,
                            onClick = actions.onDonate,
                        )
                    }
                }
            }

            AppSnackbarHost(
                hostState = snackbarHostState,
                bottomPadding = contentPadding.calculateBottomPadding() + 16.dp,
            )
        }
    }
}

/**
 * Miuix status card, ported from KernelSU's `HomeMiuix.StatusCard`.
 *
 * The card is driven by the runtime connection state, not by the Xposed hook flags: hook state is a
 * diagnostic detail and lives on the diagnostics page. Connected takes KernelSU's "working" branch
 * — a green-tinted [Card] with tilt press feedback and an oversized
 * [Icons.Rounded.CheckCircleOutline] anchored bottom-end, deliberately offset past the card bounds
 * so it is clipped into a corner motif. Any other state reuses the same geometry with KernelSU's
 * `WarningLevel.Notice` amber palette and [Icons.Rounded.ErrorOutline], which is the outlined
 * circle-with-bang counterpart of the check so the corner motif keeps its weight.
 *
 * Deviation from KernelSU: KernelSU switches these tints on `MiuixTheme.isDynamicColor`. MiPush's
 * `MagiskUiKitTheme` builds the miuix theme through the `MiuixTheme(colors = ...)` overload, which
 * never publishes `LocalColorSchemeMode`, so `isDynamicColor` is always false here. The fixed
 * palettes (KernelSU's own non-dynamic paths) are used instead, with the light/dark variant picked
 * from the resolved miuix background luminance so it tracks the app's theme mode, pure black
 * included.
 */
@Composable
private fun MiuixStatusCard(
    connectionState: String?,
    onClick: () -> Unit,
) {
    val connected = connectionState == ManagerProtocol.CONNECTION_STATE_CONNECTED
    val darkSurface = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val container = when {
        connected && darkSurface -> STATUS_CARD_CONNECTED_DARK
        connected -> STATUS_CARD_CONNECTED_LIGHT
        darkSurface -> STATUS_CARD_NOTICE_DARK
        else -> STATUS_CARD_NOTICE_LIGHT
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        colors = CardDefaults.defaultColors(color = container),
        onClick = onClick,
        pressFeedbackType = PressFeedbackType.Tilt,
    ) {
        Box {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(27.dp, 31.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Icon(
                    modifier = Modifier.size(110.dp),
                    imageVector = if (connected) {
                        Icons.Rounded.CheckCircleOutline
                    } else {
                        Icons.Rounded.ErrorOutline
                    },
                    tint = if (connected) STATUS_CARD_CONNECTED_MARK else STATUS_CARD_NOTICE_MARK,
                    contentDescription = null,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp, 10.dp),
                contentAlignment = Alignment.BottomStart,
            ) {
                Text(
                    text = stringResource(connectionState.statusBadgeLabel()),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp, 14.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                Text(
                    text = stringResource(
                        if (connected) {
                            R.string.overview_status_working
                        } else {
                            R.string.overview_status_not_connected
                        },
                    ),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * Bottom-start badge, the MiPush counterpart of KernelSU's `LKM`/`GKI` mode label. Connected shows
 * `Connected`; every other state names itself so the card still distinguishes a handshake in
 * progress from a dropped link.
 */
@StringRes
private fun String?.statusBadgeLabel(): Int = when (this) {
    ManagerProtocol.CONNECTION_STATE_CONNECTED -> R.string.connection_status_state_connected
    ManagerProtocol.CONNECTION_STATE_CONNECTING -> R.string.connection_status_state_connecting
    ManagerProtocol.CONNECTION_STATE_DISCONNECTED -> R.string.connection_status_state_disconnected
    else -> R.string.connection_status_unknown
}

private val STATUS_CARD_CONNECTED_DARK = Color(0xFF1A3825)
private val STATUS_CARD_CONNECTED_LIGHT = Color(0xFFDFFAE4)
private val STATUS_CARD_CONNECTED_MARK = Color(0xFF36D167)

// KernelSU's WarningLevel.Notice palette, reused so the down state matches the ported look.
private val STATUS_CARD_NOTICE_DARK = Color(0xFF3E2F1B)
private val STATUS_CARD_NOTICE_LIGHT = Color(0xFFFFF0DB)
private val STATUS_CARD_NOTICE_MARK = Color(0xFFF5A623)

@Composable
private fun MiuixInfoRow(@StringRes label: Int, value: String) {
    BasicComponent(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(label),
        summary = value,
    )
}

@Composable
private fun MiuixActionRow(@StringRes label: Int, @StringRes summary: Int, onClick: () -> Unit) {
    BasicComponent(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(label),
        summary = stringResource(summary),
        onClick = onClick,
    )
}
