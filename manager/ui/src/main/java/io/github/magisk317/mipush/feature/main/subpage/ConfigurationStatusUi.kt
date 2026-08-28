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
internal fun StatusBadge(status: ConfigSyncStatus) {
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

internal fun statusIcon(status: ConfigSyncStatus): Int {
    return when (status) {
        ConfigSyncStatus.IN_SYNC -> CommonR.drawable.ic_check_circle_black_24dp
        ConfigSyncStatus.INVALID_LOCAL -> CommonR.drawable.ic_error_outline_black_24dp
        else -> CommonR.drawable.ic_tune_24dp
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
