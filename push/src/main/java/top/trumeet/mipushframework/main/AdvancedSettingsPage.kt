@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
package top.trumeet.mipushframework.main

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.magisk317.main.viewmodel.AdvancedSettingsViewModel
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.xiaomi.xmsf.R
import com.xiaomi.xmsf.SettingUtils
import top.trumeet.common.utils.Utils
import com.catchingnow.icebox.sdk_client.IceBox
import top.trumeet.ui.theme.Theme
import top.trumeet.mipushframework.component.SettingsGroup
import top.trumeet.mipushframework.component.SettingsItem
import top.trumeet.mipushframework.component.SettingsSwitchItem
import top.trumeet.mipushframework.component.SettingsListItem
import androidx.activity.compose.rememberLauncherForActivityResult

class AdvancedSettingsPage : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Theme {
                SettingsApp()
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SettingsApp() {
    Theme {
        androidx.compose.material3.Scaffold(
            topBar = {
                androidx.compose.material3.TopAppBar(
                    title = { androidx.compose.material3.Text(stringResource(R.string.app_name) + " " + stringResource(R.string.title_activity_advance_setting)) }
                )
            }
        ) { innerPadding ->
            val viewModel: AdvancedSettingsViewModel = viewModel()
            SettingsScreen(viewModel, Modifier.padding(innerPadding))
        }
    }
}


@Composable
private fun SettingsScreen(viewModel: AdvancedSettingsViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        CleanUpBlock()
        ExperimentalBlock(viewModel)
        ConfigurationsBlock(viewModel)
    }
}

@Composable
fun ConfigurationsBlock(viewModel: AdvancedSettingsViewModel) {
    val notificationOnRegister by viewModel.notificationOnRegister.collectAsStateWithLifecycle()
    val showConfigurationList by viewModel.showConfigurationList.collectAsStateWithLifecycle()
    val debugMode by viewModel.debugMode.collectAsStateWithLifecycle()
    val showAllEvents by viewModel.showAllEvents.collectAsStateWithLifecycle()
    val isStartForeground by viewModel.isStartForeground.collectAsStateWithLifecycle()
    val accessMode by viewModel.accessMode.collectAsStateWithLifecycle()

    val context = LocalContext.current

    SettingsGroup(title = stringResource(R.string.settings_options)) {
        SettingsSwitchItem(
            title = stringResource(R.string.settings_notify_on_register),
            checked = notificationOnRegister,
        ) { viewModel.setNotificationOnRegister(it) }

        SettingsSwitchItem(
            title = stringResource(R.string.settings_show_loaded_file_after_configurations_loaded),
            checked = showConfigurationList,
        ) { viewModel.setShowConfigurationList(it) }

        SettingsSwitchItem(
            title = stringResource(R.string.settings_debug_mode),
            summary = stringResource(R.string.settings_debug_mode_summary),
            checked = debugMode,
        ) { viewModel.setDebugMode(it) }

        SettingsSwitchItem(
            title = stringResource(R.string.settings_show_all_events),
            checked = showAllEvents,
        ) { viewModel.setShowAllEvents(it) }

        SettingsSwitchItem(
            title = stringResource(R.string.settings_start_foreground_service),
            summary = stringResource(R.string.settings_start_foreground_service_summary),
            checked = isStartForeground,
        ) {
            viewModel.setStartForeground(it)
            if (it) SettingUtils.startMiPushServiceAsForegroundService(context)
        }

        SettingsListItem(
            title = stringResource(R.string.pref_title_access_mode),
            summary = stringResource(R.string.pref_summary_access_mode),
            values = stringArrayResource(R.array.pref_title_access_mode_list_titles),
            selected = accessMode.toIntOrNull() ?: 0,
            onValueSelected = { index: Int -> viewModel.setAccessMode(index) }
        )
    }
}

@Composable
private fun ExperimentalBlock(viewModel: AdvancedSettingsViewModel) {
    val context = LocalContext.current
    val iceboxSupported by viewModel.iceboxSupported.collectAsStateWithLifecycle()
    
    var iceBoxGranted by remember {
        mutableStateOf(
            SettingUtils.isIceBoxInstalled()
                    && SettingUtils.iceBoxPermissionGranted(context)
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
            SettingUtils.notifyMockNotification(context)
        }

        SettingsSwitchItem(
            title = stringResource(R.string.settings_icebox_permission),
            summary = stringResource(R.string.settings_icebox_permission_summary),
            checked = iceboxSupported || iceBoxGranted,
            enabled = SettingUtils.isIceBoxInstalled()
        ) {
            if (!iceBoxGranted) {
                permissionsLauncher.launch(arrayOf(IceBox.SDK_PERMISSION))
            } else {
                viewModel.setIceboxSupported(!iceboxSupported)
            }
        }
    }
}

@Composable
private fun CleanUpBlock() {
    val context = LocalContext.current
    SettingsGroup(title = stringResource(R.string.settings_clear)) {
        SettingsItem(
            title = stringResource(R.string.settings_clear_history),
            summary = stringResource(R.string.settings_clear_history_summary)
        ) {
            SettingUtils.clearHistory(context)
        }

        SettingsItem(
            title = stringResource(R.string.settings_clear_log),
            summary = stringResource(R.string.settings_clear_log_summary)
        ) {
            SettingUtils.clearLog(context)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsPreview() {
    Utils.context = LocalContext.current
    SettingsApp()
}
