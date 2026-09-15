package io.github.magisk317.mipush.feature.main

import io.github.magisk317.uikit.surface.AppPrimaryButton
import io.github.magisk317.uikit.preference.AppDropdownMenu
import io.github.magisk317.uikit.preference.AppSwitch
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import io.github.magisk317.uikit.surface.AppCircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import io.github.magisk317.uikit.surface.AppFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import io.github.magisk317.uikit.common.AppSnackbarDuration
import io.github.magisk317.uikit.common.AppSnackbarHostState
import io.github.magisk317.uikit.surface.AppSurface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.magisk317.mipush.manager.application.ManagerApplication
import io.github.magisk317.mipush.feature.ui.theme.Theme
import io.github.magisk317.mipush.feature.ui.theme.spacing
import io.github.magisk317.mipush.main.viewmodel.ZygiskConfigViewModel
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.uikit.surface.AppIconImage
import io.github.magisk317.uikit.theme.UiKitStyle
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import io.github.magisk317.uikit.theme.applyEdgeToEdge
import io.github.magisk317.uikit.theme.currentUiKitStyle
import io.github.magisk317.uikit.surface.AppSurface

class ZygiskConfigPage : ComponentActivity() {

    private val viewModel: ZygiskConfigViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyEdgeToEdge(this)
        setContent {
            // Theme() resolves every field from the stored preferences itself.
            Theme {
                ZygiskConfigApp()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ZygiskConfigApp() {
        val state by viewModel.state.collectAsStateWithLifecycle()
        val snackbarHostState = remember { AppSnackbarHostState() }
        val scope = rememberCoroutineScope()
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        val saveSuccessMessage = stringResource(R.string.zygisk_save_success)
        val saveFailedMessage = stringResource(R.string.zygisk_save_failed)
        
        LaunchedEffect(Unit) {
            viewModel.load()
        }

        val saveFab: @Composable () -> Unit = {
            if (state.configReadAvailable) AppFloatingActionButton(
                onClick = {
                    viewModel.saveConfig(
                        onSuccess = {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    saveSuccessMessage,
                                    duration = AppSnackbarDuration.Short,
                                )
                            }
                        },
                        onError = {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    saveFailedMessage,
                                    duration = AppSnackbarDuration.Short,
                                )
                            }
                        }
                    )
                }
            ) {
                Icon(
                    Icons.Filled.Save,
                    contentDescription = stringResource(R.string.zygisk_save),
                )
            }
        }

        val body: @Composable (PaddingValues, Modifier) -> Unit = { innerPadding, scrollModifier ->
            AppSurface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                color = MaterialTheme.colorScheme.background,
            ) {
                if (state.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AppCircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(scrollModifier),
                        state = listState,
                        contentPadding = PaddingValues(
                            horizontal = MaterialTheme.spacing.medium,
                            vertical = MaterialTheme.spacing.small,
                        ),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                    ) {
                        item {
                            state.configReadError?.let { error ->
                                Text(
                                    text = stringResource(R.string.zygisk_config_read_error, error),
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(MaterialTheme.spacing.large),
                                )
                            }
                        }
                        item {
                            ZygiskStatusHeader(
                                isZygiskEnabled = state.isZygiskEnabled,
                                isAvailable = state.zygiskStatusAvailable,
                                error = state.zygiskStatusError,
                            )
                        }
                        item {
                            ZygiskOptions(
                                profile = state.profile,
                                observe = state.observe,
                                onProfileChanged = viewModel::setProfile,
                                onObserveChanged = viewModel::setObserve,
                                enabled = state.configReadAvailable,
                                candidates = state.scanCandidates,
                                scanError = state.scanError,
                                onScan = viewModel::scan,
                            )
                        }
                        items(state.installedApps) { app ->
                            AppItem(
                                app = app,
                                isChecked = state.spoofPackages.contains(app.packageName),
                                enabled = state.configReadAvailable,
                                onCheckedChange = { checked ->
                                    viewModel.togglePackage(app.packageName, checked)
                                }
                            )
                        }
                    }
                }
            }
        }

        when (currentUiKitStyle()) {
            UiKitStyle.Miuix -> ZygiskConfigMiuix(
                title = stringResource(R.string.zygisk_status),
                snackbarHostState = snackbarHostState,
                floatingActionButton = saveFab,
                body = body,
            )

            UiKitStyle.Expressive -> ZygiskConfigExpressive(
                title = stringResource(R.string.zygisk_status),
                snackbarHostState = snackbarHostState,
                floatingActionButton = saveFab,
                body = body,
            )
        }
    }

    @Composable
    private fun ZygiskOptions(
        profile: String,
        observe: Boolean,
        onProfileChanged: (String) -> Unit,
        onObserveChanged: (Boolean) -> Unit,
        candidates: List<String>,
        scanError: String?,
        onScan: () -> Unit,
        enabled: Boolean,
    ) {
        var expanded by remember { mutableStateOf(false) }
        AppSurface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.spacing.large),
            ) {
                Text(
                    stringResource(R.string.zygisk_profile_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                AppDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    title = stringResource(R.string.zygisk_profile_title),
                    options = listOf(
                        stringResource(R.string.zygisk_profile_miui14),
                        stringResource(R.string.zygisk_profile_os4),
                    ),
                    selectedIndex = if (profile == "miui14") 0 else 1,
                    onSelectionChange = { index ->
                        onProfileChanged(if (index == 0) "miui14" else "os4")
                        expanded = false
                    },
                    enabled = enabled,
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.zygisk_observe_keys), modifier = Modifier.weight(1f))
                AppSwitch(checked = observe, enabled = enabled, onCheckedChange = onObserveChanged)
            }
            Text(
                stringResource(R.string.zygisk_scan_title),
                style = MaterialTheme.typography.titleMedium,
            )
            AppPrimaryButton(
                onClick = onScan,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.zygisk_scan))
            }
            scanError?.let { error ->
                Text(
                    text = stringResource(R.string.zygisk_scan_error, error),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            candidates.take(32).forEach { candidate ->
                Text(candidate, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    @Composable
    private fun ZygiskStatusHeader(
        isZygiskEnabled: Boolean,
        isAvailable: Boolean,
        error: String?,
    ) {
        val statusText = when {
            !isAvailable -> stringResource(R.string.zygisk_unavailable_detail, error.orEmpty())
            isZygiskEnabled -> stringResource(R.string.zygisk_enabled)
            else -> stringResource(R.string.zygisk_disabled)
        }
        val color = if (isZygiskEnabled && isAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        AppSurface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.large,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.spacing.large),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.zygisk_module_status, statusText),
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                )
            }
        }
    }

    @Composable
    private fun AppItem(
        app: ManagerApplication,
        isChecked: Boolean,
        enabled: Boolean,
        onCheckedChange: (Boolean) -> Unit
    ) {
        AppSurface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.large,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIconImage(
                    packageName = app.packageName,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(MaterialTheme.spacing.medium))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                AppSwitch(
                    checked = isChecked,
                    enabled = enabled,
                    onCheckedChange = onCheckedChange,
                )
            }
        }
    }
}
