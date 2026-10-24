@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.magisk317.mipush.feature.main

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
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
import io.github.magisk317.mipush.feature.wizard.support.WizardSPUtils
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.main.viewmodel.requiresRuntimeWarning
import io.github.magisk317.mipush.main.viewmodel.runtimeCommitMismatch
import io.github.magisk317.mipush.manager.BuildConfig
import io.github.magisk317.mipush.manager.SettingsManager
import io.github.magisk317.mipush.manager.LegacyModuleDetector
import io.github.magisk317.mipush.manager.application.ManagerConfigGateway
import io.github.magisk317.mipush.common.BuildConfig as CommonBuildConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.compose.koinInject
import io.github.magisk317.uikit.update.AppUpdateController
import io.github.magisk317.uikit.update.AppUpdateDialog
import io.github.magisk317.uikit.update.FlavorPlayUpdateDelegate
import io.github.magisk317.uikit.update.UpdatePlayPolicy
import io.github.magisk317.uikit.update.WifiState
import io.github.magisk317.uikit.shell.PlayUpdateDelegate
import io.github.magisk317.uikit.theme.applyEdgeToEdge
import io.github.magisk317.uikit.theme.ThemeRevealOverlay
import io.github.magisk317.uikit.theme.rememberThemeRevealState
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.theme.AppColorRole
import io.github.magisk317.uikit.theme.appColor

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
    private var incompatibleModules by mutableStateOf<List<String>>(emptyList())
    private val playUpdateDelegate: PlayUpdateDelegate by lazy { FlavorPlayUpdateDelegate() }
    private var autoUpdateChecked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incompatibleModules = LegacyModuleDetector.findInstalledPackages(packageManager)
        applyEdgeToEdge(this)
        playUpdateDelegate.onCreate(this) {
            UpdateFallback.openPlayStoreOrGithub(this)
        }
        triggerAutoUpdateIfEnabled()
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
        // The report overlays the main screen instead of replacing it: everything the
        // user can grant later is still grantable from inside, and the runtime client
        // starts connecting behind the report instead of after it.
        if (savedInstanceState == null) {
            lifecycleScope.launch {
                if (WizardSPUtils.shouldShowWizard(this@MainActivity, preferenceRepository)) {
                    startActivity(
                        Intent(this@MainActivity, RequestPermissionPage::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION),
                    )
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, 0)
                    }
                }
            }
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
                    val managerWarning = incompatibleModules.isNotEmpty()
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
                                packageNames = incompatibleModules,
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

                    val updateController = koinInject<AppUpdateController>()
                    // 冷启动静默检测更新：后台不打扰用户，强制更新照常弹窗
                    LaunchedEffect(Unit) {
                        updateController.check(userInitiated = false)
                    }
                    AppUpdateDialog(
                        controller = updateController,
                        onInstall = { updateController.install(this@MainActivity) },
                    )

                    ThemeRevealOverlay(themeRevealState)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        incompatibleModules = LegacyModuleDetector.findInstalledPackages(packageManager)
        playUpdateDelegate.onResume(this) {
            UpdateFallback.openPlayStoreOrGithub(this)
        }
    }

    override fun onDestroy() {
        mainActivityUtils.close()
        playUpdateDelegate.onDestroy()
        super.onDestroy()
    }

    private fun requestPlayUpdateInternal(silentIfNoUpdate: Boolean, fallbackOnQueryFailure: Boolean) {
        playUpdateDelegate.requestUpdate(
            activity = this,
            silentIfNoUpdate = silentIfNoUpdate,
            fallbackOnQueryFailure = fallbackOnQueryFailure,
        ) {
            UpdateFallback.openPlayStoreOrGithub(this)
        }
    }

    private fun triggerAutoUpdateIfEnabled() {
        if (!BuildConfig.HAS_BILLING || autoUpdateChecked) return
        autoUpdateChecked = true

        lifecycleScope.launch {
            val enabled = preferenceRepository.autoUpdateOnStart.first()
            if (!enabled) return@launch

            val wifiOnly = preferenceRepository.autoUpdateWifiOnly.first()
            val onWifi = WifiState.isOnWifi(this@MainActivity)
            if (!UpdatePlayPolicy.shouldRunAutoCheck(enabled, wifiOnly, onWifi)) return@launch

            requestPlayUpdateInternal(silentIfNoUpdate = true, fallbackOnQueryFailure = false)
        }
    }
}

@Composable
private fun RuntimeCompatibilityWarningDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AppAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
        title = {
            AppText(text = stringResource(R.string.runtime_missing_dialog_title), color = appColor(AppColorRole.OnSurface))
        },
        text = {
            AppText(text = stringResource(R.string.runtime_missing_dialog_message), color = appColor(AppColorRole.OnSurface))
        },
        confirmButton = {
            AppTextButton(
                text = stringResource(R.string.runtime_warning_check_permissions),
                onClick = {
                    context.startActivity(
                        Intent(context, RequestPermissionPage::class.java)
                            .putExtra(RequestPermissionPage.EXTRA_RECHECK_ONLY, true),
                    )
                    onDismiss()
                },
            )
        },
        dismissButton = {
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
            AppText(text = stringResource(R.string.runtime_commit_mismatch_dialog_title), color = appColor(AppColorRole.OnSurface))
        },
        text = {
            AppText(
                text = stringResource(
                    R.string.runtime_commit_mismatch_dialog_message,
                    mismatch.moduleCommit,
                    mismatch.runtimeCommit,
                ),
                color = appColor(AppColorRole.OnSurface),
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
private fun LegacyModuleWarningDialog(
    packageNames: List<String>,
    onDismiss: () -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
        title = {
            AppText(text = stringResource(R.string.legacy_module_dialog_title), color = appColor(AppColorRole.OnSurface))
        },
        text = {
            AppText(
                text = stringResource(
                    R.string.legacy_module_dialog_message,
                    packageNames.joinToString(", "),
                ),
                color = appColor(AppColorRole.OnSurface),
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
