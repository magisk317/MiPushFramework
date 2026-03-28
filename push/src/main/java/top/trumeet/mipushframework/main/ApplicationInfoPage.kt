package top.trumeet.mipushframework.main

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.magisk317.compat.RegistrationStateCompat
import com.magisk317.compat.RegistrationStateStore
import com.magisk317.utils.RegistrationHelper
import com.topjohnwu.superuser.Shell
import com.xiaomi.xmsf.BuildConfig
import com.xiaomi.xmsf.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.trumeet.common.utils.Utils
import top.trumeet.mipush.provider.db.RegisteredApplicationDb
import top.trumeet.mipush.provider.entities.Event
import top.trumeet.mipush.provider.entities.RegisteredApplication
import top.trumeet.mipush.provider.entities.RegisteredApplication.RegisteredType
import top.trumeet.mipushframework.component.AppIcon
import top.trumeet.mipushframework.component.ActionStrip
import top.trumeet.mipushframework.component.DetailDivider
import top.trumeet.mipushframework.component.DetailSectionCard
import top.trumeet.mipushframework.component.DialogAction
import top.trumeet.mipushframework.component.DialogActionRow
import top.trumeet.mipushframework.component.ExpressiveHeroCard
import top.trumeet.mipushframework.component.InfoPill
import top.trumeet.mipushframework.component.LabelValueBlock
import top.trumeet.mipushframework.component.MarkdownView
import top.trumeet.mipushframework.component.MetricCard
import top.trumeet.mipushframework.component.MetricGrid
import top.trumeet.mipushframework.component.MetricSpec
import top.trumeet.mipushframework.component.SectionColumn
import top.trumeet.mipushframework.component.SettingsDialogItem
import top.trumeet.mipushframework.wizard.WizardSPUtils
import top.trumeet.ui.theme.Theme
import top.trumeet.ui.theme.spacing
import java.util.Date

class ApplicationInfoPage : ComponentActivity() {
    companion object {
        const val EXTRA_PACKAGE_NAME: String = "EXTRA_PACKAGE_NAME"
        const val EXTRA_IGNORE_NOT_REGISTERED: String = "EXTRA_IGNORE_NOT_REGISTERED"
    }

    private lateinit var applicationInfo: RegisteredApplication
    private lateinit var appConfigurationUtils: AppConfigurationUtils

    fun init(applicationInfo: RegisteredApplication) {
        this.applicationInfo = applicationInfo
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = getRegisteredApplication()
        if (app == null) {
            finish()
            return
        }
        init(app)
        setContent {
            Theme {
                SettingsApp()
            }
        }
    }

    private fun getRegisteredApplication(): RegisteredApplication? {
        if (intent.hasExtra(EXTRA_PACKAGE_NAME)) {
            val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return null
            var application = RegisteredApplicationDb.getRegisteredApplication(pkg)

            if (application == null &&
                intent.getBooleanExtra(EXTRA_IGNORE_NOT_REGISTERED, false)
            ) {
                application = RegisteredApplication()
                application.packageName = pkg
                application.registeredType = RegisteredType.NotRegistered
                application.appName = com.magisk317.Global.ApplicationNameCache()
                    .getAppName(this, pkg).toString()
            }
            if (
                application != null &&
                application.registeredType == RegisteredType.NotRegistered &&
                RegistrationStateCompat.hasValidLocalRegistration(pkg)
            ) {
                RegistrationStateStore.updateIfChanged(
                    application = application,
                    nextType = RegisteredType.Registered,
                    source = RegistrationStateStore.Source.LOCAL_PROBE,
                )
            }
            return application
        }
        return null
    }

    @Composable
    fun SettingsApp() {
        if (!::appConfigurationUtils.isInitialized) {
            appConfigurationUtils = AppConfigurationUtils(
                LocalContext.current,
                applicationInfo,
            )
        }

        Theme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                SectionColumn(
                    modifier = Modifier
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = MaterialTheme.spacing.medium),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        top = MaterialTheme.spacing.medium,
                        bottom = MaterialTheme.spacing.medium,
                    ),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                ) {
                    SettingsScreen()
                }
            }
        }
    }

    @Composable
    fun SettingsScreen() {
        ApplicationInfoHeader()
        RegistrationDiagnosticsCard()
        RegistrationActionsCard()
        TipsCard()
        ActivitySectionCard()
        NotificationSection()
    }

    @Composable
    private fun ApplicationInfoHeader() {
        val context = LocalContext.current
        val registrationState = remember(
            applicationInfo.packageName,
            applicationInfo.registeredType,
            applicationInfo.lastReceiveTime.time,
        ) {
            RegistrationStateStyle.contentOf(applicationInfo, context)
        }
        val typeLabel = stringResource(registrationTypeLabelRes(applicationInfo.registrationTypeReason))
        val lastPush = formatTime(applicationInfo.lastReceiveTime.time)
        val uidText = applicationInfo.getUid(context).takeIf { it >= 0 }?.toString() ?: "-"
        val activityState = if (applicationInfo.lastReceiveTime.time > 0L) {
            stringResource(R.string.app_list_item_delivery_active)
        } else {
            stringResource(R.string.app_list_item_delivery_idle)
        }

        ExpressiveHeroCard(
            title = applicationInfo.appName,
            subtitle = applicationInfo.packageName,
        ) {
            Column(
                modifier = Modifier.padding(
                    start = MaterialTheme.spacing.large,
                    end = MaterialTheme.spacing.large,
                    bottom = MaterialTheme.spacing.large,
                ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(
                        packageName = applicationInfo.packageName,
                        appName = applicationInfo.appName,
                        modifier = Modifier.size(72.dp),
                    )
                    Spacer(Modifier.width(MaterialTheme.spacing.medium))
                    Text(
                        text = stringResource(R.string.app_detail_identity_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                ) {
                    InfoPill(
                        text = registrationState.first,
                        containerColor = registrationState.second
                            .takeIf { it != Color.Unspecified }
                            ?.copy(alpha = 0.16f)
                            ?: MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = registrationState.second
                            .takeIf { it != Color.Unspecified }
                            ?: MaterialTheme.colorScheme.onSurface,
                    )
                    InfoPill(
                        text = context.getString(R.string.app_registration_type_format, typeLabel),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }

                MetricGrid(
                    metrics = listOf(
                        MetricSpec(
                            label = stringResource(R.string.app_detail_uid),
                            value = uidText,
                            accent = MaterialTheme.colorScheme.primary,
                        ),
                        MetricSpec(
                            label = stringResource(R.string.app_detail_last_push),
                            value = lastPush,
                            accent = MaterialTheme.colorScheme.secondary,
                        ),
                        MetricSpec(
                            label = stringResource(R.string.app_detail_registered_state),
                            value = activityState,
                            accent = if (applicationInfo.lastReceiveTime.time > 0L) {
                                RegistrationStateStyle.GreenColor
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            },
                        ),
                    ),
                )

                ActionStrip {
                    FilledTonalButton(
                        onClick = {
                            launchTargetAppAndForceRegister(context, applicationInfo.packageName)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.app_detail_force_register))
                    }
                    OutlinedButton(
                        onClick = { openSystemAppInfo(context) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.app_detail_open_system_settings))
                    }
                }
            }
        }
    }

    private fun openSystemAppInfo(context: Context) {
        val uri = Uri.fromParts("package", applicationInfo.packageName, null)
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun launchTargetAppAndForceRegister(context: Context, packageName: String) {
        val plan = RegistrationHelper.inspectForceRegisterPlan(packageName)
        if (!plan.supportsServiceDispatch) {
            Toast.makeText(context, R.string.force_register_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        stopTargetAppBestEffort(packageName)
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            Toast.makeText(context, R.string.force_register_failed, Toast.LENGTH_LONG).show()
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching { context.startActivity(launchIntent) }
            .onFailure {
                Toast.makeText(context, R.string.force_register_failed, Toast.LENGTH_LONG).show()
                return
            }
        forceRegisterWithFeedback(context, packageName)
    }

    private fun stopTargetAppBestEffort(packageName: String) {
        runCatching {
            Shell.cmd("am force-stop $packageName").exec()
        }
    }

    private fun forceRegisterWithFeedback(context: Context, packageName: String) {
        val uid = runCatching { Shell.cmd("id -u").exec().out.firstOrNull()?.trim() }.getOrNull()
        if (uid != "0") {
            Toast.makeText(context, R.string.force_register_requires_root, Toast.LENGTH_LONG).show()
            return
        }
        val result = runCatching {
            RegistrationHelper.tryForceRegister(packageName)
        }
        if (result.isSuccess) {
            Toast.makeText(context, R.string.force_register_sent, Toast.LENGTH_SHORT).show()
            return
        }
        val cause = result.exceptionOrNull()
        if (cause is NoClassDefFoundError || cause is ClassNotFoundException || cause is UnsupportedOperationException) {
            Toast.makeText(context, R.string.force_register_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        if (RegistrationHelper.tryForceRegisterFallback(packageName)) {
            Toast.makeText(context, R.string.force_register_sent, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, R.string.force_register_failed, Toast.LENGTH_LONG).show()
        }
    }

    @Composable
    private fun RegistrationDiagnosticsCard() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var diagnostics by remember(applicationInfo.packageName) { mutableStateOf<AppRegistrationDiagnostics?>(null) }

        fun refreshDiagnostics() {
            scope.launch(Dispatchers.IO) {
                val loaded = AppRegistrationDiagnosticsHelper.load(
                    context = context,
                    packageName = applicationInfo.packageName,
                    registeredType = applicationInfo.registeredType,
                )
                withContext(Dispatchers.Main) {
                    diagnostics = loaded
                }
            }
        }

        LaunchedEffect(applicationInfo.packageName) {
            refreshDiagnostics()
        }

        DetailSectionCard(
            title = stringResource(R.string.registration_diagnostics_group),
            summary = stringResource(R.string.registration_diagnostics_refresh_summary),
        ) {
            val info = diagnostics
            if (info == null) {
                Text(
                    text = stringResource(R.string.registration_diagnostics_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(MaterialTheme.spacing.large),
                )
                return@DetailSectionCard
            }

            AppDetailValueRow(
                label = stringResource(R.string.registration_diagnostics_display_type),
                value = stringResource(registrationTypeLabelRes(info.displayTypeReason)),
            )
            AppDetailValueRow(
                label = stringResource(R.string.registration_diagnostics_force_type),
                value = stringResource(registrationTypeLabelRes(info.forceTypeReason)),
            )
            AppDetailValueRow(
                label = stringResource(R.string.registration_diagnostics_components),
                value = context.getString(
                    R.string.registration_diagnostics_components_value,
                    info.hasRuntimeService.toFlagValue(),
                    info.hasHandlerService.toFlagValue(),
                    info.hasOfficialReceiver.toFlagValue(),
                    info.hasBridgeComponent.toFlagValue(),
                    info.hasLauncherEntry.toFlagValue(),
                ),
            )
            AppDetailValueRow(
                label = stringResource(R.string.registration_diagnostics_local_state),
                value = context.getString(
                    R.string.registration_diagnostics_local_state_value,
                    info.hasLocalRegistration.toFlagValue(),
                    info.regSecCount,
                    formatTime(info.lastReceiveTime),
                ),
            )
            AppDetailValueRow(
                label = stringResource(R.string.registration_diagnostics_recent_event),
                value = formatRecentRegistrationEvent(context, info),
            )
            AppDetailValueRow(
                label = stringResource(R.string.registration_diagnostics_inference),
                value = stringResource(registrationInferenceLabelRes(info.inferenceReason)),
                showDivider = false,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.spacing.large),
                contentAlignment = Alignment.CenterEnd,
            ) {
                OutlinedButton(onClick = { refreshDiagnostics() }) {
                    Text(stringResource(R.string.action_update))
                }
            }
        }
    }

    @Composable
    private fun RegistrationActionsCard() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        DetailSectionCard(
            title = stringResource(R.string.registration_actions_group),
            summary = stringResource(R.string.registration_action_launch_observe_summary),
        ) {
            ActionSummaryRow(
                title = stringResource(R.string.registration_action_launch_observe),
                summary = stringResource(R.string.registration_action_launch_observe_summary),
                actionLabel = stringResource(R.string.registration_action_launch_observe),
            ) {
                scope.launch(Dispatchers.IO) {
                    val packageName = applicationInfo.packageName
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                    if (launchIntent == null) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                R.string.registration_action_launch_observe_no_launcher,
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                        return@launch
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            R.string.registration_action_launch_observe_started,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    stopTargetAppBestEffort(packageName)
                    val diagnostics = AppRegistrationDiagnosticsHelper.launchAndObserve(
                        context = context,
                        packageName = packageName,
                        registeredTypeProvider = {
                            RegisteredApplicationDb.getRegisteredApplication(packageName)?.registeredType
                                ?: applicationInfo.registeredType
                        },
                    )
                    withContext(Dispatchers.Main) {
                        val resultText = if (
                            diagnostics.registeredType == RegisteredType.Registered ||
                            diagnostics.hasLocalRegistration ||
                            diagnostics.latestRegistrationEventDate != null
                        ) {
                            context.getString(
                                R.string.registration_action_launch_observe_result,
                                context.getString(registrationInferenceLabelRes(diagnostics.inferenceReason)),
                            )
                        } else {
                            context.getString(R.string.registration_action_launch_observe_timeout)
                        }
                        Toast.makeText(context, resultText, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    @Composable
    private fun TipsCard() {
        val shouldSuggestFakeApp = appConfigurationUtils.shouldSuggestFakeApp(applicationInfo.packageName)
        val registeredType = applicationInfo.registeredType
        if (registeredType != RegisteredType.NotRegistered && registeredType != RegisteredType.Unregistered) {
            return
        }

        val title: String
        val description: String
        if (registeredType == RegisteredType.NotRegistered) {
            title = stringResource(R.string.status_app_not_registered_title)
            description = stringResource(
                if (shouldSuggestFakeApp) {
                    R.string.status_app_not_registered_detail_with_fake_suggest
                } else {
                    R.string.status_app_not_registered_detail_without_fake_suggest
                },
            )
        } else {
            title = stringResource(R.string.status_app_registered_error_title)
            description = stringResource(R.string.status_app_registered_error_desc)
        }

        DetailSectionCard(
            title = title,
            summary = stringResource(R.string.registration_diagnostics_group),
        ) {
            Tips(description = description)
        }
    }

    @Composable
    private fun ActivitySectionCard() {
        var checked by remember { mutableStateOf(applicationInfo.notificationOnRegister) }

        DetailSectionCard(
            title = stringResource(R.string.app_detail_activity_and_behavior),
        ) {
            ActionSummaryRow(
                title = stringResource(R.string.recent_activity_view),
                summary = stringResource(R.string.app_detail_recent_activity_summary),
                actionLabel = stringResource(R.string.recent_activity_view),
                showDivider = true,
            ) {
                appConfigurationUtils.gotoRecentEventsPage()
            }

            SettingSwitchRow(
                title = stringResource(R.string.permission_notification_on_register),
                summary = stringResource(R.string.permission_summary_notification_on_register),
                checked = checked,
                showDivider = false,
            ) {
                checked = it
                applicationInfo.notificationOnRegister = checked
            }
        }
    }

    @Composable
    private fun NotificationSection() {
        val isPreview = LocalInspectionMode.current

        DetailSectionCard(
            title = stringResource(R.string.app_detail_notifications),
            summary = stringResource(R.string.settings_manage_app_notifications_summary),
        ) {
            if (Build.VERSION.SDK_INT >= VERSION_CODES.O && !isPreview) {
                NotificationChannelGroups()
            } else {
                ActionSummaryRow(
                    title = stringResource(R.string.settings_manage_app_notifications),
                    summary = stringResource(R.string.settings_manage_app_notifications_summary),
                    actionLabel = stringResource(R.string.settings_manage_app_notifications),
                ) {
                    appConfigurationUtils.gotoNotificationSettingPage()
                }
            }
        }
    }

    @RequiresApi(VERSION_CODES.O)
    @Composable
    private fun NotificationChannelGroups() {
        val isPreview = LocalInspectionMode.current
        val groups: List<NotificationChannelGroup>
        val notificationChannels: List<NotificationChannel>

        if (isPreview) {
            groups = emptyList()
            notificationChannels = emptyList()
        } else {
            groups = appConfigurationUtils.notificationChannelGroups
            notificationChannels = appConfigurationUtils.notificationChannels ?: emptyList()
        }

        if (groups.isEmpty()) {
            ActionSummaryRow(
                title = stringResource(R.string.settings_manage_app_notifications),
                summary = stringResource(R.string.settings_manage_app_notifications_summary),
                actionLabel = stringResource(R.string.settings_manage_app_notifications),
                enabled = applicationInfo.registeredType == RegisteredType.NotRegistered,
            ) {
                appConfigurationUtils.gotoNotificationSettingPage()
            }
            return
        }

        groups.forEach { group ->
            val categoryName = appConfigurationUtils.getNotificationCategoryName(group)
            NotificationCategoryCard(
                categoryName = categoryName,
                channels = notificationChannels.filter { it.group == group.id },
            )
        }
    }

    @Composable
    private fun NotificationCategoryCard(categoryName: String, channels: List<NotificationChannel>) {
        DetailSectionCard(
            title = categoryName,
            summary = stringResource(R.string.notification_channels_group_summary),
        ) {
            channels.forEach { channel ->
                var shouldShowDialog by remember { mutableStateOf(false) }
                SettingsDialogItem(
                    title = AppConfigurationUtils.getNotificationTitle(channel).toString(),
                    summary = AppConfigurationUtils.getNotificationSummary(channel),
                    confirmButton = {},
                    actions = listOf(
                        DialogAction(
                            label = stringResource(android.R.string.ok),
                            onClick = { shouldShowDialog = false },
                        ),
                    ),
                    onClick = { shouldShowDialog = true },
                    shouldShowDialog = shouldShowDialog,
                    onDismiss = { shouldShowDialog = false },
                    content = {
                        NotificationChannelDialog(channel, appConfigurationUtils)
                    },
                )
            }
        }
    }
}

@Composable
private fun ActionSummaryRow(
    title: String,
    summary: String,
    actionLabel: String,
    enabled: Boolean = true,
    showDivider: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(MaterialTheme.spacing.medium))
        FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
        ) {
            Text(actionLabel)
        }
    }
    if (showDivider) {
        DetailDivider()
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    showDivider: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(MaterialTheme.spacing.medium))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
    if (showDivider) {
        DetailDivider()
    }
}

@Composable
private fun AppDetailValueRow(
    label: String,
    value: String,
    showDivider: Boolean = true,
) {
    LabelValueBlock(
        label = label,
        value = value,
    )
    if (showDivider) {
        DetailDivider()
    }
}

private fun Boolean.toFlagValue(): String = if (this) "Y" else "N"

private fun formatTime(time: Long?): String {
    if (time == null || time <= 0L) return "-"
    return Date(time).toString()
}

private fun formatRecentRegistrationEvent(context: Context, diagnostics: AppRegistrationDiagnostics): String {
    val label = when (diagnostics.latestRegistrationEventType) {
        Event.Type.Registration -> context.getString(R.string.registration_event_registration)
        Event.Type.RegistrationResult -> if (diagnostics.latestRegistrationEventResult == Event.ResultType.OK) {
            context.getString(R.string.registration_event_registration_result_ok)
        } else {
            context.getString(R.string.registration_event_registration_result_failed)
        }
        Event.Type.UnRegistration -> context.getString(R.string.registration_event_unregistration)
        else -> context.getString(R.string.registration_event_none)
    }
    val date = formatTime(diagnostics.latestRegistrationEventDate)
    return if (date == "-") label else "$label @ $date"
}

private fun registrationTypeLabelRes(reason: String): Int {
    return when (reason) {
        "direct_sdk" -> R.string.registration_type_direct_sdk
        "receiver_only" -> R.string.registration_type_receiver_only
        "bridge_wrapper" -> R.string.registration_type_bridge_wrapper
        "unsupported_components" -> R.string.registration_type_unsupported_components
        "package_not_found" -> R.string.registration_type_package_not_found
        "application_unavailable" -> R.string.registration_type_application_unavailable
        else -> R.string.registration_type_unknown
    }
}

private fun registrationInferenceLabelRes(reason: String): Int {
    return when (reason) {
        "registered" -> R.string.registration_inference_registered
        "never_attempted" -> R.string.registration_inference_never_attempted
        "unregistered_after_attempt" -> R.string.registration_inference_unregistered_after_attempt
        "registration_result_failed" -> R.string.registration_inference_registration_result_failed
        "registering_or_waiting_result" -> R.string.registration_inference_registering_or_waiting_result
        "local_state_stale" -> R.string.registration_inference_local_state_stale
        "has_secret_but_no_local_reg" -> R.string.registration_inference_has_secret_but_no_local_reg
        else -> R.string.registration_inference_unknown
    }
}

@Composable
private fun Tips(description: String) {
    Row(
        modifier = Modifier.padding(MaterialTheme.spacing.large),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    color = MaterialTheme.colorScheme.error,
                    shape = RoundedCornerShape(999.dp),
                ),
        )
        Spacer(Modifier.width(MaterialTheme.spacing.medium))
        MarkdownView(
            description,
            textSize = MaterialTheme.typography.bodyMedium.fontSize.value,
        )
    }
}

@Composable
private fun NotificationChannelDialog(
    channel: NotificationChannel,
    appConfigurationUtils: AppConfigurationUtils,
) {
    DialogActionRow(
        actions = listOf(
            DialogAction(
                label = stringResource(R.string.notification_channels_delete),
                onClick = { appConfigurationUtils.deleteNotificationChannel(channel) },
            ),
            DialogAction(
                label = stringResource(R.string.notification_channels_copy_id),
                onClick = { appConfigurationUtils.copyToClipboard(channel) },
            ),
            DialogAction(
                label = stringResource(R.string.notification_channels_setting),
                onClick = {
                    appConfigurationUtils.gotoNotificationChannelSettingPage(
                        channel,
                        appConfigurationUtils.configApp,
                    )
                },
            ),
        ),
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsPreview() {
    val context = LocalContext.current
    Utils.context = context

    val app = RegisteredApplication()
    app.packageName = BuildConfig.APPLICATION_ID
    app.appName = "test app"
    val page = ApplicationInfoPage()
    page.init(app)
    page.SettingsApp()
}
