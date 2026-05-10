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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.magisk317.mipush.main.viewmodel.SettingsViewModel
import com.xiaomi.xmsf.BuildConfig
import com.xiaomi.xmsf.R
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import io.github.magisk317.uikit.preference.SectionCard
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.feature.main.MainActivityOperation
import io.github.magisk317.mipush.feature.ui.component.DialogAction
import io.github.magisk317.mipush.feature.ui.component.SectionColumn
import io.github.magisk317.mipush.feature.ui.component.SettingsDialogItem
import io.github.magisk317.mipush.feature.ui.component.SettingsItem
import io.github.magisk317.mipush.feature.ui.component.SettingsSwitchItem
import io.github.magisk317.mipush.platform.support.LegacyUiEntryPoints
import io.github.magisk317.mipush.feature.ui.theme.Theme
import io.github.magisk317.mipush.feature.ui.theme.spacing
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import io.github.magisk317.mipush.service.KeepAliveAccessibilityService
import io.github.magisk317.mipush.utils.LogBundleExporter
import io.github.magisk317.mipush.utils.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun Settings(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: SettingsViewModel = hiltViewModel(),
    onShowAboutDialog: (String) -> Unit = {},
    onSectionChanged: (String?) -> Unit = {},
    sectionBackSignal: Int = 0,
    hazeState: HazeState? = null,
    hazeStyle: HazeStyle? = null,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    Page {
        Box(modifier = Modifier.fillMaxSize()) {
            SettingsScreen(
                contentPadding = contentPadding,
                onShowAboutDialog = onShowAboutDialog,
                viewModel = viewModel,
                onSectionChanged = onSectionChanged,
                sectionBackSignal = sectionBackSignal,
                hazeState = hazeState,
                hazeStyle = hazeStyle,
                snackbarHostState = snackbarHostState,
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    contentPadding: PaddingValues,
    onShowAboutDialog: (String) -> Unit,
    viewModel: SettingsViewModel,
    onSectionChanged: (String?) -> Unit,
    sectionBackSignal: Int,
    hazeState: HazeState?,
    hazeStyle: HazeStyle?,
    snackbarHostState: SnackbarHostState,
) {
    val title = stringResource(R.string.main_settings)
    val density = LocalDensity.current
    var fixedTopHeightPx by remember { mutableIntStateOf(0) }
    var serviceExpanded by rememberSaveable { mutableStateOf(false) }
    var displayExpanded by rememberSaveable { mutableStateOf(false) }
    var dataExpanded by rememberSaveable { mutableStateOf(false) }
    var developerExpanded by rememberSaveable { mutableStateOf(false) }
    var aboutExpanded by rememberSaveable { mutableStateOf(false) }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val fixedTopHeight = if (fixedTopHeightPx > 0) {
        with(density) { fixedTopHeightPx.toDp() }
    } else {
        topInset + 64.dp
    }

    LaunchedEffect(title) {
        onSectionChanged(title)
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                .verticalScroll(rememberScrollState()),
            contentPadding = PaddingValues(
                start = MaterialTheme.spacing.medium,
                top = fixedTopHeight + MaterialTheme.spacing.small,
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
                ServiceConfigurationBlock(viewModel, snackbarHostState)
            }

            SettingsSectionCard(
                title = stringResource(R.string.settings_home_display_title),
                expanded = displayExpanded,
                onExpandedChange = { displayExpanded = !displayExpanded },
            ) {
                DisplayBlock(viewModel)
            }

            SettingsSectionCard(
                title = stringResource(R.string.settings_home_data_title),
                expanded = dataExpanded,
                onExpandedChange = { dataExpanded = !dataExpanded },
            ) {
                DataMaintenanceBlock(viewModel, snackbarHostState)
            }

            SettingsSectionCard(
                title = stringResource(R.string.settings_home_developer_title),
                expanded = developerExpanded,
                onExpandedChange = { developerExpanded = !developerExpanded },
            ) {
                ExperimentalBlock(viewModel)
            }

            SettingsSectionCard(
                title = stringResource(R.string.action_about),
                expanded = aboutExpanded,
                onExpandedChange = { aboutExpanded = !aboutExpanded },
            ) {
                AboutBlock(onShowAboutDialog)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { fixedTopHeightPx = it.height }
                .then(
                    if (hazeState != null && hazeStyle != null) {
                        Modifier.hazeEffect(hazeState, hazeStyle) {
                            forceInvalidateOnPreDraw = true
                        }
                    } else {
                        Modifier
                    }
                ),
        ) {
            TopAppBar(
                title = { Text(title) },
                windowInsets = WindowInsets.statusBars,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            )
        }
    }
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
private fun ServiceConfigurationBlock(viewModel: SettingsViewModel, snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isStartForeground by viewModel.isStartForeground.collectAsStateWithLifecycle()
    val notificationOnRegister by viewModel.notificationOnRegister.collectAsStateWithLifecycle()

    SetXMPPServer(viewModel)
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
    val notificationOnRegisterDisabledMessage = stringResource(R.string.notification_on_register_global_disabled_hint)

    SettingsSwitchItem(
        title = stringResource(R.string.settings_start_foreground_service),
        summary = stringResource(R.string.settings_start_foreground_service_summary),
        checked = isStartForeground,
    ) {
        viewModel.setStartForeground(it)
        viewModel.startMiPushServiceAsForegroundService(context)
    }

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


    SettingsSwitchItem(
        title = stringResource(R.string.settings_notify_on_register),
        checked = notificationOnRegister,
    ) { newValue ->
        viewModel.setNotificationOnRegister(newValue)
        if (!newValue) {
            scope.launch(Dispatchers.IO) {
                RegisteredApplicationDb.updateAllNotificationOnRegister(false)
            }
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = notificationOnRegisterDisabledMessage,
                    duration = SnackbarDuration.Short,
                )
            }
        }
    }

    SettingsItem(
        title = stringResource(R.string.settings_permission_check),
        summary = stringResource(R.string.settings_permission_check_summary),
    ) {
        context.startActivity(LegacyUiEntryPoints.requestPermissionIntent(context, recheckOnly = true))
    }
}

@Composable
private fun DisplayBlock(viewModel: SettingsViewModel) {
    val showAllEvents by viewModel.showAllEvents.collectAsStateWithLifecycle()
    val showConfigurationList by viewModel.showConfigurationList.collectAsStateWithLifecycle()

    SettingsSwitchItem(
        title = stringResource(R.string.settings_show_all_events),
        checked = showAllEvents,
    ) { viewModel.setShowAllEvents(it) }


    SettingsSwitchItem(
        title = stringResource(R.string.settings_show_loaded_file_after_configurations_loaded),
        checked = showConfigurationList,
    ) { viewModel.setShowConfigurationList(it) }
}

@Composable
private fun DataMaintenanceBlock(viewModel: SettingsViewModel, snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val debugMode by viewModel.debugMode.collectAsStateWithLifecycle()
    val runtimeLogRetentionDays by viewModel.runtimeLogRetentionDays.collectAsStateWithLifecycle()
    var showRuntimeLogInfoDialog by remember { mutableStateOf(false) }
    var runtimeLogDialogData by remember { mutableStateOf<RuntimeLogDialogData?>(null) }
    var showRuntimeLogFullScreenPreview by remember { mutableStateOf(false) }
    var runtimeLogWrapLines by rememberSaveable { mutableStateOf(false) }
    var showRuntimeLogRetentionDialog by remember { mutableStateOf(false) }
    var runtimeLogRetentionInput by remember(runtimeLogRetentionDays) {
        mutableStateOf(runtimeLogRetentionDays.toString())
    }

    fun shareRuntimeLogBundle() {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                LogBundleExporter.buildLogBundle(context)
            }
            val file = result.file
            if (file == null) {
                snackbarHostState.showSnackbar(context.getString(R.string.runtime_log_export_failed, result.details))
                return@launch
            }
            runCatching {
                val intent = LogBundleExporter.buildShareIntent(context, file)
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.log_share_title)))
            }.onFailure {
                snackbarHostState.showSnackbar(
                    context.getString(
                        R.string.runtime_log_share_failed,
                        it.message ?: it.javaClass.simpleName,
                    ),
                )
            }
        }
    }

    fun loadRuntimeLogDialog(selectedFileName: String? = null) {
        scope.launch {
            runtimeLogDialogData = withContext(Dispatchers.IO) {
                loadRuntimeLogDialogData(context, selectedFileName)
            }
        }
    }

    SettingsItem(
        title = stringResource(R.string.settings_clear_history),
        summary = stringResource(R.string.settings_clear_history_summary),
    ) {
        viewModel.clearHistory(context)
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


    SettingsSwitchItem(
        title = stringResource(R.string.settings_debug_mode),
        summary = stringResource(R.string.settings_debug_mode_summary),
        checked = debugMode,
    ) { viewModel.setDebugMode(it) }

    if (showRuntimeLogInfoDialog) {
        LaunchedEffect(showRuntimeLogInfoDialog) {
            runtimeLogDialogData = withContext(Dispatchers.IO) {
                loadRuntimeLogDialogData(context, runtimeLogDialogData?.selectedFileName)
            }
        }
        val dialogData = runtimeLogDialogData
        RuntimeLogInfoDialog(
            data = dialogData,
            onDismiss = { showRuntimeLogInfoDialog = false },
            onShare = { shareRuntimeLogBundle() },
            onSelectFile = { fileName -> loadRuntimeLogDialog(fileName) },
            onOpenPreview = { showRuntimeLogFullScreenPreview = true },
            onClear = {
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        LogBundleExporter.clearLogFolders(context)
                    }
                    runtimeLogDialogData = withContext(Dispatchers.IO) {
                        loadRuntimeLogDialogData(context)
                    }
                    snackbarHostState.showSnackbar(
                        if (result.success) {
                            context.getString(R.string.runtime_log_cleared)
                        } else {
                            context.getString(R.string.runtime_log_clear_partial_failed, result.details)
                        },
                    )
                }
            },
        )
        val content = dialogData?.content
        if (showRuntimeLogFullScreenPreview && content != null) {
            RuntimeLogFullScreenPreviewDialog(
                fileName = content.name,
                text = dialogData.formattedPreview,
                wrapLines = runtimeLogWrapLines,
                onWrapLinesChange = { runtimeLogWrapLines = it },
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
                                    context.getString(R.string.settings_runtime_log_retention_days_error),
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
    val summary: LogUtils.RuntimeLogFileSummary,
    val selectedFileName: String?,
    val content: LogUtils.RuntimeLogFileContent?,
    val formattedPreview: String,
)

@Composable
private fun RuntimeLogInfoDialog(
    data: RuntimeLogDialogData?,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onSelectFile: (String) -> Unit,
    onOpenPreview: () -> Unit,
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
                                formatLogSize(summary.totalBytes),
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
                            Text(
                                text = formatRuntimeLogFileListLine(file, selected),
                                modifier = Modifier
                                    .clickable { onSelectFile(file.name) }
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
                        }
                    }
                }
                Text(
                    text = stringResource(id = R.string.runtime_log_info_preview_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = data.formattedPreview.ifBlank { stringResource(id = R.string.runtime_log_info_empty) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                        .clickable(enabled = data.content != null, onClick = onOpenPreview),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    softWrap = true,
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
    text: String,
    wrapLines: Boolean,
    onWrapLinesChange: (Boolean) -> Unit,
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
                            TextButton(onClick = { onWrapLinesChange(!wrapLines) }) {
                                Text(
                                    text = stringResource(
                                        id = if (wrapLines) {
                                            R.string.runtime_log_action_no_wrap
                                        } else {
                                            R.string.runtime_log_action_wrap
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
                Text(
                    text = text.ifBlank { stringResource(id = R.string.runtime_log_info_empty) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(12.dp)
                        .verticalScroll(vertical)
                        .then(if (wrapLines) Modifier else Modifier.horizontalScroll(horizontal)),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    softWrap = wrapLines,
                )
            }
        }
    }
}

private fun loadRuntimeLogDialogData(context: Context, selectedFileName: String? = null): RuntimeLogDialogData {
    val summary = runCatching {
        LogUtils.summarizeFiles(context)
    }.getOrElse {
        LogUtils.RuntimeLogFileSummary(
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
        runCatching { LogUtils.readLogFile(context, fileName) }.getOrNull()
    }
    val preview = content?.let { formatRuntimeLogContent(it.name, it.text) }.orEmpty()
    return RuntimeLogDialogData(
        summary = summary,
        selectedFileName = selected,
        content = content,
        formattedPreview = preview,
    )
}

private fun selectRuntimeLogFile(summary: LogUtils.RuntimeLogFileSummary, selectedFileName: String?): String? {
    val files = summary.files
    if (files.any { it.name == selectedFileName }) return selectedFileName
    return files.lastOrNull { it.name.matches(Regex("""runtime\.\d{4}-\d{2}-\d{2}\.jsonl""")) }?.name
        ?: files.lastOrNull()?.name
}

private fun formatRuntimeLogFileListLine(file: LogUtils.RuntimeLogFileInfo, selected: Boolean): String {
    val marker = if (selected) "*" else " "
    val lines = file.lineCount.toString().padStart(5)
    val size = formatLogSize(file.sizeBytes).padStart(8)
    val modified = file.lastTimestamp?.let(::formatLogTimestamp).orEmpty().padEnd(19)
    return "$marker -rw------- $lines $size $modified ${file.name}"
}

private fun formatRuntimeLogContent(fileName: String, text: String): String {
    if (!fileName.endsWith(".jsonl")) return text
    return text.lineSequence()
        .filter { it.isNotBlank() }
        .joinToString(separator = "\n\n") { line ->
            formatJsonLine(line)
        }
}

private fun formatJsonLine(line: String): String {
    return runCatching {
        when (val value = JSONTokener(line).nextValue()) {
            is JSONObject -> value.toString(2)
            is JSONArray -> value.toString(2)
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
private fun ExperimentalBlock(viewModel: SettingsViewModel) {
    val context = LocalContext.current

    SettingsItem(
        title = stringResource(R.string.settings_mock_notification),
        summary = stringResource(R.string.settings_mock_notification_summary),
    ) {
        viewModel.notifyMockNotification(context)
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
    val expected = ComponentName(context, KeepAliveAccessibilityService::class.java)
    return enabledServices.split(':').any { service ->
        val component = ComponentName.unflattenFromString(service) ?: return@any false
        component.packageName == expected.packageName && component.className == expected.className
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
