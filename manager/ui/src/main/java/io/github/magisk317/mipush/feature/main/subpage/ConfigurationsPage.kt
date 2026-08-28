@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.magisk317.mipush.feature.main.subpage

import io.github.magisk317.mipush.common.R as CommonR
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import io.github.magisk317.uikit.surface.WorkspaceEmptyState
import java.util.Locale
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import io.github.magisk317.uikit.surface.OverlayHeaderScaffold
import io.github.magisk317.uikit.surface.ScrollToTopFAB
import io.github.magisk317.uikit.surface.WorkspaceSearchField
import io.github.magisk317.uikit.surface.WorkspaceListItem
import io.github.magisk317.uikit.surface.chromeTopAppBarColors
import io.github.magisk317.uikit.scroll.ScrollChromeState
import io.github.magisk317.uikit.scroll.ReportLazyListScrollToChrome
import io.github.magisk317.mipush.feature.ui.theme.spacing
import io.github.magisk317.mipush.main.viewmodel.ConfigEditorViewModel
import io.github.magisk317.mipush.main.viewmodel.ConfigManagerViewModel
import io.github.magisk317.mipush.core.configuration.ConfigContentSource
import io.github.magisk317.mipush.utils.ConfigDefaults
import io.github.magisk317.mipush.core.configuration.ConfigListItem
import io.github.magisk317.mipush.utils.ConfigRemoteSource
import io.github.magisk317.mipush.core.configuration.ConfigSyncStatus
import org.koin.compose.viewmodel.koinViewModel


@Composable
fun Configurations(
    initialQuery: String = "",
    contentPadding: PaddingValues = PaddingValues(0.dp),
    refreshSignal: Int = 0,
    isActive: Boolean = true,
    onOpenEditor: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: ConfigManagerViewModel = koinViewModel(),
    scrollChromeState: ScrollChromeState? = null,
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

        LaunchedEffect(isActive, initialQuery) {
            if (!isActive) return@LaunchedEffect
            viewModel.setQuery(initialQuery)
        }
        var handledRefreshSignal by rememberSaveable { mutableIntStateOf(0) }
        LaunchedEffect(isActive, refreshSignal) {
            if (!isActive) return@LaunchedEffect
            if (refreshSignal > handledRefreshSignal) {
                viewModel.refresh()
                handledRefreshSignal = refreshSignal
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

        val filteredItems by remember(uiState.items, uiState.query) {
            derivedStateOf {
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
        }
        val listState = rememberLazyListState()
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
            headerOffsetY = scrollChromeState?.animatedHeaderOffsetY ?: 0f,
            onHeaderHeightChanged = { scrollChromeState?.headerHeightPx = it.toFloat() },
            overlayModifier = Modifier
                .fillMaxWidth(),
            overlay = {
                TopAppBar(
                    title = { Text(stringResource(R.string.main_configs)) },
                    navigationIcon = {
                        onBack?.let { back ->
                            IconButton(onClick = back) {
                                Icon(
                                    painter = painterResource(CommonR.drawable.ic_arrow_back_black_24dp),
                                    contentDescription = stringResource(android.R.string.cancel),
                                )
                            }
                        }
                    },
                    windowInsets = WindowInsets.statusBars,
                    colors = chromeTopAppBarColors(),
                )
            },
            content = { listPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize(),
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
                                        painter = painterResource(CommonR.drawable.ic_arrow_back_black_24dp),
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
        ScrollToTopFAB(listState, visible = scrollChromeState?.isChromeVisible != true, extraBottomPadding = 80.dp)
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
                            painter = painterResource(CommonR.drawable.ic_arrow_back_black_24dp),
                            contentDescription = stringResource(android.R.string.cancel),
                        )
                    }
                },
                colors = chromeTopAppBarColors(),
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
