@file:android.annotation.SuppressLint("LocalContextGetResourceValueCall")
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.magisk317.mipush.feature.main.subpage
import android.content.Context
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import android.content.Intent
import android.widget.Toast
import io.github.magisk317.mipush.common.utils.VerboseLogEnableTracker
import com.topjohnwu.superuser.Shell
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import io.github.magisk317.uikit.surface.AppAlertDialog
import androidx.compose.material3.MaterialTheme
import io.github.magisk317.uikit.common.AppSnackbarDuration
import io.github.magisk317.uikit.common.AppSnackbarHostState
import androidx.compose.material3.Text
import io.github.magisk317.uikit.surface.AppTextButton
import io.github.magisk317.uikit.surface.AppTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.mipush.common.ACTION_PREF_CHANGED
import io.github.magisk317.mipush.feature.wizard.RequestPermissionPage
import io.github.magisk317.uikit.preference.Item as SettingsItem
import io.github.magisk317.uikit.preference.StateSwitchItem as SettingsSwitchItem
import io.github.magisk317.mipush.feature.ui.theme.Theme
import io.github.magisk317.uikit.theme.spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
internal fun rememberSwitchFeedback(snackbarHostState: AppSnackbarHostState): (String, Boolean, Boolean) -> Unit {
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
                    duration = AppSnackbarDuration.Short,
                )
            }
        }
    }
}

@Composable
internal fun ConnectionServiceBlock(viewModel: SettingsViewModel, snackbarHostState: AppSnackbarHostState, onNavigateToConnectionStatus: () -> Unit) {
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

private enum class AppearanceChoice {
    Theme,
    UiKitStyle,
    LayoutScale,
    PaletteStyle,
    ColorSpec,
    Accent,
}

@Composable
internal fun AppearanceBlock(
    viewModel: SettingsViewModel,
    onNavigateToStatusBarIconSettings: () -> Unit,
    onNavigateToThemeSettings: () -> Unit,
) {
    SettingsItem(
        title = stringResource(R.string.pref_choose_theme_title),
        summary = stringResource(R.string.pref_theme_details_summary),
        onClick = { onNavigateToThemeSettings() },
    )
    SettingsItem(
        title = stringResource(R.string.pref_color_status_bar_icon_title),
        summary = stringResource(R.string.pref_color_status_bar_icon_summary),
        onClick = { onNavigateToStatusBarIconSettings() },
    )
}

@Composable
internal fun ConfigurationsBlock(
    onNavigateToConfigurations: () -> Unit,
) {
    SettingsItem(
        title = stringResource(R.string.settings_configurations_management_title),
        summary = stringResource(R.string.settings_configurations_entry_summary),
        onClick = onNavigateToConfigurations,
    )
}

@Composable
internal fun IntegrationsBlock(
    viewModel: SettingsViewModel,
    snackbarHostState: AppSnackbarHostState,
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
internal fun NotificationsBlock(viewModel: SettingsViewModel, snackbarHostState: AppSnackbarHostState) {
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
    val islandTimeoutState = remember(islandTimeout) { TextFieldState(islandTimeout.toString()) }
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
        summary = pluralStringResource(
            R.plurals.pref_island_timeout_summary,
            islandTimeout,
            islandTimeout,
        ),
        enabled = islandEnabled,
    ) {
        islandTimeoutState.setTextAndPlaceCursorAtEnd(islandTimeout.toString())
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
        // Reflect the effective value: when the dynamic island is disabled the original is always
        // shown (no proxy can take over), so the toggle reads as enabled/on instead of a misleading
        // greyed-off state. See the island-disabled convergence in IslandOptionsSnapshotReader.merge.
        checked = !islandEnabled || islandShowOriginalNotification,
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

    if (showIslandTimeoutDialog) {
        AppAlertDialog(
            onDismissRequest = { showIslandTimeoutDialog = false },
            title = { Text(stringResource(R.string.pref_island_timeout_title)) },
            text = {
                AppTextField(
                    state = islandTimeoutState,
                    supportingText = { Text(stringResource(R.string.pref_island_timeout_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                AppTextButton(
                    text = stringResource(android.R.string.ok),
                    onClick = {
                        val days = islandTimeoutState.text.toString().toIntOrNull()
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
                )
            },
            dismissButton = {
                AppTextButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = { showIslandTimeoutDialog = false },
                )
            },
        )
    }
}

internal fun notifyPrefChanged(context: Context) {
    context.sendBroadcast(Intent(ACTION_PREF_CHANGED))
}

@Composable
internal fun DiagnosticsBlock(viewModel: SettingsViewModel, snackbarHostState: AppSnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val debugMode by viewModel.debugMode.collectAsStateWithLifecycle()
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
        val blockReason = VerboseLogEnableTracker.checkPreExport(debugMode) { Shell.getShell().isRoot }
        if (blockReason != null) {
            val resId = context.resources.getIdentifier(blockReason, "string", context.packageName)
            val message = if (resId != 0) context.getString(resId) else blockReason
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            return
        }
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
    RuntimeLogDiagnosticsItems(
        labels = RuntimeLogDiagnosticsLabels(
            shareLogTitle = stringResource(R.string.settings_get_log),
            shareLogSummary = stringResource(R.string.settings_get_log_summary),
            verboseLogTitle = debugModeTitle,
            verboseLogSummary = stringResource(R.string.settings_debug_mode_summary),
            retentionTitle = stringResource(R.string.settings_runtime_log_retention_days),
            retentionSummary = pluralStringResource(
                R.plurals.settings_runtime_log_retention_days_summary,
                runtimeLogRetentionDays,
                runtimeLogRetentionDays,
            ),
            clearLogTitle = stringResource(R.string.runtime_log_clear_confirm_title),
            clearLogSummary = stringResource(R.string.runtime_log_clear_summary),
        ),
        state = RuntimeLogDiagnosticsState(
            verboseLogEnabled = debugMode,
        ),
        callbacks = RuntimeLogDiagnosticsCallbacks(
            onShareLog = ::saveRuntimeLogBundle,
            onVerboseLogEnabledChange = { enabled ->
                VerboseLogEnableTracker.onVerboseLogToggled(enabled)
                io.github.magisk317.xposed.logging.LogSanitizerConfig
                    .syncFromVerboseMode(enabled)
                viewModel.setDebugMode(enabled) { success ->
                    showSwitchFeedback(debugModeTitle, enabled, success)
                }
            },
            onRetentionClick = { showRuntimeLogRetentionDialog = true },
            onClearLogClick = { showClearConfirmDialog = true },
        ),
        layout = RuntimeLogDiagnosticsLayout(
            shareEntryMode = RuntimeLogShareEntryMode.SEPARATE_ITEM,
            itemOrder = listOf(
                RuntimeLogDiagnosticsItem.SHARE_LOG,
                RuntimeLogDiagnosticsItem.VERBOSE_LOG,
                RuntimeLogDiagnosticsItem.RETENTION,
                RuntimeLogDiagnosticsItem.CLEAR_LOG,
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
