@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.magisk317.mipush.feature.main.subpage

import android.content.Intent
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import io.github.magisk317.uikit.surface.AppAlertDialog
import io.github.magisk317.uikit.surface.WorkspaceFilterPill
import io.github.magisk317.uikit.surface.AppPrimaryButton
import io.github.magisk317.uikit.surface.AppSecondaryButton
import io.github.magisk317.uikit.surface.AppSurface
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import io.github.magisk317.uikit.surface.AppTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.uikit.surface.WorkspaceEmptyState
import io.github.magisk317.uikit.surface.WorkspaceListItem
import io.github.magisk317.uikit.scroll.ScrollChromeState
import io.github.magisk317.uikit.scroll.ReportLazyListScrollToChrome
import io.github.magisk317.uikit.theme.spacing
import io.github.magisk317.mipush.main.viewmodel.ConfigEditorViewModel
import io.github.magisk317.mipush.main.viewmodel.ConfigManagerViewModel
import io.github.magisk317.mipush.core.configuration.ConfigContentSource
import io.github.magisk317.mipush.utils.ConfigDefaults
import io.github.magisk317.mipush.feature.ui.theme.Theme
import io.github.magisk317.uikit.theme.applyEdgeToEdge
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle
import org.koin.compose.viewmodel.koinViewModel

class ConfigurationsPage : ComponentActivity() {
    companion object {
        const val EXTRA_INITIAL_QUERY = "extra_initial_query"
        const val EXTRA_INITIAL_PATH = "extra_initial_path"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyEdgeToEdge(this)
        val initialQuery = intent.getStringExtra(EXTRA_INITIAL_QUERY).orEmpty()
        val initialPath = intent.getStringExtra(EXTRA_INITIAL_PATH)
        setContent {
            Theme {
                ConfigurationsPageContent(
                    initialQuery = initialQuery,
                    initialPath = initialPath,
                    onFinish = { finish() },
                )
            }
        }
    }
}

@Composable
private fun ConfigurationsPageContent(
    initialQuery: String,
    initialPath: String?,
    onFinish: () -> Unit,
) {
    var editingPath by rememberSaveable { mutableStateOf(initialPath) }

    androidx.activity.compose.BackHandler {
        if (editingPath != null) {
            editingPath = null
        } else {
            onFinish()
        }
    }

    val path = editingPath
    if (path == null) {
        Configurations(
            initialQuery = initialQuery,
            isActive = true,
            onOpenEditor = { editingPath = it },
            onBack = onFinish,
        )
    } else {
        ConfigurationEditor(
            path = path,
            onBack = { editingPath = null },
        )
    }
}

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
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var editingRemoteSourceType by rememberSaveable { mutableStateOf<String?>(null) }
    var remoteRepositoryDraft by rememberSaveable { mutableStateOf("") }
    var remoteBranchDraft by rememberSaveable { mutableStateOf("") }
    var remoteAcceleratorDraft by rememberSaveable { mutableStateOf("") }

    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    var pendingImportIsIcon by rememberSaveable { mutableStateOf(false) }
    var configPreviewExpanded by rememberSaveable { mutableStateOf(true) }
    var iconPreviewExpanded by rememberSaveable { mutableStateOf(false) }
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

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(Unit) {
        if (initialQuery.isEmpty()) {
            focusManager.clearFocus()
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
    val scrollScope = rememberCoroutineScope()
    ReportLazyListScrollToChrome(listState, scrollChromeState)

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
        AppAlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text(stringResource(R.string.config_import_dialog_title)) },
            text = { Text(stringResource(R.string.config_import_dialog_message)) },
            confirmButton = {
                AppPrimaryButton(
                    text = stringResource(R.string.config_import_configuration),
                    onClick = {
                        showImportDialog = false
                        pendingImportIsIcon = false
                        importLauncher.launch(arrayOf("application/json", "*/*"))
                    },
                )
            },
            dismissButton = {
                AppSecondaryButton(
                    text = stringResource(R.string.config_import_icons),
                    onClick = {
                        showImportDialog = false
                        pendingImportIsIcon = true
                        importLauncher.launch(arrayOf("application/json", "*/*"))
                    },
                )
            }
        )
    }

    val body: @Composable (PaddingValues, Modifier) -> Unit = { listPadding, scrollModifier ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(scrollModifier),
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
            } else {
                val categoryItems = filteredItems.filter { !it.path.startsWith("icon/") }
                val iconItems = filteredItems.filter { it.path.startsWith("icon/") }

                item {
                    SettingsSectionCard(
                        title = stringResource(R.string.config_preview_title),
                        summary = stringResource(R.string.config_preview_summary),
                        expanded = configPreviewExpanded,
                        onExpandedChange = {
                            configPreviewExpanded = !configPreviewExpanded
                        },
                    ) {
                        if (categoryItems.isEmpty()) {
                            WorkspaceEmptyState(
                                title = stringResource(R.string.config_empty_title),
                                summary = stringResource(R.string.config_empty_summary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 180.dp),
                            )
                        } else {
                            categoryItems.forEach { configItem ->
                                ConfigListEntry(
                                    item = configItem,
                                    onClick = { onOpenEditor(configItem.path) },
                                )
                            }
                        }
                    }
                }

                item {
                    SettingsSectionCard(
                        title = stringResource(R.string.icon_preview_title),
                        summary = stringResource(R.string.icon_preview_summary),
                        expanded = iconPreviewExpanded,
                        onExpandedChange = {
                            iconPreviewExpanded = !iconPreviewExpanded
                        },
                    ) {
                        if (iconItems.isEmpty()) {
                            WorkspaceEmptyState(
                                title = stringResource(R.string.config_empty_title),
                                summary = stringResource(R.string.config_empty_summary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 180.dp),
                            )
                        } else {
                            iconItems.forEach { configItem ->
                                ConfigListEntry(
                                    item = configItem,
                                    onClick = { onOpenEditor(configItem.path) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    when (currentUiKitStyle()) {
        UiKitStyle.Miuix -> ConfigurationsMiuix(
            onBack = onBack,
            contentPadding = contentPadding,
            scrollChromeState = scrollChromeState,
            listState = listState,
            scrollScope = scrollScope,
            body = body,
        )

        UiKitStyle.Expressive -> ConfigurationsExpressive(
            onBack = onBack,
            contentPadding = contentPadding,
            scrollChromeState = scrollChromeState,
            listState = listState,
            scrollScope = scrollScope,
            body = body,
        )
    }
}

@Composable
fun ConfigurationEditor(
    path: String,
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: ConfigEditorViewModel = koinViewModel(),
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val headerScrollState = rememberScrollState()
    val editorListState = rememberLazyListState()

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

    val body: @Composable (PaddingValues, Modifier) -> Unit = { listPadding, scrollModifier ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = listPadding.calculateTopPadding())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(androidx.compose.foundation.layout.WindowInsetsSides.Horizontal))
                .padding(bottom = contentPadding.calculateBottomPadding()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = MaterialTheme.spacing.medium),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .then(scrollModifier),
                    state = editorListState,
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(headerScrollState),
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                        ) {
                            WorkspaceFilterPill(
                                selected = uiState.selectedSource == ConfigContentSource.LOCAL,
                                onClick = { viewModel.selectSource(ConfigContentSource.LOCAL) },
                                enabled = uiState.hasLocal,
                                label = stringResource(R.string.config_source_local),
                            )
                            WorkspaceFilterPill(
                                selected = uiState.selectedSource == ConfigContentSource.REMOTE,
                                onClick = { viewModel.selectSource(ConfigContentSource.REMOTE) },
                                enabled = uiState.hasRemote,
                                label = stringResource(R.string.config_source_remote),
                            )
                            WorkspaceFilterPill(
                                selected = false,
                                onClick = {},
                                enabled = false,
                                label = statusLabel(currentEditorStatus(uiState)),
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
                            // State-based field: re-seed when editing opens, stream edits to the VM.
                            val draftState = remember { TextFieldState(uiState.draft) }
                            LaunchedEffect(uiState.isEditing) {
                                if (uiState.isEditing) draftState.setTextAndPlaceCursorAtEnd(uiState.draft)
                            }
                            LaunchedEffect(draftState) {
                                snapshotFlow { draftState.text }.collect { viewModel.updateDraft(it.toString()) }
                            }
                            AppTextField(
                                state = draftState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 420.dp),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            )
                        } else {
                            AppSurface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                tonalElevation = 0.dp,
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
                        AppSecondaryButton(onClick = viewModel::cancelEdit) {
                            Text(stringResource(android.R.string.cancel))
                        }
                        AppPrimaryButton(
                            onClick = viewModel::save,
                            enabled = uiState.hasDirectory && !uiState.isSaving,
                        ) {
                            Text(stringResource(android.R.string.ok))
                        }
                    } else {
                        AppPrimaryButton(
                            onClick = viewModel::beginEdit,
                            enabled = uiState.hasDirectory && (uiState.hasLocal || uiState.hasRemote),
                        ) {
                            Text(stringResource(R.string.config_edit))
                        }
                        AppSecondaryButton(
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

    when (currentUiKitStyle()) {
        UiKitStyle.Miuix -> ConfigurationEditorMiuix(
            path = path,
            onBack = onBack,
            body = body,
        )

        UiKitStyle.Expressive -> ConfigurationEditorExpressive(
            path = path,
            onBack = onBack,
            body = body,
        )
    }
}
