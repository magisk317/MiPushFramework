@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.magisk317.mipush.feature.main.subpage

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.mipush.common.manager.ManagerConnectionSnapshot
import io.github.magisk317.mipush.feature.ui.theme.spacing
import io.github.magisk317.mipush.main.viewmodel.ConnectionStatusViewModel
import io.github.magisk317.mipush.main.viewmodel.ReconnectFeedback
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.uikit.surface.DetailSectionCard
import io.github.magisk317.uikit.surface.OverlayHeaderScaffold
import io.github.magisk317.uikit.surface.chromeTopAppBarColors
import io.github.magisk317.uikit.surface.SectionColumn

@Composable
fun ConnectionStatusPage(
    viewModel: ConnectionStatusViewModel,
    onBack: () -> Unit,
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val currentTimeMs by viewModel.currentTimeMs.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isReconnecting by viewModel.isReconnecting.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        // Let the NavHost enter transition finish before triggering remote Binder work.
        delay(io.github.magisk317.uikit.surface.TAB_NAV_TRANSITION_MS.toLong())
        viewModel.startAutoRefresh()
    }
    LaunchedEffect(viewModel) {
        viewModel.reconnectFeedback.collect { feedback ->
            val message = when (feedback) {
                ReconnectFeedback.REQUESTED -> R.string.connection_status_reconnect_requested
                ReconnectFeedback.FAILED -> R.string.connection_status_reconnect_failed
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.stopAutoRefresh() }
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Page {
        OverlayHeaderScaffold(
            fallbackTopPadding = topInset + 64.dp,
            overlayModifier = Modifier
                .fillMaxWidth(),
            overlay = {
                TopAppBar(
                    title = { Text(stringResource(R.string.connection_status_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = viewModel::forceReconnect,
                            enabled = !isReconnecting,
                        ) {
                            if (isReconnecting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    Icons.Default.RestartAlt,
                                    contentDescription = stringResource(R.string.connection_status_force_reconnect),
                                )
                            }
                        }
                        IconButton(
                            onClick = viewModel::refresh,
                            enabled = !isRefreshing,
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.connection_status_refresh),
                                )
                            }
                        }
                    },
                    windowInsets = WindowInsets.statusBars,
                    colors = chromeTopAppBarColors(),
                )
            },
            content = { listPadding ->
                SectionColumn(
                    modifier = Modifier
                        .fillMaxSize()
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
                        XmppServerEditor { uiState, onEditHost ->
                            ServerSection(
                                data = data,
                                onEditHost = onEditHost.takeIf {
                                    uiState.isLoaded && !uiState.isSaving
                                },
                            )
                        }
                        TimingSection(data, currentTimeMs)
                        HeartbeatSection(data)
                        RecoverySection(data)
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
private fun ServerSection(
    data: ManagerConnectionSnapshot,
    onEditHost: (() -> Unit)?,
) {
    val na = stringResource(R.string.connection_status_not_available)
    DetailSectionCard(title = stringResource(R.string.connection_status_section_server)) {
        InfoRow(
            label = stringResource(R.string.connection_status_host),
            value = data.serverHost ?: na,
            summary = stringResource(R.string.connection_status_host_summary),
            onClick = onEditHost,
        )
        InfoRow(
            label = stringResource(R.string.connection_status_ip),
            value = data.serverIp ?: na,
            summary = stringResource(R.string.connection_status_ip_summary),
        )
    }
}

@Composable
private fun TimingSection(data: ManagerConnectionSnapshot, currentTimeMs: Long) {
    val na = stringResource(R.string.connection_status_not_available)
    DetailSectionCard(title = stringResource(R.string.connection_status_section_timing)) {
        InfoRow(
            label = stringResource(R.string.connection_status_connected_at),
            value = formatTimestamp(data.connectedAtMs, na),
            summary = stringResource(R.string.connection_status_connected_at_summary),
        )
        if (data.connectedAtMs > 0 && data.connectionState == "Connected") {
            val durationMs = currentTimeMs - data.connectedAtMs
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
        InfoRow(
            label = stringResource(R.string.connection_status_timer),
            value = data.timerClassName?.substringAfterLast('.') ?: stringResource(R.string.connection_status_unknown),
            summary = stringResource(R.string.connection_status_timer_summary),
        )
        InfoRow(
            label = stringResource(R.string.connection_status_exact_alarm),
            value = stringResource(
                if (data.exactAlarmAvailable) R.string.connection_status_available
                else R.string.connection_status_unavailable,
            ),
            summary = stringResource(R.string.connection_status_exact_alarm_summary),
        )
        InfoRow(
            label = stringResource(R.string.connection_status_alarm_mode),
            value = data.alarmMode ?: stringResource(R.string.connection_status_unknown),
            summary = data.alarmFallbackReason ?: stringResource(R.string.connection_status_alarm_mode_summary),
        )
        InfoRow(
            label = stringResource(R.string.connection_status_alarm_alive),
            value = stringResource(
                if (data.alarmAlive) R.string.connection_status_active
                else R.string.connection_status_inactive,
            ),
            summary = stringResource(R.string.connection_status_alarm_alive_summary),
        )
        InfoRow(
            label = stringResource(R.string.connection_status_next_timer),
            value = formatTimestamp(data.nextTimerAtMs, stringResource(R.string.connection_status_unknown)),
            summary = stringResource(R.string.connection_status_next_timer_summary),
        )
        InfoRow(
            label = stringResource(R.string.connection_status_last_timer_callback),
            value = formatTimestamp(data.lastTimerCallbackAtMs, stringResource(R.string.connection_status_unknown)),
            summary = stringResource(
                R.string.connection_status_timer_callback_delay,
                data.lastTimerCallbackDelayMs,
            ),
        )
        InfoRow(
            label = stringResource(R.string.connection_status_doze_whitelist),
            value = stringResource(
                if (data.deviceIdleWhitelistXmsf) R.string.connection_status_enabled
                else R.string.connection_status_disabled,
            ),
            summary = stringResource(
                R.string.connection_status_doze_whitelist_summary,
                data.checkedPackageName,
            ),
        )
        InfoRow(
            label = stringResource(R.string.connection_status_device_idle),
            value = stringResource(
                if (data.deviceIdle) R.string.connection_status_active
                else R.string.connection_status_inactive,
            ),
            summary = stringResource(R.string.connection_status_device_idle_summary),
        )
    }
}

@Composable
private fun RecoverySection(data: ManagerConnectionSnapshot) {
    val na = stringResource(R.string.connection_status_unknown)
    DetailSectionCard(title = stringResource(R.string.connection_status_section_recovery)) {
        InfoRow(
            label = stringResource(R.string.connection_status_last_disconnect_reason),
            value = data.lastDisconnectReason?.let(::formatDisconnectReason) ?: na,
            summary = stringResource(R.string.connection_status_last_disconnect_reason_summary),
        )
        InfoRow(
            label = stringResource(R.string.connection_status_last_ping),
            value = formatTimestamp(data.lastPingSentAtMs, na),
            summary = stringResource(R.string.connection_status_last_ping_summary),
        )
        InfoRow(
            label = stringResource(R.string.connection_status_last_read_alive),
            value = formatTimestamp(data.lastReadAliveAtMs, na),
            summary = stringResource(R.string.connection_status_last_read_alive_summary),
        )
        InfoRow(
            label = stringResource(R.string.connection_status_last_timeout),
            value = formatTimestamp(data.lastPingTimeoutAtMs, na),
            summary = stringResource(R.string.connection_status_last_timeout_summary),
        )
        InfoRow(
            label = stringResource(R.string.connection_status_reconnect_started),
            value = formatTimestamp(data.lastReconnectStartedAtMs, na),
            summary = stringResource(R.string.connection_status_reconnect_started_summary),
        )
        InfoRow(
            label = stringResource(R.string.connection_status_reconnect_latency),
            value = if (data.lastReconnectLatencyMs > 0) {
                formatDuration(data.lastReconnectLatencyMs)
            } else {
                na
            },
            summary = stringResource(R.string.connection_status_reconnect_latency_summary),
        )
        InfoRow(
            label = stringResource(R.string.connection_status_disconnect_to_reconnect),
            value = data.lastDisconnectToReconnectLatencyMs.takeIf { it > 0L }
                ?.let(::formatDuration) ?: na,
            summary = stringResource(R.string.connection_status_disconnect_to_reconnect_summary),
        )
        InfoRow(
            label = stringResource(R.string.connection_status_reconnect_to_connected),
            value = data.lastReconnectToConnectedLatencyMs.takeIf { it > 0L }
                ?.let(::formatDuration) ?: na,
            summary = stringResource(R.string.connection_status_reconnect_to_connected_summary),
        )
    }
}

private fun formatDisconnectReason(reason: Int): String = when (reason) {
    DISCONNECT_REASON_PING_TIMEOUT -> "PING_TIMEOUT ($DISCONNECT_REASON_PING_TIMEOUT)"
    DISCONNECT_REASON_READ_ERROR -> "READ_ERROR ($DISCONNECT_REASON_READ_ERROR)"
    else -> reason.toString()
}

private const val DISCONNECT_REASON_PING_TIMEOUT = 22
private const val DISCONNECT_REASON_READ_ERROR = 9

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
            label = stringResource(R.string.connection_status_framework_registered),
            value = if (data.frameworkRegistered) "✓" else "✗",
            summary = stringResource(R.string.connection_status_framework_registered_summary),
        )
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
    onClick: (() -> Unit)? = null,
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (onClick != null) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.settings_XMPP_server),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        modifier = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick),
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
