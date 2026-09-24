@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.magisk317.mipush.feature.main.subpage

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.mipush.main.viewmodel.IconLibraryEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import io.github.magisk317.uikit.surface.WorkspaceSearchField
import io.github.magisk317.uikit.surface.WorkspaceListItem
import io.github.magisk317.uikit.theme.spacing
import io.github.magisk317.mipush.main.viewmodel.ConfigManagerViewModel
import io.github.magisk317.mipush.core.configuration.ConfigListItem
import io.github.magisk317.mipush.utils.ConfigDefaults


internal fun LazyListScope.configListHeader(
    uiState: ConfigManagerViewModel.UiState,
    onClickRemoteSource: () -> Unit,
    onUpdateIcons: () -> Unit,
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
                title = stringResource(R.string.config_icon_source_title),
                value = if (uiState.isUpdatingIcons) {
                    stringResource(R.string.config_icons_updating)
                } else {
                    stringResource(
                        R.string.config_icon_source_value,
                        ConfigDefaults.ICON_REMOTE_REPOSITORY,
                    )
                },
                onClick = onUpdateIcons,
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
                    text = stringResource(R.string.config_choose_directory),
                    onClick = onChooseDirectory,
                    modifier = Modifier.weight(1f),
                )
                AppSecondaryButton(
                    text = stringResource(R.string.config_import_local),
                    onClick = onImportLocal,
                    enabled = !uiState.directoryUri.isNullOrBlank(),
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                AppSecondaryButton(
                    text = stringResource(R.string.config_pull_remote),
                    onClick = onPullRemote,
                    enabled = !uiState.directoryUri.isNullOrBlank() && !uiState.isSyncing,
                    modifier = Modifier.weight(1f),
                )
                AppSecondaryButton(
                    text = stringResource(R.string.config_reload),
                    onClick = onReload,
                    modifier = Modifier.weight(1f),
                )
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
    val (primaryTitle, secondarySubtitle) = remember(item.displayName, item.path) {
        val rawName = item.displayName.removeSuffix(".json")
        val delimiterIndex = rawName.indexOf('_')
        if (delimiterIndex != -1) {
            val prefix = rawName.substring(0, delimiterIndex)
            val suffix = rawName.substring(delimiterIndex + 1)
            // If prefix starts with digit (e.g., 0_基础配置_工具 -> prefix: 0, suffix: 基础配置_工具)
            if (prefix.all { it.isDigit() }) {
                val nextDelimiter = suffix.indexOf('_')
                if (nextDelimiter != -1) {
                    val category = suffix.substring(0, nextDelimiter)
                    val detail = suffix.substring(nextDelimiter + 1)
                    detail to category
                } else {
                    suffix to prefix
                }
            } else {
                suffix to prefix
            }
        } else {
            rawName to item.path
        }
    }

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
            text = primaryTitle,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = secondarySubtitle,
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

/**
 * One ANIP icon library entry, rendered with the same [WorkspaceListItem] shell used by the
 * configuration list: icon on the left, label / package name / update time on the right.
 */
@Composable
internal fun IconLibraryEntryRow(
    entry: IconLibraryEntry,
    bitmap: ImageBitmap?,
    updatedAtLabel: String,
    modifier: Modifier = Modifier,
) {
    WorkspaceListItem(
        modifier = modifier.fillMaxWidth(),
        onClick = {},
        leadingContent = {
            Box(
                modifier = Modifier.size(ICON_ITEM_SIZE),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = entry.label,
                        modifier = Modifier.size(ICON_ITEM_SIZE),
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        },
        trailingContent = {
            if (entry.overlay) {
                Text(
                    text = stringResource(R.string.icon_item_overlay),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) {
        Text(
            text = entry.label.ifBlank { entry.packageName },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = entry.packageName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = updatedAtLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun formatIconUpdateTime(epochMillis: Long?): String {
    if (epochMillis == null || epochMillis <= 0L) return "--"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMillis))
}

/**
 * Sticky header shown above each icon-library category (app / game / system) when the list is
 * flattened into the page-level LazyColumn.
 */
@Composable
internal fun CategoryHeader(
    label: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(
                start = MaterialTheme.spacing.medium,
                end = MaterialTheme.spacing.medium,
                top = MaterialTheme.spacing.small,
                bottom = MaterialTheme.spacing.small,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 16.dp)
                .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = if (expanded) {
                Icons.Filled.KeyboardArrowUp
            } else {
                Icons.Filled.KeyboardArrowDown
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Maps the wire category key to a localized label; falls back to the raw key. */
internal fun iconCategoryLabel(category: String): String = when (category) {
    "app" -> "应用"
    "game" -> "游戏"
    "system" -> "系统"
    else -> category
}

private val ICON_ITEM_SIZE = 40.dp
