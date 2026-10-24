@file:android.annotation.SuppressLint("LocalContextGetResourceValueCall")

package io.github.magisk317.mipush.feature.wizard

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.feature.ui.theme.Theme
import io.github.magisk317.mipush.feature.wizard.permission.AccessibilityPermissionInfo
import io.github.magisk317.mipush.feature.wizard.permission.AlertWindowPermissionInfo
import io.github.magisk317.mipush.feature.wizard.permission.NotificationPermissionInfo
import io.github.magisk317.mipush.feature.wizard.permission.PermissionInfo
import io.github.magisk317.mipush.feature.wizard.permission.RequestIgnoreBatteryOptimizationsPermissionInfo
import io.github.magisk317.mipush.feature.wizard.permission.UsageStatsPermissionInfo
import io.github.magisk317.mipush.feature.wizard.permission.requirementGroupKey
import io.github.magisk317.mipush.feature.wizard.support.WizardSPUtils
import io.github.magisk317.mipush.main.viewmodel.GrantLogEntry
import io.github.magisk317.mipush.main.viewmodel.GrantOutcome
import io.github.magisk317.mipush.main.viewmodel.RequestPermissionViewModel
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.mipush.manager.application.ManagerRootAccessSnapshot
import io.github.magisk317.mipush.manager.application.ManagerRootAccessState
import io.github.magisk317.mipush.manager.application.ManagerRootTarget
import io.github.magisk317.uikit.onboarding.OnboardingActionRowCard
import io.github.magisk317.uikit.onboarding.OnboardingInfoCard
import io.github.magisk317.uikit.onboarding.OnboardingItemState
import io.github.magisk317.uikit.onboarding.OnboardingLogCard
import io.github.magisk317.uikit.onboarding.OnboardingSummaryHeader
import io.github.magisk317.uikit.surface.AppIcon
import io.github.magisk317.uikit.surface.AppIconButton
import io.github.magisk317.uikit.surface.AppPrimaryButton
import io.github.magisk317.uikit.surface.AppSecondaryButton
import io.github.magisk317.uikit.surface.SectionHeading
import io.github.magisk317.uikit.theme.AppColorRole
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.appColor
import io.github.magisk317.uikit.theme.currentUiKitStyle
import org.koin.android.ext.android.inject
import org.koin.compose.viewmodel.koinViewModel

private val TAG = "WizardPermission"

open class RequestPermissionPage : ComponentActivity() {
    companion object {
        const val EXTRA_RECHECK_ONLY = "extra_recheck_only"
    }

    private val preferenceRepository: PreferenceRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val recheckOnly = intent?.getBooleanExtra(EXTRA_RECHECK_ONLY, false) ?: false
        setContent {
            // Theme() resolves every field from the stored preferences itself.
            Theme {
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
    val permissionInfos = remember { getPermissionInfos(context) }
    val permissionStates by permissionViewModel.permissionStates.collectAsState()
    val rootAccessSnapshot by permissionViewModel.rootAccessSnapshot.collectAsState()
    val grantLog by permissionViewModel.grantLog.collectAsState()
    val grantingAll by permissionViewModel.grantingAll.collectAsState()
    val processingKeys by permissionViewModel.processingKeys.collectAsState()
    val canGrantSilently by permissionViewModel.canGrantSilently.collectAsState()

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

    // First composition and every ON_RESUME only refresh state: no system page pops up.
    LaunchedEffect(checkTrigger) {
        permissionViewModel.evaluatePermissions(permissionInfos)
    }

    val navigationIcon: @Composable () -> Unit = {
        if (recheckOnly) {
            AppIconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                AppIcon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
        }
    }

    val bottomBar: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppSecondaryButton(
                text = stringResource(R.string.wizard_action_grant_all),
                onClick = { permissionViewModel.grantAll(permissionInfos) },
                enabled = canGrantSilently && !grantingAll,
                modifier = Modifier.weight(1f),
            )
            // Entering is never gated: this report is the only screen that explains an
            // unmet requirement, and the diagnostics live behind it.
            AppPrimaryButton(
                text = stringResource(
                    if (recheckOnly) R.string.status_ok else R.string.wizard_action_enter_app,
                ),
                onClick = {
                    if (recheckOnly) {
                        (context as? ComponentActivity)?.finish()
                    } else {
                        onFinishWizard()
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }

    val body: @Composable (PaddingValues, Modifier) -> Unit = { innerPadding, scrollModifier ->
        PermissionSinglePage(
            permissionInfos = permissionInfos,
            permissionStates = permissionStates,
            rootAccessSnapshot = rootAccessSnapshot,
            grantLog = grantLog,
            grantingAll = grantingAll,
            processingKeys = processingKeys,
            onRequestPermission = { info ->
                permissionViewModel.requestPermission(info, permissionInfos) { granted ->
                    showToast(
                        context,
                        context.getString(
                            if (granted) R.string.wizard_log_granted else R.string.wizard_log_denied,
                            info.permissionTitle,
                        ),
                    )
                }
            },
            onRequestRootAccess = { target ->
                permissionViewModel.requestRootAccess(target, permissionInfos) { state ->
                    val title = context.getString(
                        if (target == ManagerRootTarget.MANAGER) {
                            R.string.wizard_root_manager_title
                        } else {
                            R.string.wizard_root_runtime_title
                        },
                    )
                    permissionViewModel.appendGrantLog(
                        GrantLogEntry(
                            title = title,
                            outcome = state.toGrantOutcome(),
                            isRoot = true,
                        ),
                    )
                    showToast(context, context.getString(rootToastResId(state), title))
                }
            },
            modifier = Modifier
                .padding(innerPadding)
                .then(scrollModifier),
        )
    }

    when (currentUiKitStyle()) {
        UiKitStyle.Miuix -> RequestPermissionMiuix(
            title = stringResource(R.string.settings_permission_check),
            navigationIcon = navigationIcon,
            bottomBar = bottomBar,
            body = body,
        )

        UiKitStyle.Expressive -> RequestPermissionMaterial(
            title = stringResource(R.string.settings_permission_check),
            navigationIcon = navigationIcon,
            bottomBar = bottomBar,
            body = body,
        )
    }
}

/**
 * Intro body carrying the repository URL as a real hyperlink. `wizard_descr`
 * keeps the wording and owns the `%1$s` placement, so each locale decides its
 * own punctuation; the link target stays a single non-translatable resource.
 */
@Composable
private fun wizardIntroSummary(): AnnotatedString {
    val url = stringResource(R.string.wizard_repo_url)
    val linkColor = appColor(AppColorRole.Primary)
    val body = stringResource(R.string.wizard_descr, url)
    return buildAnnotatedString {
        append(body)
        val start = body.indexOf(url)
        if (start >= 0) {
            val end = start + url.length
            addStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline), start, end)
            addLink(LinkAnnotation.Url(url), start, end)
        }
    }
}

/**
 * Single-page grant surface in the DataBackup spirit: intro card, required
 * root rows, live grant log and tap-to-grant optional rows. No step wizard,
 * no stacked system pages.
 */
@Composable
private fun PermissionSinglePage(
    permissionInfos: List<PermissionInfo>,
    permissionStates: Map<Int, Boolean>,
    rootAccessSnapshot: ManagerRootAccessSnapshot,
    grantLog: List<GrantLogEntry>,
    grantingAll: Boolean,
    processingKeys: Set<String>,
    onRequestPermission: (PermissionInfo) -> Unit,
    onRequestRootAccess: (ManagerRootTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val foregroundGroupCovered = permissionStates.any { (index, granted) ->
        granted && (
            permissionInfos.getOrNull(index) is UsageStatsPermissionInfo ||
                permissionInfos.getOrNull(index) is AccessibilityPermissionInfo
            )
    }
    val rootRows = listOf(rootAccessSnapshot.runtime, rootAccessSnapshot.manager)
    val grantedCount = rootRows.count { it.isGranted } +
        permissionInfos.indices.count { isPermissionRequirementSatisfied(it, permissionInfos, permissionStates) }
    val totalCount = rootRows.size + permissionInfos.size
    val outstandingCount = totalCount - grantedCount
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OnboardingInfoCard(
                title = stringResource(R.string.app_name),
                summary = wizardIntroSummary(),
            )
        }
        item {
            OnboardingSummaryHeader(
                title = stringResource(R.string.wizard_report_title),
                grantedText = stringResource(R.string.wizard_report_granted, grantedCount, totalCount),
                missingText = if (outstandingCount > 0) {
                    stringResource(R.string.wizard_report_missing, outstandingCount)
                } else {
                    null
                },
            )
        }
        item {
            SectionHeading(title = stringResource(R.string.wizard_section_required))
        }
        item {
            val status = rootAccessSnapshot.runtime
            val processing = isRootProcessing(processingKeys, ManagerRootTarget.RUNTIME)
            OnboardingActionRowCard(
                label = stringResource(R.string.wizard_root_runtime_title),
                desc = stringResource(R.string.wizard_root_runtime_summary),
                actionText = rootStatusText(state = status.state, processing = processing),
                state = onboardingItemState(processing = processing, granted = status.isGranted),
                onClick = {
                    if (!status.isGranted) onRequestRootAccess(ManagerRootTarget.RUNTIME)
                },
            )
        }
        item {
            val status = rootAccessSnapshot.manager
            val processing = isRootProcessing(processingKeys, ManagerRootTarget.MANAGER)
            OnboardingActionRowCard(
                label = stringResource(R.string.wizard_root_manager_title),
                desc = stringResource(R.string.wizard_root_manager_summary),
                actionText = rootStatusText(state = status.state, processing = processing),
                state = onboardingItemState(processing = processing, granted = status.isGranted),
                onClick = {
                    if (!status.isGranted) onRequestRootAccess(ManagerRootTarget.MANAGER)
                },
            )
        }
        if (grantLog.isNotEmpty()) {
            item {
                OnboardingLogCard(
                    lines = grantLog.map { entry ->
                        val resId = when {
                            entry.isRoot && entry.outcome == GrantOutcome.GRANTED ->
                                R.string.wizard_root_permission_target_granted_toast

                            entry.isRoot && entry.outcome == GrantOutcome.UNAVAILABLE ->
                                R.string.wizard_root_permission_target_unavailable_toast

                            entry.isRoot -> R.string.wizard_root_permission_target_denied_toast
                            entry.outcome == GrantOutcome.GRANTED -> R.string.wizard_log_granted
                            else -> R.string.wizard_log_denied
                        }
                        stringResource(resId, entry.title)
                    },
                )
            }
        }
        item {
            SectionHeading(title = stringResource(R.string.wizard_section_optional))
        }
        itemsIndexed(permissionInfos) { index, info ->
            val granted = isPermissionRequirementSatisfied(index, permissionInfos, permissionStates)
            val processing = grantingAll && !granted || info.permissionTitle in processingKeys
            val desc = if (
                foregroundGroupCovered &&
                (info is UsageStatsPermissionInfo || info is AccessibilityPermissionInfo)
            ) {
                info.permissionDescription + " " + stringResource(R.string.wizard_descr_foreground_covered)
            } else {
                info.permissionDescription
            }
            OnboardingActionRowCard(
                label = info.permissionTitle,
                desc = desc,
                actionText = when {
                    processing -> stringResource(R.string.wizard_status_processing)
                    granted -> stringResource(R.string.status_granted)
                    else -> stringResource(R.string.status_pending)
                },
                state = onboardingItemState(processing = processing, granted = granted),
                onClick = { if (!granted) onRequestPermission(info) },
            )
        }
    }
}

@Composable
private fun rootStatusText(state: ManagerRootAccessState, processing: Boolean): String = when {
    processing -> stringResource(R.string.wizard_status_processing)
    state == ManagerRootAccessState.GRANTED -> stringResource(R.string.wizard_root_status_granted)
    state == ManagerRootAccessState.UNAVAILABLE -> stringResource(R.string.wizard_root_status_unavailable)
    else -> stringResource(R.string.wizard_root_status_not_granted)
}

private fun isRootProcessing(processingKeys: Set<String>, target: ManagerRootTarget): Boolean =
    "root_${target.name.lowercase()}" in processingKeys

private fun onboardingItemState(processing: Boolean, granted: Boolean): OnboardingItemState = when {
    processing -> OnboardingItemState.Pending
    granted -> OnboardingItemState.Granted
    else -> OnboardingItemState.Outstanding
}

private fun ManagerRootAccessState.toGrantOutcome(): GrantOutcome = when (this) {
    ManagerRootAccessState.GRANTED -> GrantOutcome.GRANTED
    ManagerRootAccessState.NOT_GRANTED -> GrantOutcome.DENIED
    ManagerRootAccessState.UNAVAILABLE -> GrantOutcome.UNAVAILABLE
}

private fun rootToastResId(state: ManagerRootAccessState): Int = when (state) {
    ManagerRootAccessState.GRANTED -> R.string.wizard_root_permission_target_granted_toast
    ManagerRootAccessState.NOT_GRANTED -> R.string.wizard_root_permission_target_denied_toast
    ManagerRootAccessState.UNAVAILABLE -> R.string.wizard_root_permission_target_unavailable_toast
}

private fun showToast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

private fun getPermissionInfos(context: Context): List<PermissionInfo> {
    val pages = mutableListOf<PermissionInfo>().apply {
        add(UsageStatsPermissionInfo(context))
        add(AccessibilityPermissionInfo(context))
        add(RequestIgnoreBatteryOptimizationsPermissionInfo(context))
        add(AlertWindowPermissionInfo(context))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(NotificationPermissionInfo(context))
        }
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
