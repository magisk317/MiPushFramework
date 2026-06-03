@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.magisk317.mipush.feature.main.subpage

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import dev.chrisbanes.haze.hazeSource
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
import io.github.magisk317.mipush.manager.R
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.magisk317.mipush.feature.main.MainActivityOperation
import io.github.magisk317.mipush.feature.ui.component.DialogAction
import io.github.magisk317.mipush.feature.ui.component.DialogActionRow
import io.github.magisk317.mipush.feature.ui.component.SectionColumn
import io.github.magisk317.mipush.feature.ui.theme.spacing
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

private val OverviewCardShape = RoundedCornerShape(28.dp)
private const val ALIPAY_PACKAGE_NAME = "com.eg.android.AlipayGphone"
private const val WECHAT_PACKAGE_NAME = "com.tencent.mm"
private const val ALIPAY_POCKET_TOKEN = "J:/wkSIPXL689C 或📸復 zhi📸此消息打开🔍吱.f`u宝🔎，得幸福宏饱，天天等着你  s:/r HU6311 $801"

@Composable
fun Overview(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onShowAboutDialog: (String) -> Unit = {},
    hazeState: HazeState? = null,
    hazeStyle: HazeBlurStyle? = null,
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
    hazeStyle: HazeBlurStyle?,
) {
    val context = LocalContext.current
    val mainActivityOperation = MainActivityOperation(context)
    var showDonateDialog by remember { mutableStateOf(false) }
    var showAlipayChoiceDialog by remember { mutableStateOf(false) }
    var showQRCodeDialog by remember { mutableStateOf<Pair<Int, String>?>(null) }
    val appStats by produceState(
        initialValue = ApplicationStats(),
    ) {
        value = withContext(Dispatchers.IO) {
            loadApplicationStats(context)
        }
    }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        SectionColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (hazeState != null) {
                        Modifier.hazeSource(state = hazeState)
                    } else {
                        Modifier
                    }
                )
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

            OverviewProjectCard(
                mainActivityOperation = mainActivityOperation,
                onShowDonate = { showDonateDialog = true },
                onShowAboutDialog = onShowAboutDialog,
            )
        }

        TopAppBar(
            title = { Text(text = stringResource(R.string.app_name)) },
            windowInsets = WindowInsets.statusBars,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .then(
                    if (hazeState != null && hazeStyle != null) {
                        Modifier.hazeEffect(hazeState) {
                            blurEffect { style = hazeStyle }
                            forceInvalidateOnPreDraw = true
                        }
                    } else {
                        Modifier
                    }
                ),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        )
    }

    if (showDonateDialog) {
        DonateDialog(
            onDismiss = { showDonateDialog = false },
            onAlipay = {
                showDonateDialog = false
                showAlipayChoiceDialog = true
            },
            onWechat = {
                showDonateDialog = false
                showQRCodeDialog = R.drawable.wx to "wechat"
            },
        )
    }

    if (showAlipayChoiceDialog) {
        AlipayChoiceDialog(
            onDismiss = { showAlipayChoiceDialog = false },
            onQRCode = {
                showAlipayChoiceDialog = false
                showQRCodeDialog = R.drawable.alipay to "alipay"
            },
            onToken = {
                showAlipayChoiceDialog = false
                Toast.makeText(context, copyAlipayPocketToken(context), Toast.LENGTH_LONG).show()
                startAlipayActivity(context)?.let {
                    Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                }
            },
        )
    }

    showQRCodeDialog?.let { (resId, type) ->
        QRCodeDialog(
            resId = resId,
            type = type,
            onDismiss = { showQRCodeDialog = null },
            onSave = {
                saveImageToGallery(context, resId, "${type}_qrcode")
                    .forEach { message ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
            },
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

@Composable
private fun OverviewProjectCard(
    mainActivityOperation: MainActivityOperation,
    onShowDonate: () -> Unit,
    onShowAboutDialog: (String) -> Unit,
) {
    OverviewListCard(title = stringResource(R.string.overview_project_title)) {
        OverviewListItem(
            iconRes = R.drawable.ic_home_black_24dp,
            label = stringResource(R.string.overview_project_github_title),
            value = stringResource(R.string.overview_project_github_summary),
            onClick = { mainActivityOperation.gotoGitHubProjectPage() },
        )
        OverviewListItem(
            iconRes = R.drawable.ic_notifications_black_24dp,
            label = stringResource(R.string.action_update),
            value = stringResource(R.string.overview_project_update_summary),
            onClick = { mainActivityOperation.gotoGitHubReleasePage() },
        )
        OverviewListItem(
            iconRes = R.drawable.ic_info_outline_black_24dp,
            label = stringResource(R.string.pref_donate_by_alipay_title),
            value = stringResource(R.string.dialog_donate_summary),
            onClick = onShowDonate,
        )
        OverviewListItem(
            iconRes = R.drawable.ic_help_outline_24,
            label = stringResource(R.string.helplib_action_telegram_group),
            value = stringResource(R.string.help_page_contact_telegram_summary),
            onClick = { mainActivityOperation.gotoTelegramGroup() },
        )
        OverviewListItem(
            iconRes = R.drawable.ic_help_outline_24,
            label = stringResource(R.string.helplib_action_qq_group),
            value = stringResource(R.string.help_page_contact_qq_summary),
            onClick = { mainActivityOperation.gotoQQGroup() },
        )
        OverviewListItem(
            iconRes = R.drawable.ic_info_outline_black_24dp,
            label = stringResource(R.string.action_about),
            value = stringResource(R.string.overview_project_about_summary),
            onClick = { mainActivityOperation.showAboutDialog(onShowAboutDialog) },
        )
    }
}

@Composable
private fun OverviewListCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = OverviewCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(
                    start = MaterialTheme.spacing.large,
                    top = MaterialTheme.spacing.large,
                    end = MaterialTheme.spacing.large,
                    bottom = MaterialTheme.spacing.medium,
                ),
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            Column(
                modifier = Modifier.padding(vertical = MaterialTheme.spacing.small),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun OverviewListItem(
    iconRes: Int,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    ListItem(
        leadingContent = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        headlineContent = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = if (onClick != null) {
            Modifier.clickable(onClick = onClick)
        } else {
            Modifier
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun DonateDialog(
    onDismiss: () -> Unit,
    onAlipay: () -> Unit,
    onWechat: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.dialog_donate_title)) },
        text = { Text(text = stringResource(R.string.dialog_donate_summary)) },
        confirmButton = {
            DialogActionRow(
                actions = listOf(
                    DialogAction(
                        label = stringResource(R.string.dialog_donate_alipay),
                        onClick = onAlipay,
                    ),
                    DialogAction(
                        label = stringResource(R.string.dialog_donate_wechat),
                        onClick = onWechat,
                    ),
                    DialogAction(
                        label = stringResource(R.string.dialog_donate_cancel),
                        onClick = onDismiss,
                    ),
                ),
            )
        },
    )
}

@Composable
private fun AlipayChoiceDialog(
    onDismiss: () -> Unit,
    onQRCode: () -> Unit,
    onToken: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.dialog_donate_alipay_choice_title)) },
        text = { Text(text = stringResource(R.string.dialog_donate_alipay_choice_content)) },
        confirmButton = {
            DialogActionRow(
                actions = listOf(
                    DialogAction(
                        label = stringResource(R.string.dialog_donate_alipay_qrcode),
                        onClick = onQRCode,
                    ),
                    DialogAction(
                        label = stringResource(R.string.dialog_donate_alipay_token),
                        onClick = onToken,
                    ),
                    DialogAction(
                        label = stringResource(R.string.dialog_donate_cancel),
                        onClick = onDismiss,
                    ),
                ),
            )
        },
    )
}

@Composable
private fun QRCodeDialog(
    resId: Int,
    type: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (type == "alipay") {
                    stringResource(R.string.dialog_donate_alipay)
                } else {
                    stringResource(R.string.dialog_donate_wechat)
                },
            )
        },
        text = {
            androidx.compose.foundation.Image(
                painter = painterResource(id = resId),
                contentDescription = type,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            DialogActionRow(
                actions = listOf(
                    DialogAction(
                        label = stringResource(R.string.save_to_gallery),
                        onClick = onSave,
                    ),
                    DialogAction(
                        label = stringResource(R.string.dialog_donate_cancel),
                        onClick = onDismiss,
                    ),
                ),
            )
        },
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboardManager.setPrimaryClip(ClipData.newPlainText("mipush", text))
}

private fun copyAlipayPocketToken(context: Context): String {
    copyToClipboard(context, ALIPAY_POCKET_TOKEN)
    return context.getString(R.string.alipay_red_packet_code_copied, ALIPAY_POCKET_TOKEN)
}

private fun startAlipayActivity(context: Context): String? {
    val message = checkPackageStateMessage(
        context = context,
        packageName = ALIPAY_PACKAGE_NAME,
        installPromptRes = R.string.alipay_install_prompt,
        enablePromptRes = R.string.alipay_enable_prompt,
    )
    if (message != null) return message
    val intent = context.packageManager.getLaunchIntentForPackage(ALIPAY_PACKAGE_NAME)
    context.startActivity(intent)
    return null
}

private fun startWechatActivity(context: Context): String? {
    val message = checkPackageStateMessage(
        context = context,
        packageName = WECHAT_PACKAGE_NAME,
        installPromptRes = R.string.wechat_install_prompt,
        enablePromptRes = R.string.wechat_enable_prompt,
    )
    if (message != null) return message
    val intent = context.packageManager.getLaunchIntentForPackage(WECHAT_PACKAGE_NAME)
    context.startActivity(intent)
    return null
}

private fun checkPackageStateMessage(
    context: Context,
    packageName: String,
    installPromptRes: Int,
    enablePromptRes: Int,
): String? {
    val pm = context.packageManager
    return try {
        val appInfo = PackageManagerCompatBridge.getApplicationInfo(pm, packageName, 0)
        if (appInfo.enabled) null else context.getString(enablePromptRes)
    } catch (_: Exception) {
        context.getString(installPromptRes)
    }
}

private fun saveImageToGallery(context: Context, resId: Int, fileName: String): List<String> {
    val bitmap = BitmapFactory.decodeResource(context.resources, resId)
    val resolver = context.contentResolver
    val messages = mutableListOf<String>()
    val contentValues = android.content.ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.png")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }

    val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    if (imageUri == null) {
        messages += context.getString(R.string.save_to_gallery_failed)
        return messages
    }

    try {
        resolver.openOutputStream(imageUri)?.use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(imageUri, contentValues, null, null)
        }
        val appName = if (fileName.contains("alipay")) {
            context.getString(R.string.dialog_donate_alipay).substringBefore(" (")
        } else {
            context.getString(R.string.dialog_donate_wechat).substringBefore(" (")
        }
        messages += context.getString(R.string.save_to_gallery_success, appName)
        if (fileName.contains("alipay")) {
            startAlipayActivity(context)?.let(messages::add)
        } else if (fileName.contains("wechat")) {
            startWechatActivity(context)?.let(messages::add)
        }
    } catch (_: Exception) {
        messages += context.getString(R.string.save_to_gallery_failed)
    }
    return messages
}
