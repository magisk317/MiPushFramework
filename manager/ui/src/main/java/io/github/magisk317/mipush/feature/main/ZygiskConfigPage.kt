package io.github.magisk317.mipush.feature.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.mipush.manager.application.ManagerApplication
import io.github.magisk317.mipush.feature.ui.theme.Theme
import io.github.magisk317.mipush.feature.ui.theme.spacing
import io.github.magisk317.mipush.main.viewmodel.ZygiskConfigViewModel
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.uikit.surface.AppIconImage
import io.github.magisk317.uikit.surface.chromeTopAppBarColors
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class ZygiskConfigPage : ComponentActivity() {

    private val viewModel: ZygiskConfigViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Theme {
                ZygiskConfigApp()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ZygiskConfigApp() {
        val state by viewModel.state.collectAsStateWithLifecycle()
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val saveSuccessMessage = stringResource(R.string.zygisk_save_success)
        val saveFailedMessage = stringResource(R.string.zygisk_save_failed)
        
        LaunchedEffect(Unit) {
            viewModel.load()
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.zygisk_status)) },
                    colors = chromeTopAppBarColors(),
                )
            },
            floatingActionButton = {
                if (state.configReadAvailable) FloatingActionButton(
                    onClick = {
                        viewModel.saveConfig(
                            onSuccess = {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        saveSuccessMessage,
                                        duration = SnackbarDuration.Short,
                                    )
                                }
                            },
                            onError = {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        saveFailedMessage,
                                        duration = SnackbarDuration.Short,
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
            },
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            }
        ) { innerPadding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (state.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
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
        Column(modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.large)) {
            Text(
                stringResource(R.string.zygisk_profile_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Box {
                Text(
                    text = when (profile) {
                        "miui14" -> stringResource(R.string.zygisk_profile_miui14)
                        else -> stringResource(R.string.zygisk_profile_os4)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = enabled) { expanded = true }
                        .padding(vertical = MaterialTheme.spacing.medium),
                    color = MaterialTheme.colorScheme.primary,
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    listOf(
                        "miui14" to R.string.zygisk_profile_miui14,
                        "os4" to R.string.zygisk_profile_os4,
                    ).forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(label)) },
                            enabled = enabled,
                            onClick = { onProfileChanged(value); expanded = false },
                        )
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.zygisk_observe_keys), modifier = Modifier.weight(1f))
                Switch(checked = observe, enabled = enabled, onCheckedChange = onObserveChanged)
            }
            Text(
                stringResource(R.string.zygisk_scan_title),
                style = MaterialTheme.typography.titleMedium,
            )
            androidx.compose.material3.Button(
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.large),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.zygisk_module_status, statusText),
                style = MaterialTheme.typography.titleMedium,
                color = color
            )
        }
    }

    @Composable
    private fun AppItem(
        app: ManagerApplication,
        isChecked: Boolean,
        enabled: Boolean,
        onCheckedChange: (Boolean) -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically
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
            Switch(
                checked = isChecked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}
