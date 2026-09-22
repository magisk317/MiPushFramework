@file:android.annotation.SuppressLint("LocalContextGetResourceValueCall")

package io.github.magisk317.mipush.feature.main.subpage

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import io.github.magisk317.mipush.common.BuildConfig as CommonBuildConfig
import io.github.magisk317.mipush.feature.main.MainActivityOperation
import io.github.magisk317.mipush.feature.ui.theme.spacing
import io.github.magisk317.mipush.main.viewmodel.OverviewViewModel
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.mipush.manager.billing.BillingProvider
import io.github.magisk317.uikit.common.AppSnackbarHostState
import io.github.magisk317.uikit.common.showLatestSnackbar
import io.github.magisk317.uikit.surface.DonateDialog
import io.github.magisk317.uikit.surface.QRCodeDialog
import io.github.magisk317.uikit.surface.saveImageToGalleryAsync
import io.github.magisk317.uikit.surface.startAlipayPlatformDonate
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import io.github.magisk317.uikit.R as UiKitR

/**
 * Style-independent render state for the Overview page (KernelSU `HomeUiState` model).
 */
internal data class OverviewUiState(
    val appStats: ApplicationStats,
    val runtimeVersionName: String?,
    val appVersionName: String?,
    val appVersionCode: String,
    /**
     * Raw runtime connection state (`ManagerProtocol.CONNECTION_STATE_*`). The Miuix status card is
     * driven by this rather than by the hook flag: hook state is a diagnostic detail and now lives
     * on the diagnostics page, while the home card answers "is push actually connected".
     */
    val connectionState: String? = null,
)

/**
 * Action callbacks assembled by the dispatcher, consumed verbatim by each style
 * implementation (KernelSU `HomeActions` model).
 */
internal class OverviewActions(
    val onConnectionStatusClick: () -> Unit,
    val onJoinTelegram: () -> Unit,
    val onJoinQqChannel: () -> Unit,
    val onSourceCode: () -> Unit,
    val onDonate: () -> Unit,
    val onStatusCardClick: () -> Unit = {},
)

@Composable
fun Overview(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    isActive: Boolean = true,
    onNavigateToConnectionStatus: () -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val overviewViewModel: OverviewViewModel = koinViewModel()
    val billingProvider: BillingProvider = koinInject()
    val mainActivityOperation = MainActivityOperation(context)
    var showDonateDialog by remember { mutableStateOf(false) }
    var showQRCodeDialog by remember { mutableStateOf<Pair<Int, String>?>(null) }
    val snackbarHostState = remember { AppSnackbarHostState() }
    val scope = rememberCoroutineScope()
    val appStats by overviewViewModel.stats.collectAsState()
    val runtimeVersionName by overviewViewModel.runtimeVersionName.collectAsState()
    // Same activity-scoped instance that drives the diagnostics page, so the status card rides
    // along with the auto-refreshing connection snapshot instead of adding a second remote poll.
    val connectionStatusViewModel: io.github.magisk317.mipush.main.viewmodel.ConnectionStatusViewModel =
        koinViewModel()
    val connectionSnapshot by connectionStatusViewModel.snapshot.collectAsState()
    // The Miuix card is the only connection readout on this page now that the top-bar indicator is
    // gone, so the page itself has to keep the snapshot fresh.
    LaunchedEffect(Unit) {
        connectionStatusViewModel.startAutoRefresh()
    }
    var hasLoadedStats by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isActive) {
        if (!isActive || hasLoadedStats) return@LaunchedEffect
        // Let the pager settle and draw its first frame before doing page IO/state work.
        withFrameNanos { }
        overviewViewModel.loadStats()
        hasLoadedStats = true
    }

    val packageInfo = remember { context.packageManager.getPackageInfo(context.packageName, 0) }
    val appVersionName = packageInfo.versionName
        ?: context.getString(UiKitR.string.unknown)
    val appVersionCode = CommonBuildConfig.GIT_COMMIT
        .takeIf { it.isNotBlank() && it != "unknown" }
        ?: PackageInfoCompat.getLongVersionCode(packageInfo).toString()

    val state = OverviewUiState(
        appStats = appStats,
        runtimeVersionName = runtimeVersionName,
        appVersionName = appVersionName,
        appVersionCode = appVersionCode,
        connectionState = connectionSnapshot?.connectionState,
    )
    val actions = OverviewActions(
        onConnectionStatusClick = onNavigateToConnectionStatus,
        onJoinTelegram = { mainActivityOperation.gotoTelegramGroup() },
        onJoinQqChannel = { mainActivityOperation.gotoQqChannel() },
        onSourceCode = { mainActivityOperation.gotoGitLabProjectPage() },
        onDonate = { showDonateDialog = true },
        onStatusCardClick = onNavigateToConnectionStatus,
    )

    when (currentUiKitStyle()) {
        UiKitStyle.Miuix -> OverviewMiuix(
            state = state,
            actions = actions,
            contentPadding = contentPadding,
            snackbarHostState = snackbarHostState,
        )

        UiKitStyle.Expressive -> OverviewExpressive(
            state = state,
            actions = actions,
            contentPadding = contentPadding,
            snackbarHostState = snackbarHostState,
        )
    }

    if (showDonateDialog) {
        DonateDialog(
            onDismiss = { showDonateDialog = false },
            onAlipay = {
                showDonateDialog = false
                Toast.makeText(
                    context,
                    UiKitR.string.alipay_platform_opening,
                    Toast.LENGTH_SHORT,
                ).show()
                scope.launch {
                    val error = startAlipayPlatformDonate(context)
                    if (error != null) {
                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                        showQRCodeDialog = UiKitR.drawable.alipay to "alipay"
                    }
                }
            },
            onWechat = {
                showDonateDialog = false
                showQRCodeDialog = UiKitR.drawable.wx to "wechat"
            },
            showPlayDonations = billingProvider.supportsPlayDonations,
            onDonate099 = { activity?.let { billingProvider.launchDonation(it, "donate_099") } },
            onDonate200 = { activity?.let { billingProvider.launchDonation(it, "donate_200") } },
            onDonate999 = { activity?.let { billingProvider.launchDonation(it, "donate_999") } },
            onDonate1999 = { activity?.let { billingProvider.launchDonation(it, "donate_1999") } },
        )
    }

    showQRCodeDialog?.let { (resId, type) ->
        QRCodeDialog(
            resId = resId,
            type = type,
            onDismiss = { showQRCodeDialog = null },
            onSave = {
                scope.launch {
                    saveImageToGalleryAsync(context, resId, "${type}_qrcode")
                        .forEach { message ->
                            snackbarHostState.showLatestSnackbar(message)
                        }
                }
            },
        )
    }
}

@Composable
internal fun ConnectionStatusIndicator(onClick: () -> Unit = {}) {
    val viewModel: io.github.magisk317.mipush.main.viewmodel.ConnectionStatusViewModel = koinViewModel()
    val snapshot by viewModel.snapshot.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startAutoRefresh()
    }

    val data = snapshot
    val state = data?.connectionState ?: "Idle"
    val indicatorColor = when (state) {
        "Connected" -> Color(0xFF4CAF50)
        "Connecting" -> Color(0xFFFFC107)
        "Disconnected" -> Color(0xFFF44336)
        else -> Color.Gray
    }

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(indicatorColor, CircleShape)
        )
        Text(
            text = state,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun AppStatsDonutSection(
    appStats: ApplicationStats,
) {
    val activeSliceColor = MaterialTheme.colorScheme.primary
    val inactiveSliceColor = MaterialTheme.colorScheme.primaryContainer
    val outerSlices = listOf(
        DonutSlice(
            label = stringResource(R.string.overview_chart_integrated_label),
            value = appStats.usingMiPush,
            total = max(appStats.total, 0),
            color = activeSliceColor,
        ),
        DonutSlice(
            label = stringResource(R.string.overview_chart_not_integrated_label),
            value = appStats.notUsingMiPush,
            total = max(appStats.total, 0),
            color = inactiveSliceColor,
        ),
    )
    val innerSlices = listOf(
        DonutSlice(
            label = stringResource(R.string.overview_chart_registered_label),
            value = appStats.registered,
            total = max(appStats.usingMiPush, 0),
            color = activeSliceColor,
        ),
        DonutSlice(
            label = stringResource(R.string.overview_chart_unregistered_label),
            value = appStats.notRegistered,
            total = max(appStats.usingMiPush, 0),
            color = inactiveSliceColor,
        ),
    )

    Column(
        modifier = Modifier.padding(
            start = MaterialTheme.spacing.large,
            top = MaterialTheme.spacing.large,
            end = MaterialTheme.spacing.large,
            bottom = MaterialTheme.spacing.large,
        ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
        ) {
            val chartSpacing = MaterialTheme.spacing.medium
            val panelWidth = (maxWidth - chartSpacing) / 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(chartSpacing),
                verticalAlignment = Alignment.Top,
            ) {
                OverviewChartPanel(
                    modifier = Modifier.width(panelWidth),
                    title = stringResource(R.string.overview_chart_outer_title),
                    slices = outerSlices,
                )
                OverviewChartPanel(
                    modifier = Modifier.width(panelWidth),
                    title = stringResource(R.string.overview_chart_inner_title),
                    slices = innerSlices,
                )
            }
        }
    }
}

private data class DonutSlice(
    val label: String,
    val value: Int,
    val total: Int,
    val color: Color,
)

@Composable
private fun OverviewChartPanel(
    modifier: Modifier = Modifier,
    title: String,
    slices: List<DonutSlice>,
) {
    var selectedIndex by remember(title) { mutableIntStateOf(0) }
    val selectedSlice = slices.getOrNull(selectedIndex) ?: slices.first()
    val selectedPercent = selectedSlice.percent()

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            DonutChart(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                slices = slices,
                stroke = 18.dp,
                onSliceTap = { tappedIndex -> selectedIndex = tappedIndex },
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = selectedSlice.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "${selectedSlice.value}  $selectedPercent%",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }
        OverviewLegendBlock(
            slices = slices,
            selectedIndex = selectedIndex,
            onSelect = { selectedIndex = it },
        )
    }
}

@Composable
private fun DonutChart(
    modifier: Modifier = Modifier,
    slices: List<DonutSlice>,
    stroke: Dp,
    onSliceTap: (Int) -> Unit,
) {
    val baseTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val density = LocalDensity.current
    val strokePx = with(density) { stroke.toPx() }
    var chartSize by remember { mutableStateOf(IntSize.Zero) }

    Canvas(
        modifier = modifier
            .onSizeChanged { chartSize = it }
            .pointerInput(slices, chartSize, strokePx) {
                detectTapGestures { tapOffset ->
                    resolveSliceIndexForTap(
                        tapOffset = tapOffset,
                        chartSize = chartSize,
                        strokePx = strokePx,
                        slices = slices,
                    )?.let(onSliceTap)
                }
            },
    ) {
        val diameter = size.minDimension
        val topLeft = Offset(
            (size.width - diameter) / 2f,
            (size.height - diameter) / 2f,
        )
        val arcSize = Size(diameter, diameter)

        drawArc(
            color = baseTrackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round),
        )

        drawSlices(
            slices = slices,
            diameter = diameter,
            topLeft = topLeft,
            arcSize = arcSize,
            stroke = strokePx,
        )
    }
}

private fun resolveSliceIndexForTap(
    tapOffset: Offset,
    chartSize: IntSize,
    strokePx: Float,
    slices: List<DonutSlice>,
): Int? {
    if (chartSize == IntSize.Zero || slices.isEmpty()) return null
    val width = chartSize.width.toFloat()
    val height = chartSize.height.toFloat()
    val centerX = width / 2f
    val centerY = height / 2f
    val radius = minOf(width, height) / 2f
    val distance = hypot(tapOffset.x - centerX, tapOffset.y - centerY)
    val innerRadius = radius - strokePx
    if (distance < innerRadius || distance > radius) return null

    val angle = ((Math.toDegrees(atan2((tapOffset.y - centerY).toDouble(), (tapOffset.x - centerX).toDouble())) + 90.0) + 360.0) % 360.0
    var currentSweepStart = 0.0
    slices.forEachIndexed { index, slice ->
        if (slice.total <= 0 || slice.value <= 0) return@forEachIndexed
        val sweep = 360.0 * slice.value.toDouble() / slice.total.toDouble()
        if (angle >= currentSweepStart && angle < currentSweepStart + sweep) {
            return index
        }
        currentSweepStart += sweep
    }
    return null
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
    slices: List<DonutSlice>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        slices.forEachIndexed { index, slice ->
            OverviewLegendItem(
                slice = slice,
                selected = selectedIndex == index,
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun OverviewLegendItem(
    slice: DonutSlice,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(slice.color, CircleShape),
        )
        Text(
            text = slice.label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

private fun DonutSlice.percent(): Int {
    val safeTotal = max(total, 0)
    if (safeTotal == 0) return 0
    return value * 100 / safeTotal
}
