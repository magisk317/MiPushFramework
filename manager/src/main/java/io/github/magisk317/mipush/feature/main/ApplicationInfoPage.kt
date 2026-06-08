package io.github.magisk317.mipush.feature.main

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.magisk317.mipush.app.di.ManagerGatewayAccess
import io.github.magisk317.mipush.common.manager.ManagerApplication
import io.github.magisk317.mipush.common.manager.ManagerApplicationDiagnostics
import io.github.magisk317.mipush.common.manager.ManagerApplicationGateway
import io.github.magisk317.mipush.common.manager.ManagerConfigSyncGateway
import io.github.magisk317.mipush.manager.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.feature.ui.component.AppIcon
import io.github.magisk317.mipush.feature.ui.component.DetailDivider
import io.github.magisk317.mipush.feature.ui.component.DetailSectionCard
import io.github.magisk317.mipush.feature.ui.component.DialogAction
import io.github.magisk317.mipush.feature.ui.component.DialogActionRow
import io.github.magisk317.mipush.feature.ui.component.MarkdownView
import io.github.magisk317.mipush.feature.ui.component.SectionColumn
import io.github.magisk317.mipush.feature.ui.component.SettingsItem
import io.github.magisk317.mipush.feature.wizard.support.WizardSPUtils
import io.github.magisk317.mipush.feature.ui.theme.Theme
import io.github.magisk317.mipush.feature.ui.theme.spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

open class ApplicationInfoPage : ComponentActivity() {
    companion object {
        const val EXTRA_PACKAGE_NAME: String = "EXTRA_PACKAGE_NAME"
        const val EXTRA_IGNORE_NOT_REGISTERED: String = "EXTRA_IGNORE_NOT_REGISTERED"
    }

    private val applicationGateway: ManagerApplicationGateway
        get() = ManagerGatewayAccess.get()
    private val configSyncGateway: ManagerConfigSyncGateway
        get() = ManagerGatewayAccess.get()

    private lateinit var applicationInfo: ManagerApplication
    private lateinit var appConfigurationUtils: AppConfigurationUtils

    fun init(applicationInfo: ManagerApplication) {
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

    private fun getRegisteredApplication(): ManagerApplication? {
        if (!intent.hasExtra(EXTRA_PACKAGE_NAME)) return null
        val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return null
        return applicationGateway.getApplication(
            context = this,
            packageName = pkg,
            ignoreNotRegistered = intent.getBooleanExtra(EXTRA_IGNORE_NOT_REGISTERED, false),
        )
    }

    @Composable
    fun SettingsApp() {
        if (!::appConfigurationUtils.isInitialized) {
            appConfigurationUtils = AppConfigurationUtils(
                LocalContext.current,
                applicationInfo,
            )
        }

        val snackbarHostState = remember { SnackbarHostState() }

        Theme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    SectionColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = MaterialTheme.spacing.medium),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            top = topInset + MaterialTheme.spacing.medium,
                            bottom = bottomInset + MaterialTheme.spacing.medium,
                        ),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                    ) {
                        SettingsScreen(snackbarHostState)
                    }
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }

    @Composable
    fun SettingsScreen(snackbarHostState: SnackbarHostState) {
        ApplicationInfoHeader(snackbarHostState)
        TipsCard()
        ActivitySectionCard(snackbarHostState)
        IslandDisplaySection(snackbarHostState)
        NotificationSection()
    }

    @Composable
    private fun rememberSwitchFeedback(snackbarHostState: SnackbarHostState): (String, Boolean) -> Unit {
        val scope = rememberCoroutineScope()
        val enabledTemplate = stringResource(R.string.settings_switch_enabled_feedback)
        val disabledTemplate = stringResource(R.string.settings_switch_disabled_feedback)
        return remember(snackbarHostState, scope, enabledTemplate, disabledTemplate) {
            { title, enabled ->
                val template = if (enabled) enabledTemplate else disabledTemplate
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(
                        message = String.format(Locale.getDefault(), template, title),
                        duration = SnackbarDuration.Short,
                    )
                }
            }
        }
    }

    @Composable
    private fun ApplicationInfoHeader(snackbarHostState: SnackbarHostState) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val integrationType = remember(applicationInfo.packageName) {
            applicationGateway.loadIntegrationTypeReason(context, applicationInfo.packageName)
        }
        val integrationTypeLabel = registrationTypeShortLabel(integrationType)
        val serviceState = if (applicationInfo.existServices) {
            stringResource(R.string.app_detail_service_ready)
        } else {
            stringResource(R.string.mipush_services_not_found)
        }
        val registrationValue = stringResource(RegistrationStateStyle.registrationLabelResOf(applicationInfo))
        val lastPush = formatTime(applicationInfo.lastReceiveTimeMs)

        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f),
                                ),
                            ),
                        ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MaterialTheme.spacing.extraLarge),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                    ) {
                        AppIcon(
                            packageName = applicationInfo.packageName,
                            appName = applicationInfo.appName,
                            modifier = Modifier.size(52.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = applicationInfo.appName,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                            Text(
                                text = applicationInfo.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.padding(
                        start = MaterialTheme.spacing.large,
                        top = MaterialTheme.spacing.large,
                        end = MaterialTheme.spacing.large,
                        bottom = MaterialTheme.spacing.large,
                    ),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                    ) {
                        HeaderMetricCard(
                            label = stringResource(R.string.app_detail_service_status),
                            value = serviceState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            accent = if (applicationInfo.existServices) {
                                RegistrationStateStyle.GreenColor
                            } else {
                                RegistrationStateStyle.ErrorColor
                            },
                        )
                        HeaderMetricCard(
                            label = stringResource(R.string.app_detail_integration_type),
                            value = integrationTypeLabel,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            accent = if (applicationInfo.existServices) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                RegistrationStateStyle.ErrorColor
                            },
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                    ) {
                        HeaderMetricCard(
                            label = stringResource(R.string.app_detail_last_push),
                            value = lastPush,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            accent = MaterialTheme.colorScheme.secondary,
                        )
                        HeaderMetricCard(
                            label = stringResource(R.string.app_detail_registration_status),
                            value = registrationValue,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            accent = RegistrationStateStyle.registrationColorOf(applicationInfo)
                                .takeIf { it != Color.Unspecified }
                                ?: MaterialTheme.colorScheme.tertiary,
                        )
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(
                            MaterialTheme.spacing.small,
                            Alignment.CenterHorizontally,
                        ),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                    ) {
                        FilledTonalButton(
                            onClick = {
                                launchTargetAppAndForceRegister(
                                    context,
                                    applicationInfo.packageName,
                                    snackbarHostState,
                                )
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.app_detail_force_register),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        OutlinedButton(
                            onClick = { openSystemAppInfo(context) },
                        ) {
                            Text(
                                text = stringResource(R.string.app_detail_open_system_settings),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    configSyncGateway.openForPackage(applicationInfo.packageName)
                                }
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.main_configs),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
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

    private fun launchTargetAppAndForceRegister(
        context: Context,
        packageName: String,
        snackbarHostState: SnackbarHostState,
    ) {
        lifecycleScope.launch {
            val message = withContext(Dispatchers.IO) {
                applicationGateway.launchTargetAppAndForceRegister(
                    context = context,
                    packageName = packageName,
                    registeredType = applicationInfo.registeredType,
                )
            }
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short,
            )
        }
    }

    @Composable
    private fun TipsCard() {
        val context = LocalContext.current
        val shouldSuggestFakeApp = appConfigurationUtils.shouldSuggestFakeApp(applicationInfo.packageName)
        val registeredType = applicationInfo.registeredType
        if (
            registeredType != ManagerApplication.RegisteredType.NOT_REGISTERED &&
            registeredType != ManagerApplication.RegisteredType.UNREGISTERED
        ) {
            return
        }
        var diagnostics by remember(applicationInfo.packageName, registeredType) {
            mutableStateOf<ManagerApplicationDiagnostics?>(null)
        }
        LaunchedEffect(applicationInfo.packageName, registeredType) {
            diagnostics = withContext(Dispatchers.IO) {
                AppRegistrationDiagnosticsHelper.load(
                    packageName = applicationInfo.packageName,
                    registeredType = registeredType,
                )
            }
        }
        val shouldSuggestResetprop = shouldSuggestFakeApp && diagnostics?.inferenceReason in setOf(
            "unregistered_after_attempt",
            "registration_result_failed",
            "local_state_stale",
            "has_secret_but_no_local_reg",
        )

        val title: String
        val description: String
        if (registeredType == ManagerApplication.RegisteredType.NOT_REGISTERED) {
            title = stringResource(R.string.status_app_not_registered_title)
            description = stringResource(
                if (shouldSuggestResetprop) {
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
            summary = stringResource(R.string.app_detail_registration_tip_summary),
        ) {
            Tips(description = description)
        }
    }

    @Composable
    private fun ActivitySectionCard(snackbarHostState: SnackbarHostState) {
        val showSwitchFeedback = rememberSwitchFeedback(snackbarHostState)
        var blocked by remember { mutableStateOf(applicationInfo.blocked) }

        DetailSectionCard(
            title = stringResource(R.string.app_detail_activity_and_behavior),
        ) {
            val blockTitle = stringResource(R.string.app_detail_block)
            SettingSwitchRow(
                title = blockTitle,
                summary = stringResource(R.string.app_detail_block_summary),
                checked = blocked,
                showDivider = true,
            ) { enabled ->
                blocked = enabled
                applicationInfo = applicationInfo.copy(blocked = blocked)
                applicationGateway.updateApplication(applicationInfo)
                showSwitchFeedback(blockTitle, enabled)
            }

            ActionSummaryRow(
                title = stringResource(R.string.recent_activity_view),
                summary = stringResource(R.string.app_detail_recent_activity_summary),
                actionLabel = stringResource(R.string.recent_activity_view),
                showDivider = true,
                enabled = !blocked,
            ) {
                appConfigurationUtils.gotoRecentEventsPage()
            }
        }
    }

    @Composable
    private fun IslandDisplaySection(snackbarHostState: SnackbarHostState) {
        val showSwitchFeedback = rememberSwitchFeedback(snackbarHostState)
        var islandEnabled by remember { mutableStateOf(applicationInfo.islandEnabled) }
        var islandFocusNotification by remember {
            mutableStateOf(applicationInfo.islandFocusNotification)
        }

        DetailSectionCard(
            title = stringResource(R.string.app_detail_island_controls),
            summary = stringResource(R.string.app_detail_island_controls_summary),
        ) {
            val islandEnabledTitle = stringResource(R.string.app_detail_island_enabled)
            SettingSwitchRow(
                title = islandEnabledTitle,
                summary = stringResource(R.string.app_detail_island_enabled_summary),
                checked = islandEnabled,
                showDivider = true,
            ) { enabled ->
                islandEnabled = enabled
                applicationInfo = applicationInfo.copy(islandEnabled = islandEnabled)
                applicationGateway.updateApplication(applicationInfo)
                showSwitchFeedback(islandEnabledTitle, enabled)
            }

            val islandFocusNotificationTitle = stringResource(R.string.app_detail_island_focus_notification)
            SettingSwitchRow(
                title = islandFocusNotificationTitle,
                summary = stringResource(R.string.app_detail_island_focus_notification_summary),
                checked = islandFocusNotification,
                enabled = islandEnabled,
            ) { enabled ->
                islandFocusNotification = enabled
                applicationInfo = applicationInfo.copy(islandFocusNotification = islandFocusNotification)
                applicationGateway.updateApplication(applicationInfo)
                showSwitchFeedback(islandFocusNotificationTitle, enabled)
            }
        }
    }

    @SuppressLint("LocalContextGetResourceValueCall")
    @Composable
    private fun NotificationSection() {
        val isPreview = LocalInspectionMode.current

        DetailSectionCard(
            title = stringResource(R.string.app_detail_notifications),
            summary = stringResource(R.string.settings_manage_app_notifications_summary),
        ) {
            if (!isPreview) {
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
                enabled = applicationInfo.registeredType == ManagerApplication.RegisteredType.NOT_REGISTERED,
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
                SettingsItem(
                    title = AppConfigurationUtils.getNotificationTitle(channel).toString(),
                    summary = AppConfigurationUtils.getNotificationSummary(channel),
                    onClick = { shouldShowDialog = true },
                )
                if (shouldShowDialog) {
                    AlertDialog(
                        onDismissRequest = { shouldShowDialog = false },
                        title = {
                            Text(AppConfigurationUtils.getNotificationTitle(channel).toString())
                        },
                        text = {
                            Text(AppConfigurationUtils.getNotificationSummary(channel))
                        },
                        confirmButton = {
                            DialogActionRow(
                                actions = listOf(
                                    DialogAction(
                                        label = stringResource(R.string.notification_channels_setting),
                                        onClick = {
                                            appConfigurationUtils.gotoNotificationChannelSettingPage(
                                                channel,
                                                appConfigurationUtils.configApp,
                                            )
                                            shouldShowDialog = false
                                        },
                                    ),
                                    DialogAction(
                                        label = stringResource(R.string.notification_channels_copy_id),
                                        onClick = {
                                            appConfigurationUtils.copyToClipboard(channel)
                                            shouldShowDialog = false
                                        },
                                    ),
                                    DialogAction(
                                        label = stringResource(R.string.notification_channels_delete),
                                        onClick = {
                                            appConfigurationUtils.deleteNotificationChannel(channel)
                                            shouldShowDialog = false
                                        },
                                        style = io.github.magisk317.mipush.feature.ui.component.DialogActionStyle.Danger,
                                    ),
                                ),
                            )
                        },
                    )
                }
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
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
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
    enabled: Boolean = true,
    showDivider: Boolean = false,
    onClickWhenDisabled: (() -> Unit)? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (!enabled && onClickWhenDisabled != null) {
                    Modifier.clickable { onClickWhenDisabled() }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
        }
        Spacer(Modifier.width(MaterialTheme.spacing.medium))
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled,
        )
    }
    if (showDivider) {
        DetailDivider()
    }
}

@Composable
private fun HeaderMetricCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(accent.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
            )
        }
    }
}

private fun formatTime(time: Long?): String {
    if (time == null || time <= 0L) return "-"
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(time))
}

private fun registrationTypeShortLabel(reason: String): String {
    return reason.replace('_', '-')
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

@Preview(showBackground = true)
@Composable
private fun SettingsPreview() {
    val context = LocalContext.current
    Utils.context = context

    val app = ManagerApplication(
        packageName = Constants.SERVICE_APP_NAME,
        appName = "test app",
    )
    val page = ApplicationInfoPage()
    page.init(app)
    page.SettingsApp()
}
