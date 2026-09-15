@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.magisk317.mipush.feature.main.subpage

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import io.github.magisk317.uikit.surface.AppBadge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.magisk317.mipush.manager.R
import java.util.Locale
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import io.github.magisk317.mipush.main.viewmodel.ConfigEditorViewModel
import io.github.magisk317.mipush.core.configuration.ConfigSyncStatus


@Composable
internal fun StatusBadge(status: ConfigSyncStatus) {
    AppBadge(
        text = statusLabel(status),
        containerColor = statusColor(status).copy(alpha = 0.15f),
        contentColor = statusColor(status),
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
internal fun CodePreview(
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
internal fun currentEditorStatus(uiState: ConfigEditorViewModel.UiState): ConfigSyncStatus {
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
internal fun statusLabel(status: ConfigSyncStatus): String {
    return when (status) {
        ConfigSyncStatus.IN_SYNC -> stringResource(R.string.config_status_in_sync)
        ConfigSyncStatus.REMOTE_ONLY -> stringResource(R.string.config_status_remote_only)
        ConfigSyncStatus.LOCAL_ONLY -> stringResource(R.string.config_status_local_only)
        ConfigSyncStatus.LOCAL_OVERRIDE -> stringResource(R.string.config_status_local_override)
        ConfigSyncStatus.INVALID_LOCAL -> stringResource(R.string.config_status_invalid)
    }
}

@Composable
internal fun statusColor(status: ConfigSyncStatus): Color {
    return when (status) {
        ConfigSyncStatus.IN_SYNC -> MaterialTheme.colorScheme.primary
        ConfigSyncStatus.REMOTE_ONLY -> MaterialTheme.colorScheme.secondary
        ConfigSyncStatus.LOCAL_ONLY -> MaterialTheme.colorScheme.tertiary
        ConfigSyncStatus.LOCAL_OVERRIDE -> MaterialTheme.colorScheme.tertiary
        ConfigSyncStatus.INVALID_LOCAL -> MaterialTheme.colorScheme.error
    }
}

internal fun statusIcon(status: ConfigSyncStatus): ImageVector {
    return when (status) {
        ConfigSyncStatus.IN_SYNC -> Icons.Default.CheckCircle
        ConfigSyncStatus.INVALID_LOCAL -> Icons.Default.ErrorOutline
        else -> Icons.Default.Tune
    }
}

internal fun Long?.asReadableTime(): String {
    if (this == null || this <= 0L) return "未记录"
    return Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault()))
}

internal fun String?.asReadableRemoteTime(): String? {
    if (this.isNullOrBlank()) return null
    return runCatching {
        val localDateTime = OffsetDateTime.parse(this, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            .toLocalDateTime()
        localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    }.getOrDefault(this)
}
