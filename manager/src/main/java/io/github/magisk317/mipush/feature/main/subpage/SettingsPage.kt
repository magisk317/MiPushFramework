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
                    ConnectionServiceBlock(viewModel)
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
private fun ConnectionServiceBlock(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val isStartForeground by viewModel.isStartForeground.collectAsStateWithLifecycle()

    SetXMPPServer(viewModel)

    SettingsSwitchItem(
        title = stringResource(R.string.settings_start_foreground_service),
        summary = stringResource(R.string.settings_start_foreground_service_summary),
        checked = isStartForeground,
    ) {
        viewModel.setStartForeground(it)
        viewModel.startMiPushServiceAsForegroundService(context)
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

    SettingsSwitchItem(
        title = stringResource(R.string.pref_keepalive_oom_adj_title),
        summary = stringResource(R.string.pref_keepalive_oom_adj_summary),
        checked = keepAliveOomAdj,
    ) {
        viewModel.setKeepAliveOomAdj(it)
    }

    SettingsSwitchItem(
        title = stringResource(R.string.pref_keepalive_anti_kill_title),
        summary = stringResource(R.string.pref_keepalive_anti_kill_summary),
        checked = keepAliveAntiKill,
    ) {
        viewModel.setKeepAliveAntiKill(it)
    }

    SettingsSwitchItem(
        title = stringResource(R.string.pref_keepalive_standby_bypass_title),
        summary = stringResource(R.string.pref_keepalive_standby_bypass_summary),
        checked = keepAliveStandbyBypass,
    ) {
        viewModel.setKeepAliveStandbyBypass(it)
    }

    SettingsSwitchItem(
        title = stringResource(R.string.pref_keepalive_doze_bypass_title),
        summary = stringResource(R.string.pref_keepalive_doze_bypass_summary),
        checked = keepAliveDozeBypass,
    ) {
        viewModel.setKeepAliveDozeBypass(it)
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
    val showConfigurationList by viewModel.showConfigurationList.collectAsStateWithLifecycle()
    val islandEnabled by viewModel.islandEnabled.collectAsStateWithLifecycle()
    val islandTimeout by viewModel.islandTimeout.collectAsStateWithLifecycle()
    val islandFirstFloat by viewModel.islandFirstFloat.collectAsStateWithLifecycle()
    val islandEnableFloat by viewModel.islandEnableFloat.collectAsStateWithLifecycle()
    val islandShowNotification by viewModel.islandShowNotification.collectAsStateWithLifecycle()
    val islandFocusNotification by viewModel.islandFocusNotification.collectAsStateWithLifecycle()
    val notificationOnRegisterDisabledMessage = stringResource(R.string.notification_on_register_global_disabled_hint)
    var showIslandTimeoutDialog by remember { mutableStateOf(false) }
    var islandTimeoutInput by remember(islandTimeout) { mutableStateOf(islandTimeout.toString()) }
    val islandTimeoutError = stringResource(R.string.pref_island_timeout_error)

    SettingsSwitchItem(
        title = stringResource(R.string.settings_notify_on_register),
        checked = notificationOnRegister,
    ) { newValue ->
        viewModel.setNotificationOnRegister(newValue)
        if (!newValue) {
            scope.launch(Dispatchers.IO) {
                viewModel.updateAllNotificationOnRegister(false)
            }
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = notificationOnRegisterDisabledMessage,
                    duration = SnackbarDuration.Short,
                )
            }
        }
    }

    SettingsSwitchItem(
        title = stringResource(R.string.settings_show_all_events),
        checked = showAllEvents,
    ) { viewModel.setShowAllEvents(it) }

    SettingsSwitchItem(
        title = stringResource(R.string.settings_show_loaded_file_after_configurations_loaded),
        checked = showConfigurationList,
    ) { viewModel.setShowConfigurationList(it) }

    SettingsSwitchItem(
        title = stringResource(R.string.pref_island_enabled_title),
        summary = stringResource(R.string.pref_island_enabled_summary),
        checked = islandEnabled,
    ) { viewModel.setIslandEnabled(it) }

    SettingsItem(
        title = stringResource(R.string.pref_island_timeout_title),
        summary = stringResource(R.string.pref_island_timeout_summary, islandTimeout),
        enabled = islandEnabled,
    ) {
        islandTimeoutInput = islandTimeout.toString()
        showIslandTimeoutDialog = true
    }

    SettingsSwitchItem(
        title = stringResource(R.string.pref_island_first_float_title),
        summary = stringResource(R.string.pref_island_first_float_summary),
        checked = islandFirstFloat,
        enabled = islandEnabled,
    ) { viewModel.setIslandFirstFloat(it) }

    SettingsSwitchItem(
        title = stringResource(R.string.pref_island_enable_float_title),
        summary = stringResource(R.string.pref_island_enable_float_summary),
        checked = islandEnableFloat,
        enabled = islandEnabled,
    ) { viewModel.setIslandEnableFloat(it) }

    SettingsSwitchItem(
        title = stringResource(R.string.pref_island_show_notification_title),
        summary = stringResource(R.string.pref_island_show_notification_summary),
        checked = islandShowNotification,
        enabled = islandEnabled,
    ) { viewModel.setIslandShowNotification(it) }

    SettingsSwitchItem(
        title = stringResource(R.string.pref_island_focus_notif_title),
        summary = stringResource(R.string.pref_island_focus_notif_summary),
        checked = islandFocusNotification,
        enabled = islandEnabled,
    ) { viewModel.setIslandFocusNotification(it) }

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
    var showRuntimeLogInfoDialog by remember { mutableStateOf(false) }
    var runtimeLogDialogData by remember { mutableStateOf<RuntimeLogDialogData?>(null) }
    var showRuntimeLogFullScreenPreview by remember { mutableStateOf(false) }
    var runtimeLogExpandedFormat by rememberSaveable { mutableStateOf(false) }
    var showRuntimeLogRetentionDialog by remember { mutableStateOf(false) }
    var runtimeLogRetentionInput by remember(runtimeLogRetentionDays) {
        mutableStateOf(runtimeLogRetentionDays.toString())
    }
    val runtimeLogExportFailedTemplate = stringResource(R.string.runtime_log_export_failed)
    val logShareTitle = stringResource(R.string.log_share_title)
    val runtimeLogShareFailedTemplate = stringResource(R.string.runtime_log_share_failed)
    val runtimeLogDeleteFailedTemplate = stringResource(R.string.runtime_log_delete_failed)
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

    fun loadRuntimeLogDialog(selectedFileName: String? = null) {
        scope.launch {
            runtimeLogDialogData = withContext(Dispatchers.IO) {
                loadRuntimeLogDialogData(context, viewModel, selectedFileName)
            }
        }
    }

    SettingsItem(
        title = stringResource(R.string.settings_get_log),
        summary = stringResource(R.string.settings_get_log_summary),
    ) {
        runtimeLogDialogData = null
        showRuntimeLogInfoDialog = true
    }

    SettingsItem(
        title = stringResource(R.string.settings_runtime_log_retention_days),
        summary = stringResource(R.string.settings_runtime_log_retention_days_summary, runtimeLogRetentionDays),
    ) {
        runtimeLogRetentionInput = runtimeLogRetentionDays.toString()
        showRuntimeLogRetentionDialog = true
    }

    SettingsItem(
        title = stringResource(R.string.settings_clear_log),
        summary = stringResource(R.string.settings_clear_log_summary),
    ) {
        viewModel.clearLog(context)
    }

    SettingsSwitchItem(
        title = stringResource(R.string.settings_debug_mode),
        summary = stringResource(R.string.settings_debug_mode_summary),
        checked = debugMode,
    ) { viewModel.setDebugMode(it) }

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

    if (showRuntimeLogInfoDialog) {
        LaunchedEffect(showRuntimeLogInfoDialog) {
            runtimeLogDialogData = withContext(Dispatchers.IO) {
                loadRuntimeLogDialogData(context, viewModel, runtimeLogDialogData?.selectedFileName)
            }
        }
        val dialogData = runtimeLogDialogData
        RuntimeLogInfoDialog(
            data = dialogData,
            onDismiss = { showRuntimeLogInfoDialog = false },
            onShare = { shareRuntimeLogBundle() },
            onSelectFile = { fileName -> loadRuntimeLogDialog(fileName) },
            onOpenPreview = {
                runtimeLogExpandedFormat = false
                showRuntimeLogFullScreenPreview = true
            },
            onDeleteFile = { fileName ->
                scope.launch {
                    val files = dialogData?.summary?.files.orEmpty()
                    val selectedIndex = files.indexOfFirst { it.name == fileName }
                    val nextSelection = if (selectedIndex >= 0) {
                        files.getOrNull(selectedIndex + 1)?.name ?: files.getOrNull(selectedIndex - 1)?.name
                    } else {
                        null
                    }
                    val deleted = withContext(Dispatchers.IO) {
                        viewModel.deleteRuntimeLogFile(context, fileName)
                    }
                    showRuntimeLogFullScreenPreview = false
                    runtimeLogDialogData = withContext(Dispatchers.IO) {
                        loadRuntimeLogDialogData(context, viewModel, nextSelection)
                    }
                    if (!deleted) {
                        snackbarHostState.showSnackbar(
                            formatMessage(runtimeLogDeleteFailedTemplate, fileName),
                        )
                    }
                }
            },
            onClear = {
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        viewModel.clearRuntimeLogFolders(context)
                    }
                    runtimeLogDialogData = withContext(Dispatchers.IO) {
                        loadRuntimeLogDialogData(context, viewModel)
                    }
                    snackbarHostState.showSnackbar(
                        if (result.success) {
                            runtimeLogClearedMessage
                        } else {
                            formatMessage(runtimeLogClearPartialFailedTemplate, result.details)
                        },
                    )
                }
            },
        )
        val content = dialogData?.content
        if (showRuntimeLogFullScreenPreview && content != null) {
            RuntimeLogFullScreenPreviewDialog(
                fileName = content.name,
                compactText = dialogData.compactPreview,
                formattedText = dialogData.formattedPreview,
                expandedFormat = runtimeLogExpandedFormat,
                onExpandedFormatChange = { runtimeLogExpandedFormat = it },
                onDismiss = { showRuntimeLogFullScreenPreview = false },
            )
        }
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

private data class RuntimeLogDialogData(
    val summary: ManagerRuntimeLogFileSummary,
    val selectedFileName: String?,
    val content: ManagerRuntimeLogFileContent?,
    val compactPreview: String,
    val formattedPreview: String,
)

@Composable
private fun RuntimeLogInfoDialog(
    data: RuntimeLogDialogData?,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onSelectFile: (String) -> Unit,
    onOpenPreview: () -> Unit,
    onDeleteFile: (String) -> Unit,
    onClear: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.runtime_log_viewer_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (data == null) {
                    Text(text = stringResource(id = R.string.runtime_log_info_loading))
                    return@Column
                }
                val summary = data.summary
                if (summary.fileCount == 0) {
                    Text(text = stringResource(id = R.string.runtime_log_info_empty))
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = stringResource(
                                id = R.string.runtime_log_info_summary,
                                summary.fileCount,
                                summary.entryCount,
                            ),
                            maxLines = 1,
                            softWrap = false,
                        )
                        val first = summary.firstTimestamp
                        val last = summary.lastTimestamp
                        if (first != null && last != null) {
                            Text(
                                text = stringResource(
                                    id = R.string.runtime_log_info_range,
                                    formatLogTimestamp(first),
                                    formatLogTimestamp(last),
                                ),
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 150.dp)
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        summary.files.forEach { file ->
                            val selected = file.name == data.selectedFileName
                            Row(
                                modifier = Modifier
                                    .clickable { onSelectFile(file.name) }
                                    .padding(vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = formatRuntimeLogFileListLine(file, selected),
                                    modifier = Modifier
                                        .padding(vertical = 2.dp),
                                    maxLines = 1,
                                    softWrap = false,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                if (!selected) {
                                    IconButton(onClick = { onDeleteFile(file.name) }) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = stringResource(id = R.string.action_delete),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Text(
                    text = stringResource(id = R.string.runtime_log_info_preview_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                val previewVerticalScroll = rememberScrollState()
                val previewHorizontalScroll = rememberScrollState()
                Text(
                    text = data.compactPreview.ifBlank { stringResource(id = R.string.runtime_log_info_empty) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(previewVerticalScroll)
                        .horizontalScroll(previewHorizontalScroll)
                        .clickable(enabled = data.content != null, onClick = onOpenPreview),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    softWrap = false,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onShare, enabled = data != null) {
                Text(text = stringResource(id = R.string.action_share))
            }
        },
        dismissButton = {
            TextButton(onClick = onClear, enabled = data != null) {
                Text(text = stringResource(id = R.string.action_clear))
            }
        },
    )
}

@Composable
private fun RuntimeLogFullScreenPreviewDialog(
    fileName: String,
    compactText: String,
    formattedText: String,
    expandedFormat: Boolean,
    onExpandedFormatChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(text = fileName, maxLines = 1, softWrap = false) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(id = android.R.string.cancel),
                                )
                            }
                        },
                        actions = {
                            TextButton(onClick = { onExpandedFormatChange(!expandedFormat) }) {
                                Text(
                                    text = stringResource(
                                        id = if (expandedFormat) {
                                            R.string.runtime_log_action_compact
                                        } else {
                                            R.string.runtime_log_action_format
                                        },
                                    ),
                                )
                            }
                        },
                    )
                },
            ) { padding ->
                val vertical = rememberScrollState()
                val horizontal = rememberScrollState()
                val text = if (expandedFormat) formattedText else compactText
                Text(
                    text = text.ifBlank { stringResource(id = R.string.runtime_log_info_empty) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(12.dp)
                        .verticalScroll(vertical)
                        .horizontalScroll(horizontal),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    softWrap = false,
                )
            }
        }
    }
}

private fun loadRuntimeLogDialogData(
    context: Context,
    viewModel: SettingsViewModel,
    selectedFileName: String? = null,
): RuntimeLogDialogData {
    val summary = runCatching {
        viewModel.summarizeRuntimeLogFiles(context)
    }.getOrElse {
        ManagerRuntimeLogFileSummary(
            fileCount = 0,
            totalBytes = 0L,
            entryCount = 0,
            firstTimestamp = null,
            lastTimestamp = null,
            files = emptyList(),
        )
    }
    val selected = selectRuntimeLogFile(summary, selectedFileName)
    val content = selected?.let { fileName ->
        runCatching { viewModel.readRuntimeLogFile(context, fileName) }.getOrNull()
    }
    val compactPreview = content?.let { formatRuntimeLogContent(it.name, it.text, expanded = false) }.orEmpty()
    val formattedPreview = content?.let { formatRuntimeLogContent(it.name, it.text, expanded = true) }.orEmpty()
    return RuntimeLogDialogData(
        summary = summary,
        selectedFileName = selected,
        content = content,
        compactPreview = compactPreview,
        formattedPreview = formattedPreview,
    )
}

private fun selectRuntimeLogFile(summary: ManagerRuntimeLogFileSummary, selectedFileName: String?): String? {
    val files = summary.files
    if (files.any { it.name == selectedFileName }) return selectedFileName
    return files.lastOrNull { it.name.matches(Regex("""runtime\.\d{4}-\d{2}-\d{2}\.jsonl""")) }?.name
        ?: files.lastOrNull()?.name
}

private fun formatRuntimeLogFileListLine(file: ManagerRuntimeLogFileInfo, selected: Boolean): String {
    val marker = if (selected) "*" else " "
    val lines = file.lineCount.toString().padStart(5)
    val size = formatLogSize(file.sizeBytes).padStart(8)
    val modified = file.lastTimestamp?.let(::formatLogTimestamp).orEmpty().padEnd(19)
    return "$marker -rw------- $lines $size $modified ${file.name}"
}

private fun formatRuntimeLogContent(fileName: String, text: String, expanded: Boolean): String {
    if (!fileName.endsWith(".jsonl")) return text
    val separator = if (expanded) "\n\n" else "\n"
    return text.lineSequence()
        .filter { it.isNotBlank() }
        .joinToString(separator = separator) { line ->
            formatJsonLine(line, expanded)
        }
}

private fun formatJsonLine(line: String, expanded: Boolean): String {
    return runCatching {
        when (val value = ConfigJson.parse(line)) {
            is ConfigJsonObject -> if (expanded) value.toString(2) else value.toString()
            is ConfigJsonArray -> if (expanded) value.toString(2) else value.toString()
            else -> line
        }
    }.getOrDefault(line)
}

private fun formatLogTimestamp(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

private fun formatLogSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var value = bytes.toDouble() / 1024.0
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return String.format(Locale.getDefault(), "%.1f %s", value, units[unitIndex])
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
