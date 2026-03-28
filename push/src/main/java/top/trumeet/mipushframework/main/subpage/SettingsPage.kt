package top.trumeet.mipushframework.main.subpage

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magisk317.main.viewmodel.SettingsViewModel
import com.xiaomi.xmsf.R
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import io.github.magisk317.uikit.preference.SectionCard
import top.trumeet.common.utils.Utils
import top.trumeet.mipushframework.MainActivityOperation
import top.trumeet.mipushframework.component.DialogAction
import top.trumeet.mipushframework.component.ExpressiveHeroCard
import top.trumeet.mipushframework.component.SectionColumn
import top.trumeet.mipushframework.component.SettingsDialogItem
import top.trumeet.mipushframework.component.SettingsItem
import top.trumeet.mipushframework.component.SettingsListItem
import top.trumeet.mipushframework.component.SettingsSwitchItem
import top.trumeet.mipushframework.wizard.RequestPermissionPage
import top.trumeet.ui.theme.Theme
import top.trumeet.ui.theme.spacing

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
    Page {
        SettingsScreen(
            contentPadding = contentPadding,
            onShowAboutDialog = onShowAboutDialog,
            viewModel = viewModel,
            onSectionChanged = onSectionChanged,
            sectionBackSignal = sectionBackSignal,
        )
    }
}

@Composable
private fun SettingsScreen(
    contentPadding: PaddingValues,
    onShowAboutDialog: (String) -> Unit,
    viewModel: SettingsViewModel,
    onSectionChanged: (String?) -> Unit,
    sectionBackSignal: Int,
) {
    val title = stringResource(R.string.main_settings)
    var expandService by rememberSaveable { mutableStateOf(true) }
    var expandDisplay by rememberSaveable { mutableStateOf(false) }
    var expandData by rememberSaveable { mutableStateOf(false) }
    var expandDeveloper by rememberSaveable { mutableStateOf(false) }
    var expandAbout by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(title) {
        onSectionChanged(title)
    }

    SectionColumn(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentPadding = PaddingValues(
            start = MaterialTheme.spacing.medium,
            top = contentPadding.calculateTopPadding() + 8.dp,
            end = MaterialTheme.spacing.medium,
            bottom = contentPadding.calculateBottomPadding() + MaterialTheme.spacing.large,
        ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        ExpressiveHeroCard(
            title = stringResource(R.string.settings_options),
        )

        SettingsAccordionSection(
            title = stringResource(R.string.settings_home_service_title),
            expanded = expandService,
            onExpandedChange = { expandService = !expandService },
        ) {
            ServiceConfigurationBlock(viewModel)
        }

        SettingsAccordionSection(
            title = stringResource(R.string.settings_home_display_title),
            expanded = expandDisplay,
            onExpandedChange = { expandDisplay = !expandDisplay },
        ) {
            DisplayBlock(viewModel)
        }

        SettingsAccordionSection(
            title = stringResource(R.string.settings_home_data_title),
            expanded = expandData,
            onExpandedChange = { expandData = !expandData },
        ) {
            DataMaintenanceBlock(viewModel)
        }

        SettingsAccordionSection(
            title = stringResource(R.string.settings_home_developer_title),
            expanded = expandDeveloper,
            onExpandedChange = { expandDeveloper = !expandDeveloper },
        ) {
            ExperimentalBlock(viewModel)
        }

        SettingsAccordionSection(
            title = stringResource(R.string.action_about),
            expanded = expandAbout,
            onExpandedChange = { expandAbout = !expandAbout },
        ) {
            AboutBlock(onShowAboutDialog)
        }
    }
}

@Composable
private fun SettingsAccordionSection(
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
        ) {
            content()
        }
    }
}

@Composable
private fun ServiceConfigurationBlock(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val isStartForeground by viewModel.isStartForeground.collectAsStateWithLifecycle()
    val accessMode by viewModel.accessMode.collectAsStateWithLifecycle()
    val notificationOnRegister by viewModel.notificationOnRegister.collectAsStateWithLifecycle()

    SetXMPPServer(viewModel)
    SetConfigurationsDirectory(viewModel)

    SettingsSwitchItem(
        title = stringResource(R.string.settings_start_foreground_service),
        summary = stringResource(R.string.settings_start_foreground_service_summary),
        checked = isStartForeground,
    ) {
        viewModel.setStartForeground(it)
        viewModel.startMiPushServiceAsForegroundService(context)
    }

    SettingsListItem(
        title = stringResource(R.string.pref_title_access_mode),
        summary = stringResource(R.string.pref_summary_access_mode),
        values = stringArrayResource(R.array.pref_title_access_mode_list_titles),
        selected = accessMode.toIntOrNull() ?: 0,
        onValueSelected = { index: Int -> viewModel.setAccessMode(index) },
    )

    SettingsSwitchItem(
        title = stringResource(R.string.settings_notify_on_register),
        checked = notificationOnRegister,
    ) { viewModel.setNotificationOnRegister(it) }

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

@Composable
private fun SetConfigurationsDirectory(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val savedConfigDir by viewModel.configDirectory.collectAsStateWithLifecycle()

    val openDocumentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            viewModel.updateConfigDirectory(uri.toString())
        }
    }
    SettingsItem(
        title = stringResource(R.string.settings_configuration_directory),
        summary = savedConfigDir,
    ) {
        openDocumentTreeLauncher.launch(null)
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsPagePreview() {
    Utils.context = LocalContext.current
    Theme {
        Settings(PaddingValues(0.dp), onShowAboutDialog = {})
    }
}
