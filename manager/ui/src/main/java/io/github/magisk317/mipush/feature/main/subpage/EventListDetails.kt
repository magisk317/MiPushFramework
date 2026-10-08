package io.github.magisk317.mipush.feature.main.subpage

import io.github.magisk317.uikit.scroll.uiKitScrollEndHaptic
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import io.github.magisk317.uikit.surface.AppAlertDialog
import io.github.magisk317.uikit.surface.AppIcon
import io.github.magisk317.uikit.surface.AppIconButton
import io.github.magisk317.uikit.text.AppText
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import io.github.magisk317.mipush.manager.application.EventDebugJson
import io.github.magisk317.mipush.manager.application.MockReplayOutcome
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.uikit.surface.DialogAction
import io.github.magisk317.uikit.surface.DialogActionRow
import io.github.magisk317.uikit.surface.DialogActionStyle
import java.time.Instant
import java.time.ZoneId
import io.github.magisk317.mipush.main.viewmodel.EventListViewModel
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import io.github.magisk317.uikit.theme.AppColorRole
import io.github.magisk317.uikit.theme.appColor

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
                AppText(
                    stringResource(R.string.event_detail_developer_info),
                    role = AppTextRole.Title,
                    color = appColor(AppColorRole.OnSurface),
                    modifier = Modifier.weight(1f),
                )
                AppIconButton(onClick = { viewModel.startManagePermissions(clickedEvent.packageName) }) {
                    AppIcon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = stringResource(R.string.action_app_info),
                        tint = appColor(AppColorRole.OnSurfaceVariant),
                    )
                }
            }
        },
        text = {
            // Render the debug JSON as a collapsible, colour-coded tree; fall back to the
            // original plain-text block when the payload is not valid JSON.
            val parsedElement = remember(json) {
                runCatching { Json.parseToJsonElement(json) }.getOrNull()
            }
            SelectionContainer {
                if (parsedElement != null) {
                    JsonTreeView(
                        element = parsedElement,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = bodyMaxHeight)
                            .verticalScroll(verticalScroll)
                            .horizontalScroll(horizontalScroll)
                            .uiKitScrollEndHaptic(),
                    )
                } else {
                    AppText(
                        text = json,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = bodyMaxHeight)
                            .verticalScroll(verticalScroll)
                            .horizontalScroll(horizontalScroll)
                            .uiKitScrollEndHaptic(),
                        role = AppTextRole.BodySmall, fontFamily = FontFamily.Monospace,
                        color = appColor(AppColorRole.OnSurfaceVariant),
                        softWrap = false,
                    )
                }
            }
        },
        modifier = Modifier
            .padding(start = DialogScreenMargin, end = DialogScreenMargin, bottom = DialogBottomMargin)
            .heightIn(Dp.Unspecified, targetHeight)
    )
}

/** Depth whose containers are expanded when the dialog opens; deeper ones start collapsed. */
private const val JSON_TREE_DEFAULT_EXPANDED_DEPTH = 1
private val JsonTreeIndentStep = 12.dp

/** Safety margins keeping the dialog off the screen edges (on top of the kit's own insets). */
private val DialogScreenMargin = 8.dp
private val DialogBottomMargin = 20.dp

/**
 * Collapsible, colour-coded renderer for the event debug JSON. kotlinx.serialization already
 * hands us a typed tree, so "highlighting" is a per-node-type colour choice and collapsing is
 * a path-keyed set - no text parsing, no extra dependency. Copy keeps using the canonical
 * [json] string, so collapsing here never loses information.
 */
@Composable
private fun JsonTreeView(element: JsonElement, modifier: Modifier = Modifier) {
    var collapsedPaths by remember { mutableStateOf(setOf<String>()) }
    var expandedPaths by remember { mutableStateOf(setOf<String>()) }
    val onToggle: (String, Boolean) -> Unit = { path, currentlyExpanded ->
        if (currentlyExpanded) {
            collapsedPaths = collapsedPaths + path
            expandedPaths = expandedPaths - path
        } else {
            collapsedPaths = collapsedPaths - path
            expandedPaths = expandedPaths + path
        }
    }
    Column(modifier = modifier) {
        JsonNodeRow(
            key = null,
            element = element,
            path = "$",
            depth = 0,
            isLast = true,
            collapsed = collapsedPaths,
            expandedOverrides = expandedPaths,
            onToggle = onToggle,
        )
    }
}

@Composable
private fun JsonNodeRow(
    key: String?,
    element: JsonElement,
    path: String,
    depth: Int,
    isLast: Boolean,
    collapsed: Set<String>,
    expandedOverrides: Set<String>,
    onToggle: (String, Boolean) -> Unit,
) {
    when (element) {
        is JsonObject -> JsonContainerRow(key, element, path, depth, isLast, collapsed, expandedOverrides, onToggle)
        is JsonArray -> JsonContainerRow(key, element, path, depth, isLast, collapsed, expandedOverrides, onToggle)
        is JsonNull -> JsonLeafRow(key, "null", AppColorRole.Outline, depth, isLast)
        is JsonPrimitive ->
            if (element.isString) {
                JsonLeafRow(key, "\"${element.content}\"", AppColorRole.Tertiary, depth, isLast)
            } else {
                // numbers and booleans share the value colour; null is handled above
                JsonLeafRow(key, element.content, AppColorRole.Secondary, depth, isLast)
            }
    }
}

@Composable
private fun JsonContainerRow(
    key: String?,
    element: JsonElement,
    path: String,
    depth: Int,
    isLast: Boolean,
    collapsed: Set<String>,
    expandedOverrides: Set<String>,
    onToggle: (String, Boolean) -> Unit,
) {
    val entries: List<Pair<String?, JsonElement>> = when (element) {
        is JsonObject -> element.entries.map { (k, v) -> k to v }
        is JsonArray -> element.map { null to it }
        is JsonNull, is JsonPrimitive -> emptyList()
    }
    val openBracket = if (element is JsonObject) "{" else "["
    val closeBracket = if (element is JsonObject) "}" else "]"
    // Shallow containers start expanded and opt out via collapsedPaths; deeper ones start
    // collapsed and opt in via expandedOverrides.
    val expanded = if (depth <= JSON_TREE_DEFAULT_EXPANDED_DEPTH) {
        path !in collapsed
    } else {
        path in expandedOverrides
    }
    val suffix = if (isLast) "" else ","

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = entries.isNotEmpty()) { onToggle(path, expanded) }
            .padding(start = JsonTreeIndentStep * depth),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (entries.isNotEmpty()) {
            AppText(
                text = if (expanded) "▾ " else "▸ ",
                role = AppTextRole.BodySmall, fontFamily = FontFamily.Monospace,
                color = appColor(AppColorRole.OnSurfaceVariant),
            )
        }
        key?.let {
            AppText(
                text = "\"$it\": ",
                role = AppTextRole.BodySmall, fontFamily = FontFamily.Monospace,
                color = appColor(AppColorRole.Primary),
            )
        }
        AppText(
            text = openBracket,
            role = AppTextRole.BodySmall, fontFamily = FontFamily.Monospace,
            color = appColor(AppColorRole.OnSurfaceVariant),
        )
        if (!expanded) {
            AppText(
                text = "…$closeBracket$suffix  // ${entries.size}",
                role = AppTextRole.BodySmall, fontFamily = FontFamily.Monospace,
                color = appColor(AppColorRole.OnSurfaceVariant),
            )
        }
    }

    if (expanded) {
        Column(modifier = Modifier.padding(start = JsonTreeIndentStep * (depth + 1))) {
            entries.forEachIndexed { index, (childKey, child) ->
                JsonNodeRow(
                    key = childKey,
                    element = child,
                    path = when (element) {
                        is JsonObject -> "$path.${childKey}"
                        else -> "$path[$index]"
                    },
                    depth = depth + 1,
                    isLast = index == entries.lastIndex,
                    collapsed = collapsed,
                    expandedOverrides = expandedOverrides,
                    onToggle = onToggle,
                )
            }
        }
        Row(modifier = Modifier.padding(start = JsonTreeIndentStep * depth)) {
            AppText(
                text = closeBracket + suffix,
                role = AppTextRole.BodySmall, fontFamily = FontFamily.Monospace,
                color = appColor(AppColorRole.OnSurfaceVariant),
            )
        }
    }
}

@Composable
private fun JsonLeafRow(
    key: String?,
    value: String,
    valueColorRole: AppColorRole,
    depth: Int,
    isLast: Boolean,
) {
    Row(modifier = Modifier.padding(start = JsonTreeIndentStep * depth)) {
        key?.let {
            AppText(
                text = "\"$it\": ",
                role = AppTextRole.BodySmall, fontFamily = FontFamily.Monospace,
                color = appColor(AppColorRole.Primary),
            )
        }
        AppText(
            text = value + if (isLast) "" else ",",
            role = AppTextRole.BodySmall, fontFamily = FontFamily.Monospace,
            color = appColor(valueColorRole),
            softWrap = false,
        )
    }
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
