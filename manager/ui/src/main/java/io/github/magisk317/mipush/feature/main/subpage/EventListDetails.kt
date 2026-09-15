package io.github.magisk317.mipush.feature.main.subpage

import io.github.magisk317.uikit.scroll.uiKitScrollEndHaptic
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import io.github.magisk317.uikit.surface.AppAlertDialog
import androidx.compose.material3.Icon
import io.github.magisk317.uikit.surface.AppIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import io.github.magisk317.mipush.manager.R
import kotlinx.coroutines.launch
import io.github.magisk317.mipush.manager.application.EventDebugJson
import io.github.magisk317.mipush.manager.application.MockReplayOutcome
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.uikit.surface.DialogAction
import io.github.magisk317.uikit.surface.DialogActionRow
import io.github.magisk317.uikit.surface.DialogActionStyle
import java.time.Instant
import java.time.ZoneId
import io.github.magisk317.mipush.main.viewmodel.EventListViewModel

@Composable
internal fun EventDetailsDialog(
    clickedEvent: EventInfoForDisplay,
    content: String? = null,
    viewModel: EventListViewModel,
    onDismiss: () -> Unit
) {
    var json by remember(clickedEvent.id, content) {
        mutableStateOf(
            content ?: buildEventDebugInfo(clickedEvent)
        )
    }
    LaunchedEffect(clickedEvent.id, content) {
        if (content == null) {
            json = viewModel.getJson(clickedEvent.event) ?: buildEventDebugInfo(clickedEvent)
        }
    }
    val context = LocalContext.current
    val replayFeedback = mapOf(
        MockReplayOutcome.BlockedByPermission to stringResource(R.string.mock_notification_blocked_by_permission),
        MockReplayOutcome.Dispatched to stringResource(R.string.mock_notification_dispatched),
        MockReplayOutcome.Posted to stringResource(R.string.mock_notification_posted),
        MockReplayOutcome.FailedChannelDisabled to stringResource(R.string.mock_notification_failed_channel_disabled),
        MockReplayOutcome.FailedEventNotFound to stringResource(R.string.mock_notification_failed_event_not_found),
        MockReplayOutcome.FailedPayloadMissing to stringResource(R.string.mock_notification_failed_payload_missing),
        MockReplayOutcome.FailedServiceNotReady to stringResource(R.string.mock_notification_failed_service_not_ready),
        MockReplayOutcome.FailedAppNotInstalled to stringResource(R.string.mock_notification_failed_app_not_installed),
        MockReplayOutcome.FailedMissingRegSec to stringResource(R.string.mock_notification_missing_regsec),
        MockReplayOutcome.FailedNoReceiver to stringResource(R.string.mock_notification_no_receiver),
        MockReplayOutcome.Failed to stringResource(R.string.mock_notification_failed),
    )
    val replayScope = rememberCoroutineScope()
    val canReplayNotification = clickedEvent.event.canReplayNotification()
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    val screenHeight = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp()
    }
    val targetHeight = screenHeight * 0.9f
    val bodyMaxHeight = screenHeight * 0.62f

    AppAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            DialogActionRow(
                actions = buildList {
                    add(
                        DialogAction(
                            label = stringResource(android.R.string.copy),
                            onClick = { viewModel.copyToClipboard(json) },
                            style = if (canReplayNotification) DialogActionStyle.Secondary else DialogActionStyle.Primary,
                        )
                    )
                    if (canReplayNotification) {
                        add(
                            DialogAction(
                                label = stringResource(R.string.action_notify),
                                onClick = {
                                    replayScope.launch {
                                        val outcome = viewModel.mockMessage(clickedEvent.event)
                                        Logger.withTag("EventListPage").d {
                                            "Replay event id=${clickedEvent.id} pkg=${clickedEvent.packageName} outcome=$outcome"
                                        }
                                        val isSuccess = outcome == MockReplayOutcome.Posted || outcome == MockReplayOutcome.Dispatched
                                        Utils.makeText(
                                            context,
                                            replayFeedback.getValue(outcome),
                                            if (isSuccess) 0 else 1,
                                        )
                                    }
                                }
                            )
                        )
                    }
                }
            )
        },
        title = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.event_detail_developer_info),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                AppIconButton(onClick = { viewModel.startManagePermissions(clickedEvent.packageName) }) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = stringResource(R.string.action_app_info),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            SelectionContainer {
                Text(
                    text = json,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = bodyMaxHeight)
                        .verticalScroll(verticalScroll)
                        .horizontalScroll(horizontalScroll)
                        .uiKitScrollEndHaptic(),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    softWrap = false,
                )
            }
        },
        modifier = Modifier.heightIn(Dp.Unspecified, targetHeight)
    )
}

internal fun MockReplayOutcome.feedbackStringRes(): Int = when (this) {
    MockReplayOutcome.BlockedByPermission -> R.string.mock_notification_blocked_by_permission
    MockReplayOutcome.Dispatched -> R.string.mock_notification_dispatched
    MockReplayOutcome.Posted -> R.string.mock_notification_posted
    MockReplayOutcome.FailedChannelDisabled -> R.string.mock_notification_failed_channel_disabled
    MockReplayOutcome.FailedEventNotFound -> R.string.mock_notification_failed_event_not_found
    MockReplayOutcome.FailedPayloadMissing -> R.string.mock_notification_failed_payload_missing
    MockReplayOutcome.FailedServiceNotReady -> R.string.mock_notification_failed_service_not_ready
    MockReplayOutcome.FailedAppNotInstalled -> R.string.mock_notification_failed_app_not_installed
    MockReplayOutcome.FailedMissingRegSec -> R.string.mock_notification_missing_regsec
    MockReplayOutcome.FailedNoReceiver -> R.string.mock_notification_no_receiver
    MockReplayOutcome.Failed -> R.string.mock_notification_failed
}

private fun buildEventDebugInfo(event: EventInfoForDisplay): String {
    return runCatching { EventDebugJson.format(event.event) }.getOrElse {
        buildString {
            appendLine("packageName=${event.packageName}")
            appendLine("appName=${event.appName ?: "<unknown>"}")
            appendLine("title=${event.title}")
            appendLine("channel=${event.channel.ifBlank { "<none>" }}")
            appendLine("configOptions=${event.configOptions.joinToString(",").ifBlank { "<none>" }}")
            appendLine("receiveDate=${Instant.ofEpochMilli(event.receiveDate.time).atZone(ZoneId.systemDefault()).format(receiveDateTimeFormatter)}")
            appendLine("type=${event.event.type}")
            appendLine("result=${event.event.result}")
            appendLine("info=${event.event.info ?: "<none>"}")
            appendLine("payloadBytes=${event.event.payload?.size ?: 0}")
            appendLine("content=${event.content}")
        }
    }
}
