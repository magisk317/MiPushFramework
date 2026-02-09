package top.trumeet.mipushframework.main.subpage

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import com.magisk317.Global
import com.magisk317.InternalMessenger
import com.xiaomi.push.service.XMPushServiceMessenger
import com.xiaomi.xmsf.R
import top.trumeet.common.utils.Utils
import top.trumeet.mipushframework.MainActivityOperation
import top.trumeet.mipushframework.component.SettingsGroup
import top.trumeet.mipushframework.component.SettingsItem
import top.trumeet.mipushframework.component.SettingsDialogItem
import top.trumeet.mipushframework.component.SettingsSwitchItem
import top.trumeet.mipushframework.component.SettingsListItem
import top.trumeet.mipushframework.main.AdvancedSettingsPage
import top.trumeet.mipushframework.main.HelpPage
import top.trumeet.ui.theme.Theme

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.magisk317.main.viewmodel.SettingsViewModel

@Composable
fun Settings(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: SettingsViewModel = viewModel(),
    onShowAboutDialog: (String) -> Unit = {}
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
                viewModel = viewModel
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
    viewModel: SettingsViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(contentPadding.calculateTopPadding()))

        ServiceConfigurationBlock(viewModel)
        DisplayBlock(
            hazeBlurRadius,
            hazeTintAlpha,
            onHazeBlurRadiusChange,
            onHazeTintAlphaChange,
            viewModel
        )
        DataMaintenanceBlock(viewModel)
        ExperimentalBlock(viewModel)
        AboutBlock(onShowAboutDialog)

        Spacer(Modifier.height(contentPadding.calculateBottomPadding() + 16.dp))
    }
}

@Composable
private fun ServiceConfigurationBlock(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val isStartForeground by viewModel.isStartForeground.collectAsStateWithLifecycle()
    val accessMode by viewModel.accessMode.collectAsStateWithLifecycle()
    val notificationOnRegister by viewModel.notificationOnRegister.collectAsStateWithLifecycle()

    SettingsGroup(title = stringResource(R.string.settings_service_setting)) {
        SetXMPPServer(context, viewModel)
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

    SettingsGroup(title = "记录与显示") {
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
        var initialBlurValue by remember { mutableStateOf(blurRadius) }
        var initialAlphaValue by remember { mutableStateOf(tintAlpha) }
        var isDragging by remember { mutableStateOf(false) }

        SettingsDialogItem(
            title = "背景模糊强度",
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
                initialBlurValue = blurRadius
                blurValue = blurRadius
                showBlurDialog = true
            },
            confirmButton = {
                TextButton(onClick = {
                    onBlurChange(blurValue)
                    viewModel.previewHazeBlurRadius(null)
                    showBlurDialog = false
                    isDragging = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.previewHazeBlurRadius(null)
                    showBlurDialog = false
                    isDragging = false
                }) { Text(stringResource(android.R.string.cancel)) }
            },
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
            title = "模糊遮罩透明度",
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
                initialAlphaValue = tintAlpha
                alphaValue = tintAlpha
                showAlphaDialog = true
            },
            confirmButton = {
                TextButton(onClick = {
                    onAlphaChange(alphaValue)
                    viewModel.previewHazeTintAlpha(null)
                    showAlphaDialog = false
                    isDragging = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.previewHazeTintAlpha(null)
                    showAlphaDialog = false
                    isDragging = false
                }) { Text(stringResource(android.R.string.cancel)) }
            },
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

    SettingsGroup(title = "数据与调试") {
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
    val iceboxSupported by viewModel.iceboxSupported.collectAsStateWithLifecycle()

    var iceBoxGranted by remember {
        mutableStateOf(
            viewModel.isIceBoxInstalled()
                    && viewModel.iceBoxPermissionGranted(context)
        )
    }
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        iceBoxGranted = it.values.all { granted -> granted }
        viewModel.setIceboxSupported(iceBoxGranted)
    }

    SettingsGroup(title = stringResource(R.string.settings_experimental)) {
        SettingsItem(
            title = stringResource(R.string.settings_mock_notification),
            summary = stringResource(R.string.settings_mock_notification_summary)
        ) {
            viewModel.notifyMockNotification(context)
        }

        SettingsSwitchItem(
            title = stringResource(R.string.settings_icebox_permission),
            summary = stringResource(R.string.settings_icebox_permission_summary),
            checked = iceboxSupported || iceBoxGranted,
            enabled = viewModel.isIceBoxInstalled()
        ) {
            if (!iceBoxGranted) {
                permissionsLauncher.launch(arrayOf(com.catchingnow.icebox.sdk_client.IceBox.SDK_PERMISSION))
            } else {
                viewModel.setIceboxSupported(!iceboxSupported)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SetXMPPServer(context: Context, viewModel: SettingsViewModel) {
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
        confirmButton = @Composable {
            TextButton(onClick = {
                viewModel.updateXmppServer(text)
                // Reconnect request is handled in ViewModel
                shouldShowDialog = false
            }) {
                Text(stringResource(android.R.string.ok))
            }
        },
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
