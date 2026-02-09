@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
package top.trumeet.mipushframework.wizard

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.elvishew.xlog.XLog
import android.widget.Toast
import top.trumeet.mipushframework.component.MarkdownView
import top.trumeet.mipushframework.main.MainPage
import top.trumeet.mipushframework.wizard.permission.AlertWindowPermissionInfo
import top.trumeet.mipushframework.wizard.permission.PermissionInfo
import top.trumeet.mipushframework.wizard.permission.RequestIgnoreBatteryOptimizationsPermissionInfo
import top.trumeet.mipushframework.wizard.permission.UsageStatsPermissionInfo
import top.trumeet.ui.theme.Theme
import com.magisk317.data.DataStoreManager
import com.xiaomi.xmsf.MiPushFrameworkApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val logger = XLog.tag("WizardPermission").build()

class RequestPermissionPage : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            Theme {
                PermissionMainPage()
            }
        }
    }
}

@Preview(
    showBackground = true,
)
@Composable
fun PermissionMainPage(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val permissionInfos = getPermissionInfos(context)

    val currentItem = remember { mutableStateOf(0) }
    if (allPermissionsGranted(currentItem, permissionInfos)) {
        JumpToMainActivity()
        WizardSPUtils.finishWizard(context as ComponentActivity)
        return
    }

    NavigateToNextPageIfPermissionGranted(permissionInfos, currentItem)

    // Logic for Next/Prev
    val onPrev: () -> Unit = {
        if (currentItem.value > 0) {
            currentItem.value--
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val onNext: () -> Unit = {
        val operator = permissionInfos[currentItem.value].permissionOperator
        if (operator.isPermissionGranted()) {
            logger.d("manual-advance index=${currentItem.value}")
            currentItem.value++
        } else {
            val index = currentItem.value
            if (operator is top.trumeet.mipushframework.wizard.permission.UsageStatsPermissionOperator) {
                val requestedBefore = runBlocking { DataStoreManager.usageStatsRequested.first() }
                if (requestedBefore) {
                    logger.w("force-advance usage-stats page index=$index by persisted flag")
                    currentItem.value++
                } else {
                    coroutineScope.launch {
                        DataStoreManager.setUsageStatsRequested(true)
                    }
                    Toast.makeText(
                        context,
                        "如已授权，返回后再次点击下一步继续",
                        Toast.LENGTH_SHORT
                    ).show()
                    logger.d("request-permission index=$index first-time")
                    operator.requestPermission()
                }
            } else {
                logger.d("request-permission index=$index")
                operator.requestPermission()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                    )
                )
            )
    ) {
        var dragOffset = remember { mutableFloatStateOf(0f) }
            Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                BottomBar(currentItem, onPrev, onNext)
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragOffset.floatValue < -100) {
                                onNext()
                            } else if (dragOffset.floatValue > 100) {
                                onPrev()
                            }
                            dragOffset.floatValue = 0f
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        dragOffset.floatValue += dragAmount
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (offset.x < size.width / 2) {
                            onPrev()
                        } else {
                            onNext()
                        }
                    }
                }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                RequestPermissionContent(permissionInfos[currentItem.value])
            }
        }
    }
}

@Composable
private fun JumpToMainActivity() {
    val context = LocalContext.current
    context.startActivity(Intent(context, MainPage::class.java))
}

private fun allPermissionsGranted(
    currentItem: MutableState<Int>, permissionInfos: MutableList<PermissionInfo>
) = currentItem.value >= permissionInfos.size

private fun getPermissionInfos(context: Context): MutableList<PermissionInfo> {
    val pages = mutableListOf<PermissionInfo>().apply {
        add(WelcomePhonyPermissionInfo(context))
        add(UsageStatsPermissionInfo(context))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            add(RequestIgnoreBatteryOptimizationsPermissionInfo(context))
            add(AlertWindowPermissionInfo(context))
        }
        add(FinishedPhonyPermissionInfo(context))
    }
    return pages
}

@Composable
private fun NavigateToNextPageIfPermissionGranted(
    pages: List<PermissionInfo>, currentItem: MutableState<Int>
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val page = pages[currentItem.value]
            val granted = page.permissionOperator.isPermissionGranted()
            logger.d("event=$event index=${currentItem.value} page=${page.javaClass.simpleName} granted=$granted")
            if (event == Lifecycle.Event.ON_RESUME) {
                // Non-display pages should recover after activity recreation and continue automatically.
                if (page !is DisplayOnlyPhonyPermissionInfo && granted) {
                    logger.d("auto-advance on resume index=${currentItem.value}")
                    currentItem.value++
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

@Composable
fun RequestPermissionContent(permissionInfo: PermissionInfo) {
    Column {
        Title(permissionInfo.permissionTitle)
        Description(permissionInfo.permissionDescription)
    }
}

@Composable
private fun Description(description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.28f))
    ) {
        MarkdownView(
            description,
            textSize = MaterialTheme.typography.bodyLarge.fontSize.value,
            modifier = Modifier
                .align(Alignment.Bottom)
                .padding(16.dp)
        )
    }
}

@Composable
private fun Title(title: String) {
    Row(
        Modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f),
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                        Color.Transparent,
                    )
                )
            )
            .fillMaxWidth()
            .fillMaxHeight(0.4f)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .align(Alignment.Bottom)
                .padding(16.dp)
        )
    }
}

@Composable
private fun BottomBar(
    currentItem: MutableState<Int>,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    BottomAppBar(
        modifier = Modifier
            .height(56.dp),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.32f),
        tonalElevation = 0.dp
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (currentItem.value > 0) {
            IconButton(
                onClick = { onPrev() }, enabled = currentItem.value > 0
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "上一项",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            } else {
                 androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(48.dp))
            }

            IconButton(onClick = { onNext() }) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "下一项",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
