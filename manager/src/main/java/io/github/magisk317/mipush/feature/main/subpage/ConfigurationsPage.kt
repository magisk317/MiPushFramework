@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.magisk317.mipush.feature.main.subpage

import android.content.Intent
import android.widget.Toast
import dev.chrisbanes.haze.hazeEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.mipush.manager.R
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeSource
import io.github.magisk317.uikit.surface.WorkspaceEmptyState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import io.github.magisk317.mipush.feature.ui.component.OverlayHeaderScaffold
import io.github.magisk317.mipush.feature.ui.component.ScrollToTopFAB
import io.github.magisk317.mipush.feature.ui.component.SearchBar
import io.github.magisk317.mipush.feature.ui.component.WorkspaceListItem
import io.github.magisk317.mipush.feature.main.MainScrollChromeState
import io.github.magisk317.mipush.feature.main.ReportLazyListScrollToChrome
import io.github.magisk317.mipush.feature.ui.theme.spacing
import io.github.magisk317.mipush.main.viewmodel.ConfigEditorViewModel
import io.github.magisk317.mipush.main.viewmodel.ConfigManagerViewModel
import io.github.magisk317.mipush.utils.ConfigContentSource
import io.github.magisk317.mipush.utils.ConfigDefaults
import io.github.magisk317.mipush.utils.ConfigListItem
import io.github.magisk317.mipush.utils.ConfigRemoteSource
import io.github.magisk317.mipush.utils.ConfigSyncStatus
import org.koin.compose.viewmodel.koinViewModel


@Composable
fun Configurations(
    initialQuery: String = "",
    contentPadding: PaddingValues = PaddingValues(0.dp),
    refreshSignal: Int = 0,
    onOpenEditor: (String) -> Unit,
    viewModel: ConfigManagerViewModel = koinViewModel(),
    hazeState: HazeState? = null,
    hazeStyle: HazeBlurStyle? = null,
    scrollChromeState: MainScrollChromeState? = null,
) {
    Page {
        val context = androidx.compose.ui.platform.LocalContext.current
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        var editingRemoteSourceType by rememberSaveable { mutableStateOf<String?>(null) }
        var remoteRepositoryDraft by rememberSaveable { mutableStateOf("") }
        var remoteBranchDraft by rememberSaveable { mutableStateOf("") }
        var remoteAcceleratorDraft by rememberSaveable { mutableStateOf("") }
        
        var showImportDialog by rememberSaveable { mutableStateOf(false) }
        var pendingImportIsIcon by rememberSaveable { mutableStateOf(false) }
        var expandedCategory by rememberSaveable { mutableStateOf<String?>(null) }
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
                viewModel.importDocuments(uris, pendingImportIsIcon)
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
        LaunchedEffect(editingRemoteSourceType, uiState.remoteSource, uiState.iconRemoteSource) {
            if (editingRemoteSourceType == "config") {
                remoteRepositoryDraft = uiState.remoteSource.repository
                remoteBranchDraft = uiState.remoteSource.branch
                remoteAcceleratorDraft = uiState.remoteSource.accelerator
            } else if (editingRemoteSourceType == "icon") {
                remoteRepositoryDraft = uiState.iconRemoteSource.repository
                remoteBranchDraft = uiState.iconRemoteSource.branch
                remoteAcceleratorDraft = uiState.iconRemoteSource.accelerator
            }
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
        val listState = rememberLazyListState()
        val headerVisible = scrollChromeState?.isChromeVisible ?: true
        ReportLazyListScrollToChrome(listState, scrollChromeState)
        val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

        if (editingRemoteSourceType != null) {
            RemoteSourceDialog(
                repository = remoteRepositoryDraft,
                branch = remoteBranchDraft,
                accelerator = remoteAcceleratorDraft,
                defaultRepository = if (editingRemoteSourceType == "icon") ConfigDefaults.ICON_REMOTE_REPOSITORY else ConfigDefaults.REMOTE_REPOSITORY,
                defaultBranch = if (editingRemoteSourceType == "icon") ConfigDefaults.ICON_REMOTE_BRANCH else ConfigDefaults.REMOTE_BRANCH,
                onRepositoryChange = { remoteRepositoryDraft = it },
                onBranchChange = { remoteBranchDraft = it },
                onAcceleratorChange = { remoteAcceleratorDraft = it },
                onDismiss = { editingRemoteSourceType = null },
                onResetDefault = {
                    if (editingRemoteSourceType == "icon") {
                        remoteRepositoryDraft = ConfigDefaults.ICON_REMOTE_REPOSITORY
                        remoteBranchDraft = ConfigDefaults.ICON_REMOTE_BRANCH
                        remoteAcceleratorDraft = ConfigDefaults.ICON_REMOTE_ACCELERATOR
                    } else {
                        remoteRepositoryDraft = ConfigDefaults.REMOTE_REPOSITORY
                        remoteBranchDraft = ConfigDefaults.REMOTE_BRANCH
                        remoteAcceleratorDraft = ConfigDefaults.REMOTE_ACCELERATOR
                    }
                },
                onConfirm = {
                    if (editingRemoteSourceType == "config") {
                        viewModel.updateRemoteSource(
                            remoteRepositoryDraft,
                            remoteBranchDraft,
                            remoteAcceleratorDraft,
                        )
                    } else if (editingRemoteSourceType == "icon") {
                        viewModel.updateIconRemoteSource(
                            remoteRepositoryDraft,
                            remoteBranchDraft,
                            remoteAcceleratorDraft,
                        )
                    }
                    editingRemoteSourceType = null
                },
            )
        }

        if (showImportDialog) {
            AlertDialog(
                onDismissRequest = { showImportDialog = false },
                title = { Text("本地导入") },
                text = { Text("请选择要导入的文件类型：图标还是配置？") },
                confirmButton = {
                    FilledTonalButton(onClick = {
                        showImportDialog = false
                        pendingImportIsIcon = false
                        importLauncher.launch(arrayOf("application/json", "*/*"))
                    }) {
                        Text("导入配置")
                    }
                },
                dismissButton = {
                    FilledTonalButton(onClick = {
                        showImportDialog = false
                        pendingImportIsIcon = true
                        importLauncher.launch(arrayOf("application/json", "*/*"))
                    }) {
                        Text("导入图标")
                    }
                }
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
        OverlayHeaderScaffold(
            fallbackTopPadding = topInset + 64.dp,
            headerVisible = headerVisible,
            overlayModifier = Modifier
                .fillMaxWidth()
                .then(
                    if (hazeState != null && hazeStyle != null) {
                        Modifier.hazeEffect(hazeState) {
                            blurEffect { style = hazeStyle }
                            forceInvalidateOnPreDraw = true
                        }
                    } else {
                        Modifier
                    }
                ),
            overlay = {
                TopAppBar(
                    title = { Text(stringResource(R.string.main_configs)) },
                    windowInsets = WindowInsets.statusBars,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                )
            },
            content = { listPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier),
                    state = listState,
                    contentPadding = PaddingValues(
                        start = MaterialTheme.spacing.medium,
                        end = MaterialTheme.spacing.medium,
                        top = listPadding.calculateTopPadding() + 8.dp,
                        bottom = contentPadding.calculateBottomPadding() + MaterialTheme.spacing.large,
                    ),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                ) {
                    configListHeader(
                        uiState = uiState,
                        onClickRemoteSource = { editingRemoteSourceType = "config" },
                        onClickIconRemoteSource = { editingRemoteSourceType = "icon" },
                        onChooseDirectory = { openDirectoryLauncher.launch(null) },
                        onImportLocal = { showImportDialog = true },
                        onPullRemote = viewModel::pullRemote,
                        onReload = viewModel::reloadConfigurations,
                        onQueryChange = viewModel::setQuery,
                    )

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
                    } else if (expandedCategory == null) {
                        item {
                            WorkspaceListItem(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { expandedCategory = "config" },
                            ) {
                                Text("配置预览", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        item {
                            WorkspaceListItem(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { expandedCategory = "icon" },
                            ) {
                                Text("图标预览", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    } else {
                        item {
                            WorkspaceListItem(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { expandedCategory = null },
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_arrow_back_black_24dp),
                                        contentDescription = "返回"
                                    )
                                }
                            ) {
                                Text(
                                    if (expandedCategory == "config") "返回 / 配置预览" else "返回 / 图标预览",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                        
                        val categoryItems = if (expandedCategory == "config") {
                            filteredItems.filter { !it.path.startsWith("icon/") }
                        } else {
                            filteredItems.filter { it.path.startsWith("icon/") }
                        }
                        
                        if (categoryItems.isEmpty()) {
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
                            items(categoryItems, key = { it.path }) { item ->
                                ConfigListEntry(
                                    item = item,
                                    onClick = { onOpenEditor(item.path) },
                                )
                            }
                        }
                    }
                }
            },
        )
        ScrollToTopFAB(listState)
        }
    }
}

@Composable
fun ConfigurationEditor(
    path: String,
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: ConfigEditorViewModel = koinViewModel(),
) {
    Page {
        val context = androidx.compose.ui.platform.LocalContext.current
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val headerScrollState = rememberScrollState()

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
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(headerScrollState),
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
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
                            FilterChip(
                                selected = false,
                                onClick = {},
                                enabled = false,
                                label = { Text(statusLabel(currentEditorStatus(uiState))) },
                            )
                        }
                    }
                    item {
                        Text(
                            text = stringResource(
                                R.string.config_editor_meta,
                                uiState.localMeta?.lastModified.asReadableTime(),
                                uiState.remoteMeta?.updatedAt.asReadableRemoteTime()
                                    ?: stringResource(R.string.config_time_unknown),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    uiState.remoteError?.takeIf { it.isNotBlank() }?.let { error ->
                        item {
                            Text(
                                text = stringResource(R.string.config_remote_error, error),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    uiState.validationError?.takeIf { it.isNotBlank() }?.let { error ->
                        item {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    item {
                        if (uiState.isEditing) {
                            TextField(
                                value = uiState.draft,
                                onValueChange = viewModel::updateDraft,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 420.dp),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            )
                        } else {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                tonalElevation = 1.dp,
                                shape = MaterialTheme.shapes.large,
                            ) {
                                if (selectedContent == null) {
                                    WorkspaceEmptyState(
                                        title = stringResource(R.string.config_editor_empty_title),
                                        summary = stringResource(R.string.config_editor_empty_summary),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 360.dp),
                                    )
                                } else {
                                    CodePreview(
                                        text = selectedContent.displayText,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(MaterialTheme.spacing.medium),
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.heightIn(min = 4.dp))
                    }
                }
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
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

private fun LazyListScope.configListHeader(
    uiState: ConfigManagerViewModel.UiState,
    onClickRemoteSource: () -> Unit,
    onClickIconRemoteSource: () -> Unit,
    onChooseDirectory: () -> Unit,
    onImportLocal: () -> Unit,
    onPullRemote: () -> Unit,
    onReload: () -> Unit,
    onQueryChange: (String) -> Unit,
) {
    item {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            SettingLinkCard(
                title = stringResource(R.string.config_remote_source_title) + " (配置源)",
                value = if (uiState.remoteSource.accelerator.isBlank()) {
                    stringResource(R.string.config_remote_source_label, uiState.remoteSource.displayName)
                } else {
                    stringResource(
                        R.string.config_remote_source_with_accelerator_label,
                        uiState.remoteSource.displayName,
                        uiState.remoteSource.accelerator,
                    )
                },
                onClick = onClickRemoteSource,
            )
            SettingLinkCard(
                title = stringResource(R.string.config_remote_source_title) + " (图标源)",
                value = if (uiState.iconRemoteSource.accelerator.isBlank()) {
                    stringResource(R.string.config_remote_source_label, uiState.iconRemoteSource.displayName)
                } else {
                    stringResource(
                        R.string.config_remote_source_with_accelerator_label,
                        uiState.iconRemoteSource.displayName,
                        uiState.iconRemoteSource.accelerator,
                    )
                },
                onClick = onClickIconRemoteSource,
            )
            val directoryUri = uiState.directoryUri
            SettingLinkCard(
                title = stringResource(R.string.config_directory_title),
                value = if (directoryUri.isNullOrBlank()) {
                    stringResource(R.string.config_directory_not_selected)
                } else {
                    stringResource(R.string.config_directory_label, directoryUri)
                },
                onClick = onChooseDirectory,
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
                onValueChange = onQueryChange,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                FilledTonalButton(
                    onClick = onChooseDirectory,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.config_choose_directory), maxLines = 1)
                }
                OutlinedButton(
                    onClick = onImportLocal,
                    enabled = !uiState.directoryUri.isNullOrBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.config_import_local), maxLines = 1)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                OutlinedButton(
                    onClick = onPullRemote,
                    enabled = !uiState.directoryUri.isNullOrBlank() && !uiState.isSyncing,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.config_pull_remote), maxLines = 1)
                }
                OutlinedButton(
                    onClick = onReload,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.config_reload), maxLines = 1)
                }
            }

            if (uiState.isSyncing && uiState.syncTotal > 0) {
                LinearProgressIndicator(
                    progress = { uiState.syncCurrent.toFloat() / uiState.syncTotal.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(
                        R.string.config_sync_progress,
                        uiState.syncCurrent,
                        uiState.syncTotal,
                        uiState.syncPath ?: "",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            uiState.remoteError?.takeIf { it.isNotBlank() }?.let { error ->
                Text(
                    text = stringResource(R.string.config_remote_error, error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
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
                append("本地：")
                append(item.local?.lastModified.asReadableTime())
                append('\n')
                append("远端：")
                append(item.remote?.updatedAt.asReadableRemoteTime() ?: "未发现")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingLinkCard(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RemoteSourceDialog(
    repository: String,
    branch: String,
    accelerator: String,
    defaultRepository: String,
    defaultBranch: String,
    onRepositoryChange: (String) -> Unit,
    onBranchChange: (String) -> Unit,
    onAcceleratorChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onResetDefault: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.config_remote_source_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                TextField(
                    value = repository,
                    onValueChange = onRepositoryChange,
                    label = { Text(stringResource(R.string.config_remote_repository_label)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                    singleLine = true,
                )
                TextField(
                    value = branch,
                    onValueChange = onBranchChange,
                    label = { Text(stringResource(R.string.config_remote_branch_label)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                    singleLine = true,
                )
                TextField(
                    value = accelerator,
                    onValueChange = onAcceleratorChange,
                    label = { Text(stringResource(R.string.config_remote_accelerator_label)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                    singleLine = true,
                )
                Text(
                    text = stringResource(
                        R.string.config_remote_source_default_hint,
                        "${defaultRepository}@${defaultBranch}",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = onConfirm) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                OutlinedButton(onClick = onResetDefault) {
                    Text(stringResource(R.string.action_reset_default))
                }
                OutlinedButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        },
    )
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
private fun CodePreview(
    text: String,
    modifier: Modifier = Modifier,
) {
    SelectionContainer {
        Text(
            text = text,
            modifier = modifier,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun currentEditorStatus(uiState: ConfigEditorViewModel.UiState): ConfigSyncStatus {
    val localMeta = uiState.localMeta
    val remoteMeta = uiState.remoteMeta
    return when {
        localMeta?.isValid == false -> ConfigSyncStatus.INVALID_LOCAL
        remoteMeta != null && localMeta != null && localMeta.sha == remoteMeta.sha ->
            ConfigSyncStatus.IN_SYNC
        remoteMeta != null && localMeta == null -> ConfigSyncStatus.REMOTE_ONLY
        remoteMeta == null && localMeta != null -> ConfigSyncStatus.LOCAL_ONLY
        else -> ConfigSyncStatus.LOCAL_OVERRIDE
    }
}

@Composable
private fun statusLabel(status: ConfigSyncStatus): String {
    return when (status) {
        ConfigSyncStatus.IN_SYNC -> stringResource(R.string.config_status_in_sync)
        ConfigSyncStatus.REMOTE_ONLY -> stringResource(R.string.config_status_remote_only)
        ConfigSyncStatus.LOCAL_ONLY -> stringResource(R.string.config_status_local_only)
        ConfigSyncStatus.LOCAL_OVERRIDE -> stringResource(R.string.config_status_local_override)
        ConfigSyncStatus.INVALID_LOCAL -> stringResource(R.string.config_status_invalid)
    }
}

@Composable
private fun statusColor(status: ConfigSyncStatus): Color {
    return when (status) {
        ConfigSyncStatus.IN_SYNC -> MaterialTheme.colorScheme.primary
        ConfigSyncStatus.REMOTE_ONLY -> MaterialTheme.colorScheme.secondary
        ConfigSyncStatus.LOCAL_ONLY -> MaterialTheme.colorScheme.tertiary
        ConfigSyncStatus.LOCAL_OVERRIDE -> MaterialTheme.colorScheme.tertiary
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

private fun String?.asReadableRemoteTime(): String? {
    if (this.isNullOrBlank()) return null
    return runCatching {
        val localDateTime = OffsetDateTime.parse(this, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            .toLocalDateTime()
        localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    }.getOrDefault(this)
}
