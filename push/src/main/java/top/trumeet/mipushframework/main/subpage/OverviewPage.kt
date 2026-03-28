package top.trumeet.mipushframework.main.subpage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xiaomi.xmsf.BuildConfig
import com.xiaomi.xmsf.R
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.trumeet.mipushframework.MainActivityOperation
import top.trumeet.mipushframework.component.DetailSectionCard
import top.trumeet.mipushframework.component.FeatureEntryCard
import top.trumeet.mipushframework.component.InfoPill
import top.trumeet.mipushframework.component.OverlayHeaderPanel
import top.trumeet.mipushframework.component.OverlayHeaderScaffold
import top.trumeet.mipushframework.component.SectionColumn
import top.trumeet.ui.theme.spacing
import kotlin.math.max

@Composable
fun Overview(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onShowAboutDialog: (String) -> Unit = {},
    hazeState: HazeState? = null,
    hazeStyle: HazeStyle? = null,
) {
    Page {
        OverviewScreen(
            contentPadding = contentPadding,
            onShowAboutDialog = onShowAboutDialog,
            hazeState = hazeState,
            hazeStyle = hazeStyle,
        )
    }
}

@Composable
private fun OverviewScreen(
    contentPadding: PaddingValues,
    onShowAboutDialog: (String) -> Unit,
    hazeState: HazeState?,
    hazeStyle: HazeStyle?,
) {
    val context = LocalContext.current
    val mainActivityOperation = MainActivityOperation(context)
    val appStats by produceState(
        initialValue = ApplicationStats(),
    ) {
        value = withContext(Dispatchers.IO) {
            loadApplicationStats(context)
        }
    }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topOverlayHeight = topInset + 110.dp

    OverlayHeaderScaffold(
        fallbackTopPadding = topOverlayHeight,
        bottomPadding = contentPadding.calculateBottomPadding() + bottomInset + 24.dp,
        overlayModifier = Modifier
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
            .then(
                if (hazeState != null && hazeStyle != null) {
                    Modifier.hazeEffect(hazeState, hazeStyle) {
                        forceInvalidateOnPreDraw = true
                    }
                } else {
                    Modifier
                }
            ),
        content = { padding ->
            SectionColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                contentPadding = PaddingValues(
                    start = MaterialTheme.spacing.medium,
                    top = padding.calculateTopPadding() + 8.dp,
                    end = MaterialTheme.spacing.medium,
                    bottom = padding.calculateBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                DetailSectionCard(
                    title = stringResource(R.string.overview_app_stats_title),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AppStatsDonutSection(appStats = appStats)
                }

                DetailSectionCard(
                    title = stringResource(R.string.overview_project_title),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(
                            start = MaterialTheme.spacing.large,
                            end = MaterialTheme.spacing.large,
                            bottom = MaterialTheme.spacing.large,
                        ),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                    ) {
                        FeatureEntryCard(
                            title = stringResource(R.string.overview_project_github_title),
                            summary = stringResource(R.string.overview_project_github_summary),
                            onClick = { mainActivityOperation.gotoGitHubProjectPage() },
                        )
                        FeatureEntryCard(
                            title = stringResource(R.string.helplib_action_telegram_group),
                            summary = stringResource(R.string.help_page_contact_telegram_summary),
                            onClick = { mainActivityOperation.gotoTelegramGroup() },
                        )
                        FeatureEntryCard(
                            title = stringResource(R.string.helplib_action_qq_group),
                            summary = stringResource(R.string.help_page_contact_qq_summary),
                            onClick = { mainActivityOperation.gotoQQGroup() },
                        )
                        FeatureEntryCard(
                            title = stringResource(R.string.action_update),
                            summary = stringResource(R.string.overview_project_update_summary),
                            onClick = { mainActivityOperation.gotoGitHubReleasePage() },
                        )
                        FeatureEntryCard(
                            title = stringResource(R.string.action_about),
                            summary = stringResource(R.string.overview_project_about_summary),
                            onClick = { mainActivityOperation.showAboutDialog(onShowAboutDialog) },
                        )
                    }
                }
            }
        },
        overlay = {
            OverlayHeaderPanel(
                title = stringResource(R.string.app_name),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    InfoPill(
                        text = stringResource(
                            R.string.overview_version_format,
                            BuildConfig.VERSION_NAME,
                        ),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    InfoPill(
                        text = stringResource(
                            R.string.overview_detected_apps_format,
                            appStats.usingMiPush,
                            max(appStats.total, 0),
                        ),
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
    )
}

@Composable
private fun AppStatsDonutSection(
    appStats: ApplicationStats,
) {
    val outerSlices = listOf(
        DonutSlice(
            label = stringResource(R.string.app_list_stats_using_mipush),
            value = appStats.usingMiPush,
            total = max(appStats.total, 0),
            color = MaterialTheme.colorScheme.primary,
        ),
        DonutSlice(
            label = stringResource(R.string.app_list_stats_not_using_mipush),
            value = appStats.notUsingMiPush,
            total = max(appStats.total, 0),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    )
    val innerSlices = listOf(
        DonutSlice(
            label = stringResource(R.string.app_list_stats_registered),
            value = appStats.registered,
            total = max(appStats.usingMiPush, 0),
            color = MaterialTheme.colorScheme.tertiary,
        ),
        DonutSlice(
            label = stringResource(R.string.app_list_stats_not_registered),
            value = appStats.notRegistered,
            total = max(appStats.usingMiPush, 0),
            color = MaterialTheme.colorScheme.secondary,
        ),
    )

    Column(
        modifier = Modifier.padding(
            start = MaterialTheme.spacing.large,
            end = MaterialTheme.spacing.large,
            bottom = MaterialTheme.spacing.large,
        ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
            contentAlignment = Alignment.Center,
        ) {
            DonutChart(
                modifier = Modifier.size(232.dp),
                outerSlices = outerSlices,
                innerSlices = innerSlices,
                outerStroke = 28.dp,
                innerStroke = 20.dp,
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = appStats.total.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.app_list_stats_total),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        OverviewLegendBlock(
            title = stringResource(R.string.overview_chart_outer_title),
            slices = outerSlices,
        )
        OverviewLegendBlock(
            title = stringResource(R.string.overview_chart_inner_title),
            slices = innerSlices,
        )
    }
}

private data class DonutSlice(
    val label: String,
    val value: Int,
    val total: Int,
    val color: Color,
)

@Composable
private fun DonutChart(
    modifier: Modifier = Modifier,
    outerSlices: List<DonutSlice>,
    innerSlices: List<DonutSlice>,
    outerStroke: androidx.compose.ui.unit.Dp,
    innerStroke: androidx.compose.ui.unit.Dp,
) {
    val baseTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Canvas(modifier = modifier) {
        val outerStrokePx = outerStroke.toPx()
        val innerStrokePx = innerStroke.toPx()
        val outerDiameter = size.minDimension
        val outerTopLeft = Offset(
            (size.width - outerDiameter) / 2f,
            (size.height - outerDiameter) / 2f,
        )
        val outerSize = Size(outerDiameter, outerDiameter)
        val innerDiameter = outerDiameter - outerStrokePx * 1.95f
        val innerTopLeft = Offset(
            (size.width - innerDiameter) / 2f,
            (size.height - innerDiameter) / 2f,
        )
        val innerSize = Size(innerDiameter, innerDiameter)

        drawArc(
            color = baseTrackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = outerTopLeft,
            size = outerSize,
            style = Stroke(width = outerStrokePx, cap = StrokeCap.Round),
        )
        drawArc(
            color = baseTrackColor.copy(alpha = 0.75f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = innerTopLeft,
            size = innerSize,
            style = Stroke(width = innerStrokePx, cap = StrokeCap.Round),
        )

        drawSlices(
            slices = outerSlices,
            diameter = outerDiameter,
            topLeft = outerTopLeft,
            arcSize = outerSize,
            stroke = outerStrokePx,
        )
        drawSlices(
            slices = innerSlices,
            diameter = innerDiameter,
            topLeft = innerTopLeft,
            arcSize = innerSize,
            stroke = innerStrokePx,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSlices(
    slices: List<DonutSlice>,
    diameter: Float,
    topLeft: Offset,
    arcSize: Size,
    stroke: Float,
) {
    if (diameter <= 0f) return
    var startAngle = -90f
    slices.forEach { slice ->
        if (slice.total <= 0 || slice.value <= 0) return@forEach
        val sweep = 360f * (slice.value.toFloat() / slice.total.toFloat())
        drawArc(
            color = slice.color,
            startAngle = startAngle,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        startAngle += sweep
    }
}

@Composable
private fun OverviewLegendBlock(
    title: String,
    slices: List<DonutSlice>,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        slices.forEach { slice ->
            OverviewLegendItem(slice = slice)
        }
    }
}

@Composable
private fun OverviewLegendItem(
    slice: DonutSlice,
) {
    val total = max(slice.total, 0)
    val percent = if (total == 0) 0 else (slice.value * 100 / total)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(slice.color, CircleShape),
        )
        Column(
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = slice.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(
                    R.string.overview_chart_ratio_format,
                    slice.value,
                    percent,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = slice.value.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(52.dp),
        )
    }
}
