@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.magisk317.mipush.feature.main

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import io.github.magisk317.uikit.surface.AppAlertDialog
import io.github.magisk317.uikit.surface.AppTextButton
import androidx.compose.ui.res.stringResource

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogProperties
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.mipush.feature.navigation.*

import io.github.magisk317.mipush.feature.main.MainActivityUtils
import io.github.magisk317.mipush.feature.main.subpage.Settings
import io.github.magisk317.mipush.feature.ui.theme.*
import io.github.magisk317.mipush.main.viewmodel.SettingsViewModel
import io.github.magisk317.mipush.main.viewmodel.RuntimeCommitMismatch
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.feature.wizard.RequestPermissionPage
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.main.viewmodel.requiresRuntimeWarning
import io.github.magisk317.mipush.main.viewmodel.runtimeCommitMismatch
import io.github.magisk317.mipush.manager.SettingsManager
import io.github.magisk317.mipush.manager.LegacyModuleDetector
import io.github.magisk317.mipush.manager.application.ManagerConfigGateway
import io.github.magisk317.mipush.common.BuildConfig as CommonBuildConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import io.github.magisk317.uikit.theme.applyEdgeToEdge
import io.github.magisk317.uikit.theme.ThemeRevealOverlay
import io.github.magisk317.uikit.theme.rememberThemeRevealState

private var placeholder by mutableStateOf("Search...")

open class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_START_TAB = "extra_start_tab"
        const val START_TAB_SETTINGS = "settings"
        const val EXTRA_START_ROUTE = "extra_start_route"
    }

    private val configGateway: ManagerConfigGateway by inject()

    private val settingsViewModel: SettingsViewModel by viewModel()
    private val preferenceRepository: PreferenceRepository by inject()
    private val settingsManager: SettingsManager by inject()
    private val runtimeClient: ManagerRuntimeClient by inject()
    private val mainActivityUtils by lazy { MainActivityUtils(settingsManager) }
    private var legacyModuleInstalled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        legacyModuleInstalled = LegacyModuleDetector.isInstalled(packageManager)
        applyEdgeToEdge(this)
        WelcomeIslandNotifier.notifyAfterInstallOrUpdate(this)
        mainActivityUtils.initOnCreate(
            context = applicationContext,
            loadConfigurations = configGateway::loadConfigurations,
            connectionStatusChanged = { placeholder = it.toString() },
            scope = lifecycleScope,
        )
        val explicitRoute = intent?.getStringExtra(EXTRA_START_ROUTE)
            ?.takeIf { it.isNotBlank() }
        val startTab = intent?.getStringExtra(EXTRA_START_TAB)
        val startDestination = when {
            explicitRoute?.startsWith(AppDestinations.Settings.ROUTE) == true ||
                explicitRoute?.startsWith(AppDestinations.StatusBarIconSettings.ROUTE) == true ||
                explicitRoute?.startsWith(AppDestinations.ConnectionStatus.ROUTE) == true ||
                startTab == START_TAB_SETTINGS -> AppDestinations.Settings.ROUTE

            else -> AppDestinations.Overview.ROUTE
        }
        lifecycleScope.launch {
            if (preferenceRepository.showWizard.first()) {
                startActivity(
                    Intent(this@MainActivity, RequestPermissionPage::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION),
                )
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    @Suppress("DEPRECATION")
                    overridePendingTransition(0, 0)
                }
                finish()
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    @Suppress("DEPRECATION")
                    overridePendingTransition(0, 0)
                }
                return@launch
            }

            setContent {
            val runtimeAvailability by runtimeClient.availability.collectAsStateWithLifecycle()
            val themeState by settingsViewModel.themeState.collectAsStateWithLifecycle()

            var currentThemeMode by remember { mutableIntStateOf(themeState.mode) }
            var currentUiKitStyle by remember { mutableIntStateOf(themeState.uiKitStyle) }
            var currentDynamicColor by remember { mutableStateOf(themeState.dynamicColor) }
            var currentAccentColor by remember { mutableIntStateOf(themeState.accentColorArgb) }
            var currentMonetEnabled by remember { mutableStateOf(themeState.monetEnabled) }
            var currentPaletteStyle by remember { mutableIntStateOf(themeState.paletteStyle) }
            var currentColorSpec by remember { mutableIntStateOf(themeState.colorSpec) }
            var currentSurfaceBlur by remember { mutableStateOf(themeState.surfaceBlur) }
            var currentLayoutScale by remember { mutableIntStateOf(themeState.layoutScale) }

            val themeRevealState = rememberThemeRevealState()

            val view = LocalView.current

            val applyThemeState: () -> Unit = {
                currentThemeMode = themeState.mode
                currentUiKitStyle = themeState.uiKitStyle
                currentDynamicColor = themeState.dynamicColor
                currentAccentColor = themeState.accentColorArgb
                currentMonetEnabled = themeState.monetEnabled
                currentPaletteStyle = themeState.paletteStyle
                currentColorSpec = themeState.colorSpec
                currentSurfaceBlur = themeState.surfaceBlur
                currentLayoutScale = themeState.layoutScale
            }

            LaunchedEffect(themeState) {
                if (themeState.mode != currentThemeMode) {
                    val requestedCenter = if (themeState.centerX >= 0f && themeState.centerY >= 0f) {
                        Offset(themeState.centerX, themeState.centerY)
                    } else {
                        Offset.Unspecified
                    }
                    val animated = themeRevealState.animateThemeChange(
                        view = view,
                        requestedCenter = requestedCenter,
                        onContentUpdate = applyThemeState,
                    )
                    if (!animated) {
                        applyThemeState()
                    }
                } else {
                    applyThemeState()
                }
            }

            Theme(
                themeMode = ThemeMode.fromValue(currentThemeMode),
                uiKitStyle = currentUiKitStyle,
                dynamicColor = currentDynamicColor,
                accentColorArgb = currentAccentColor,
                monetEnabled = currentMonetEnabled,
                paletteStyle = currentPaletteStyle,
                colorSpec = currentColorSpec,
                surfaceBlur = currentSurfaceBlur,
                layoutScale = currentLayoutScale,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val runtimeWarning = runtimeAvailability.requiresRuntimeWarning()
                    val managerWarning = legacyModuleInstalled
                    val commitMismatch = runtimeAvailability.runtimeCommitMismatch(
                        moduleCommit = CommonBuildConfig.GIT_COMMIT,
                    )

                    // 每次冷启动都会重新提醒（rememberSaveable 跟随 Activity 实例），
                    // app 内切 tab / 重组不会反复弹。
                    var runtimeWarningDismissed by rememberSaveable { mutableStateOf(false) }
                    var commitMismatchDismissed by rememberSaveable { mutableStateOf(false) }
                    var managerWarningDismissed by rememberSaveable { mutableStateOf(false) }

                    // 错配发生时 Toast 简短通知一次。
                    val toastContext = LocalContext.current
                    LaunchedEffect(commitMismatch) {
                        if (commitMismatch != null) {
                            Toast.makeText(
                                toastContext,
                                R.string.runtime_commit_mismatch_toast,
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }

                    when {
                        runtimeWarning && !runtimeWarningDismissed -> {
                            RuntimeCompatibilityWarningDialog(
                                onDismiss = { runtimeWarningDismissed = true },
                            )
                        }

                        commitMismatch != null && !commitMismatchDismissed -> {
                            RuntimeCommitMismatchDialog(
                                mismatch = commitMismatch,
                                onDismiss = { commitMismatchDismissed = true },
                            )
                        }

                        managerWarning && !managerWarningDismissed -> {
                            LegacyModuleWarningDialog(
                                onDismiss = { managerWarningDismissed = true },
                            )
                        }

                        else -> {
                            MainScreen(
                                startDestination = startDestination,
                                initialRouteOverride = explicitRoute,
                            )
                        }
                    }

                    ThemeRevealOverlay(themeRevealState)
                }
            }
        }
        }
    }

    override fun onResume() {
        super.onResume()
        legacyModuleInstalled = LegacyModuleDetector.isInstalled(packageManager)
    }

    override fun onDestroy() {
        mainActivityUtils.close()
        super.onDestroy()
    }
}

@Composable
private fun RuntimeCompatibilityWarningDialog(onDismiss: () -> Unit) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
        title = {
            Text(text = stringResource(R.string.runtime_missing_dialog_title), color = MaterialTheme.colorScheme.onSurface)
        },
        text = {
            Text(text = stringResource(R.string.runtime_missing_dialog_message), color = MaterialTheme.colorScheme.onSurface)
        },
        confirmButton = {
            AppTextButton(
                text = stringResource(R.string.dialog_acknowledge),
                onClick = onDismiss,
            )
        },
    )
}

@Composable
private fun RuntimeCommitMismatchDialog(
    mismatch: RuntimeCommitMismatch,
    onDismiss: () -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
        title = {
            Text(text = stringResource(R.string.runtime_commit_mismatch_dialog_title), color = MaterialTheme.colorScheme.onSurface)
        },
        text = {
            Text(
                text = stringResource(
                    R.string.runtime_commit_mismatch_dialog_message,
                    mismatch.moduleCommit,
                    mismatch.runtimeCommit,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        confirmButton = {
            AppTextButton(
                text = stringResource(R.string.dialog_acknowledge),
                onClick = onDismiss,
            )
        },
    )
}

@Composable
private fun LegacyModuleWarningDialog(onDismiss: () -> Unit) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
        title = {
            Text(text = stringResource(R.string.legacy_module_dialog_title), color = MaterialTheme.colorScheme.onSurface)
        },
        text = {
            Text(text = stringResource(R.string.legacy_module_dialog_message), color = MaterialTheme.colorScheme.onSurface)
        },
        confirmButton = {
            AppTextButton(
                text = stringResource(R.string.dialog_acknowledge),
                onClick = onDismiss,
            )
        },
    )
}
