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
import top.trumeet.mipushframework.main.MainActivity
import top.trumeet.mipushframework.wizard.permission.AlertWindowPermissionInfo
import top.trumeet.mipushframework.wizard.permission.PermissionInfo
import top.trumeet.mipushframework.wizard.permission.RequestIgnoreBatteryOptimizationsPermissionInfo
import top.trumeet.mipushframework.wizard.permission.NotificationPermissionInfo
import top.trumeet.mipushframework.wizard.permission.UsageStatsPermissionInfo
import top.trumeet.ui.theme.Theme
import com.magisk317.data.DataStoreManager
import com.xiaomi.xmsf.MiPushFrameworkApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.xiaomi.xmsf.R
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.CheckCircle

private val logger = XLog.tag("WizardPermission").build()

class RequestPermissionPage : ComponentActivity() {
    companion object {
        const val EXTRA_RECHECK_ONLY = "extra_recheck_only"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val recheckOnly = intent?.getBooleanExtra(EXTRA_RECHECK_ONLY, false) ?: false
        setContent {
            Theme {
                PermissionMainActivity(recheckOnly = recheckOnly)
            }
        }
    }
}

@Preview(
    showBackground = true,
)
@Composable
fun PermissionMainActivity(
    modifier: Modifier = Modifier,
    recheckOnly: Boolean = false
) {
    val context = LocalContext.current
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
            .navigationBarsPadding()
    ) {
        val permissionInfos = remember {
            getPermissionInfos(context).filter { it !is DisplayOnlyPhonyPermissionInfo }
        }

        // Use a key to trigger recomposition when we return from settings
        var checkTrigger by remember { mutableIntStateOf(0) }
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    checkTrigger++
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        val allGranted = permissionInfos.all {
            // Reference checkTrigger to ensure it updates when activity resumes
            checkTrigger.let { _ -> it.permissionOperator.isPermissionGranted() }
        }

        // Track which permissions we've already tried to auto-request this session
        val autoRequestedSet = remember { mutableStateOf(setOf<Int>()) }

        LaunchedEffect(checkTrigger) {
            if (!allGranted) {
                permissionInfos.forEachIndexed { index, it ->
                    if (!it.permissionOperator.isPermissionGranted() && index !in autoRequestedSet.value) {
                        logger.d("Auto-requesting permission: ${it.permissionTitle}")
                        autoRequestedSet.value += index
                        
                        // Special handling for usage stats
                        if (it is UsageStatsPermissionInfo) {
                            val requestedBefore = DataStoreManager.usageStatsRequested.first()
                            if (!requestedBefore) {
                                DataStoreManager.setUsageStatsRequested(true)
                                it.permissionOperator.requestPermission()
                            }
                        } else {
                            it.permissionOperator.requestPermission()
                        }
                        return@LaunchedEffect // Only one at a time
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp, top = 32.dp)
            )
            Text(
                text = stringResource(id = R.string.wizard_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(permissionInfos) { info ->
                    PermissionItem(info, checkTrigger)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (recheckOnly) {
                        (context as? ComponentActivity)?.finish()
                    } else {
                        WizardSPUtils.finishWizard(context as ComponentActivity)
                        context.startActivity(Intent(context, MainActivity::class.java))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = true
            ) {
                Text(
                    text = if (allGranted) {
                        stringResource(id = R.string.wizard_title_finish_button)
                    } else {
                        stringResource(id = R.string.wizard_title_continue_button)
                    }
                )
            }
        }
    }
}

@Composable
fun PermissionItem(info: PermissionInfo, checkTrigger: Int) {
    val isGranted = checkTrigger.let { _ -> info.permissionOperator.isPermissionGranted() }
    
    ListItem(
        headlineContent = { 
            Text(
                text = info.permissionTitle,
                fontWeight = FontWeight.SemiBold
            ) 
        },
        supportingContent = { Text(text = info.permissionDescription) },
        leadingContent = {
            if (isGranted) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = stringResource(id = R.string.status_granted),
                    tint = Color(0xFF4CAF50)
                )
            } else {
                Icon(
                    Icons.Outlined.Circle,
                    contentDescription = stringResource(id = R.string.status_pending),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        },
        modifier = Modifier
            .clickable {
                if (!isGranted) {
                    info.permissionOperator.requestPermission()
                }
            }
            .background(
                color = if (isGranted) 
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                else 
                    MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium
            ),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        )
    )
}

@Composable
private fun JumpToMainActivity() {
    val context = LocalContext.current
    context.startActivity(Intent(context, MainActivity::class.java))
}

private fun getPermissionInfos(context: Context): List<PermissionInfo> {
    val pages = mutableListOf<PermissionInfo>().apply {
        add(WelcomePhonyPermissionInfo(context))
        add(UsageStatsPermissionInfo(context))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            add(RequestIgnoreBatteryOptimizationsPermissionInfo(context))
            add(AlertWindowPermissionInfo(context))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(NotificationPermissionInfo(context))
        }
        add(FinishedPhonyPermissionInfo(context))
    }
    return pages
}
