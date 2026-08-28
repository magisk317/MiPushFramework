@file:android.annotation.SuppressLint("LocalContextGetResourceValueCall")
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
import androidx.compose.runtime.withFrameNanos
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
import io.github.magisk317.mipush.common.BuildConfig
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
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.magisk317.mipush.common.configurations.ConfigJson
import io.github.magisk317.mipush.common.configurations.ConfigJsonArray
import io.github.magisk317.mipush.common.configurations.ConfigJsonObject
import org.koin.compose.viewmodel.koinViewModel
import java.util.Locale

@Composable
internal fun SettingsSectionCard(
    title: String,
    summary: String,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    SectionCard(
        title = title,
        summary = summary,
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
internal fun rememberSwitchFeedback(snackbarHostState: SnackbarHostState): (String, Boolean, Boolean) -> Unit {
    val scope = rememberCoroutineScope()
    val enabledTemplate = stringResource(R.string.settings_switch_enabled_feedback)
    val disabledTemplate = stringResource(R.string.settings_switch_disabled_feedback)
    val failedTemplate = stringResource(R.string.settings_runtime_preference_update_failed)
    return remember(snackbarHostState, scope, enabledTemplate, disabledTemplate, failedTemplate) {
        { title, enabled, success ->
            val template = when {
                !success -> failedTemplate
                enabled -> enabledTemplate
                else -> disabledTemplate
            }
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
internal fun ConnectionServiceBlock(viewModel: SettingsViewModel, snackbarHostState: SnackbarHostState, onNavigateToConnectionStatus: () -> Unit) {
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
internal fun AppearanceBlock(
    viewModel: SettingsViewModel,
    onNavigateToStatusBarIconSettings: () -> Unit,
) {
    val context = LocalContext.current
    val selectedLauncherIcon by viewModel.selectedLauncherIcon.collectAsStateWithLifecycle()
    val normalizedLauncherIcon = if (selectedLauncherIcon == "legacy") "legacy" else "default"
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
        title = stringResource(R.string.pref_color_status_bar_icon_title),
        summary = stringResource(R.string.pref_color_status_bar_icon_summary),
        onClick = onNavigateToStatusBarIconSettings,
    )

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
}

@Composable
internal fun ConfigurationsBlock(
    viewModel: SettingsViewModel,
    snackbarHostState: SnackbarHostState,
    onNavigateToConfigurations: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    SettingsItem(
        title = stringResource(R.string.main_configs),
        summary = stringResource(R.string.settings_configurations_entry_summary),
        onClick = onNavigateToConfigurations,
    )

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
}

@Composable
internal fun IntegrationsBlock(
    viewModel: SettingsViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dualAppEnabled by viewModel.dualAppEnabled.collectAsStateWithLifecycle()
    val dualAppProcessing by viewModel.dualAppProcessing.collectAsStateWithLifecycle()

    SettingsSwitchItem(
        title = stringResource(R.string.settings_dual_app_title),
        summary = stringResource(
            if (viewModel.canManageDualApp) {
                R.string.settings_dual_app_summary
            } else {
                R.string.settings_dual_app_primary_user_only_summary
            },
        ),
        checked = dualAppEnabled,
        enabled = viewModel.canManageDualApp && !dualAppProcessing,
    ) { enabled ->
        viewModel.setDualAppEnabled(enabled) { _, message ->
            scope.launch {
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    SettingsItem(
        title = stringResource(R.string.zygisk_status),
        summary = stringResource(R.string.zygisk_status_summary),
    ) {
        context.startActivity(
            Intent(context, io.github.magisk317.mipush.feature.main.ZygiskConfigPage::class.java),
        )
    }
}

@Composable
internal fun NotificationsBlock(viewModel: SettingsViewModel, snackbarHostState: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val islandEnabled by viewModel.islandEnabled.collectAsStateWithLifecycle()
    val islandTimeout by viewModel.islandTimeout.collectAsStateWithLifecycle()
    val islandFirstFloat by viewModel.islandFirstFloat.collectAsStateWithLifecycle()
    val islandEnableFloat by viewModel.islandEnableFloat.collectAsStateWithLifecycle()
    val islandShowNotification by viewModel.islandShowNotification.collectAsStateWithLifecycle()
    val islandShowOriginalNotification by viewModel.islandShowOriginalNotification.collectAsStateWithLifecycle()
    val islandFocusNotification by viewModel.islandFocusNotification.collectAsStateWithLifecycle()
    val islandRendererMode by viewModel.islandRendererMode.collectAsStateWithLifecycle()
    val islandVisualEnabled by viewModel.islandVisualEnabled.collectAsStateWithLifecycle()
    val islandDynamicColor by viewModel.islandDynamicColor.collectAsStateWithLifecycle()
    val islandBlurEnabled by viewModel.islandBlurEnabled.collectAsStateWithLifecycle()
    val islandGlassEnabled by viewModel.islandGlassEnabled.collectAsStateWithLifecycle()
    val islandOuterGlowEnabled by viewModel.islandOuterGlowEnabled.collectAsStateWithLifecycle()
    val islandAnimationEnabled by viewModel.islandAnimationEnabled.collectAsStateWithLifecycle()
    val showSwitchFeedback = rememberSwitchFeedback(snackbarHostState)
    var showIslandTimeoutDialog by remember { mutableStateOf(false) }
    var showIslandRendererDialog by remember { mutableStateOf(false) }
    var islandTimeoutInput by remember(islandTimeout) { mutableStateOf(islandTimeout.toString()) }
    val islandTimeoutError = stringResource(R.string.pref_island_timeout_error)

    val islandEnabledTitle = stringResource(R.string.pref_island_enabled_title)
    SettingsSwitchItem(
        title = islandEnabledTitle,
        summary = stringResource(R.string.pref_island_enabled_summary),
        checked = islandEnabled,
    ) { enabled ->
        viewModel.setIslandEnabled(enabled) { success ->
            if (success) notifyPrefChanged(context)
            showSwitchFeedback(islandEnabledTitle, enabled, success)
        }
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
        viewModel.setIslandFirstFloat(enabled) { success ->
            if (success) notifyPrefChanged(context)
            showSwitchFeedback(islandFirstFloatTitle, enabled, success)
        }
    }

    val islandEnableFloatTitle = stringResource(R.string.pref_island_enable_float_title)
    SettingsSwitchItem(
        title = islandEnableFloatTitle,
        summary = stringResource(R.string.pref_island_enable_float_summary),
        checked = islandEnableFloat,
        enabled = islandEnabled,
    ) { enabled ->
        viewModel.setIslandEnableFloat(enabled) { success ->
            if (success) notifyPrefChanged(context)
            showSwitchFeedback(islandEnableFloatTitle, enabled, success)
        }
    }

    val islandShowNotificationTitle = stringResource(R.string.pref_island_show_notification_title)
    SettingsSwitchItem(
        title = islandShowNotificationTitle,
        summary = stringResource(R.string.pref_island_show_notification_summary),
        checked = islandShowNotification,
        enabled = islandEnabled,
    ) { enabled ->
        viewModel.setIslandShowNotification(enabled) { success ->
            if (success) notifyPrefChanged(context)
            showSwitchFeedback(islandShowNotificationTitle, enabled, success)
        }
    }

    val islandShowOriginalNotificationTitle = stringResource(R.string.pref_island_show_original_notification_title)
    SettingsSwitchItem(
        title = islandShowOriginalNotificationTitle,
        summary = stringResource(R.string.pref_island_show_original_notification_summary),
        checked = islandShowOriginalNotification,
        enabled = islandEnabled,
    ) { enabled ->
        viewModel.setIslandShowOriginalNotification(enabled) { success ->
            if (success) notifyPrefChanged(context)
            showSwitchFeedback(islandShowOriginalNotificationTitle, enabled, success)
        }
    }

    val islandFocusNotificationTitle = stringResource(R.string.pref_island_focus_notif_title)
    SettingsSwitchItem(
        title = islandFocusNotificationTitle,
        summary = stringResource(R.string.pref_island_focus_notif_summary),
        checked = islandFocusNotification,
        enabled = islandEnabled,
    ) { enabled ->
        viewModel.setIslandFocusNotification(enabled) { success ->
            if (success) notifyPrefChanged(context)
            showSwitchFeedback(islandFocusNotificationTitle, enabled, success)
        }
    }

    val rendererModeTitle = stringResource(R.string.pref_island_renderer_mode_title)
    val rendererModeLabel = when (islandRendererMode) {
        "mipush" -> stringResource(R.string.pref_island_renderer_mode_mipush)
        "hyperisland" -> stringResource(R.string.pref_island_renderer_mode_hyperisland)
        else -> stringResource(R.string.pref_island_renderer_mode_auto)
    }
    SettingsItem(
        title = rendererModeTitle,
        summary = "$rendererModeLabel - ${stringResource(R.string.pref_island_renderer_mode_summary)}",
        enabled = islandEnabled,
    ) {
        showIslandRendererDialog = true
    }

    val visualTitle = stringResource(R.string.pref_island_visual_title)
    SettingsSwitchItem(
        title = visualTitle,
        summary = stringResource(R.string.pref_island_visual_summary),
        checked = islandVisualEnabled,
        enabled = islandEnabled,
    ) { enabled ->
        viewModel.setIslandVisualEnabled(enabled) { success ->
            if (success) notifyPrefChanged(context)
            showSwitchFeedback(visualTitle, enabled, success)
        }
    }

    val dynamicColorTitle = stringResource(R.string.pref_island_dynamic_color_title)
    SettingsSwitchItem(
        title = dynamicColorTitle,
        summary = stringResource(R.string.pref_island_dynamic_color_summary),
        checked = islandDynamicColor,
        enabled = islandEnabled && islandVisualEnabled,
    ) { enabled ->
        viewModel.setIslandDynamicColor(enabled) { success ->
            if (success) notifyPrefChanged(context)
            showSwitchFeedback(dynamicColorTitle, enabled, success)
        }
    }

    val blurTitle = stringResource(R.string.pref_island_blur_title)
    SettingsSwitchItem(
        title = blurTitle,
        summary = stringResource(R.string.pref_island_blur_summary),
        checked = islandBlurEnabled,
        enabled = islandEnabled && islandVisualEnabled,
    ) { enabled ->
        viewModel.setIslandBlurEnabled(enabled) { success ->
            if (success) notifyPrefChanged(context)
            showSwitchFeedback(blurTitle, enabled, success)
        }
    }

    val glassTitle = stringResource(R.string.pref_island_glass_title)
    SettingsSwitchItem(
        title = glassTitle,
        summary = stringResource(R.string.pref_island_glass_summary),
        checked = islandGlassEnabled,
        enabled = islandEnabled && islandVisualEnabled,
    ) { enabled ->
        viewModel.setIslandGlassEnabled(enabled) { success ->
            if (success) notifyPrefChanged(context)
            showSwitchFeedback(glassTitle, enabled, success)
        }
    }

    val outerGlowTitle = stringResource(R.string.pref_island_outer_glow_title)
    SettingsSwitchItem(
        title = outerGlowTitle,
        summary = stringResource(R.string.pref_island_outer_glow_summary),
        checked = islandOuterGlowEnabled,
        enabled = islandEnabled && islandVisualEnabled,
    ) { enabled ->
        viewModel.setIslandOuterGlowEnabled(enabled) { success ->
            if (success) notifyPrefChanged(context)
            showSwitchFeedback(outerGlowTitle, enabled, success)
        }
    }

    val animationTitle = stringResource(R.string.pref_island_animation_title)
    SettingsSwitchItem(
        title = animationTitle,
        summary = stringResource(R.string.pref_island_animation_summary),
        checked = islandAnimationEnabled,
        enabled = islandEnabled && islandVisualEnabled,
    ) { enabled ->
        viewModel.setIslandAnimationEnabled(enabled) { success ->
            if (success) notifyPrefChanged(context)
            showSwitchFeedback(animationTitle, enabled, success)
        }
    }

    if (showIslandRendererDialog) {
        val rendererOptions = listOf(
            "auto" to stringResource(R.string.pref_island_renderer_mode_auto),
            "mipush" to stringResource(R.string.pref_island_renderer_mode_mipush),
            "hyperisland" to stringResource(R.string.pref_island_renderer_mode_hyperisland),
        )
        AlertDialog(
            onDismissRequest = { showIslandRendererDialog = false },
            title = { Text(rendererModeTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    rendererOptions.forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setIslandRendererMode(mode) { success ->
                                        if (success) notifyPrefChanged(context)
                                        showSwitchFeedback(rendererModeTitle, true, success)
                                    }
                                    showIslandRendererDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = islandRendererMode == mode,
                                onClick = null,
                            )
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIslandRendererDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
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
                            viewModel.setIslandTimeout(days) { success ->
                                if (success) notifyPrefChanged(context)
                                showSwitchFeedback(
                                    context.getString(R.string.pref_island_timeout_title),
                                    true,
                                    success,
                                )
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

internal fun notifyPrefChanged(context: Context) {
    context.sendBroadcast(Intent(ACTION_PREF_CHANGED))
}

@Composable
internal fun DiagnosticsBlock(viewModel: SettingsViewModel, snackbarHostState: SnackbarHostState) {
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
    val runtimeLogSavedMessage = stringResource(R.string.runtime_log_saved)
    val runtimeLogClearedMessage = stringResource(R.string.runtime_log_cleared)
    val runtimeLogClearPartialFailedTemplate = stringResource(R.string.runtime_log_clear_partial_failed)
    val runtimeLogRetentionDaysError = stringResource(R.string.settings_runtime_log_retention_days_error)

    fun formatMessage(template: String, vararg args: Any?): String {
        return String.format(Locale.getDefault(), template, *args)
    }

    val saveRuntimeLogLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { destination ->
        if (destination == null) return@rememberLauncherForActivityResult
        Toast.makeText(context, R.string.runtime_log_exporting, Toast.LENGTH_SHORT).show()
        viewModel.saveRuntimeLogBundle(context, destination) { result ->
            val message = if (result.success) {
                runtimeLogSavedMessage
            } else {
                formatMessage(runtimeLogExportFailedTemplate, result.details)
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    fun saveRuntimeLogBundle() {
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
            .format(java.util.Date())
        saveRuntimeLogLauncher.launch("mipush_logs_$timestamp.zip")
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
            viewModel.setAnalyticsEnabled(enabled) { success ->
                if (success) notifyPrefChanged(context)
                showSwitchFeedback(analyticsTitle, enabled, success)
            }
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
            onShareLog = ::saveRuntimeLogBundle,
            onVerboseLogEnabledChange = { enabled ->
                viewModel.setDebugMode(enabled) { success ->
                    showSwitchFeedback(debugModeTitle, enabled, success)
                }
            },
            onRetentionClick = { showRuntimeLogRetentionDialog = true },
            onClearLogClick = { showClearConfirmDialog = true },
            onSensitiveLogEnabledChange = { enabled ->
                viewModel.setLogSanitizationEnabled(enabled) { success ->
                    if (success) context.sendBroadcast(Intent(ACTION_PREF_CHANGED))
                    showSwitchFeedback(logSanitizationTitle, enabled, success)
                }
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
            viewModel.setRuntimeLogRetentionDays(days) { success ->
                if (success) {
                    showRuntimeLogRetentionDialog = false
                } else {
                    showSwitchFeedback(
                        context.getString(R.string.settings_runtime_log_retention_days),
                        true,
                        false,
                    )
                }
            }
        }
    }
}

@Composable
internal fun AboutBlock(onShowAboutDialog: (String) -> Unit) {
    val context = LocalContext.current
    val mainActivityOperation = MainActivityOperation(context)

    SettingsItem(
        title = stringResource(R.string.action_about),
    ) {
        mainActivityOperation.showAboutDialog(onShowAboutDialog)
    }
}

@Composable
internal fun SetXMPPServer() {
    XmppServerEditor { uiState, onEdit ->
        SettingsItem(
            title = stringResource(R.string.settings_XMPP_server),
            summary = when {
                uiState.loadFailed -> stringResource(R.string.settings_XMPP_server_load_failed)
                else -> uiState.configuredServer?.takeIf(String::isNotBlank)
                    ?: stringResource(R.string.settings_XMPP_server_summary)
            },
            enabled = uiState.isLoaded && !uiState.isSaving,
            onClick = onEdit,
        )
    }
}

@Preview(showBackground = true)


@Composable
internal fun LauncherIconPickerDialog(
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
