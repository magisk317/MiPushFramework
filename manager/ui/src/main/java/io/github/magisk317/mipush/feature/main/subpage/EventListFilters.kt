package io.github.magisk317.mipush.feature.main.subpage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import io.github.magisk317.uikit.surface.AppTextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.uikit.surface.WorkspaceFilterPill
import io.github.magisk317.mipush.manager.application.ManagerEvent
import io.github.magisk317.mipush.manager.application.ManagerEventResult
import io.github.magisk317.mipush.manager.application.ManagerEventType

@Composable
internal fun EventFilters(
    expanded: Boolean,
    selectedTypeFilters: Set<EventTypeFilter>,
    selectedStatusFilters: Set<EventStatusFilter>,
    onExpandedChange: () -> Unit,
    onTypeFiltersChange: (Set<EventTypeFilter>) -> Unit,
    onStatusFiltersChange: (Set<EventStatusFilter>) -> Unit,
    showToggleAction: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.recent_activity_filter_prefix),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showToggleAction) {
                AppTextButton(
                    text = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
                    onClick = onExpandedChange,
                )
            }
        }
        if (!expanded) return@Column
        Text(stringResource(R.string.recent_activity_filter_type_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WorkspaceFilterPill(
                label = stringResource(R.string.recent_activity_filter_type_all),
                selected = selectedTypeFilters.isEmpty(),
                onClick = { onTypeFiltersChange(emptySet()) },
            )
            EventTypeFilter.entries.forEach { filter ->
                WorkspaceFilterPill(
                    label = stringResource(filter.labelRes),
                    selected = filter in selectedTypeFilters,
                    onClick = { onTypeFiltersChange(selectedTypeFilters.toggle(filter)) },
                )
            }
        }
        Text(stringResource(R.string.recent_activity_filter_status_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WorkspaceFilterPill(
                label = stringResource(R.string.recent_activity_filter_status_all),
                selected = selectedStatusFilters.isEmpty(),
                onClick = { onStatusFiltersChange(emptySet()) },
            )
            EventStatusFilter.entries.forEach { filter ->
                WorkspaceFilterPill(
                    label = stringResource(filter.labelRes),
                    selected = filter in selectedStatusFilters,
                    onClick = { onStatusFiltersChange(selectedStatusFilters.toggle(filter)) },
                )
            }
        }
    }
}

internal enum class EventTypeFilter(val labelRes: Int) {
    Notification(R.string.recent_activity_filter_type_notification),
    PassThrough(R.string.recent_activity_filter_type_pass_through),
    Registration(R.string.recent_activity_filter_type_registration),
    Other(R.string.recent_activity_filter_type_other),
}

internal enum class EventStatusFilter(val labelRes: Int) {
    Normal(R.string.recent_activity_filter_status_normal),
    Disabled(R.string.recent_activity_filter_status_disabled),
    Denied(R.string.recent_activity_filter_status_denied),
}

internal fun EventInfoForDisplay.matchesFilters(
    selectedTypeFilters: Set<EventTypeFilter>,
    selectedStatusFilters: Set<EventStatusFilter>,
): Boolean {
    val matchesType = selectedTypeFilters.isEmpty() || selectedTypeFilters.any { filter ->
        when (filter) {
            EventTypeFilter.Notification -> event.canReplayNotification() && !isPassThroughMessage()
            EventTypeFilter.PassThrough -> event.type == ManagerEventType.SEND_MESSAGE && isPassThroughMessage()
            EventTypeFilter.Registration -> event.type in setOf(
                ManagerEventType.REGISTRATION,
                ManagerEventType.REGISTRATION_RESULT,
                ManagerEventType.UN_REGISTRATION,
            )
            EventTypeFilter.Other -> event.type !in setOf(
                ManagerEventType.SEND_MESSAGE,
                ManagerEventType.NOTIFICATION,
                ManagerEventType.REGISTRATION,
                ManagerEventType.REGISTRATION_RESULT,
                ManagerEventType.UN_REGISTRATION,
            )
        }
    }
    val matchesStatus = selectedStatusFilters.isEmpty() || selectedStatusFilters.any { filter ->
        when (filter) {
            EventStatusFilter.Normal -> !isDisabled() && event.result == ManagerEventResult.OK
            EventStatusFilter.Disabled -> isDisabled()
            EventStatusFilter.Denied -> event.result != ManagerEventResult.OK
        }
    }
    return matchesType && matchesStatus
}

internal fun <T> Set<T>.toggle(value: T): Set<T> = if (contains(value)) this - value else this + value

internal fun ManagerEvent.canReplayNotification(): Boolean =
    type == ManagerEventType.SEND_MESSAGE || type == ManagerEventType.NOTIFICATION

internal fun EventInfoForDisplay.isPassThroughMessage(): Boolean =
    channel == Utils.getApplication()?.getString(R.string.message_type_pass_through)

internal fun EventInfoForDisplay.isDisabled(): Boolean = configOptions.contains("disable")
