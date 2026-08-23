@file:android.annotation.SuppressLint("LocalContextGetResourceValueCall")

package io.github.magisk317.mipush.feature.wizard

import io.github.magisk317.mipush.common.R as CommonR
import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.widget.Toast
import io.github.magisk317.mipush.feature.wizard.support.DisplayOnlyPhonyPermissionInfo
import io.github.magisk317.mipush.feature.wizard.support.FinishedPhonyPermissionInfo
import io.github.magisk317.mipush.feature.wizard.support.WelcomePhonyPermissionInfo
import io.github.magisk317.mipush.feature.wizard.support.WizardSPUtils
import io.github.magisk317.mipush.feature.wizard.permission.AlertWindowPermissionInfo
import io.github.magisk317.mipush.feature.wizard.permission.AccessibilityPermissionInfo
import io.github.magisk317.mipush.feature.wizard.permission.PermissionInfo
import io.github.magisk317.mipush.feature.wizard.permission.RequestIgnoreBatteryOptimizationsPermissionInfo
import io.github.magisk317.mipush.feature.wizard.permission.NotificationPermissionInfo
import io.github.magisk317.mipush.feature.wizard.permission.RootPermissionInfo
import io.github.magisk317.mipush.feature.wizard.permission.RootPermissionOperator
import io.github.magisk317.mipush.feature.wizard.permission.UsageStatsPermissionInfo
import io.github.magisk317.mipush.feature.wizard.permission.requirementGroupKey
import io.github.magisk317.mipush.feature.ui.theme.Theme
import io.github.magisk317.mipush.feature.ui.theme.ThemeMode
import io.github.magisk317.mipush.common.manager.ManagerRootAccessSnapshot
import io.github.magisk317.mipush.common.manager.ManagerRootAccessState
import io.github.magisk317.mipush.common.manager.ManagerRootSubjectStatus
import io.github.magisk317.mipush.common.manager.ManagerRootTarget
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.main.viewmodel.RequestPermissionViewModel
import io.github.magisk317.uikit.theme.UiKitStyle
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.uikit.surface.chromeTopAppBarColors
import androidx.compose.foundation.clickable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import io.github.magisk317.mipush.feature.main.MainActivity
import org.koin.compose.viewmodel.koinViewModel
import org.koin.android.ext.android.inject
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars

private val TAG = "WizardPermission"

open class RequestPermissionPage : ComponentActivity() {
    companion object {
        const val EXTRA_RECHECK_ONLY = "extra_recheck_only"
        private val COLOR_GRANTED = Color(0xFF4CAF50) // Green 500
    }

    private val preferenceRepository: PreferenceRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val recheckOnly = intent?.getBooleanExtra(EXTRA_RECHECK_ONLY, false) ?: false
        setContent {
            val themeMode by preferenceRepository.themeMode.collectAsStateWithLifecycle(
                initialValue = ThemeMode.System.value,
            )
            val uiKitStyle by preferenceRepository.uiKitStyle.collectAsStateWithLifecycle(
                initialValue = UiKitStyle.Expressive.value,
            )
            Theme(
                themeMode = ThemeMode.fromValue(themeMode),
                uiKitStyle = uiKitStyle,
            ) {
                PermissionMainActivity(
                    recheckOnly = recheckOnly,
                    onFinishWizard = {
                        WizardSPUtils.finishWizard(this, preferenceRepository)
                    },
                )
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
    recheckOnly: Boolean = false,
    onFinishWizard: () -> Unit = {},
) {
    val context = LocalContext.current
    val permissionViewModel: RequestPermissionViewModel = koinViewModel()
    val permissionInfos = remember {
        getPermissionInfos(context).filter { it !is DisplayOnlyPhonyPermissionInfo }
    }
    val permissionStates by permissionViewModel.permissionStates.collectAsState()
    val rootAccessSnapshot by permissionViewModel.rootAccessSnapshot.collectAsState()

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

    val allGranted = areAllPermissionRequirementsSatisfied(permissionInfos, permissionStates)
    val autoRequestedSet = remember { mutableStateOf(setOf<Int>()) }

    LaunchedEffect(checkTrigger) {
        permissionViewModel.autoRequestPermissions(permissionInfos, autoRequestedSet.value)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_permission_check)) },
                navigationIcon = {
                    if (recheckOnly) {
                        IconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
                windowInsets = WindowInsets.statusBars,
                colors = chromeTopAppBarColors(),
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    if (recheckOnly) {
                        (context as? ComponentActivity)?.finish()
                    } else {
                        onFinishWizard()
                        context.startActivity(Intent(context, MainActivity::class.java))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = if (allGranted) {
                        stringResource(id = R.string.wizard_title_finish_button)
                    } else {
                        stringResource(id = R.string.wizard_title_continue_button)
                    },
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = stringResource(id = R.string.wizard_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            itemsIndexed(permissionInfos) { index, info ->
                if (info is RootPermissionInfo) {
                    RootPermissionItem(
                        info = info,
                        snapshot = rootAccessSnapshot,
                        onRequest = { target ->
                            permissionViewModel.requestRootAccess(target) { isGranted ->
                                checkTrigger++
                                val targetName = context.getString(
                                    if (target == ManagerRootTarget.MANAGER) {
                                        R.string.wizard_root_manager_title
                                    } else {
                                        R.string.wizard_root_runtime_title
                                    },
                                )
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        if (isGranted) {
                                            R.string.wizard_root_permission_target_granted_toast
                                        } else {
                                            R.string.wizard_root_permission_target_denied_toast
                                        },
                                        targetName,
                                    ),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                    )
                } else {
                    PermissionItem(
                        info = info,
                        isGranted = isPermissionRequirementSatisfied(index, permissionInfos, permissionStates),
                    ) {
                        permissionViewModel.requestPermission(info) {
                            checkTrigger++
                        }
                    }
                }
            }
        }
    }
}

private val COLOR_GRANTED = Color(0xFF4CAF50) // Green 500

@Composable
private fun RootPermissionItem(
    info: RootPermissionInfo,
    snapshot: ManagerRootAccessSnapshot,
    onRequest: (ManagerRootTarget) -> Unit,
) {
    val spaceLabel = when (snapshot.userId) {
        0 -> stringResource(R.string.wizard_root_space_primary)
        999 -> stringResource(R.string.wizard_root_space_dual)
        else -> stringResource(R.string.wizard_root_space_other, snapshot.userId)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
            ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = info.permissionTitle,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = spaceLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = info.permissionDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        RootSubjectItem(
            title = stringResource(R.string.wizard_root_manager_title),
            summary = stringResource(R.string.wizard_root_manager_summary),
            subject = snapshot.manager,
            onRequest = onRequest,
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(start = 56.dp),
        )
        RootSubjectItem(
            title = stringResource(R.string.wizard_root_runtime_title),
            summary = stringResource(R.string.wizard_root_runtime_summary),
            subject = snapshot.runtime,
            onRequest = onRequest,
        )
    }
}

@Composable
private fun RootSubjectItem(
    title: String,
    summary: String,
    subject: ManagerRootSubjectStatus,
    onRequest: (ManagerRootTarget) -> Unit,
) {
    val granted = subject.state == ManagerRootAccessState.GRANTED
    val statusText = when (subject.state) {
        ManagerRootAccessState.GRANTED -> stringResource(R.string.wizard_root_status_granted)
        ManagerRootAccessState.NOT_GRANTED -> stringResource(R.string.wizard_root_status_not_granted)
        ManagerRootAccessState.UNAVAILABLE -> stringResource(R.string.wizard_root_status_unavailable)
    }
    val details = buildString {
        append(summary)
        append('\n')
        append(subject.packageName)
        subject.uid?.let {
            append(" · UID ")
            append(it)
        }
    }
    ListItem(
        supportingContent = {
            Text(text = details, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        leadingContent = {
            Icon(
                painter = painterResource(
                    if (granted) {
                        CommonR.drawable.ic_check_circle_black_24dp
                    } else {
                        CommonR.drawable.ic_radio_button_unchecked_black_24dp
                    },
                ),
                contentDescription = statusText,
                tint = if (granted) COLOR_GRANTED else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(24.dp),
            )
        },
        trailingContent = {
            if (subject.state == ManagerRootAccessState.NOT_GRANTED) {
                TextButton(onClick = { onRequest(subject.target) }) {
                    Text(stringResource(R.string.wizard_root_request))
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                color = if (granted) COLOR_GRANTED else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun PermissionItem(
    info: PermissionInfo,
    isGranted: Boolean,
    onPermissionStateChanged: () -> Unit
) {
    ListItem(
        leadingContent = {
            if (isGranted) {
                Icon(
                    painter = painterResource(CommonR.drawable.ic_check_circle_black_24dp),
                    contentDescription = stringResource(id = R.string.status_granted),
                    tint = COLOR_GRANTED
                )
            } else {
                Icon(
                    painter = painterResource(CommonR.drawable.ic_radio_button_unchecked_black_24dp),
                    contentDescription = stringResource(id = R.string.status_pending),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        },
        modifier = Modifier
            .clickable {
                if (!isGranted || info.permissionOperator is RootPermissionOperator) {
                    onPermissionStateChanged()
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
    ) {
        Text(
            text = info.permissionTitle,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun JumpToMainActivity() {
    val context = LocalContext.current
    context.startActivity(Intent(context, MainActivity::class.java))
}

private fun getPermissionInfos(context: Context): List<PermissionInfo> {
    val pages = mutableListOf<PermissionInfo>().apply {
        add(WelcomePhonyPermissionInfo(context))
        add(RootPermissionInfo(context))
        add(UsageStatsPermissionInfo(context))
        add(AccessibilityPermissionInfo(context))
        add(RequestIgnoreBatteryOptimizationsPermissionInfo(context))
        add(AlertWindowPermissionInfo(context))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(NotificationPermissionInfo(context))
        }
        add(FinishedPhonyPermissionInfo(context))
    }
    return pages
}

private fun isPermissionRequirementSatisfied(
    index: Int,
    permissionInfos: List<PermissionInfo>,
    permissionStates: Map<Int, Boolean>
): Boolean {
    val info = permissionInfos[index]
    val groupKey = info.requirementGroupKey()
    return if (groupKey == null) {
        permissionStates[index] == true
    } else {
        permissionInfos.withIndex().any {
            it.value.requirementGroupKey() == groupKey &&
                permissionStates[it.index] == true
        }
    }
}

private fun areAllPermissionRequirementsSatisfied(
    permissionInfos: List<PermissionInfo>,
    permissionStates: Map<Int, Boolean>
): Boolean {
    val groupedInfos = permissionInfos.withIndex()
        .filter { it.value.isRequired }
        .groupBy { it.value.requirementGroupKey() ?: "single:${it.value::class.java.name}" }
    return groupedInfos.values.all { group ->
        group.any { permissionStates[it.index] == true }
    }
}
