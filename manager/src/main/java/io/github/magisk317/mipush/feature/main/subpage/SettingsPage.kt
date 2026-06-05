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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import io.github.magisk317.mipush.manager.R
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import io.github.magisk317.uikit.preference.SectionCard
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.common.manager.ManagerRuntimeLogFileContent
import io.github.magisk317.mipush.common.manager.ManagerRuntimeLogFileInfo
import io.github.magisk317.mipush.common.manager.ManagerRuntimeLogFileSummary
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.feature.main.MainActivityOperation
import io.github.magisk317.mipush.feature.main.MainScrollChromeState
import io.github.magisk317.mipush.feature.main.ReportScrollStateToChrome
import io.github.magisk317.mipush.feature.ui.component.DialogAction
import io.github.magisk317.mipush.feature.ui.component.OverlayHeaderScaffold
import io.github.magisk317.mipush.feature.ui.component.ScrollToTopFAB
import io.github.magisk317.mipush.feature.ui.component.SectionColumn
import io.github.magisk317.mipush.feature.ui.component.SettingsDialogItem
import io.github.magisk317.mipush.feature.ui.component.SettingsItem
import io.github.magisk317.mipush.feature.ui.component.SettingsSwitchItem
import io.github.magisk317.mipush.platform.support.LegacyUiEntryPoints
import io.github.magisk317.mipush.feature.ui.theme.Theme
import io.github.magisk317.mipush.feature.ui.theme.spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.magisk317.mipush.common.configurations.ConfigJson
import io.github.magisk317.mipush.common.configurations.ConfigJsonArray
import io.github.magisk317.mipush.common.configurations.ConfigJsonObject
import org.koin.compose.viewmodel.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@Composable
fun Settings(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: SettingsViewModel = koinViewModel(),
    onShowAboutDialog: (String) -> Unit = {},
    onSectionChanged: (String?) -> Unit = {},
    sectionBackSignal: Int = 0,
    hazeState: HazeState? = null,
    hazeStyle: HazeBlurStyle? = null,
    scrollChromeState: MainScrollChromeState? = null,
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
                sectionBackSignal = sectionBackSignal,
                hazeState = hazeState,
                hazeStyle = hazeStyle,
                snackbarHostState = snackbarHostState,
                scrollChromeState = scrollChromeState,
                scrollState = scrollState,
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            ScrollToTopFAB(scrollState)
        }
    }
}

@Composable
private fun SettingsScreen(
    contentPadding: PaddingValues,
    onShowAboutDialog: (String) -> Unit,
    viewModel: SettingsViewModel,
    onSectionChanged: (String?) -> Unit,
    scrollState: androidx.compose.foundation.ScrollState = androidx.compose.foundation.rememberScrollState(),
    sectionBackSignal: Int,
    hazeState: HazeState?,
    hazeStyle: HazeBlurStyle?,
    snackbarHostState: SnackbarHostState,
    scrollChromeState: MainScrollChromeState?,
) {
    val title = stringResource(R.string.main_settings)
    var serviceExpanded by rememberSaveable { mutableStateOf(false) }
    var keepAliveExpanded by rememberSaveable { mutableStateOf(false) }
    var notificationsExpanded by rememberSaveable { mutableStateOf(false) }
    var diagnosticsExpanded by rememberSaveable { mutableStateOf(false) }
    var registrationExpanded by rememberSaveable { mutableStateOf(false) }
    var aboutExpanded by rememberSaveable { mutableStateOf(false) }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerVisible = scrollChromeState?.isChromeVisible ?: true
    ReportScrollStateToChrome(scrollState, scrollChromeState)

    LaunchedEffect(title) {
        onSectionChanged(title)
    }

    OverlayHeaderScaffold(
        fallbackTopPadding = topInset + 64.dp,
        headerVisible = headerVisible,
        overlayModifier = Modifier
            .fillMaxWidth()
            .then(
                if (hazeState != null && hazeStyle != null) {
                    Modifier.hazeEffect(hazeState) {
                        blurEffect { style = hazeStyle }
                        forceInvalidateOnPreDraw = true
                    }
                } else {
                    Modifier
                }
            ),
        overlay = {
            TopAppBar(
                title = { Text(title) },
                windowInsets = WindowInsets.statusBars,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            )
        },
        content = { listPadding ->
            SectionColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (hazeState != null) {
                            Modifier.hazeSource(state = hazeState)
                        } else {
                            Modifier
                        }
                    )
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
                    ConnectionServiceBlock(viewModel, snackbarHostState)
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
                    title = stringResource(R.string.settings_home_diagnostics_title),
                    expanded = diagnosticsExpanded,
                    onExpandedChange = { diagnosticsExpanded = !diagnosticsExpanded },
                ) {
                    DiagnosticsBlock(viewModel, snackbarHostState)
                }

                SettingsSectionCard(
                    title = stringResource(R.string.settings_home_registration_title),
                    expanded = registrationExpanded,
                    onExpandedChange = { registrationExpanded = !registrationExpanded },
                ) {
                    DataRegistrationBlock(viewModel, snackbarHostState)
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
private fun ConnectionServiceBlock(viewModel: SettingsViewModel, snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val isStartForeground by viewModel.isStartForeground.collectAsStateWithLifecycle()
    val showSwitchFeedback = rememberSwitchFeedback(snackbarHostState)

    SetXMPPServer(viewModel)

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

    SettingsItem(
        title = stringResource(R.string.settings_permission_check),
        summary = stringResource(R.string.settings_permission_check_summary),
    ) {
        context.startActivity(LegacyUiEntryPoints.requestPermissionIntent(context, recheckOnly = true))
    }
}

@Composable
private fun KeepAliveBlock(viewModel: SettingsViewModel, snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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

    SettingsItem(
        title = stringResource(R.string.pref_keepalive_dedicated_service_title),
        summary = stringResource(
            if (keepAliveAccessibilityServiceEnabled) {
                R.string.pref_keepalive_dedicated_service_enabled_summary
            } else {
                R.string.pref_keepalive_dedicated_service_disabled_summary
            }
        ),
    ) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = activityIntentNotFoundMessage,
                    duration = SnackbarDuration.Short,
                )
            }
        }
    }
}

@Composable
private fun NotificationsBlock(viewModel: SettingsViewModel, snackbarHostState: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    val notificationOnRegister by viewModel.notificationOnRegister.collectAsStateWithLifecycle()
    val showAllEvents by viewModel.showAllEvents.collectAsStateWithLifecycle()
    val islandEnabled by viewModel.islandEnabled.collectAsStateWithLifecycle()
    val islandTimeout by viewModel.islandTimeout.collectAsStateWithLifecycle()
    val islandFirstFloat by viewModel.islandFirstFloat.collectAsStateWithLifecycle()
    val islandEnableFloat by viewModel.islandEnableFloat.collectAsStateWithLifecycle()
    val islandShowNotification by viewModel.islandShowNotification.collectAsStateWithLifecycle()
    val islandFocusNotification by viewModel.islandFocusNotification.collectAsStateWithLifecycle()
    val showSwitchFeedback = rememberSwitchFeedback(snackbarHostState)
    var showIslandTimeoutDialog by remember { mutableStateOf(false) }
    var islandTimeoutInput by remember(islandTimeout) { mutableStateOf(islandTimeout.toString()) }
    val islandTimeoutError = stringResource(R.string.pref_island_timeout_error)

    val notificationOnRegisterTitle = stringResource(R.string.settings_notify_on_register)
    SettingsSwitchItem(
        title = notificationOnRegisterTitle,
        checked = notificationOnRegister,
    ) { newValue ->
        viewModel.setNotificationOnRegister(newValue)
        showSwitchFeedback(notificationOnRegisterTitle, newValue)
        if (!newValue) {
            scope.launch(Dispatchers.IO) {
                viewModel.updateAllNotificationOnRegister(false)
            }
        }
    }

    val showAllEventsTitle = stringResource(R.string.settings_show_all_events)
    SettingsSwitchItem(
        title = showAllEventsTitle,
        checked = showAllEvents,
    ) { enabled ->
        viewModel.setShowAllEvents(enabled)
        showSwitchFeedback(showAllEventsTitle, enabled)
    }

    val islandEnabledTitle = stringResource(R.string.pref_island_enabled_title)
    SettingsSwitchItem(
        title = islandEnabledTitle,
        summary = stringResource(R.string.pref_island_enabled_summary),
        checked = islandEnabled,
    ) { enabled ->
        viewModel.setIslandEnabled(enabled)
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
        viewModel.setIslandFirstFloat(enabled)
        showSwitchFeedback(islandFirstFloatTitle, enabled)
    }

    val islandEnableFloatTitle = stringResource(R.string.pref_island_enable_float_title)
    SettingsSwitchItem(
        title = islandEnableFloatTitle,
        summary = stringResource(R.string.pref_island_enable_float_summary),
        checked = islandEnableFloat,
        enabled = islandEnabled,
    ) { enabled ->
        viewModel.setIslandEnableFloat(enabled)
        showSwitchFeedback(islandEnableFloatTitle, enabled)
    }

    val islandShowNotificationTitle = stringResource(R.string.pref_island_show_notification_title)
    SettingsSwitchItem(
        title = islandShowNotificationTitle,
        summary = stringResource(R.string.pref_island_show_notification_summary),
        checked = islandShowNotification,
        enabled = islandEnabled,
    ) { enabled ->
        viewModel.setIslandShowNotification(enabled)
        showSwitchFeedback(islandShowNotificationTitle, enabled)
    }

    val islandFocusNotificationTitle = stringResource(R.string.pref_island_focus_notif_title)
    SettingsSwitchItem(
        title = islandFocusNotificationTitle,
        summary = stringResource(R.string.pref_island_focus_notif_summary),
        checked = islandFocusNotification,
        enabled = islandEnabled,
    ) { enabled ->
        viewModel.setIslandFocusNotification(enabled)
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
                            viewModel.setIslandTimeout(days)
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

@Composable
private fun DiagnosticsBlock(viewModel: SettingsViewModel, snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val debugMode by viewModel.debugMode.collectAsStateWithLifecycle()
    val runtimeLogRetentionDays by viewModel.runtimeLogRetentionDays.collectAsStateWithLifecycle()
    val showSwitchFeedback = rememberSwitchFeedback(snackbarHostState)
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showRuntimeLogRetentionDialog by remember { mutableStateOf(false) }
    var runtimeLogRetentionInput by remember(runtimeLogRetentionDays) {
        mutableStateOf(runtimeLogRetentionDays.toString())
    }
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

    SettingsItem(
        title = stringResource(R.string.settings_get_log),
        summary = stringResource(R.string.settings_get_log_summary),
    ) {
        shareRuntimeLogBundle()
    }

    SettingsItem(
        title = stringResource(R.string.runtime_log_clear_confirm_title),
        summary = stringResource(R.string.runtime_log_clear_summary),
    ) {
        showClearConfirmDialog = true
    }

    SettingsItem(
        title = stringResource(R.string.settings_runtime_log_retention_days),
        summary = stringResource(R.string.settings_runtime_log_retention_days_summary, runtimeLogRetentionDays),
    ) {
        runtimeLogRetentionInput = runtimeLogRetentionDays.toString()
        showRuntimeLogRetentionDialog = true
    }

    val debugModeTitle = stringResource(R.string.settings_debug_mode)
    SettingsSwitchItem(
        title = debugModeTitle,
        summary = stringResource(R.string.settings_debug_mode_summary),
        checked = debugMode,
    ) { enabled ->
        viewModel.setDebugMode(enabled)
        showSwitchFeedback(debugModeTitle, enabled)
    }

    var showMockPanel by remember { mutableStateOf(false) }
    SettingsItem(
        title = stringResource(R.string.settings_mock_notification),
        summary = stringResource(R.string.settings_mock_notification_summary),
    ) {
        showMockPanel = true
    }
    if (showMockPanel) {
        io.github.magisk317.mipush.feature.diagnostic.MockNotificationPanel(
            onDismiss = { showMockPanel = false },
            onFire = { kind, pkg -> viewModel.notifyMockNotification(context, kind, pkg) },
        )
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text(stringResource(R.string.runtime_log_clear_confirm_title)) },
            text = { Text(stringResource(R.string.runtime_log_clear_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirmDialog = false
                    clearRuntimeLogFolders()
                }) {
                    Text(stringResource(R.string.action_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    if (showRuntimeLogRetentionDialog) {
        AlertDialog(
            onDismissRequest = { showRuntimeLogRetentionDialog = false },
            title = { Text(stringResource(R.string.settings_runtime_log_retention_days)) },
            text = {
                TextField(
                    value = runtimeLogRetentionInput,
                    onValueChange = { value ->
                        runtimeLogRetentionInput = value.filter { it.isDigit() }
                    },
                    supportingText = { Text(stringResource(R.string.settings_runtime_log_retention_days_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val days = runtimeLogRetentionInput.toIntOrNull()
                        if (days == null || days < 1) {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    runtimeLogRetentionDaysError,
                                )
                            }
                            return@TextButton
                        }
                        viewModel.setRuntimeLogRetentionDays(days)
                        showRuntimeLogRetentionDialog = false
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRuntimeLogRetentionDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun DataRegistrationBlock(viewModel: SettingsViewModel, snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    SettingsItem(
        title = stringResource(R.string.settings_clear_history),
        summary = stringResource(R.string.settings_clear_history_summary),
    ) {
        viewModel.clearHistory(context)
    }

    SettingsItem(
        title = stringResource(R.string.try_to_force_register_all_applications),
    ) {
        scope.launch {
            val message = withContext(Dispatchers.IO) {
                viewModel.tryForceRegisterAllApplications(context)
            }
            snackbarHostState.showSnackbar(message)
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
@OptIn(ExperimentalMaterial3Api::class)
private fun SetXMPPServer(viewModel: SettingsViewModel) {
    val savedXmppServer by viewModel.xmppServer.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf(savedXmppServer ?: "") }
    var shouldShowDialog by remember { mutableStateOf(false) }

    LaunchedEffect(shouldShowDialog) {
        if (shouldShowDialog) {
            text = savedXmppServer ?: ""
        }
    }

    SettingsDialogItem(
        title = stringResource(R.string.settings_XMPP_server),
        summary = if (savedXmppServer.isNullOrEmpty()) {
            stringResource(R.string.settings_XMPP_server_summary)
        } else {
            savedXmppServer!!
        },
        shouldShowDialog = shouldShowDialog,
        onDismiss = {
            shouldShowDialog = false
            text = ""
        },
        onClick = { shouldShowDialog = true },
        confirmButton = {},
        actions = listOf(
            DialogAction(
                label = stringResource(android.R.string.cancel),
                onClick = {
                    shouldShowDialog = false
                    text = ""
                },
            ),
            DialogAction(
                label = stringResource(android.R.string.ok),
                onClick = {
                    viewModel.updateXmppServer(text)
                    shouldShowDialog = false
                },
            ),
        ),
        content = {
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(viewModel.getXMPPServerHint()) },
                singleLine = true,
            )
        },
    )
}

@Preview(showBackground = true)
@Composable
fun SettingsPagePreview() {
    Utils.context = LocalContext.current
    Theme {
        Settings(PaddingValues(0.dp), onShowAboutDialog = {})
    }
}
