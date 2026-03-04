package top.trumeet.mipushframework.main.subpage

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import com.xiaomi.xmsf.R
import top.trumeet.common.utils.Utils
import top.trumeet.mipushframework.MainActivityOperation
import top.trumeet.mipushframework.component.SettingsGroup
import top.trumeet.mipushframework.component.SettingsItem
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import top.trumeet.mipushframework.component.SettingsDialogItem
import top.trumeet.mipushframework.component.SettingsSwitchItem
import top.trumeet.mipushframework.component.SettingsListItem
import top.trumeet.mipushframework.component.DialogAction
import top.trumeet.mipushframework.main.HelpPage
import top.trumeet.ui.theme.Theme
import top.trumeet.ui.theme.spacing

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.magisk317.main.viewmodel.SettingsViewModel
import top.trumeet.mipushframework.wizard.RequestPermissionPage

private enum class SettingsSection {
    Service,
    Display,
    DataMaintenance,
    Developer,
    About
}

@Composable
fun Settings(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: SettingsViewModel = hiltViewModel(),
    onShowAboutDialog: (String) -> Unit = {},
    onSectionChanged: (String?) -> Unit = {},
    sectionBackSignal: Int = 0,
    hazeState: HazeState? = null,
    hazeStyle: HazeStyle? = null
) {
    val hazeBlurRadius by viewModel.hazeBlurRadius.collectAsStateWithLifecycle()
    val hazeTintAlpha by viewModel.hazeTintAlpha.collectAsStateWithLifecycle()

    Page {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface
        ) {
            SettingsScreen(
                contentPadding,
                hazeBlurRadius.toFloat(),
                hazeTintAlpha,
                onHazeBlurRadiusChange = { viewModel.updateHazeBlurRadius(it.toInt()) },
                onHazeTintAlphaChange = { viewModel.updateHazeTintAlpha(it) },
                onShowAboutDialog = onShowAboutDialog,
                viewModel = viewModel,
                onSectionChanged = onSectionChanged,
                sectionBackSignal = sectionBackSignal,
                hazeState = hazeState,
                hazeStyle = hazeStyle
            )
        }
    }
}


@Composable
private fun SettingsScreen(
    contentPadding: PaddingValues,
    hazeBlurRadius: Float,
    hazeTintAlpha: Float,
    onHazeBlurRadiusChange: (Float) -> Unit,
    onHazeTintAlphaChange: (Float) -> Unit,
    onShowAboutDialog: (String) -> Unit,
    viewModel: SettingsViewModel,
    onSectionChanged: (String?) -> Unit,
    sectionBackSignal: Int,
    hazeState: HazeState? = null,
    hazeStyle: HazeStyle? = null
) {
    var currentSection by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
    val currentTitle = when (currentSection) {
        SettingsSection.Service -> stringResource(R.string.settings_home_service_title)
        SettingsSection.Display -> stringResource(R.string.settings_home_display_title)
        SettingsSection.DataMaintenance -> stringResource(R.string.settings_home_data_title)
        SettingsSection.Developer -> stringResource(R.string.settings_home_developer_title)
        SettingsSection.About -> stringResource(R.string.action_about)
        null -> null
    }

    LaunchedEffect(currentTitle) {
        onSectionChanged(currentTitle)
    }
    LaunchedEffect(sectionBackSignal) {
        if (currentSection != null) {
            currentSection = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        BackHandler(enabled = currentSection != null) {
            currentSection = null
        }

        Spacer(Modifier.height(contentPadding.calculateTopPadding()))

        if (currentSection == null) {
            SettingsHome(
                onOpenService = { currentSection = SettingsSection.Service },
                onOpenDisplay = { currentSection = SettingsSection.Display },
                onOpenData = { currentSection = SettingsSection.DataMaintenance },
                onOpenDeveloper = { currentSection = SettingsSection.Developer },
                onOpenAbout = { currentSection = SettingsSection.About }
            )
        } else {
            when (currentSection) {
                SettingsSection.Service -> ServiceConfigurationBlock(viewModel)
                SettingsSection.Display -> DisplayBlock(
                    hazeBlurRadius,
                    hazeTintAlpha,
                    onHazeBlurRadiusChange,
                    onHazeTintAlphaChange,
                    viewModel
                )
                SettingsSection.DataMaintenance -> DataMaintenanceBlock(viewModel)
                SettingsSection.Developer -> ExperimentalBlock(viewModel)
                SettingsSection.About -> AboutBlock(onShowAboutDialog)
                null -> Unit
            }
        }

        Spacer(Modifier.height(contentPadding.calculateBottomPadding() + MaterialTheme.spacing.large))
    }
}

@Composable
private fun SettingsHome(
    onOpenService: () -> Unit,
    onOpenDisplay: () -> Unit,
    onOpenData: () -> Unit,
    onOpenDeveloper: () -> Unit,
    onOpenAbout: () -> Unit
) {
    SettingsGroup(title = stringResource(R.string.settings_options)) {
        SettingsItem(
            title = stringResource(R.string.settings_home_service_title),
            summary = stringResource(R.string.settings_home_service_summary)
        ) { onOpenService() }
        SettingsItem(
            title = stringResource(R.string.settings_home_display_title),
            summary = stringResource(R.string.settings_home_display_summary)
        ) { onOpenDisplay() }
        SettingsItem(
            title = stringResource(R.string.settings_home_data_title),
            summary = stringResource(R.string.settings_home_data_summary)
        ) { onOpenData() }
        SettingsItem(
            title = stringResource(R.string.settings_home_developer_title),
            summary = stringResource(R.string.settings_home_developer_summary)
        ) { onOpenDeveloper() }
        SettingsItem(
            title = stringResource(R.string.action_about),
            summary = stringResource(R.string.settings_home_about_summary)
        ) { onOpenAbout() }
    }
}

@Composable
private fun ServiceConfigurationBlock(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val isStartForeground by viewModel.isStartForeground.collectAsStateWithLifecycle()
    val accessMode by viewModel.accessMode.collectAsStateWithLifecycle()
    val notificationOnRegister by viewModel.notificationOnRegister.collectAsStateWithLifecycle()

    SettingsGroup(title = stringResource(R.string.settings_service_setting)) {
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
            onValueSelected = { index: Int -> viewModel.setAccessMode(index) }
        )

        SettingsSwitchItem(
            title = stringResource(R.string.settings_notify_on_register),
            checked = notificationOnRegister,
        ) { viewModel.setNotificationOnRegister(it) }

        SettingsItem(
            title = stringResource(R.string.settings_permission_check),
            summary = stringResource(R.string.settings_permission_check_summary)
        ) {
            context.startActivity(
                Intent(context, RequestPermissionPage::class.java)
                    .putExtra(RequestPermissionPage.EXTRA_RECHECK_ONLY, true)
            )
        }
    }
}

@Composable
private fun DisplayBlock(
    blurRadius: Float,
    tintAlpha: Float,
    onBlurChange: (Float) -> Unit,
    onAlphaChange: (Float) -> Unit,
    viewModel: SettingsViewModel
) {
    val showAllEvents by viewModel.showAllEvents.collectAsStateWithLifecycle()
    val showConfigurationList by viewModel.showConfigurationList.collectAsStateWithLifecycle()
    val themeState by viewModel.themeState.collectAsStateWithLifecycle()
    val themeEntries = stringArrayResource(R.array.theme_mode_entries)
    val selectedThemeIndex = themeState.mode.coerceIn(0, themeEntries.lastIndex)

    SettingsGroup(title = stringResource(R.string.settings_group_display_and_list)) {
        SettingsListItem(
            title = stringResource(R.string.pref_choose_theme_title),
            summary = stringResource(R.string.pref_choose_theme_summary) + " · " + themeEntries[selectedThemeIndex],
            values = themeEntries,
            selected = selectedThemeIndex,
            onValueSelected = { index -> viewModel.setThemeMode(index) },
            onValueSelectedWithPosition = { index, x, y ->
                viewModel.setThemeMode(index, x, y)
            }
        )

        SettingsSwitchItem(
            title = stringResource(R.string.settings_show_all_events),
            checked = showAllEvents,
        ) { viewModel.setShowAllEvents(it) }

        SettingsSwitchItem(
            title = stringResource(R.string.settings_show_loaded_file_after_configurations_loaded),
            checked = showConfigurationList,
        ) { viewModel.setShowConfigurationList(it) }

        var showBlurDialog by remember { mutableStateOf(false) }
        var showAlphaDialog by remember { mutableStateOf(false) }
        var blurValue by remember { mutableStateOf(blurRadius) }
        var alphaValue by remember { mutableStateOf(tintAlpha) }
        var isDragging by remember { mutableStateOf(false) }

        SettingsDialogItem(
            title = stringResource(R.string.settings_blur_radius),
            summary = "${blurRadius.toInt()}dp",
            shouldShowDialog = showBlurDialog,
            onDismiss = {
                if (showBlurDialog) {
                    viewModel.previewHazeBlurRadius(null)
                    showBlurDialog = false
                    isDragging = false
                }
            },
            onClick = {
                blurValue = blurRadius
                showBlurDialog = true
            },
            confirmButton = {},
            actions = listOf(
                DialogAction(
                    label = stringResource(android.R.string.cancel),
                    onClick = {
                        viewModel.previewHazeBlurRadius(null)
                        showBlurDialog = false
                        isDragging = false
                    }
                ),
                DialogAction(
                    label = stringResource(android.R.string.ok),
                    onClick = {
                        onBlurChange(blurValue)
                        viewModel.previewHazeBlurRadius(null)
                        showBlurDialog = false
                        isDragging = false
                    }
                )
            ),
            isDragging = isDragging,
            content = {
                CompositionLocalProvider(LocalContentColor provides androidx.compose.ui.graphics.Color.White) {
                    Column {
                        Text(
                            text = "${blurValue.toInt()} dp",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        androidx.compose.material3.Slider(
                            value = blurValue,
                            onValueChange = {
                            isDragging = true
                            blurValue = it
                            viewModel.previewHazeBlurRadius(it.toInt())
                        },
                        onValueChangeFinished = {
                            isDragging = false
                        },
                        valueRange = 0f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = androidx.compose.ui.graphics.Color.White,
                                activeTrackColor = androidx.compose.ui.graphics.Color.White,
                                inactiveTrackColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.24f)
                            )
                        )
                    }
                }
            }
        )

        SettingsDialogItem(
            title = stringResource(R.string.settings_blur_mask_alpha),
            summary = String.format("%.2f", tintAlpha),
            shouldShowDialog = showAlphaDialog,
            onDismiss = {
                if (showAlphaDialog) {
                    viewModel.previewHazeTintAlpha(null)
                    showAlphaDialog = false
                    isDragging = false
                }
            },
            onClick = {
                alphaValue = tintAlpha
                showAlphaDialog = true
            },
            confirmButton = {},
            actions = listOf(
                DialogAction(
                    label = stringResource(android.R.string.cancel),
                    onClick = {
                        viewModel.previewHazeTintAlpha(null)
                        showAlphaDialog = false
                        isDragging = false
                    }
                ),
                DialogAction(
                    label = stringResource(android.R.string.ok),
                    onClick = {
                        onAlphaChange(alphaValue)
                        viewModel.previewHazeTintAlpha(null)
                        showAlphaDialog = false
                        isDragging = false
                    }
                )
            ),
            isDragging = isDragging,
            content = {
                CompositionLocalProvider(LocalContentColor provides androidx.compose.ui.graphics.Color.White) {
                    Column {
                        Text(
                            text = String.format("%.2f", alphaValue),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        androidx.compose.material3.Slider(
                            value = alphaValue,
                        onValueChange = {
                            isDragging = true
                            alphaValue = it
                            viewModel.previewHazeTintAlpha(it)
                        },
                        onValueChangeFinished = {
                            isDragging = false
                        },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = androidx.compose.ui.graphics.Color.White,
                                activeTrackColor = androidx.compose.ui.graphics.Color.White,
                                inactiveTrackColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.24f)
                            )
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun DataMaintenanceBlock(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val debugMode by viewModel.debugMode.collectAsStateWithLifecycle()

    SettingsGroup(title = stringResource(R.string.settings_group_data_and_debug)) {
        SettingsItem(
            title = stringResource(R.string.settings_clear_history),
            summary = stringResource(R.string.settings_clear_history_summary)
        ) {
            viewModel.clearHistory(context)
        }

        SettingsItem(
            title = stringResource(R.string.settings_get_log),
            summary = stringResource(R.string.settings_get_log_summary)
        ) {
            viewModel.shareLogs(context)
        }

        SettingsItem(
            title = stringResource(R.string.settings_clear_log),
            summary = stringResource(R.string.settings_clear_log_summary)
        ) {
            viewModel.clearLog(context)
        }

        SettingsItem(
            title = stringResource(R.string.try_to_force_register_all_applications)
        ) {
            viewModel.tryForceRegisterAllApplications(context)
        }

        SettingsSwitchItem(
            title = stringResource(R.string.settings_debug_mode),
            summary = stringResource(R.string.settings_debug_mode_summary),
            checked = debugMode,
        ) { viewModel.setDebugMode(it) }
    }
}

@Composable
private fun ExperimentalBlock(viewModel: SettingsViewModel) {
    val context = LocalContext.current

    SettingsGroup(title = stringResource(R.string.settings_experimental)) {
        SettingsItem(
            title = stringResource(R.string.settings_mock_notification),
            summary = stringResource(R.string.settings_mock_notification_summary)
        ) {
            viewModel.notifyMockNotification(context)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SetXMPPServer(viewModel: SettingsViewModel) {
    val savedXmppServer by viewModel.xmppServer.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf(savedXmppServer ?: "") }
    var shouldShowDialog by remember { mutableStateOf(false) }

    // Synchronize text with savedXmppServer when dialog opens
    androidx.compose.runtime.LaunchedEffect(shouldShowDialog) {
        if (shouldShowDialog) {
            text = savedXmppServer ?: ""
        }
    }

    SettingsDialogItem(
        title = stringResource(R.string.settings_XMPP_server),
        summary = if (savedXmppServer.isNullOrEmpty()) stringResource(R.string.settings_XMPP_server_summary) else savedXmppServer!!,
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
                }
            ),
            DialogAction(
                label = stringResource(android.R.string.ok),
                onClick = {
                    viewModel.updateXmppServer(text)
                    shouldShowDialog = false
                }
            )
        ),
        content = @Composable {
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(viewModel.getXMPPServerHint()) },
                singleLine = true
            )
        })
}

@Composable
private fun SetConfigurationsDirectory(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val savedConfigDir by viewModel.configDirectory.collectAsStateWithLifecycle()
    
    val openDocumentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.updateConfigDirectory(uri.toString())
        }
    }
    SettingsItem(
        title = stringResource(R.string.settings_configuration_directory),
        summary = savedConfigDir
    ) {
        openDocumentTreeLauncher.launch(null) // 启动文件选择器
    }
}

@Composable
private fun AboutBlock(onShowAboutDialog: (String) -> Unit) {
    val context = LocalContext.current
    val mainActivityOperation = MainActivityOperation(context)

    SettingsGroup(title = stringResource(R.string.action_about)) {
        SettingsItem(
            title = stringResource(R.string.helplib_title)
        ) {
            context.startActivity(Intent(context, HelpPage::class.java))
        }

        SettingsItem(
            title = stringResource(R.string.action_update)
        ) {
            mainActivityOperation.gotoGitHubReleasePage()
            Toast.makeText(context, R.string.update_toast, Toast.LENGTH_LONG).show()
        }

        SettingsItem(
            title = stringResource(R.string.action_about)
        ) {
            mainActivityOperation.showAboutDialog(onShowAboutDialog)
        }
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
