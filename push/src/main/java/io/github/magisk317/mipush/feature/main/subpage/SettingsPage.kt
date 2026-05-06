@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.magisk317.mipush.feature.main.subpage

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.mipush.main.viewmodel.SettingsViewModel
import com.xiaomi.xmsf.BuildConfig
import com.xiaomi.xmsf.R
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import io.github.magisk317.uikit.preference.SectionCard
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.feature.main.MainActivityOperation
import io.github.magisk317.mipush.feature.ui.component.DialogAction
import io.github.magisk317.mipush.feature.ui.component.SectionColumn
import io.github.magisk317.mipush.feature.ui.component.SettingsDialogItem
import io.github.magisk317.mipush.feature.ui.component.SettingsItem
import io.github.magisk317.mipush.feature.ui.component.SettingsListItem
import io.github.magisk317.mipush.feature.ui.component.SettingsSwitchItem
import io.github.magisk317.mipush.platform.support.LegacyUiEntryPoints
import io.github.magisk317.mipush.feature.ui.theme.Theme
import io.github.magisk317.mipush.feature.ui.theme.spacing
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
                DataMaintenanceBlock(viewModel)
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

    SettingsSwitchItem(
        title = stringResource(R.string.settings_start_foreground_service),
        summary = stringResource(R.string.settings_start_foreground_service_summary),
        checked = isStartForeground,
    ) {
        viewModel.setStartForeground(it)
        viewModel.startMiPushServiceAsForegroundService(context)
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
                    message = context.getString(R.string.notification_on_register_global_disabled_hint),
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
    val themeState by viewModel.themeState.collectAsStateWithLifecycle()
    val themeEntries = stringArrayResource(R.array.theme_mode_entries)
    val selectedThemeIndex = themeState.mode.coerceIn(0, themeEntries.lastIndex)

    SettingsListItem(
        title = stringResource(R.string.pref_choose_theme_title),
        summary = stringResource(R.string.pref_choose_theme_summary) + " · " + themeEntries[selectedThemeIndex],
        values = themeEntries,
        selected = selectedThemeIndex,
        onValueSelected = { index -> viewModel.setThemeMode(index) },
        onValueSelectedWithPosition = { index, x, y ->
            viewModel.setThemeMode(index, x, y)
        },
    )

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
private fun DataMaintenanceBlock(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val debugMode by viewModel.debugMode.collectAsStateWithLifecycle()

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
        viewModel.shareLogs(context)
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
        viewModel.tryForceRegisterAllApplications(context)
    }

    SettingsSwitchItem(
        title = stringResource(R.string.settings_debug_mode),
        summary = stringResource(R.string.settings_debug_mode_summary),
        checked = debugMode,
    ) { viewModel.setDebugMode(it) }
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
