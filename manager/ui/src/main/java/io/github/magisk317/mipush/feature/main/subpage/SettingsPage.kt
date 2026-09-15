@file:android.annotation.SuppressLint("LocalContextGetResourceValueCall")
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.magisk317.mipush.feature.main.subpage

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import io.github.magisk317.uikit.common.AppSnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.mipush.main.viewmodel.SettingsViewModel
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.common.process.BoundedProcessRunner
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.uikit.scroll.ScrollChromeState
import io.github.magisk317.uikit.surface.SectionColumn
import io.github.magisk317.mipush.feature.ui.theme.Theme
import io.github.magisk317.mipush.feature.ui.theme.spacing
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle

private val settingsPageLogger = Logger.withTag("SettingsPage")

@Composable
fun Settings(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: SettingsViewModel = koinViewModel(),
    onSectionChanged: (String?) -> Unit = {},
    onNavigateToConnectionStatus: () -> Unit = {},
    onNavigateToStatusBarIconSettings: () -> Unit = {},
    onNavigateToThemeSettings: () -> Unit = {},
    onNavigateToConfigurations: () -> Unit = {},
    isActive: Boolean = true,
    scrollChromeState: ScrollChromeState? = null,
) {
    val snackbarHostState = remember { AppSnackbarHostState() }
    val scrollState = rememberScrollState()

    val body: @Composable (PaddingValues, Modifier) -> Unit = { listPadding, scrollModifier ->
        SettingsBody(
            listPadding = listPadding,
            scrollModifier = scrollModifier,
            scrollState = scrollState,
            contentPadding = contentPadding,
            viewModel = viewModel,
            onSectionChanged = onSectionChanged,
            onNavigateToConnectionStatus = onNavigateToConnectionStatus,
            onNavigateToStatusBarIconSettings = onNavigateToStatusBarIconSettings,
            onNavigateToThemeSettings = onNavigateToThemeSettings,
            onNavigateToConfigurations = onNavigateToConfigurations,
            isActive = isActive,
            snackbarHostState = snackbarHostState,
        )
    }

    when (currentUiKitStyle()) {
        UiKitStyle.Miuix -> SettingsMiuix(
            contentPadding = contentPadding,
            scrollChromeState = scrollChromeState,
            scrollState = scrollState,
            snackbarHostState = snackbarHostState,
            body = body,
        )

        UiKitStyle.Expressive -> SettingsExpressive(
            contentPadding = contentPadding,
            scrollChromeState = scrollChromeState,
            scrollState = scrollState,
            snackbarHostState = snackbarHostState,
            body = body,
        )
    }
}

@Composable
private fun SettingsBody(
    listPadding: PaddingValues,
    scrollModifier: Modifier,
    scrollState: androidx.compose.foundation.ScrollState,
    contentPadding: PaddingValues,
    viewModel: SettingsViewModel,
    onSectionChanged: (String?) -> Unit,
    onNavigateToConnectionStatus: () -> Unit,
    onNavigateToStatusBarIconSettings: () -> Unit,
    onNavigateToThemeSettings: () -> Unit,
    onNavigateToConfigurations: () -> Unit,
    isActive: Boolean,
    snackbarHostState: AppSnackbarHostState,
) {
    val title = stringResource(R.string.main_settings)
    val sectionExpanded by viewModel.sectionExpanded.collectAsStateWithLifecycle()
    settingsPageLogger.d { "compose: service=${sectionExpanded.service} isActive=$isActive" }
    val toggleSection = remember(viewModel) { { id: SettingsViewModel.SectionId -> viewModel.toggleSection(id) } }
    var hasLoadedRuntimeState by rememberSaveable { mutableStateOf(false) }

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

    SectionColumn(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .then(scrollModifier),
        contentPadding = PaddingValues(
            start = MaterialTheme.spacing.medium,
            top = listPadding.calculateTopPadding() + MaterialTheme.spacing.small,
            end = MaterialTheme.spacing.medium,
            bottom = contentPadding.calculateBottomPadding() + MaterialTheme.spacing.large,
        ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        SettingsSectionCard(
            title = stringResource(R.string.settings_home_appearance_title),
            summary = stringResource(R.string.settings_home_appearance_summary),
            expanded = sectionExpanded[SettingsViewModel.SectionId.APPEARANCE],
            onExpandedChange = { toggleSection(SettingsViewModel.SectionId.APPEARANCE) },
        ) {
            AppearanceBlock(viewModel, onNavigateToStatusBarIconSettings, onNavigateToThemeSettings)
        }

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
            FreezeBlock(viewModel, snackbarHostState)
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
            title = stringResource(R.string.settings_home_configurations_title),
            summary = stringResource(R.string.settings_home_configurations_summary),
            expanded = sectionExpanded[SettingsViewModel.SectionId.CONFIGURATIONS],
            onExpandedChange = { toggleSection(SettingsViewModel.SectionId.CONFIGURATIONS) },
        ) {
            ConfigurationsBlock(
                onNavigateToConfigurations = onNavigateToConfigurations,
            )
        }

        SettingsSectionCard(
            title = stringResource(R.string.settings_home_integrations_title),
            summary = stringResource(R.string.settings_home_integrations_summary),
            expanded = sectionExpanded[SettingsViewModel.SectionId.INTEGRATIONS],
            onExpandedChange = { toggleSection(SettingsViewModel.SectionId.INTEGRATIONS) },
        ) {
            IntegrationsBlock(
                viewModel = viewModel,
                snackbarHostState = snackbarHostState,
            )
        }

        SettingsSectionCard(
            title = stringResource(R.string.settings_home_diagnostics_title),
            summary = stringResource(R.string.settings_home_diagnostics_summary),
            expanded = sectionExpanded[SettingsViewModel.SectionId.DIAGNOSTICS],
            onExpandedChange = { toggleSection(SettingsViewModel.SectionId.DIAGNOSTICS) },
        ) {
            DiagnosticsBlock(viewModel, snackbarHostState)
        }

    }
}

@Composable
fun SettingsPagePreview() {
    Utils.context = LocalContext.current
    Theme {
        Settings(PaddingValues(0.dp))
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
