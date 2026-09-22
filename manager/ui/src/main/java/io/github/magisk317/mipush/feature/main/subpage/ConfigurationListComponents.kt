@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.magisk317.mipush.feature.main.subpage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import io.github.magisk317.uikit.surface.AppPrimaryButton
import io.github.magisk317.uikit.surface.AppSecondaryButton
import io.github.magisk317.uikit.surface.AppLinearProgressIndicator
import io.github.magisk317.uikit.surface.AppSurface
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.uikit.surface.WorkspaceSearchField
import io.github.magisk317.uikit.surface.WorkspaceListItem
import io.github.magisk317.uikit.theme.spacing
import io.github.magisk317.mipush.main.viewmodel.ConfigManagerViewModel
import io.github.magisk317.mipush.core.configuration.ConfigListItem


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
                title = stringResource(R.string.config_remote_source_title) +
                    stringResource(R.string.config_remote_source_config_suffix),
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
                title = stringResource(R.string.config_remote_source_title) +
                    stringResource(R.string.config_remote_source_icon_suffix),
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
                AppPrimaryButton(
                    onClick = onChooseDirectory,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.config_choose_directory), maxLines = 1)
                }
                AppSecondaryButton(
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
                AppSecondaryButton(
                    onClick = onPullRemote,
                    enabled = !uiState.directoryUri.isNullOrBlank() && !uiState.isSyncing,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.config_pull_remote), maxLines = 1)
                }
                AppSecondaryButton(
                    onClick = onReload,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.config_reload), maxLines = 1)
                }
            }

            if (uiState.isSyncing && uiState.syncTotal > 0) {
                AppLinearProgressIndicator(
                    progress = uiState.syncCurrent.toFloat() / uiState.syncTotal.toFloat(),
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
                imageVector = statusIcon(item.status),
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
                append(
                    stringResource(
                        R.string.config_item_local,
                        item.local?.lastModified.asReadableTime(),
                    )
                )
                append('\n')
                append(
                    stringResource(
                        R.string.config_item_remote,
                        item.remote?.updatedAt.asReadableRemoteTime()
                            ?: stringResource(R.string.config_item_remote_missing),
                    )
                )
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
    AppSurface(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick),
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
