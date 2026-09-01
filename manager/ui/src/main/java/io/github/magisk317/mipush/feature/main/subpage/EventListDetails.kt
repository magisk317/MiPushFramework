package io.github.magisk317.mipush.feature.main.subpage

import io.github.magisk317.mipush.common.R as CommonR
import io.github.magisk317.mipush.feature.main.RecentEventListPage
import android.content.Intent
import android.net.Uri
import android.content.Context
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.WrapText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import io.github.magisk317.uikit.common.ElevatedSnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import co.touchlab.kermit.Logger
import io.github.magisk317.mipush.manager.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import io.github.magisk317.mipush.common.cache.ApplicationNameCache
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.manager.application.EventDebugJson
import io.github.magisk317.mipush.manager.application.ManagerEvent
import io.github.magisk317.mipush.manager.application.ManagerEventResult
import io.github.magisk317.mipush.manager.application.ManagerEventType
import io.github.magisk317.mipush.manager.application.MockReplayOutcome
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.uikit.surface.AppIconImage
import io.github.magisk317.mipush.feature.ui.component.RefreshableLazyColumn
import io.github.magisk317.uikit.surface.DialogAction
import io.github.magisk317.uikit.surface.DialogActionRow
import io.github.magisk317.uikit.surface.ScrollToTopFAB
import io.github.magisk317.uikit.surface.InfoPill
import io.github.magisk317.uikit.surface.OverlayHeaderScaffold
import io.github.magisk317.uikit.surface.WorkspaceTopBarSearchOverlay
import io.github.magisk317.uikit.surface.WorkspaceEmptyState
import io.github.magisk317.uikit.surface.WorkspaceListItem
import io.github.magisk317.uikit.scroll.ScrollChromeState
import io.github.magisk317.uikit.surface.AppBottomSheet
import io.github.magisk317.uikit.preference.StateSwitchItem
import io.github.magisk317.uikit.preference.Item as SettingsItem
import io.github.magisk317.uikit.preference.TextInputDialog
import io.github.magisk317.mipush.feature.ui.theme.spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import io.github.magisk317.mipush.main.viewmodel.EventListViewModel
import io.github.magisk317.mipush.main.viewmodel.SettingsViewModel
import io.github.magisk317.mipush.manager.remote.RuntimeReadUnavailableException
import org.koin.compose.viewmodel.koinViewModel

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
    var softWrap by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val replayFeedback = mapOf(
        MockReplayOutcome.BlockedByPermission to stringResource(R.string.mock_notification_blocked_by_permission),
        MockReplayOutcome.Dispatched to stringResource(R.string.mock_notification_dispatched),
        MockReplayOutcome.Posted to stringResource(R.string.mock_notification_posted),
        MockReplayOutcome.FailedChannelDisabled to stringResource(R.string.mock_notification_failed_channel_disabled),
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

    AlertDialog(
        onDismiss,
        {
            DialogActionRow(
                actions = buildList {
                    add(
                        DialogAction(
                            label = stringResource(android.R.string.copy),
                            onClick = { viewModel.copyToClipboard(json) }
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
                                        Utils.makeText(
                                            context,
                                            replayFeedback.getValue(outcome),
                                            0,
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
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { softWrap = !softWrap }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.WrapText,
                        contentDescription = stringResource(R.string.event_detail_soft_wrap_cd),
                        tint = if (softWrap) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(
                            if (softWrap) {
                                R.string.event_detail_soft_wrap_on
                            } else {
                                R.string.event_detail_soft_wrap_off
                            }
                        )
                    )
                }
                IconButton(onClick = { viewModel.startManagePermissions(clickedEvent.packageName) }) {
                    Icon(
                        painter = painterResource(id = CommonR.drawable.ic_info_outline_black_24dp),
                        contentDescription = stringResource(R.string.action_app_info),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            SelectionContainer {
                val textModifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = bodyMaxHeight)
                    .verticalScroll(verticalScroll)
                    .then(
                        if (softWrap) {
                            Modifier
                        } else {
                            Modifier.horizontalScroll(horizontalScroll)
                        }
                    )
                Text(
                    text = json,
                    modifier = textModifier,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    softWrap = softWrap,
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
