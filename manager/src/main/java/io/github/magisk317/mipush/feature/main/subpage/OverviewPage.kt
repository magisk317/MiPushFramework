@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.magisk317.mipush.feature.main.subpage

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.pm.PackageInfoCompat
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import io.github.magisk317.uikit.common.showLatestSnackbar
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import io.github.magisk317.mipush.common.compat.PackageManagerCompatBridge
import io.github.magisk317.mipush.common.manager.ManagerApplicationGateway
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.mipush.main.viewmodel.OverviewViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.magisk317.mipush.feature.main.MainActivityOperation
import io.github.magisk317.uikit.surface.DialogAction
import io.github.magisk317.uikit.surface.DialogActionRow
import io.github.magisk317.uikit.surface.SectionColumn
import io.github.magisk317.mipush.feature.ui.theme.spacing
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import io.github.magisk317.uikit.surface.DonateDialog
import io.github.magisk317.uikit.surface.QRCodeDialog
import io.github.magisk317.uikit.surface.saveImageToGalleryAsync
import io.github.magisk317.uikit.surface.chromeTopAppBarColors
import io.github.magisk317.uikit.R as UiKitR

private val OverviewCardShape = RoundedCornerShape(28.dp)

@Composable
fun Overview(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onShowAboutDialog: (String) -> Unit = {},
    onNavigateToConnectionStatus: () -> Unit = {},
) {
    Page {
        OverviewScreen(
            contentPadding = contentPadding,
            onShowAboutDialog = onShowAboutDialog,
            onNavigateToConnectionStatus = onNavigateToConnectionStatus,
        )
    }
}

@Composable
private fun OverviewScreen(
    contentPadding: PaddingValues,
    onShowAboutDialog: (String) -> Unit,
    onNavigateToConnectionStatus: () -> Unit,
) {
    val context = LocalContext.current
    val overviewViewModel: OverviewViewModel = koinViewModel()
    val mainActivityOperation = MainActivityOperation(context)
    var showDonateDialog by remember { mutableStateOf(false) }
    var showQRCodeDialog by remember { mutableStateOf<Pair<Int, String>?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val appStats by overviewViewModel.stats.collectAsState()
    LaunchedEffect(Unit) {
        overviewViewModel.loadStats()
    }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val scrollState = rememberScrollState()

    val packageInfo = remember { context.packageManager.getPackageInfo(context.packageName, 0) }
    val appVersionName = packageInfo.versionName ?: context.getString(io.github.magisk317.uikit.R.string.unknown)
    val appVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo).toString()

    Box(modifier = Modifier.fillMaxSize()) {
        SectionColumn(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            contentPadding = PaddingValues(
                start = MaterialTheme.spacing.medium,
                top = topInset + 80.dp,
                end = MaterialTheme.spacing.medium,
                bottom = contentPadding.calculateBottomPadding() + bottomInset + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = OverviewCardShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                AppStatsDonutSection(appStats = appStats)
            }

            io.github.magisk317.uikit.surface.OverviewAppInfoCard(
                appVersionName = appVersionName,
                appVersionCode = appVersionCode,
            )

            io.github.magisk317.uikit.surface.OverviewDeviceInfoCard()

            io.github.magisk317.uikit.surface.OverviewLinksCard(
                onCheckUpdate = { mainActivityOperation.gotoGitHubReleasePage() },
                onJoinQQ = { mainActivityOperation.gotoQQGroup() },
                onJoinTelegram = { mainActivityOperation.gotoTelegramGroup() },
                onSourceCode = { mainActivityOperation.gotoGitHubProjectPage() },
                onDonate = { showDonateDialog = true },
            )
        }

        TopAppBar(
            title = { Text(text = stringResource(R.string.app_name)) },
            windowInsets = WindowInsets.statusBars,
            actions = {
                ConnectionStatusIndicator(onClick = onNavigateToConnectionStatus)
            },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            colors = chromeTopAppBarColors(),
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (showDonateDialog) {
        DonateDialog(
            onDismiss = { showDonateDialog = false },
            onAlipay = {
                showDonateDialog = false
                showQRCodeDialog = UiKitR.drawable.alipay to "alipay"
            },
            onWechat = {
                showDonateDialog = false
                showQRCodeDialog = UiKitR.drawable.wx to "wechat"
            },
            showPlayDonations = false,
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
private fun ConnectionStatusIndicator(onClick: () -> Unit = {}) {
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
private fun AppStatsDonutSection(
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
    stroke: androidx.compose.ui.unit.Dp,
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
