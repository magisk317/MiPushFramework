@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.magisk317.mipush.feature.main.subpage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import io.github.magisk317.uikit.surface.WorkspaceFilterPill
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import io.github.magisk317.mipush.main.viewmodel.EventListViewModel
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.uikit.surface.AppAlertDialog
import io.github.magisk317.uikit.surface.ConfirmActionDialog
import io.github.magisk317.uikit.surface.DialogAction
import io.github.magisk317.uikit.surface.DialogActionRow
import io.github.magisk317.uikit.surface.DialogActionStyle
import io.github.magisk317.uikit.surface.AppTextButton
import io.github.magisk317.uikit.surface.CalendarMonthGrid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dayKeyFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

private fun LocalDate.startMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** 待确认的清理动作:携带删除区间与预估条数,供二次确认弹窗展示与执行。 */
private sealed class PendingCleanup(val count: Int) {
    /** 仅清理某一天 [start, end)。 */
    class DayOnly(val day: LocalDate, val start: Long, val end: Long, count: Int) : PendingCleanup(count)
    /** 清理某个时间点之前(date < cutoff)。 */
    class Before(val day: LocalDate?, val cutoff: Long, count: Int) : PendingCleanup(count)
    /** 清理全部可清理记录。 */
    class All(val cutoff: Long, count: Int) : PendingCleanup(count)
}

/**
 * 日历式记录清理对话框。
 *
 * - 顶部快捷预设:清理 7/30 天前、清理全部;
 * - 月历:有记录的日期高亮并显示当天可清理条数,支持月份切换与当月汇总;
 * - 选中某天后可"仅清理当天"或"清理此日期及之前";
 * - 所有清理动作执行前都会二次确认;注册状态记录始终保留(由 DAO SQL 保证)。
 */
@Composable
@Suppress("TooGenericExceptionCaught")
fun EventCleanupCalendarDialog(
    viewModel: EventListViewModel,
    onDismiss: () -> Unit,
    onCleaned: (Int) -> Unit,
    onCleanupFailed: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    var dayCounts by remember { mutableStateOf<Map<LocalDate, Int>>(emptyMap()) }
    var visibleMonth by remember { mutableStateOf(YearMonth.from(today)) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    var pending by remember { mutableStateOf<PendingCleanup?>(null) }

    LaunchedEffectLoadCounts(viewModel) { counts -> dayCounts = counts }

    val totalCount = remember(dayCounts) { dayCounts.values.sum() }
    val monthCount = remember(dayCounts, visibleMonth) {
        dayCounts.entries.filter { YearMonth.from(it.key) == visibleMonth }.sumOf { it.value }
    }

    fun perform(action: PendingCleanup) {
        scope.launch {
            try {
                val deleted = when (action) {
                    is PendingCleanup.DayOnly -> viewModel.clearHistoryInRange(action.start, action.end)
                    is PendingCleanup.Before -> viewModel.clearHistoryBefore(action.cutoff)
                    is PendingCleanup.All -> viewModel.clearHistoryBefore(action.cutoff)
                }
                onCleaned(deleted)
                onDismiss()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Logger.withTag("EventCleanupCalendar").e(error) { "Failed to clean event history" }
                onCleanupFailed()
            }
        }
    }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.event_cleanup_title), color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.event_cleanup_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // 快捷预设
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WorkspaceFilterPill(
                        onClick = {
                            val cutoff = today.minusDays(7).startMillis()
                            val cnt = dayCounts.entries.filter { it.key < today.minusDays(7) }.sumOf { it.value }
                            pending = PendingCleanup.Before(null, cutoff, cnt)
                        },
                        label = stringResource(R.string.event_cleanup_preset_7),
                        selected = false,
                    )
                    WorkspaceFilterPill(
                        onClick = {
                            val cutoff = today.minusDays(30).startMillis()
                            val cnt = dayCounts.entries.filter { it.key < today.minusDays(30) }.sumOf { it.value }
                            pending = PendingCleanup.Before(null, cutoff, cnt)
                        },
                        label = stringResource(R.string.event_cleanup_preset_30),
                        selected = false,
                    )
                    WorkspaceFilterPill(
                        onClick = {
                            val cutoff = today.plusDays(1).startMillis()
                            pending = PendingCleanup.All(cutoff, totalCount)
                        },
                        label = stringResource(R.string.event_cleanup_preset_all),
                        selected = false,
                    )
                }

                // 月份切换 + 当月汇总
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIconButton(onClick = { visibleMonth = visibleMonth.minusMonths(1); selectedDay = null }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.event_cleanup_prev_month),
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${visibleMonth.year} / ${"%02d".format(visibleMonth.monthValue)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = pluralStringResource(
                                R.plurals.event_cleanup_month_summary,
                                monthCount,
                                monthCount,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AppIconButton(
                        onClick = { visibleMonth = visibleMonth.plusMonths(1); selectedDay = null },
                        enabled = visibleMonth < YearMonth.from(today),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.event_cleanup_next_month),
                        )
                    }
                }

                CalendarMonthGrid(
                    month = visibleMonth,
                    today = today,
                    dayCounts = dayCounts,
                    selectedDay = selectedDay,
                    onSelectDay = { selectedDay = if (selectedDay == it) null else it },
                )

                Text(
                    text = stringResource(R.string.event_cleanup_calendar_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // 选中某天后的两种清理动作
                selectedDay?.let { day ->
                    val dayCount = dayCounts[day] ?: 0
                    val start = day.startMillis()
                    val end = day.plusDays(1).startMillis()
                    val beforeCount = dayCounts.entries.filter { it.key <= day }.sumOf { it.value }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppTextButton(
                            text = stringResource(R.string.event_cleanup_day_only, dayKeyFormatter.format(day)),
                            onClick = {
                                pending = PendingCleanup.DayOnly(day, start, end, dayCount)
                            },
                        )
                        AppTextButton(
                            text = stringResource(R.string.event_cleanup_day_and_before, dayKeyFormatter.format(day)),
                            onClick = {
                                pending = PendingCleanup.Before(day, end, beforeCount)
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            DialogActionRow(
                actions = listOf(
                    DialogAction(
                        label = stringResource(android.R.string.cancel),
                        onClick = onDismiss,
                        style = DialogActionStyle.Secondary,
                    )
                )
            )
        },
    )

    // 二次确认
    pending?.let { action ->
        val message = when (action) {
            is PendingCleanup.DayOnly -> pluralStringResource(
                R.plurals.event_cleanup_confirm_day_only,
                action.count,
                dayKeyFormatter.format(action.day),
                action.count,
            )
            is PendingCleanup.Before -> pluralStringResource(
                R.plurals.event_cleanup_confirm_before,
                action.count,
                action.day?.let { dayKeyFormatter.format(it) } ?: dayKeyFormatter.format(
                    Instant.ofEpochMilli(action.cutoff).atZone(ZoneId.systemDefault()).toLocalDate().minusDays(1),
                ),
                action.count,
            )
            is PendingCleanup.All -> pluralStringResource(
                R.plurals.event_cleanup_confirm_all,
                action.count,
                action.count,
            )
        }
        ConfirmActionDialog(
            title = stringResource(R.string.event_cleanup_confirm_title),
            message = message,
            confirmText = stringResource(android.R.string.ok),
            onDismissRequest = { pending = null },
            onConfirm = {
                val a = action
                pending = null
                perform(a)
            },
        )
    }
}

/** 加载各日可清理条数一次;抽成单独 composable 便于 remember 键控。 */
@Suppress("TooGenericExceptionCaught")
@Composable
private fun LaunchedEffectLoadCounts(
    viewModel: EventListViewModel,
    onLoaded: (Map<LocalDate, Int>) -> Unit,
) {
    LaunchedEffect(Unit) {
        val counts: Map<String, Int> = try {
            viewModel.loadDayCounts()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Logger.withTag("EventCleanupCalendar").e(error) { "Failed to load event cleanup day counts" }
            emptyMap()
        }
        val map = HashMap<LocalDate, Int>(counts.size)
        for ((dayKey, count) in counts) {
            val date = runCatching { LocalDate.parse(dayKey, dayKeyFormatter) }.getOrNull()
            if (date != null) {
                map[date] = count
            }
        }
        onLoaded(map)
    }
}
