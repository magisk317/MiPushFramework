@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.magisk317.mipush.feature.main.subpage

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import io.github.magisk317.uikit.common.ElevatedSnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.magisk317.mipush.main.viewmodel.SettingsViewModel
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.mipush.common.BuildConfig
import io.github.magisk317.mipush.common.VERSION_NAME
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.uikit.preference.NonNegativeIntegerInputDialog
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsCallbacks
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsItem
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsItems
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsLabels
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsLayout
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsState
import io.github.magisk317.uikit.preference.RuntimeLogShareEntryMode
import io.github.magisk317.uikit.surface.ConfirmActionDialog
import io.github.magisk317.uikit.preference.SectionCard
import io.github.magisk317.mipush.common.ACTION_PREF_CHANGED
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.common.process.BoundedProcessRunner
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.feature.main.MainActivityOperation
import io.github.magisk317.mipush.feature.wizard.RequestPermissionPage
import io.github.magisk317.uikit.scroll.ScrollChromeState
import io.github.magisk317.uikit.surface.ScrollToTopFAB
import io.github.magisk317.uikit.preference.Item as SettingsItem
import io.github.magisk317.uikit.preference.StateSwitchItem as SettingsSwitchItem
import io.github.magisk317.uikit.surface.OverlayHeaderScaffold
import io.github.magisk317.uikit.surface.SectionColumn
import io.github.magisk317.uikit.surface.chromeTopAppBarColors
import io.github.magisk317.mipush.feature.ui.theme.Theme
import io.github.magisk317.mipush.feature.ui.theme.spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.magisk317.mipush.common.configurations.ConfigJson
import io.github.magisk317.mipush.common.configurations.ConfigJsonArray
import io.github.magisk317.mipush.common.configurations.ConfigJsonObject
import org.koin.compose.viewmodel.koinViewModel
import java.util.Locale



@Composable
fun Settings(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: SettingsViewModel = koinViewModel(),
    onShowAboutDialog: (String) -> Unit = {},
    onSectionChanged: (String?) -> Unit = {},
    onNavigateToConnectionStatus: () -> Unit = {},
    onNavigateToStatusBarIconSettings: () -> Unit = {},
    sectionBackSignal: Int = 0,
    scrollChromeState: ScrollChromeState? = null,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    Page {
        Box(modifier = Modifier.fillMaxSize()) {
            val scrollState = androidx.compose.foundation.rememberScrollState()
            SettingsScreen(
                contentPadding = contentPadding,
                onShowAboutDialog = onShowAboutDialog,
                viewModel = viewModel,
                onSectionChanged = onSectionChanged,
                onNavigateToConnectionStatus = onNavigateToConnectionStatus,
                onNavigateToStatusBarIconSettings = onNavigateToStatusBarIconSettings,
                sectionBackSignal = sectionBackSignal,
                snackbarHostState = snackbarHostState,
                scrollChromeState = scrollChromeState,
                scrollState = scrollState,
            )
            ElevatedSnackbarHost(
                hostState = snackbarHostState,
                bottomPadding = contentPadding.calculateBottomPadding() + 16.dp,
            )
            ScrollToTopFAB(scrollState, visible = scrollChromeState?.isChromeVisible != true, extraBottomPadding = 80.dp)
        }
    }
}

@Composable
private fun SettingsScreen(
    contentPadding: PaddingValues,
    onShowAboutDialog: (String) -> Unit,
    viewModel: SettingsViewModel,
    onSectionChanged: (String?) -> Unit,
    onNavigateToConnectionStatus: () -> Unit,
    onNavigateToStatusBarIconSettings: () -> Unit,
    scrollState: androidx.compose.foundation.ScrollState = androidx.compose.foundation.rememberScrollState(),
    sectionBackSignal: Int,
    snackbarHostState: SnackbarHostState,
    scrollChromeState: ScrollChromeState?,
) {
    val title = stringResource(R.string.main_settings)
    var serviceExpanded by rememberSaveable { mutableStateOf(false) }
    var keepAliveExpanded by rememberSaveable { mutableStateOf(false) }
    var notificationsExpanded by rememberSaveable { mutableStateOf(false) }
    var zygiskExpanded by rememberSaveable { mutableStateOf(false) }
    var diagnosticsExpanded by rememberSaveable { mutableStateOf(false) }
    var aboutExpanded by rememberSaveable { mutableStateOf(false) }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LaunchedEffect(title) {
        onSectionChanged(title)
    }
    LaunchedEffect(Unit) {
        viewModel.refreshDualAppFromRuntime()
    }

    OverlayHeaderScaffold(
        fallbackTopPadding = topInset + 64.dp,
        headerOffsetY = scrollChromeState?.animatedHeaderOffsetY ?: 0f,
        onHeaderHeightChanged = { scrollChromeState?.headerHeightPx = it.toFloat() },
        overlayModifier = Modifier
            .fillMaxWidth(),
        overlay = {
            TopAppBar(
                title = { Text(title) },
                windowInsets = WindowInsets.statusBars,
                colors = chromeTopAppBarColors(),
            )
        },
        content = { listPadding ->
            SectionColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                contentPadding = PaddingValues(
                    start = MaterialTheme.spacing.medium,
                    top = listPadding.calculateTopPadding() + MaterialTheme.spacing.small,
                    end = MaterialTheme.spacing.medium,
                    bottom = contentPadding.calculateBottomPadding() + MaterialTheme.spacing.large,
                ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                SettingsSectionCard(
                    title = stringResource(R.string.settings_home_service_title),
                    expanded = serviceExpanded,
                    onExpandedChange = { serviceExpanded = !serviceExpanded },
                ) {
                    ConnectionServiceBlock(viewModel, snackbarHostState, onNavigateToConnectionStatus)
                }

                SettingsSectionCard(
                    title = stringResource(R.string.settings_home_keepalive_title),
                    expanded = keepAliveExpanded,
                    onExpandedChange = { keepAliveExpanded = !keepAliveExpanded },
                ) {
                    KeepAliveBlock(viewModel, snackbarHostState)
                }

                SettingsSectionCard(
                    title = stringResource(R.string.settings_home_notifications_title),
                    expanded = notificationsExpanded,
                    onExpandedChange = { notificationsExpanded = !notificationsExpanded },
                ) {
                    NotificationsBlock(viewModel, snackbarHostState)
                }

                SettingsSectionCard(
                    title = stringResource(R.string.settings_home_misc_title),
                    expanded = zygiskExpanded,
                    onExpandedChange = { zygiskExpanded = !zygiskExpanded },
                ) {
                    val context = LocalContext.current
                    val scope = rememberCoroutineScope()
                    val showAllEvents by viewModel.showAllEvents.collectAsStateWithLifecycle()
                    val dualAppEnabled by viewModel.dualAppEnabled.collectAsStateWithLifecycle()
                    val dualAppProcessing by viewModel.dualAppProcessing.collectAsStateWithLifecycle()
                    val showSwitchFeedback = rememberSwitchFeedback(snackbarHostState)

                    val showAllEventsTitle = stringResource(R.string.settings_show_all_events)
                    SettingsSwitchItem(
                        title = showAllEventsTitle,
                        summary = "",
                        checked = showAllEvents,
                        onCheckedChange = { enabled ->
                            viewModel.setShowAllEvents(enabled)
                            showSwitchFeedback(showAllEventsTitle, enabled)
                        }
                    )

                    SettingsItem(
                        title = stringResource(R.string.pref_color_status_bar_icon_title),
                        summary = stringResource(R.string.pref_color_status_bar_icon_summary),
                        onClick = onNavigateToStatusBarIconSettings,
                    )

                    val selectedLauncherIcon by viewModel.selectedLauncherIcon.collectAsStateWithLifecycle()
                    val normalizedLauncherIcon =
                        if (selectedLauncherIcon == "legacy") "legacy" else "default"
                    val launcherIconSummary = when (normalizedLauncherIcon) {
                        "legacy" -> stringResource(R.string.settings_launcher_icon_legacy)
                        else -> stringResource(R.string.settings_launcher_icon_default)
                    }
                    var showLauncherIconDialog by remember { mutableStateOf(false) }
                    val currentPreviewRes = when (normalizedLauncherIcon) {
                        "legacy" -> R.mipmap.ic_launcher_preview_legacy
                        else -> R.mipmap.ic_launcher_preview_default
                    }
                    SettingsItem(
                        title = stringResource(R.string.settings_launcher_icon),
                        summary = launcherIconSummary + " · " + stringResource(R.string.settings_launcher_icon_summary),
                        trailingContent = {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                tonalElevation = 0.dp,
                            ) {
                                Image(
                                    painter = painterResource(currentPreviewRes),
                                    contentDescription = launcherIconSummary,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .padding(2.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                )
                            }
                        },
                    ) { showLauncherIconDialog = true }
                    if (showLauncherIconDialog) {
                        LauncherIconPickerDialog(
                            selectedIconId = selectedLauncherIcon,
                            onSelect = { id ->
                                viewModel.setSelectedLauncherIcon(context, id)
                                showLauncherIconDialog = false
                            },
                            onDismiss = { showLauncherIconDialog = false },
                        )
                    }

                    SettingsItem(
                        title = stringResource(R.string.settings_migrate_prefs),
                        summary = stringResource(R.string.settings_migrate_prefs_summary),
                    ) {
                        viewModel.migrateManagerPreferencesFromRuntime { written ->
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.settings_migrate_prefs_done, written),
                                )
                            }
                        }
                    }

                    val dualAppTitle = stringResource(R.string.settings_dual_app_title)
                    SettingsSwitchItem(
                        title = dualAppTitle,
                        summary = stringResource(
                            if (viewModel.canManageDualApp) {
                                R.string.settings_dual_app_summary
                            } else {
                                R.string.settings_dual_app_primary_user_only_summary
                            },
                        ),
                        checked = dualAppEnabled,
                        enabled = viewModel.canManageDualApp && !dualAppProcessing,
                        onCheckedChange = { enabled ->
                            viewModel.setDualAppEnabled(enabled) { success, message ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(message)
                                }
                            }
                        },
                    )

                    SettingsItem(
                        title = stringResource(R.string.zygisk_status),
                        summary = stringResource(R.string.zygisk_status_summary),
                    ) {
                        context.startActivity(
                            android.content.Intent(context, io.github.magisk317.mipush.feature.main.ZygiskConfigPage::class.java)
                        )
                    }
                }

                SettingsSectionCard(
                    title = stringResource(R.string.settings_home_diagnostics_title),
                    expanded = diagnosticsExpanded,
                    onExpandedChange = { diagnosticsExpanded = !diagnosticsExpanded },
                ) {
                    DiagnosticsBlock(viewModel, snackbarHostState)
                }

                SettingsSectionCard(
                    title = stringResource(R.string.action_about),
                    expanded = aboutExpanded,
                    onExpandedChange = { aboutExpanded = !aboutExpanded },
                ) {
                    AboutBlock(onShowAboutDialog)
                }
            }
        },
    )
}

@Composable
private fun SettingsSectionCard(
    title: String,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    SectionCard(
        title = title,
        accordionMode = true,
        sectionExpanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = MaterialTheme.spacing.small),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            content()
        }
    }
}

@Composable
private fun rememberSwitchFeedback(snackbarHostState: SnackbarHostState): (String, Boolean) -> Unit {
    val scope = rememberCoroutineScope()
    val enabledTemplate = stringResource(R.string.settings_switch_enabled_feedback)
    val disabledTemplate = stringResource(R.string.settings_switch_disabled_feedback)
    return remember(snackbarHostState, scope, enabledTemplate, disabledTemplate) {
        { title, enabled ->
            val template = if (enabled) enabledTemplate else disabledTemplate
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(
                    message = String.format(Locale.getDefault(), template, title),
                    duration = SnackbarDuration.Short,
                )
            }
        }
    }
}

@Composable
private fun ConnectionServiceBlock(viewModel: SettingsViewModel, snackbarHostState: SnackbarHostState, onNavigateToConnectionStatus: () -> Unit) {
    val context = LocalContext.current

    SetXMPPServer()

    SettingsItem(
        title = stringResource(R.string.settings_connection_status),
        summary = stringResource(R.string.settings_connection_status_summary),
    ) {
        onNavigateToConnectionStatus()
    }

    SettingsItem(
        title = stringResource(R.string.settings_permission_check),
        summary = stringResource(R.string.settings_permission_check_summary),
    ) {
        context.startActivity(
            Intent(context, RequestPermissionPage::class.java)
                .putExtra(RequestPermissionPage.EXTRA_RECHECK_ONLY, true),
        )
    }
}

@Composable
private fun KeepAliveBlock(viewModel: SettingsViewModel, snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isStartForeground by viewModel.isStartForeground.collectAsStateWithLifecycle()
    val keepAliveOomAdj by viewModel.keepAliveOomAdj.collectAsStateWithLifecycle()
    val keepAliveAntiKill by viewModel.keepAliveAntiKill.collectAsStateWithLifecycle()
    val keepAliveStandbyBypass by viewModel.keepAliveStandbyBypass.collectAsStateWithLifecycle()
    val keepAliveDozeBypass by viewModel.keepAliveDozeBypass.collectAsStateWithLifecycle()
    val showSwitchFeedback = rememberSwitchFeedback(snackbarHostState)
    val lifecycleOwner = LocalLifecycleOwner.current
    var accessibilityStatusRefresh by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityStatusRefresh += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val keepAliveAccessibilityServiceEnabled = remember(context, accessibilityStatusRefresh) {
        isKeepAliveAccessibilityServiceEnabled(context)
    }
    val activityIntentNotFoundMessage = stringResource(R.string.activity_intent_not_found)

    // Master switch: 推送服务保活
    val startForegroundTitle = stringResource(R.string.settings_start_foreground_service)
    SettingsSwitchItem(
        title = startForegroundTitle,
        summary = stringResource(R.string.settings_start_foreground_service_summary),
        checked = isStartForeground,
    ) { enabled ->
        viewModel.setStartForeground(enabled)
        viewModel.startMiPushServiceAsForegroundService(context)
        showSwitchFeedback(startForegroundTitle, enabled)
    }

    // Sub-switches only visible when master switch is on
    if (isStartForeground) {
        val keepAliveOomAdjTitle = stringResource(R.string.pref_keepalive_oom_adj_title)
        SettingsSwitchItem(
            title = keepAliveOomAdjTitle,
            summary = stringResource(R.string.pref_keepalive_oom_adj_summary),
            checked = keepAliveOomAdj,
        ) { enabled ->
            viewModel.setKeepAliveOomAdj(enabled)
            showSwitchFeedback(keepAliveOomAdjTitle, enabled)
        }

        val keepAliveAntiKillTitle = stringResource(R.string.pref_keepalive_anti_kill_title)
        SettingsSwitchItem(
            title = keepAliveAntiKillTitle,
            summary = stringResource(R.string.pref_keepalive_anti_kill_summary),
            checked = keepAliveAntiKill,
        ) { enabled ->
            viewModel.setKeepAliveAntiKill(enabled)
            showSwitchFeedback(keepAliveAntiKillTitle, enabled)
        }

        val keepAliveStandbyBypassTitle = stringResource(R.string.pref_keepalive_standby_bypass_title)
        SettingsSwitchItem(
            title = keepAliveStandbyBypassTitle,
            summary = stringResource(R.string.pref_keepalive_standby_bypass_summary),
            checked = keepAliveStandbyBypass,
        ) { enabled ->
            viewModel.setKeepAliveStandbyBypass(enabled)
            showSwitchFeedback(keepAliveStandbyBypassTitle, enabled)
        }

        val keepAliveDozeBypassTitle = stringResource(R.string.pref_keepalive_doze_bypass_title)
        SettingsSwitchItem(
            title = keepAliveDozeBypassTitle,
            summary = stringResource(R.string.pref_keepalive_doze_bypass_summary),
            checked = keepAliveDozeBypass,
        ) { enabled ->
            viewModel.setKeepAliveDozeBypass(enabled)
            showSwitchFeedback(keepAliveDozeBypassTitle, enabled)
        }

        SettingsSwitchItem(
            title = stringResource(R.string.pref_keepalive_dedicated_service_title),
            summary = stringResource(
                if (keepAliveAccessibilityServiceEnabled) {
                    R.string.pref_keepalive_dedicated_service_enabled_summary
                } else {
                    R.string.pref_keepalive_dedicated_service_disabled_summary
                }
            ),
            checked = keepAliveAccessibilityServiceEnabled,
        ) { enabled ->
            scope.launch {
                val success = toggleAccessibilityServiceViaRoot(context, enabled)
                if (success) {
                    accessibilityStatusRefresh += 1
                } else {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    runCatching {
                        context.startActivity(intent)
                    }.onFailure {
                        snackbarHostState.showSnackbar(
                            message = activityIntentNotFoundMessage,
                            duration = SnackbarDuration.Short,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationsBlock(viewModel: SettingsViewModel, snackbarHostState: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val islandEnabled by viewModel.islandEnabled.collectAsStateWithLifecycle()
    val islandTimeout by viewModel.islandTimeout.collectAsStateWithLifecycle()
    val islandFirstFloat by viewModel.islandFirstFloat.collectAsStateWithLifecycle()
    val islandEnableFloat by viewModel.islandEnableFloat.collectAsStateWithLifecycle()
    val islandShowNotification by viewModel.islandShowNotification.collectAsStateWithLifecycle()
    val islandShowOriginalNotification by viewModel.islandShowOriginalNotification.collectAsStateWithLifecycle()
    val islandFocusNotification by viewModel.islandFocusNotification.collectAsStateWithLifecycle()
    val showSwitchFeedback = rememberSwitchFeedback(snackbarHostState)
    var showIslandTimeoutDialog by remember { mutableStateOf(false) }
    var islandTimeoutInput by remember(islandTimeout) { mutableStateOf(islandTimeout.toString()) }
    val islandTimeoutError = stringResource(R.string.pref_island_timeout_error)

    val islandEnabledTitle = stringResource(R.string.pref_island_enabled_title)
    SettingsSwitchItem(
        title = islandEnabledTitle,
        summary = stringResource(R.string.pref_island_enabled_summary),
        checked = islandEnabled,
    ) { enabled ->
        viewModel.setIslandEnabled(enabled) {
            notifyPrefChanged(context)
        }
        showSwitchFeedback(islandEnabledTitle, enabled)
    }

    SettingsItem(
        title = stringResource(R.string.pref_island_timeout_title),
        summary = stringResource(R.string.pref_island_timeout_summary, islandTimeout),
        enabled = islandEnabled,
    ) {
        islandTimeoutInput = islandTimeout.toString()
        showIslandTimeoutDialog = true
    }

    val islandFirstFloatTitle = stringResource(R.string.pref_island_first_float_title)
    SettingsSwitchItem(
        title = islandFirstFloatTitle,
        summary = stringResource(R.string.pref_island_first_float_summary),
        checked = islandFirstFloat,
        enabled = islandEnabled,
    ) { enabled ->
        viewModel.setIslandFirstFloat(enabled) {
            notifyPrefChanged(context)
        }
        showSwitchFeedback(islandFirstFloatTitle, enabled)
    }

    val islandEnableFloatTitle = stringResource(R.string.pref_island_enable_float_title)
    SettingsSwitchItem(
        title = islandEnableFloatTitle,
        summary = stringResource(R.string.pref_island_enable_float_summary),
        checked = islandEnableFloat,
        enabled = islandEnabled,
    ) { enabled ->
        viewModel.setIslandEnableFloat(enabled) {
            notifyPrefChanged(context)
        }
        showSwitchFeedback(islandEnableFloatTitle, enabled)
    }

    val islandShowNotificationTitle = stringResource(R.string.pref_island_show_notification_title)
    SettingsSwitchItem(
        title = islandShowNotificationTitle,
        summary = stringResource(R.string.pref_island_show_notification_summary),
        checked = islandShowNotification,
        enabled = islandEnabled,
    ) { enabled ->
        viewModel.setIslandShowNotification(enabled) {
            notifyPrefChanged(context)
        }
        showSwitchFeedback(islandShowNotificationTitle, enabled)
    }

    val islandShowOriginalNotificationTitle = stringResource(R.string.pref_island_show_original_notification_title)
    SettingsSwitchItem(
        title = islandShowOriginalNotificationTitle,
        summary = stringResource(R.string.pref_island_show_original_notification_summary),
        checked = islandShowOriginalNotification,
        enabled = islandEnabled,
    ) { enabled ->
        viewModel.setIslandShowOriginalNotification(enabled) {
            notifyPrefChanged(context)
        }
        showSwitchFeedback(islandShowOriginalNotificationTitle, enabled)
    }

    val islandFocusNotificationTitle = stringResource(R.string.pref_island_focus_notif_title)
    SettingsSwitchItem(
        title = islandFocusNotificationTitle,
        summary = stringResource(R.string.pref_island_focus_notif_summary),
        checked = islandFocusNotification,
        enabled = islandEnabled,
    ) { enabled ->
        viewModel.setIslandFocusNotification(enabled) {
            notifyPrefChanged(context)
        }
        showSwitchFeedback(islandFocusNotificationTitle, enabled)
    }

    if (showIslandTimeoutDialog) {
        AlertDialog(
            onDismissRequest = { showIslandTimeoutDialog = false },
            title = { Text(stringResource(R.string.pref_island_timeout_title)) },
            text = {
                TextField(
                    value = islandTimeoutInput,
                    onValueChange = { value ->
                        islandTimeoutInput = value.filter { it.isDigit() }
                    },
                    supportingText = { Text(stringResource(R.string.pref_island_timeout_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val days = islandTimeoutInput.toIntOrNull()
                        if (days == null || days < 1) {
                            scope.launch {
                                snackbarHostState.showSnackbar(islandTimeoutError)
                            }
                        } else {
                            viewModel.setIslandTimeout(days) {
                                notifyPrefChanged(context)
                            }
                            showIslandTimeoutDialog = false
                        }
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showIslandTimeoutDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

private fun notifyPrefChanged(context: Context) {
    context.sendBroadcast(Intent(ACTION_PREF_CHANGED))
}

@Composable
private fun DiagnosticsBlock(viewModel: SettingsViewModel, snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val debugMode by viewModel.debugMode.collectAsStateWithLifecycle()
    val logSanitizationEnabled by viewModel.logSanitizationEnabled.collectAsStateWithLifecycle()
    val analyticsEnabled by viewModel.analyticsEnabled.collectAsStateWithLifecycle()
    val runtimeLogRetentionDays by viewModel.runtimeLogRetentionDays.collectAsStateWithLifecycle()
    val showSwitchFeedback = rememberSwitchFeedback(snackbarHostState)
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showRuntimeLogRetentionDialog by remember { mutableStateOf(false) }
    val runtimeLogExportFailedTemplate = stringResource(R.string.runtime_log_export_failed)
    val logShareTitle = stringResource(R.string.log_share_title)
    val runtimeLogShareFailedTemplate = stringResource(R.string.runtime_log_share_failed)
    val runtimeLogClearedMessage = stringResource(R.string.runtime_log_cleared)
    val runtimeLogClearPartialFailedTemplate = stringResource(R.string.runtime_log_clear_partial_failed)
    val runtimeLogRetentionDaysError = stringResource(R.string.settings_runtime_log_retention_days_error)

    fun formatMessage(template: String, vararg args: Any?): String {
        return String.format(Locale.getDefault(), template, *args)
    }

    fun shareRuntimeLogBundle() {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                viewModel.buildRuntimeLogBundle(context)
            }
            val file = result.file
            if (file == null) {
                snackbarHostState.showSnackbar(formatMessage(runtimeLogExportFailedTemplate, result.details))
                return@launch
            }
            runCatching {
                val intent = viewModel.buildRuntimeLogShareIntent(context, file)
                context.startActivity(Intent.createChooser(intent, logShareTitle))
            }.onFailure {
                snackbarHostState.showSnackbar(
                    formatMessage(
                        runtimeLogShareFailedTemplate,
                        it.message ?: it.javaClass.simpleName,
                    ),
                )
            }
        }
    }

    fun clearRuntimeLogFolders() {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                viewModel.clearRuntimeLogFolders(context)
            }
            snackbarHostState.showSnackbar(
                if (result.success) {
                    runtimeLogClearedMessage
                } else {
                    formatMessage(runtimeLogClearPartialFailedTemplate, result.details)
                },
            )
        }
    }

    val debugModeTitle = stringResource(R.string.settings_debug_mode)
    val analyticsTitle = stringResource(R.string.settings_enable_analytics)
    if (!BuildConfig.DEBUG) {
        SettingsSwitchItem(
            title = analyticsTitle,
            summary = stringResource(R.string.settings_enable_analytics_summary),
            checked = analyticsEnabled,
        ) { enabled ->
            viewModel.setAnalyticsEnabled(enabled)
            MagiskOtel.configureForInstallation(
                context,
                MagiskOtel.Config(
                    enabled = BuildConfig.DEBUG || enabled ||
                        (System.getProperty("magisk.otel.enabled")?.equals("true", ignoreCase = true) == true),
                    serviceName = "mipushframework",
                    serviceVersion = VERSION_NAME,
                    projectId = "83955143",
                    projectName = "MiPushFramework",
                    environment = if (BuildConfig.DEBUG) "debug" else "release",
                ),
            )
            notifyPrefChanged(context)
            showSwitchFeedback(analyticsTitle, enabled)
        }
    }
    val logSanitizationTitle = stringResource(R.string.settings_log_sanitization)
    RuntimeLogDiagnosticsItems(
        labels = RuntimeLogDiagnosticsLabels(
            shareLogTitle = stringResource(R.string.settings_get_log),
            shareLogSummary = stringResource(R.string.settings_get_log_summary),
            verboseLogTitle = debugModeTitle,
            verboseLogSummary = stringResource(R.string.settings_debug_mode_summary),
            retentionTitle = stringResource(R.string.settings_runtime_log_retention_days),
            retentionSummary = stringResource(
                R.string.settings_runtime_log_retention_days_summary,
                runtimeLogRetentionDays,
            ),
            clearLogTitle = stringResource(R.string.runtime_log_clear_confirm_title),
            clearLogSummary = stringResource(R.string.runtime_log_clear_summary),
            sensitiveLogTitle = logSanitizationTitle,
            sensitiveLogSummary = stringResource(R.string.settings_log_sanitization_summary),
        ),
        state = RuntimeLogDiagnosticsState(
            verboseLogEnabled = debugMode,
            sensitiveLogEnabled = logSanitizationEnabled,
        ),
        callbacks = RuntimeLogDiagnosticsCallbacks(
            onShareLog = ::shareRuntimeLogBundle,
            onVerboseLogEnabledChange = { enabled ->
                viewModel.setDebugMode(enabled)
                showSwitchFeedback(debugModeTitle, enabled)
            },
            onRetentionClick = { showRuntimeLogRetentionDialog = true },
            onClearLogClick = { showClearConfirmDialog = true },
            onSensitiveLogEnabledChange = { enabled ->
                viewModel.setLogSanitizationEnabled(enabled)
                context.sendBroadcast(Intent(ACTION_PREF_CHANGED))
                showSwitchFeedback(logSanitizationTitle, enabled)
            },
        ),
        layout = RuntimeLogDiagnosticsLayout(
            shareEntryMode = RuntimeLogShareEntryMode.SEPARATE_ITEM,
            itemOrder = listOf(
                RuntimeLogDiagnosticsItem.SHARE_LOG,
                RuntimeLogDiagnosticsItem.CLEAR_LOG,
                RuntimeLogDiagnosticsItem.RETENTION,
                RuntimeLogDiagnosticsItem.VERBOSE_LOG,
                RuntimeLogDiagnosticsItem.SENSITIVE_LOG,
            ),
        ),
    )

    if (showClearConfirmDialog) {
        ConfirmActionDialog(
            title = stringResource(R.string.runtime_log_clear_confirm_title),
            message = stringResource(R.string.runtime_log_clear_confirm_message),
            confirmText = stringResource(R.string.action_clear),
            cancelText = stringResource(android.R.string.cancel),
            onDismissRequest = { showClearConfirmDialog = false },
            onConfirm = {
                showClearConfirmDialog = false
                clearRuntimeLogFolders()
            },
        )
    }

    if (showRuntimeLogRetentionDialog) {
        NonNegativeIntegerInputDialog(
            title = stringResource(R.string.settings_runtime_log_retention_days),
            initialValue = runtimeLogRetentionDays,
            errorText = runtimeLogRetentionDaysError,
            onDismiss = { showRuntimeLogRetentionDialog = false },
            minimumValue = 1,
            supportingText = stringResource(R.string.settings_runtime_log_retention_days_hint),
        ) { days ->
            viewModel.setRuntimeLogRetentionDays(days)
            showRuntimeLogRetentionDialog = false
        }
    }
}

@Composable
private fun AboutBlock(onShowAboutDialog: (String) -> Unit) {
    val context = LocalContext.current
    val mainActivityOperation = MainActivityOperation(context)

    SettingsItem(
        title = stringResource(R.string.action_update),
    ) {
        mainActivityOperation.gotoGitHubReleasePage()
        Toast.makeText(context, R.string.update_toast, Toast.LENGTH_LONG).show()
    }

    SettingsItem(
        title = stringResource(R.string.action_about),
    ) {
        mainActivityOperation.showAboutDialog(onShowAboutDialog)
    }
}

private fun isKeepAliveAccessibilityServiceEnabled(context: Context): Boolean {
    val accessibilityEnabled = runCatching {
        Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
    }.getOrDefault(0)
    if (accessibilityEnabled != 1) {
        return false
    }

    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    return enabledServices.split(':').any { service ->
        val component = ComponentName.unflattenFromString(service) ?: return@any false
        component.packageName == Constants.SERVICE_APP_NAME &&
            component.className == Constants.KEEPALIVE_ACCESSIBILITY_SERVICE_CLASS
    }
}

@Composable
private fun SetXMPPServer() {
    XmppServerEditor { uiState, onEdit ->
        SettingsItem(
            title = stringResource(R.string.settings_XMPP_server),
            summary = uiState.configuredServer?.takeIf(String::isNotBlank)
                ?: stringResource(R.string.settings_XMPP_server_summary),
            enabled = uiState.isLoaded && !uiState.isSaving,
            onClick = onEdit,
        )
    }
}

@Preview(showBackground = true)


@Composable
private fun LauncherIconPickerDialog(
    selectedIconId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    data class LauncherIconOption(
        val id: String,
        val labelRes: Int,
        val iconRes: Int,
    )
    val options = listOf(
        LauncherIconOption(
            id = "default",
            labelRes = R.string.settings_launcher_icon_default,
            iconRes = R.mipmap.ic_launcher_preview_default,
        ),
        LauncherIconOption(
            id = "legacy",
            labelRes = R.string.settings_launcher_icon_legacy,
            iconRes = R.mipmap.ic_launcher_preview_legacy,
        ),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_launcher_icon)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEach { option ->
                    val selected = option.id == (if (selectedIconId == "legacy") "legacy" else "default")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = RoundedCornerShape(16.dp),
                            )
                            .clickable { onSelect(option.id) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = 0.dp,
                        ) {
                            Image(
                                painter = painterResource(option.iconRes),
                                contentDescription = stringResource(option.labelRes),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(56.dp)
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                            )
                        }
                        Text(
                            text = stringResource(option.labelRes),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        RadioButton(
                            selected = selected,
                            onClick = { onSelect(option.id) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
fun SettingsPagePreview() {
    Utils.context = LocalContext.current
    Theme {
        Settings(PaddingValues(0.dp), onShowAboutDialog = {})
    }
}

private suspend fun toggleAccessibilityServiceViaRoot(context: android.content.Context, enable: Boolean): Boolean {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val component = ComponentName(
            Constants.SERVICE_APP_NAME,
            Constants.KEEPALIVE_ACCESSIBILITY_SERVICE_CLASS,
        ).flattenToString()
        val currentServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        val newServices = if (enable) {
            if (currentServices.contains(component)) return@withContext true
            if (currentServices.isEmpty()) component else "$currentServices:$component"
        } else {
            if (!currentServices.contains(component)) return@withContext true
            currentServices.split(":").filter { it.isNotEmpty() && it != component }.joinToString(":")
        }

        val script = buildString {
            appendLine("settings put secure enabled_accessibility_services $newServices")
            if (enable) appendLine("settings put secure accessibility_enabled 1")
            appendLine("exit")
        }
        BoundedProcessRunner.run(
            command = listOf("su"),
            timeoutMillis = 8_000L,
            standardInput = script,
        ).isSuccess
    }
}
