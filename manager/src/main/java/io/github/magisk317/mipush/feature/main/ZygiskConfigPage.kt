package io.github.magisk317.mipush.feature.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.mipush.common.manager.ManagerApplication
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
                                    snackbarHostState.showSnackbar("Saved successfully", duration = SnackbarDuration.Short)
                                }
                            },
                            onError = {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Failed to save (Root required?)", duration = SnackbarDuration.Short)
                                }
                            }
                        )
                    }
                ) {
                    Icon(Icons.Filled.Save, contentDescription = "Save")
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
                                    text = "Unable to read Zygisk configuration: $error",
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
                                autoScan = state.autoScan,
                                onProfileChanged = viewModel::setProfile,
                                onObserveChanged = viewModel::setObserve,
                                onAutoScanChanged = viewModel::setAutoScan,
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
        autoScan: Boolean,
        onProfileChanged: (String) -> Unit,
        onObserveChanged: (Boolean) -> Unit,
        onAutoScanChanged: (Boolean) -> Unit,
        candidates: List<String>,
        scanError: String?,
        onScan: () -> Unit,
        enabled: Boolean,
    ) {
        var expanded by remember { mutableStateOf(false) }
        Column(modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.large)) {
            Text("Device profile", style = MaterialTheme.typography.titleMedium)
            Box {
                Text(
                    text = profile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = enabled) { expanded = true }
                        .padding(vertical = MaterialTheme.spacing.medium),
                    color = MaterialTheme.colorScheme.primary,
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    listOf("miui14", "hyperos1", "legacy-v11").forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            enabled = enabled,
                            onClick = { onProfileChanged(option); expanded = false },
                        )
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Observe property keys", modifier = Modifier.weight(1f))
                Switch(checked = observe, enabled = enabled, onCheckedChange = onObserveChanged)
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Automatic scan", modifier = Modifier.weight(1f))
                Switch(checked = autoScan, enabled = enabled, onCheckedChange = onAutoScanChanged)
            }
            Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                Text("Scan installed packages")
            }
            scanError?.let { error ->
                Text(
                    text = "Unable to scan packages: $error",
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
            !isAvailable -> "Module status unavailable: ${error.orEmpty()}"
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
                text = "Module Status: $statusText",
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
                label = app.appName,
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
