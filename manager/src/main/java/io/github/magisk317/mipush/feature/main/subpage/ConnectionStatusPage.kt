@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.magisk317.mipush.feature.main.subpage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import io.github.magisk317.mipush.common.manager.ManagerConnectionSnapshot
import io.github.magisk317.mipush.feature.ui.theme.spacing
import io.github.magisk317.mipush.main.viewmodel.ConnectionStatusViewModel
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.uikit.surface.DetailSectionCard
import io.github.magisk317.uikit.surface.OverlayHeaderScaffold
import io.github.magisk317.uikit.surface.SectionColumn

@Composable
fun ConnectionStatusPage(
    viewModel: ConnectionStatusViewModel,
    onBack: () -> Unit,
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    @Suppress("UNUSED_VARIABLE")
    val tick by viewModel.tick.collectAsStateWithLifecycle() // forces recomposition for duration updates

    LaunchedEffect(Unit) {
        viewModel.startAutoRefresh()
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.stopAutoRefresh() }
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val hazeState = remember { HazeState() }

    Page {
        OverlayHeaderScaffold(
            fallbackTopPadding = topInset + 64.dp,
            overlayModifier = Modifier
                .fillMaxWidth()
                .hazeEffect(hazeState) {
                    blurEffect { }
                    forceInvalidateOnPreDraw = true
                },
            overlay = {
                TopAppBar(
                    title = { Text(stringResource(R.string.connection_status_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                        }
                    },
                    windowInsets = WindowInsets.statusBars,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                )
            },
            content = { listPadding ->
                SectionColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(state = hazeState)
                        .verticalScroll(rememberScrollState()),
                    contentPadding = PaddingValues(
                        start = MaterialTheme.spacing.medium,
                        top = listPadding.calculateTopPadding() + MaterialTheme.spacing.small,
                        end = MaterialTheme.spacing.medium,
                        bottom = MaterialTheme.spacing.large,
                    ),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                ) {
                    val data = snapshot
                    if (data == null) {
                        Text(
                            text = stringResource(R.string.connection_status_loading),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = MaterialTheme.spacing.large),
                        )
                    } else {
                        ConnectionStateHeader(data)
                        ServerSection(data)
                        TimingSection(data)
                        HeartbeatSection(data)
                        MessagesSection(data)
                        ChannelsSection(data)
                    }
                }
            },
        )
    }
}

@Composable
private fun ConnectionStateHeader(data: ManagerConnectionSnapshot) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        val indicatorColor = when (data.connectionState) {
            "Connected" -> Color(0xFF4CAF50)
            "Connecting" -> Color(0xFFFFC107)
            "Disconnected" -> Color(0xFFF44336)
            else -> Color.Gray
        }
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(indicatorColor)
        )
        Column {
            Text(
                text = data.connectionState,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.connection_status_session_count, data.connectionSessionCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ServerSection(data: ManagerConnectionSnapshot) {
    val na = stringResource(R.string.connection_status_not_available)
    DetailSectionCard(title = stringResource(R.string.connection_status_section_server)) {
        InfoRow(
            label = stringResource(R.string.connection_status_host),
            value = data.serverHost ?: na,
            summary = stringResource(R.string.connection_status_host_summary),
        )
        InfoRow(
            label = stringResource(R.string.connection_status_ip),
            value = data.serverIp ?: na,
            summary = stringResource(R.string.connection_status_ip_summary),
        )
    }
}

@Composable
private fun TimingSection(data: ManagerConnectionSnapshot) {
    val na = stringResource(R.string.connection_status_not_available)
    DetailSectionCard(title = stringResource(R.string.connection_status_section_timing)) {
        InfoRow(
            label = stringResource(R.string.connection_status_connected_at),
            value = formatTimestamp(data.connectedAtMs, na),
            summary = stringResource(R.string.connection_status_connected_at_summary),
        )
        if (data.connectedAtMs > 0 && data.connectionState == "Connected") {
            val durationMs = System.currentTimeMillis() - data.connectedAtMs
            InfoRow(
                label = stringResource(R.string.connection_status_session_duration),
                value = formatDuration(durationMs),
                summary = stringResource(R.string.connection_status_session_duration_summary),
            )
        }
        InfoRow(
            label = stringResource(R.string.connection_status_last_disconnected),
            value = formatTimestamp(data.lastDisconnectedAtMs, na),
            summary = stringResource(R.string.connection_status_last_disconnected_summary),
        )
    }
}

@Composable
private fun HeartbeatSection(data: ManagerConnectionSnapshot) {
    DetailSectionCard(title = stringResource(R.string.connection_status_section_heartbeat)) {
        InfoRow(
            label = stringResource(R.string.connection_status_keepalive_interval),
            value = "${data.keepAliveIntervalMs / 1000}s",
            summary = stringResource(R.string.connection_status_keepalive_interval_summary),
        )
        InfoRow(
            label = stringResource(R.string.connection_status_ping_interval),
            value = "${data.pingIntervalMs / 1000}s",
            summary = stringResource(R.string.connection_status_ping_interval_summary),
        )
    }
}

@Composable
private fun MessagesSection(data: ManagerConnectionSnapshot) {
    DetailSectionCard(title = stringResource(R.string.connection_status_section_messages)) {
        InfoRow(
            label = stringResource(R.string.connection_status_downstream),
            value = "${data.downstreamMessageCount}",
            summary = stringResource(R.string.connection_status_downstream_summary),
        )
        InfoRow(
            label = stringResource(R.string.connection_status_delivered),
            value = "${data.deliveredToAppCount}",
            summary = stringResource(R.string.connection_status_delivered_summary),
        )
        InfoRow(
            label = stringResource(R.string.connection_status_duplicates),
            value = "${data.duplicateMessageCount}",
            summary = stringResource(R.string.connection_status_duplicates_summary),
        )
        InfoRow(
            label = stringResource(R.string.connection_status_acks),
            value = "${data.ackMessageCount}",
            summary = stringResource(R.string.connection_status_acks_summary),
        )
    }
}

@Composable
private fun ChannelsSection(data: ManagerConnectionSnapshot) {
    DetailSectionCard(title = stringResource(R.string.connection_status_section_channels)) {
        InfoRow(
            label = stringResource(R.string.connection_status_registered_packages),
            value = "${data.registeredPackageCount}",
            summary = stringResource(R.string.connection_status_registered_packages_summary),
        )
        InfoRow(
            label = stringResource(R.string.connection_status_tracked_channels),
            value = "${data.trackedChannelCount}",
            summary = stringResource(R.string.connection_status_tracked_channels_summary),
        )
        InfoRow(
            label = stringResource(R.string.connection_status_bound_channels),
            value = "${data.boundChannelCount}",
            summary = stringResource(R.string.connection_status_bound_channels_summary),
        )
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    summary: String,
) {
    ListItem(
        supportingContent = {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

private fun formatTimestamp(ms: Long, fallback: String): String {
    if (ms <= 0L) return fallback
    val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(ms))
}

private fun formatDuration(ms: Long): String {
    if (ms < 0) return "—"
    val totalSec = ms / 1000
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}
