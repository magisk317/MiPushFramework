package top.trumeet.mipushframework.main.subpage

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Slider
import com.magisk317.Global
import com.magisk317.InternalMessenger
import com.xiaomi.push.service.XMPushServiceMessenger
import com.xiaomi.xmsf.R
import com.xiaomi.xmsf.SettingUtils
import top.trumeet.common.utils.Utils
import top.trumeet.mipushframework.MainPageOperation
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
            .padding(horizontal = 4.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 顶部占位：包含状态栏和 TopBar 的高度
        Spacer(Modifier.height(contentPadding.calculateTopPadding()))
        
        ServiceConfigurationBlock(viewModel)
        VisualLabBlock(
            hazeBlurRadius,
            hazeTintAlpha,
            onHazeBlurRadiusChange,
            onHazeTintAlphaChange
        )
        DebugBlock()
        AboutBlock(onShowAboutDialog)
        
        // 底部占位：包含导航栏的高度
        Spacer(Modifier.height(contentPadding.calculateBottomPadding() + 16.dp))
    }
}

@Composable
private fun VisualLabBlock(
    blurRadius: Float,
    tintAlpha: Float,
    onBlurChange: (Float) -> Unit,
    onAlphaChange: (Float) -> Unit
) {
    SettingsGroup(title = "视觉实验室 (Beta)") {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("背景模糊强度: ${blurRadius.toInt()}dp", style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = blurRadius,
                onValueChange = { onBlurChange(it) },
                valueRange = 0f..100f
            )
        }

        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("模糊遮罩透明度: ${String.format("%.2f", tintAlpha)}", style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = tintAlpha,
                onValueChange = { onAlphaChange(it) },
                valueRange = 0f..1f
            )
        }
    }
}

@Composable
private fun ServiceConfigurationBlock(viewModel: SettingsViewModel) {
    val context = LocalContext.current

    SettingsGroup(title = stringResource(R.string.settings_service_setting)) {
        SettingsItem(
            title = stringResource(R.string.settings_service_advance_setting),
            summary = stringResource(R.string.settings_summary_service_advance_setting)
        ) {
            context.startActivity(Intent(context, AdvancedSettingsPage::class.java))
        }

        SetConfigurationsDirectory(viewModel)
        SetXMPPServer(context, viewModel)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SetXMPPServer(context: Context, viewModel: SettingsViewModel) {
    val savedXmppServer by viewModel.xmppServer.collectAsStateWithLifecycle()
    var currentXMPPServer by remember { mutableStateOf("") }
    val messenger = remember {
        object : InternalMessenger(context) {
            init {
                register(IntentFilter(XMPushServiceMessenger.IntentSetConnectionStatus))
                addListener { intent: Intent ->
                    val host = intent.getStringExtra("host")
                    if (host.isNullOrEmpty()) {
                        return@addListener
                    }
                    currentXMPPServer = host
                }
                send(Intent(XMPushServiceMessenger.IntentGetConnectionStatus))
            }
        }
    }
    DisposableEffect(messenger) {
        onDispose { messenger.unregister() }
    }
    var text by remember { mutableStateOf(savedXmppServer ?: "") }
    var shouldShowDialog by remember { mutableStateOf(false) }

    SettingsDialogItem(
        title = stringResource(R.string.settings_XMPP_server),
        summary = stringResource(R.string.settings_XMPP_server_summary) +
                "\nSet: [${savedXmppServer ?: ""}]" +
                "\nCurrent: [$currentXMPPServer]",
        shouldShowDialog = shouldShowDialog,
        onDismiss = {
            shouldShowDialog = false
            text = ""
        },
        onClick = { shouldShowDialog = true },
        confirmButton = @Composable {
            TextButton(onClick = {
                viewModel.updateXmppServer(text)
                SettingUtils.sendXMPPReconnectRequest(context)
                currentXMPPServer = text
                shouldShowDialog = false
            }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        content = @Composable {
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(SettingUtils.getXMPPServerHint()) },
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
private fun DebugBlock() {
    val context = LocalContext.current

    SettingsGroup(title = stringResource(R.string.settings_debug)) {
        SettingsItem(
            title = stringResource(R.string.settings_get_log),
            summary = stringResource(R.string.settings_get_log_summary)
        ) {
            SettingUtils.shareLogs(context)
        }

        SettingsItem(
            title = stringResource(R.string.try_to_force_register_all_applications)
        ) {
            SettingUtils.tryForceRegisterAllApplications(context)
        }
    }
}

@Composable
private fun AboutBlock(onShowAboutDialog: (String) -> Unit) {
    val context = LocalContext.current
    val mainPageOperation = MainPageOperation(context)

    SettingsGroup(title = stringResource(R.string.action_about)) {
        SettingsItem(
            title = stringResource(R.string.helplib_title)
        ) {
            context.startActivity(Intent(context, HelpPage::class.java))
        }

        SettingsItem(
            title = stringResource(R.string.action_update)
        ) {
            mainPageOperation.gotoGitHubReleasePage()
            Toast.makeText(context, R.string.update_toast, Toast.LENGTH_LONG).show()
        }

        SettingsItem(
            title = stringResource(R.string.action_about)
        ) {
            mainPageOperation.showAboutDialog(onShowAboutDialog)
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
