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


internal fun LazyListScope.configListHeader(
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

            WorkspaceSearchField(
                query = uiState.query,
                placeholder = stringResource(R.string.config_search_placeholder),
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
internal fun ConfigListEntry(
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
internal fun SettingLinkCard(
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
