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

private val settingsPageLogger = Logger.withTag("SettingsPage")

@Composable
fun Settings(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: SettingsViewModel = koinViewModel(),
    onShowAboutDialog: (String) -> Unit = {},
    onSectionChanged: (String?) -> Unit = {},
    onNavigateToConnectionStatus: () -> Unit = {},
    onNavigateToStatusBarIconSettings: () -> Unit = {},
    onNavigateToConfigurations: () -> Unit = {},
    sectionBackSignal: Int = 0,
    isActive: Boolean = true,
    scrollChromeState: ScrollChromeState? = null,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    Page {
        Box(modifier = Modifier.fillMaxSize()) {
            val scrollState = androidx.compose.foundation.rememberScrollState()
            SettingsScreen(
                contentPadding = contentPadding,
                onShowAboutDialog = onShowAboutDialog,
                viewModel = viewModel,
                onSectionChanged = onSectionChanged,
                onNavigateToConnectionStatus = onNavigateToConnectionStatus,
                onNavigateToStatusBarIconSettings = onNavigateToStatusBarIconSettings,
                onNavigateToConfigurations = onNavigateToConfigurations,
                sectionBackSignal = sectionBackSignal,
                isActive = isActive,
                snackbarHostState = snackbarHostState,
                scrollChromeState = scrollChromeState,
                scrollState = scrollState,
            )
            ElevatedSnackbarHost(
                hostState = snackbarHostState,
                bottomPadding = contentPadding.calculateBottomPadding() + 16.dp,
            )
            ScrollToTopFAB(scrollState, visible = scrollChromeState?.isChromeVisible != true, extraBottomPadding = 80.dp)
        }
    }
}

@Composable
private fun SettingsScreen(
    contentPadding: PaddingValues,
    onShowAboutDialog: (String) -> Unit,
    viewModel: SettingsViewModel,
    onSectionChanged: (String?) -> Unit,
    onNavigateToConnectionStatus: () -> Unit,
    onNavigateToStatusBarIconSettings: () -> Unit,
    onNavigateToConfigurations: () -> Unit,
    scrollState: androidx.compose.foundation.ScrollState = androidx.compose.foundation.rememberScrollState(),
    sectionBackSignal: Int,
    isActive: Boolean,
    snackbarHostState: SnackbarHostState,
    scrollChromeState: ScrollChromeState?,
) {
    val title = stringResource(R.string.main_settings)
    val sectionExpanded by viewModel.sectionExpanded.collectAsStateWithLifecycle()
    settingsPageLogger.d { "compose: service=${sectionExpanded.service} isActive=$isActive" }
    val toggleSection = remember(viewModel) { { id: SettingsViewModel.SectionId -> viewModel.toggleSection(id) } }
    var hasLoadedRuntimeState by rememberSaveable { mutableStateOf(false) }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LaunchedEffect(title) {
        onSectionChanged(title)
    }
    LaunchedEffect(isActive) {
        if (!isActive || hasLoadedRuntimeState) return@LaunchedEffect
        // Settings state is not part of the tab transition; defer the runtime binder read.
        withFrameNanos { }
        viewModel.refreshDualAppFromRuntime()
        hasLoadedRuntimeState = true
    }

    OverlayHeaderScaffold(
        fallbackTopPadding = topInset + 64.dp,
        headerOffsetY = scrollChromeState?.animatedHeaderOffsetY ?: 0f,
        onHeaderHeightChanged = { scrollChromeState?.headerHeightPx = it.toFloat() },
        overlayModifier = Modifier
            .fillMaxWidth(),
        overlay = {
            TopAppBar(
                title = { Text(title) },
                windowInsets = WindowInsets.statusBars,
                colors = chromeTopAppBarColors(),
            )
        },
        content = { listPadding ->
            SectionColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                contentPadding = PaddingValues(
                    start = MaterialTheme.spacing.medium,
                    top = listPadding.calculateTopPadding() + MaterialTheme.spacing.small,
                    end = MaterialTheme.spacing.medium,
                    bottom = contentPadding.calculateBottomPadding() + MaterialTheme.spacing.large,
                ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                SettingsSectionCard(
                    title = stringResource(R.string.settings_home_service_title),
                    summary = stringResource(R.string.settings_home_service_summary),
                    expanded = sectionExpanded[SettingsViewModel.SectionId.SERVICE],
                    onExpandedChange = { toggleSection(SettingsViewModel.SectionId.SERVICE) },
                ) {
                    ConnectionServiceBlock(viewModel, snackbarHostState, onNavigateToConnectionStatus)
                }

                SettingsSectionCard(
                    title = stringResource(R.string.settings_home_keepalive_title),
                    summary = stringResource(R.string.settings_home_keepalive_summary),
                    expanded = sectionExpanded[SettingsViewModel.SectionId.KEEP_ALIVE],
                    onExpandedChange = { toggleSection(SettingsViewModel.SectionId.KEEP_ALIVE) },
                ) {
                    KeepAliveBlock(viewModel, snackbarHostState)
                }

                SettingsSectionCard(
                    title = stringResource(R.string.settings_home_notifications_title),
                    summary = stringResource(R.string.settings_home_notifications_summary),
                    expanded = sectionExpanded[SettingsViewModel.SectionId.NOTIFICATIONS],
                    onExpandedChange = { toggleSection(SettingsViewModel.SectionId.NOTIFICATIONS) },
                ) {
                    NotificationsBlock(viewModel, snackbarHostState)
                }

                SettingsSectionCard(
                    title = stringResource(R.string.settings_home_appearance_title),
                    summary = stringResource(R.string.settings_home_appearance_summary),
                    expanded = sectionExpanded[SettingsViewModel.SectionId.APPEARANCE],
                    onExpandedChange = { toggleSection(SettingsViewModel.SectionId.APPEARANCE) },
                ) {
                    AppearanceBlock(viewModel, onNavigateToStatusBarIconSettings)
                }

                SettingsSectionCard(
                    title = stringResource(R.string.settings_home_configurations_title),
                    summary = stringResource(R.string.settings_home_configurations_summary),
                    expanded = sectionExpanded[SettingsViewModel.SectionId.CONFIGURATIONS],
                    onExpandedChange = { toggleSection(SettingsViewModel.SectionId.CONFIGURATIONS) },
                ) {
                    ConfigurationsBlock(
                        viewModel = viewModel,
                        snackbarHostState = snackbarHostState,
                        onNavigateToConfigurations = onNavigateToConfigurations,
                    )
                }

                SettingsSectionCard(
                    title = stringResource(R.string.settings_home_integrations_title),
                    summary = stringResource(R.string.settings_home_integrations_summary),
                    expanded = sectionExpanded[SettingsViewModel.SectionId.INTEGRATIONS],
                    onExpandedChange = { toggleSection(SettingsViewModel.SectionId.INTEGRATIONS) },
                ) {
                    IntegrationsBlock(viewModel, snackbarHostState)
                }

                SettingsSectionCard(
                    title = stringResource(R.string.settings_home_diagnostics_title),
                    summary = stringResource(R.string.settings_home_diagnostics_summary),
                    expanded = sectionExpanded[SettingsViewModel.SectionId.DIAGNOSTICS],
                    onExpandedChange = { toggleSection(SettingsViewModel.SectionId.DIAGNOSTICS) },
                ) {
                    DiagnosticsBlock(viewModel, snackbarHostState)
                }

                SettingsSectionCard(
                    title = stringResource(R.string.settings_home_about_title),
                    summary = stringResource(R.string.settings_home_about_summary),
                    expanded = sectionExpanded[SettingsViewModel.SectionId.ABOUT],
                    onExpandedChange = { toggleSection(SettingsViewModel.SectionId.ABOUT) },
                ) {
                    AboutBlock(onShowAboutDialog)
                }
            }
        },
    )
}

@Composable
fun SettingsPagePreview() {
    Utils.context = LocalContext.current
    Theme {
        Settings(PaddingValues(0.dp), onShowAboutDialog = {})
    }
}

internal suspend fun toggleAccessibilityServiceViaRoot(context: android.content.Context, enable: Boolean): Boolean {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val component = ComponentName(
            Constants.SERVICE_APP_NAME,
            Constants.KEEPALIVE_ACCESSIBILITY_SERVICE_CLASS,
        ).flattenToString()
        val currentServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        val newServices = if (enable) {
            if (currentServices.contains(component)) return@withContext true
            if (currentServices.isEmpty()) component else "$currentServices:$component"
        } else {
            if (!currentServices.contains(component)) return@withContext true
            currentServices.split(":").filter { it.isNotEmpty() && it != component }.joinToString(":")
        }

        val script = buildString {
            appendLine("settings put secure enabled_accessibility_services $newServices")
            if (enable) appendLine("settings put secure accessibility_enabled 1")
            appendLine("exit")
        }
        BoundedProcessRunner.run(
            command = listOf("su"),
            timeoutMillis = 8_000L,
            standardInput = script,
        ).isSuccess
    }
}
