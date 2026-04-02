@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package top.trumeet.mipushframework.main.subpage

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaomi.xmsf.R
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeSource
import io.github.magisk317.uikit.surface.WorkspaceEmptyState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.trumeet.mipushframework.component.SearchBar
import top.trumeet.mipushframework.component.TextView
import top.trumeet.mipushframework.component.WorkspaceListItem
import top.trumeet.mipushframework.config.ConfigContentSource
import top.trumeet.mipushframework.config.ConfigEditorViewModel
import top.trumeet.mipushframework.config.ConfigListItem
import top.trumeet.mipushframework.config.ConfigManagerViewModel
import top.trumeet.mipushframework.config.ConfigSyncStatus
import top.trumeet.ui.theme.spacing

@Composable
fun Configurations(
    initialQuery: String = "",
    contentPadding: PaddingValues = PaddingValues(0.dp),
    refreshSignal: Int = 0,
    onOpenEditor: (String) -> Unit,
    viewModel: ConfigManagerViewModel = hiltViewModel(),
    hazeState: HazeState? = null,
    hazeStyle: HazeStyle? = null,
) {
    Page {
        val context = androidx.compose.ui.platform.LocalContext.current
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

        val openDirectoryLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            if (uri != null) {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                viewModel.updateConfigurationDirectory(uri)
            }
        }
        val importLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenMultipleDocuments(),
        ) { uris ->
            if (uris.isNotEmpty()) {
                viewModel.importDocuments(uris)
            }
        }

        LaunchedEffect(initialQuery) {
            viewModel.setQuery(initialQuery)
        }
        LaunchedEffect(refreshSignal) {
            if (refreshSignal > 0) {
                viewModel.refresh()
            }
        }
        LaunchedEffect(uiState.message) {
            val message = uiState.message ?: return@LaunchedEffect
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }

        val filteredItems = remember(uiState.items, uiState.query) {
            val query = uiState.query.trim()
            if (query.isEmpty()) {
                uiState.items
            } else {
                uiState.items.filter { item ->
                    item.path.contains(query, ignoreCase = true) ||
                        item.displayName.contains(query, ignoreCase = true)
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .then(if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier),
            ) {
                TopAppBar(
                    title = { Text(stringResource(R.string.main_configs)) },
                    windowInsets = WindowInsets.statusBars,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                ) {
                    Text(
                        text = stringResource(R.string.config_remote_source_label, "magisk317/MiPushConfigurations@dev"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (uiState.directoryUri.isNullOrBlank()) {
                            stringResource(R.string.config_directory_not_selected)
                        } else {
                            stringResource(R.string.config_directory_label, uiState.directoryUri!!)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(
                            R.string.config_last_sync_label,
                            uiState.lastSyncTime.asReadableTime(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    SearchBar(
                        placeholder = stringResource(R.string.config_search_placeholder),
                        query = uiState.query,
                        onValueChange = viewModel::setQuery,
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                    ) {
                        FilledTonalButton(onClick = { openDirectoryLauncher.launch(null) }) {
                            Text(stringResource(R.string.config_choose_directory))
                        }
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                            enabled = !uiState.directoryUri.isNullOrBlank(),
                        ) {
                            Text(stringResource(R.string.config_import_local))
                        }
                        OutlinedButton(
                            onClick = viewModel::pullRemote,
                            enabled = !uiState.directoryUri.isNullOrBlank() && !uiState.isSyncing,
                        ) {
                            Text(stringResource(R.string.config_pull_remote))
                        }
                        OutlinedButton(onClick = viewModel::reloadConfigurations) {
                            Text(stringResource(R.string.config_reload))
                        }
                    }

                    uiState.remoteError?.takeIf { it.isNotBlank() }?.let { error ->
                        Text(
                            text = stringResource(R.string.config_remote_error, error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = MaterialTheme.spacing.small),
                    contentPadding = PaddingValues(
                        start = MaterialTheme.spacing.medium,
                        end = MaterialTheme.spacing.medium,
                        top = 0.dp,
                        bottom = contentPadding.calculateBottomPadding() + MaterialTheme.spacing.large,
                    ),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                ) {
                    if (filteredItems.isEmpty() && !uiState.isLoading) {
                        item {
                            WorkspaceEmptyState(
                                title = stringResource(R.string.config_empty_title),
                                summary = stringResource(R.string.config_empty_summary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 280.dp),
                            )
                        }
                    } else {
                        items(filteredItems, key = { it.path }) { item ->
                            ConfigListEntry(
                                item = item,
                                onClick = { onOpenEditor(item.path) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConfigurationEditor(
    path: String,
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: ConfigEditorViewModel = hiltViewModel(),
) {
    Page {
        val context = androidx.compose.ui.platform.LocalContext.current
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        LaunchedEffect(path) {
            viewModel.load(path)
        }
        LaunchedEffect(uiState.message) {
            val message = uiState.message ?: return@LaunchedEffect
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }

        val selectedContent = when (uiState.selectedSource) {
            ConfigContentSource.LOCAL -> uiState.localContent ?: uiState.remoteContent
            ConfigContentSource.REMOTE -> uiState.remoteContent ?: uiState.localContent
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = contentPadding.calculateBottomPadding()),
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = path,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back_black_24dp),
                            contentDescription = stringResource(android.R.string.cancel),
                        )
                    }
                },
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = MaterialTheme.spacing.medium),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                ) {
                    FilterChip(
                        selected = uiState.selectedSource == ConfigContentSource.LOCAL,
                        onClick = { viewModel.selectSource(ConfigContentSource.LOCAL) },
                        enabled = uiState.hasLocal,
                        label = { Text(stringResource(R.string.config_source_local)) },
                    )
                    FilterChip(
                        selected = uiState.selectedSource == ConfigContentSource.REMOTE,
                        onClick = { viewModel.selectSource(ConfigContentSource.REMOTE) },
                        enabled = uiState.hasRemote,
                        label = { Text(stringResource(R.string.config_source_remote)) },
                    )
                    StatusBadge(
                        status = when {
                            uiState.localMeta?.isValid == false -> ConfigSyncStatus.INVALID_LOCAL
                            uiState.remoteMeta != null && uiState.localMeta != null && uiState.localMeta?.sha == uiState.remoteMeta?.sha ->
                                ConfigSyncStatus.IN_SYNC
                            uiState.remoteMeta != null && uiState.localMeta == null -> ConfigSyncStatus.REMOTE_ONLY
                            uiState.remoteMeta == null && uiState.localMeta != null -> ConfigSyncStatus.LOCAL_ONLY
                            else -> ConfigSyncStatus.MODIFIED_LOCAL
                        },
                    )
                }

                Text(
                    text = stringResource(
                        R.string.config_editor_meta,
                        uiState.localMeta?.lastModified.asReadableTime(),
                        uiState.remoteMeta?.updatedAt ?: stringResource(R.string.config_time_unknown),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                uiState.remoteError?.takeIf { it.isNotBlank() }?.let { error ->
                    Text(
                        text = stringResource(R.string.config_remote_error, error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                uiState.validationError?.takeIf { it.isNotBlank() }?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (uiState.isEditing) {
                    TextField(
                        value = uiState.draft,
                        onValueChange = viewModel::updateDraft,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        tonalElevation = 1.dp,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        if (selectedContent == null) {
                            WorkspaceEmptyState(
                                title = stringResource(R.string.config_editor_empty_title),
                                summary = stringResource(R.string.config_editor_empty_summary),
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(MaterialTheme.spacing.medium),
                            ) {
                                TextView(selectedContent.displayText)
                            }
                        }
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                ) {
                    if (uiState.isEditing) {
                        OutlinedButton(onClick = viewModel::cancelEdit) {
                            Text(stringResource(android.R.string.cancel))
                        }
                        FilledTonalButton(
                            onClick = viewModel::save,
                            enabled = uiState.hasDirectory && !uiState.isSaving,
                        ) {
                            Text(stringResource(android.R.string.ok))
                        }
                    } else {
                        FilledTonalButton(
                            onClick = viewModel::beginEdit,
                            enabled = uiState.hasDirectory && (uiState.hasLocal || uiState.hasRemote),
                        ) {
                            Text(stringResource(R.string.config_edit))
                        }
                        OutlinedButton(
                            onClick = viewModel::resetToRemote,
                            enabled = uiState.hasDirectory && uiState.hasRemote && !uiState.isSaving,
                        ) {
                            Text(stringResource(R.string.config_reset_remote))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigListEntry(
    item: ConfigListItem,
    onClick: () -> Unit,
) {
    WorkspaceListItem(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        leadingContent = {
            Icon(
                painter = painterResource(statusIcon(item.status)),
                contentDescription = null,
                tint = statusColor(item.status),
            )
        },
        trailingContent = { StatusBadge(item.status) },
    ) {
        Text(
            text = item.displayName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.path,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = buildString {
                append("本地: ")
                append(item.local?.lastModified.asReadableTime())
                append("  ·  远端: ")
                append(item.remote?.updatedAt ?: "未发现")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusBadge(status: ConfigSyncStatus) {
    Surface(
        color = statusColor(status).copy(alpha = 0.15f),
        contentColor = statusColor(status),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = statusLabel(status),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun statusLabel(status: ConfigSyncStatus): String {
    return when (status) {
        ConfigSyncStatus.IN_SYNC -> stringResource(R.string.config_status_in_sync)
        ConfigSyncStatus.REMOTE_ONLY -> stringResource(R.string.config_status_remote_only)
        ConfigSyncStatus.LOCAL_ONLY -> stringResource(R.string.config_status_local_only)
        ConfigSyncStatus.OUTDATED_LOCAL -> stringResource(R.string.config_status_outdated)
        ConfigSyncStatus.MODIFIED_LOCAL -> stringResource(R.string.config_status_modified)
        ConfigSyncStatus.INVALID_LOCAL -> stringResource(R.string.config_status_invalid)
    }
}

@Composable
private fun statusColor(status: ConfigSyncStatus): Color {
    return when (status) {
        ConfigSyncStatus.IN_SYNC -> MaterialTheme.colorScheme.primary
        ConfigSyncStatus.REMOTE_ONLY -> MaterialTheme.colorScheme.secondary
        ConfigSyncStatus.LOCAL_ONLY -> MaterialTheme.colorScheme.tertiary
        ConfigSyncStatus.OUTDATED_LOCAL -> MaterialTheme.colorScheme.secondary
        ConfigSyncStatus.MODIFIED_LOCAL -> MaterialTheme.colorScheme.tertiary
        ConfigSyncStatus.INVALID_LOCAL -> MaterialTheme.colorScheme.error
    }
}

private fun statusIcon(status: ConfigSyncStatus): Int {
    return when (status) {
        ConfigSyncStatus.IN_SYNC -> R.drawable.ic_check_circle_black_24dp
        ConfigSyncStatus.INVALID_LOCAL -> R.drawable.ic_error_outline_black_24dp
        else -> R.drawable.ic_tune_24dp
    }
}

private fun Long?.asReadableTime(): String {
    if (this == null || this <= 0L) return "未记录"
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(this))
}
