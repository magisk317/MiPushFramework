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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.util.Log
import android.widget.Toast
import top.trumeet.mipushframework.component.MarkdownView
import top.trumeet.mipushframework.main.MainPage
import top.trumeet.mipushframework.wizard.permission.AlertWindowPermissionInfo
import top.trumeet.mipushframework.wizard.permission.PermissionInfo
import top.trumeet.mipushframework.wizard.permission.RequestIgnoreBatteryOptimizationsPermissionInfo
import top.trumeet.mipushframework.wizard.permission.UsageStatsPermissionInfo
import top.trumeet.ui.theme.Theme

class RequestPermissionPage : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            Theme {
                window.navigationBarColor = MaterialTheme.colorScheme.surfaceColorAtElevation(
                    NavigationBarDefaults.Elevation
                ).toArgb()
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

    Column(
        modifier = modifier
            .statusBarsPadding()
            .navigationBarsPadding()
            .fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        RequestPermissionContent(permissionInfos[currentItem.value])
        BottomBar(currentItem, permissionInfos)
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
            Log.d("WizardPermission", "event=$event index=${currentItem.value} page=${page.javaClass.simpleName} granted=$granted")
            if (event == Lifecycle.Event.ON_RESUME) {
                // Non-display pages should recover after activity recreation and continue automatically.
                if (page !is DisplayOnlyPhonyPermissionInfo && granted) {
                    Log.d("WizardPermission", "auto-advance on resume index=${currentItem.value}")
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
    Row {
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
            .background(MaterialTheme.colorScheme.primaryContainer)
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
    permissions: List<PermissionInfo>
) {
    val context = LocalContext.current
    val workaroundSp = context.getSharedPreferences("wizard_permission_workaround", Context.MODE_PRIVATE)
    BottomAppBar(modifier = Modifier.height(56.dp)) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { currentItem.value-- }, enabled = currentItem.value > 0
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack, contentDescription = "上一项"
                )
            }

            val operator = permissions[currentItem.value].permissionOperator
            IconButton(onClick = {
                if (operator.isPermissionGranted()) {
                    Log.d("WizardPermission", "manual-advance index=${currentItem.value}")
                    currentItem.value++
                } else {
                    val index = currentItem.value
                    if (operator is top.trumeet.mipushframework.wizard.permission.UsageStatsPermissionOperator) {
                        val requestedBefore = workaroundSp.getBoolean("usage_stats_requested_once", false)
                        if (requestedBefore) {
                            Log.w("WizardPermission", "force-advance usage-stats page index=$index by persisted flag")
                            currentItem.value++
                        } else {
                            workaroundSp.edit().putBoolean("usage_stats_requested_once", true).apply()
                            Toast.makeText(
                                context,
                                "如已授权，返回后再次点击下一步继续",
                                Toast.LENGTH_SHORT
                            ).show()
                            Log.d("WizardPermission", "request-permission index=$index first-time")
                            operator.requestPermission()
                        }
                    } else {
                        Log.d("WizardPermission", "request-permission index=$index")
                        operator.requestPermission()
                    }
                }
            }) {
                Icon(
                    imageVector = Icons.Default.ArrowForward, contentDescription = "下一项"
                )
            }
        }
    }
}
