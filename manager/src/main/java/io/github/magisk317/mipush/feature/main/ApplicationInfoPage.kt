package io.github.magisk317.mipush.feature.main

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import io.github.magisk317.uikit.common.ElevatedSnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.magisk317.mipush.common.manager.ManagerApplication
import io.github.magisk317.mipush.common.manager.ManagerApplicationDiagnostics
import io.github.magisk317.mipush.main.viewmodel.ApplicationInfoViewModel
import io.github.magisk317.mipush.manager.application.ApplicationReadResult
import io.github.magisk317.mipush.manager.application.RemoteApplicationDetailSource
import io.github.magisk317.mipush.manager.notification.NotificationChannelReadStatus
import io.github.magisk317.mipush.manager.notification.NotificationChannelSnapshot
import io.github.magisk317.mipush.manager.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.uikit.surface.AppIconImage
import io.github.magisk317.uikit.surface.DialogAction
import io.github.magisk317.uikit.surface.DialogActionRow
import io.github.magisk317.uikit.surface.DetailDivider
import io.github.magisk317.uikit.surface.DetailSectionCard
import io.github.magisk317.uikit.surface.SectionColumn
import io.github.magisk317.mipush.feature.wizard.support.WizardSPUtils
import io.github.magisk317.mipush.feature.ui.theme.Theme
import io.github.magisk317.mipush.feature.ui.theme.spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import io.github.magisk317.mipush.manager.di.ManagerDependencies
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

open class ApplicationInfoPage : ComponentActivity() {
    companion object {
        const val EXTRA_PACKAGE_NAME: String = "EXTRA_PACKAGE_NAME"
        const val EXTRA_IGNORE_NOT_REGISTERED: String = "EXTRA_IGNORE_NOT_REGISTERED"
    }

    private val applicationSource: RemoteApplicationDetailSource by inject()
    private val infoViewModel: ApplicationInfoViewModel by viewModel()

    private lateinit var applicationInfo: ManagerApplication
    private lateinit var appConfigurationUtils: AppConfigurationUtils

    fun init(applicationInfo: ManagerApplication) {
        this.applicationInfo = applicationInfo
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ManagerDependencies.ensureStarted(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = getRegisteredApplication()
        if (app == null) {
            finish()
            return
        }
        init(app)
        infoViewModel.setApplicationInfo(
            info = app,
            ignoreNotRegistered = intent.getBooleanExtra(EXTRA_IGNORE_NOT_REGISTERED, false),
        )
        appConfigurationUtils = AppConfigurationUtils(this, app)
        setContent {
            Theme {
                SettingsApp()
            }
        }
    }

    private fun getRegisteredApplication(): ManagerApplication? {
        if (!intent.hasExtra(EXTRA_PACKAGE_NAME)) return null
        val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return null
        val ignoreNotRegistered = intent.getBooleanExtra(EXTRA_IGNORE_NOT_REGISTERED, false)
        return runBlocking {
            when (val result = applicationSource.load(pkg, ignoreNotRegistered)) {
                is ApplicationReadResult.Available -> result.value
                is ApplicationReadResult.Unavailable -> null
            }
        }
    }

    @Composable
    fun SettingsApp() {
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
                    ElevatedSnackbarHost(
                        hostState = snackbarHostState,
                        bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp,
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
        val isZygiskEnabledForApp by infoViewModel.isZygiskEnabledForApp.collectAsStateWithLifecycle()
        val zygiskStateLabel = if (isZygiskEnabledForApp) stringResource(R.string.zygisk_enabled) else stringResource(R.string.zygisk_disabled)
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
                        AppIconImage(
                            packageName = applicationInfo.packageName,
                            label = applicationInfo.appName,
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
                            label = stringResource(R.string.zygisk_status),
                            value = zygiskStateLabel,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            accent = if (isZygiskEnabledForApp) {
                                RegistrationStateStyle.GreenColor
                            } else {
                                MaterialTheme.colorScheme.secondary
                            },
                            onClick = {
                                context.startActivity(android.content.Intent(context, ZygiskConfigPage::class.java))
                            }
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
                                scope.launch {
                                    val feedback = infoViewModel.launchTargetAppAndForceRegister(
                                        applicationInfo.packageName,
                                        applicationInfo.registeredType,
                                    )
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    snackbarHostState.showSnackbar(
                                        message = feedback,
                                        duration = SnackbarDuration.Short,
                                    )
                                }
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.app_detail_force_register),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        OutlinedButton(
                            onClick = { openSystemAppInfo(context) },
                        ) {
                            Text(
                                text = stringResource(R.string.app_detail_open_system_settings),
                                maxLines = 2,
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
        val diagnostics by infoViewModel.diagnostics.collectAsStateWithLifecycle()
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
        val currentInfo by infoViewModel.applicationInfo.collectAsStateWithLifecycle()
        val blocked = currentInfo?.blocked ?: applicationInfo.blocked
        val isZygiskEnabledForApp by infoViewModel.isZygiskEnabledForApp.collectAsStateWithLifecycle()
        val isZygiskConfigurableForApp by infoViewModel.isZygiskConfigurableForApp.collectAsStateWithLifecycle()

        DetailSectionCard(
            title = stringResource(R.string.app_detail_activity_and_behavior),
        ) {
            val zygiskTitle = stringResource(R.string.zygisk_spoof_switch)
            SettingSwitchRow(
                title = zygiskTitle,
                summary = stringResource(R.string.zygisk_spoof_switch_summary),
                checked = isZygiskEnabledForApp && !blocked,
                enabled = isZygiskConfigurableForApp && !blocked,
                showDivider = true,
            ) { enabled ->
                infoViewModel.updateZygiskEnabledForApp(enabled)
                showSwitchFeedback(zygiskTitle, enabled)
            }

            val blockTitle = stringResource(R.string.app_detail_block)
            SettingSwitchRow(
                title = blockTitle,
                summary = stringResource(R.string.app_detail_block_summary),
                checked = blocked,
                showDivider = true,
            ) { enabled ->
                infoViewModel.updateBlocked(enabled)
                showSwitchFeedback(blockTitle, enabled)
            }

            val redirectClickTitle = stringResource(R.string.app_detail_redirect_click)
            val redirectInDevelopment = stringResource(R.string.app_detail_redirect_click_in_development)
            val scope = rememberCoroutineScope()
            SettingSwitchRow(
                title = redirectClickTitle,
                summary = stringResource(R.string.app_detail_redirect_click_summary),
                checked = false,
                enabled = false,
                showDivider = true,
                onClickWhenDisabled = {
                    scope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(
                            message = redirectInDevelopment,
                            duration = SnackbarDuration.Short,
                        )
                    }
                },
            ) { _ -> }

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
        val currentInfo by infoViewModel.applicationInfo.collectAsStateWithLifecycle()
        val blocked = currentInfo?.blocked ?: applicationInfo.blocked
        val islandEnabled = (currentInfo?.islandEnabled ?: applicationInfo.islandEnabled) && !blocked
        val islandFocusNotification =
            (currentInfo?.islandFocusNotification ?: applicationInfo.islandFocusNotification) && !blocked

        DetailSectionCard(
            title = stringResource(R.string.app_detail_island_controls),
            summary = stringResource(R.string.app_detail_island_controls_summary),
        ) {
            val islandEnabledTitle = stringResource(R.string.app_detail_island_enabled)
            SettingSwitchRow(
                title = islandEnabledTitle,
                summary = stringResource(R.string.app_detail_island_enabled_summary),
                checked = islandEnabled,
                enabled = !blocked,
                showDivider = true,
            ) { enabled ->
                infoViewModel.updateIslandEnabled(enabled)
                showSwitchFeedback(islandEnabledTitle, enabled)
            }

            val islandFocusNotificationTitle = stringResource(R.string.app_detail_island_focus_notification)
            SettingSwitchRow(
                title = islandFocusNotificationTitle,
                summary = stringResource(R.string.app_detail_island_focus_notification_summary),
                checked = islandFocusNotification,
                enabled = !blocked && islandEnabled,
            ) { enabled ->
                infoViewModel.updateIslandFocusEnabled(enabled)
                showSwitchFeedback(islandFocusNotificationTitle, enabled)
            }
        }
    }

    @SuppressLint("LocalContextGetResourceValueCall")
    @Composable
    private fun NotificationSection() {
        val isPreview = LocalInspectionMode.current
        val channelState by infoViewModel.notificationChannels.collectAsStateWithLifecycle()
        val snapshot = channelState.snapshot
        val isHooked = snapshot?.isHooked == true
        val sections = if (snapshot == null) {
            emptyList()
        } else {
            AppConfigurationUtils.notificationChannelSections(snapshot)
        }

        DetailSectionCard(
            title = stringResource(R.string.app_detail_notifications),
            summary = stringResource(R.string.settings_manage_app_notifications_summary),
        ) {
            if (isPreview) {
                ActionSummaryRow(
                    title = stringResource(R.string.settings_manage_app_notifications),
                    summary = stringResource(R.string.settings_manage_app_notifications_summary),
                    actionLabel = stringResource(R.string.settings_manage_app_notifications),
                ) {
                    appConfigurationUtils.gotoNotificationSettingPage(isHooked)
                }
                return@DetailSectionCard
            }

            if (channelState.isLoading) {
                NotificationChannelsLoadingRow(showDivider = snapshot != null)
                if (snapshot == null) return@DetailSectionCard
            }

            channelState.unavailableStatus?.let { status ->
                ActionSummaryRow(
                    title = stringResource(R.string.notification_channels_unavailable_title),
                    summary = notificationChannelsUnavailableMessage(status),
                    actionLabel = stringResource(R.string.retry),
                    showDivider = snapshot != null,
                    onClick = infoViewModel::refreshNotificationChannels,
                )
                if (snapshot == null) return@DetailSectionCard
            }

            if (snapshot == null) {
                NotificationChannelsLoadingRow(showDivider = false)
                return@DetailSectionCard
            }

            when (notificationChannelContentKind(snapshot, sections)) {
                NotificationChannelContentKind.EMPTY -> {
                    ActionSummaryRow(
                        title = stringResource(R.string.notification_channels_empty_title),
                        summary = stringResource(R.string.notification_channels_empty_summary),
                        actionLabel = stringResource(R.string.settings_manage_app_notifications),
                        enabled = applicationInfo.registeredType == ManagerApplication.RegisteredType.NOT_REGISTERED,
                    ) {
                        appConfigurationUtils.gotoNotificationSettingPage(isHooked)
                    }
                    return@DetailSectionCard
                }
                NotificationChannelContentKind.HIDDEN -> {
                    ActionSummaryRow(
                        title = stringResource(R.string.notification_channels_native_only_title),
                        summary = stringResource(R.string.notification_channels_native_only_summary),
                        actionLabel = stringResource(R.string.settings_manage_app_notifications),
                    ) {
                        appConfigurationUtils.gotoTargetNotificationSettingPage()
                    }
                    return@DetailSectionCard
                }
                NotificationChannelContentKind.VISIBLE -> Unit
            }

            sections.forEachIndexed { sectionIndex, section ->
                val sectionTitle = when (section.kind) {
                    NotificationChannelSectionKind.MIPUSH ->
                        stringResource(R.string.notification_channels_section_mipush)
                    NotificationChannelSectionKind.NATIVE -> {
                        val baseTitle = stringResource(R.string.notification_channels_section_native)
                        section.group?.let { "$baseTitle: ${it.name} (${it.id})" } ?: baseTitle
                    }
                }
                val sectionSummary = when (section.kind) {
                    NotificationChannelSectionKind.MIPUSH ->
                        stringResource(R.string.notification_channels_section_mipush_summary)
                    NotificationChannelSectionKind.NATIVE ->
                        stringResource(R.string.notification_channels_section_native_summary)
                }
                NotificationChannelSectionHeader(
                    title = sectionTitle,
                    summary = sectionSummary,
                    showTopDivider = sectionIndex > 0,
                )
                section.channels.forEachIndexed { channelIndex, channel ->
                    var shouldShowDialog by remember(channel.id) { mutableStateOf(false) }
                    val badge = when (section.kind) {
                        NotificationChannelSectionKind.MIPUSH ->
                            stringResource(R.string.notification_channels_managed_badge)
                        NotificationChannelSectionKind.NATIVE ->
                            stringResource(R.string.notification_channels_native_badge)
                    }
                    val channelTitle = AppConfigurationUtils.getNotificationTitle(channel)
                    val dialogTitle = "[$badge] $channelTitle"
                    val summary = AppConfigurationUtils.getNotificationSummary(channel)
                    NotificationChannelRow(
                        badge = badge,
                        title = channelTitle,
                        summary = summary,
                        showDivider = channelIndex < section.channels.lastIndex ||
                            sectionIndex < sections.lastIndex,
                        onClick = { shouldShowDialog = true },
                    )
                    if (shouldShowDialog) {
                        AlertDialog(
                            onDismissRequest = { shouldShowDialog = false },
                            title = {
                                Text(
                                    text = dialogTitle,
                                    modifier = Modifier.fillMaxWidth(),
                                    softWrap = true,
                                    overflow = TextOverflow.Clip,
                                )
                            },
                            text = {
                                Text(
                                    text = summary,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            },
                            confirmButton = {
                                DialogActionRow(
                                    actions = listOf(
                                        DialogAction(
                                            label = stringResource(R.string.notification_channels_setting),
                                            onClick = {
                                                appConfigurationUtils.gotoNotificationChannelSettingPage(
                                                    channel,
                                                    isHooked,
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
                                                infoViewModel.deleteNotificationChannel(channel.id)
                                                shouldShowDialog = false
                                            },
                                            style = io.github.magisk317.uikit.surface.DialogActionStyle.Danger,
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
}

internal enum class NotificationChannelContentKind {
    EMPTY,
    HIDDEN,
    VISIBLE,
}

internal fun notificationChannelContentKind(
    snapshot: NotificationChannelSnapshot,
    sections: List<NotificationChannelSection>,
): NotificationChannelContentKind = when {
    snapshot.channels.isEmpty() -> NotificationChannelContentKind.EMPTY
    sections.isEmpty() -> NotificationChannelContentKind.HIDDEN
    else -> NotificationChannelContentKind.VISIBLE
}

@Composable
private fun NotificationChannelsLoadingRow(showDivider: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
        )
        Text(
            text = stringResource(R.string.notification_channels_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
    if (showDivider) {
        DetailDivider()
    }
}

@Composable
private fun notificationChannelsUnavailableMessage(status: NotificationChannelReadStatus): String =
    stringResource(
        when (status) {
            NotificationChannelReadStatus.RUNTIME_MISSING ->
                R.string.notification_channels_unavailable_runtime_missing
            NotificationChannelReadStatus.PERMISSION_DENIED ->
                R.string.notification_channels_unavailable_permission_denied
            NotificationChannelReadStatus.BINDING ->
                R.string.notification_channels_unavailable_binding
            NotificationChannelReadStatus.INCOMPATIBLE,
            NotificationChannelReadStatus.UNSUPPORTED,
            -> R.string.notification_channels_unavailable_incompatible
            NotificationChannelReadStatus.DISCONNECTED,
            NotificationChannelReadStatus.TIMED_OUT,
            NotificationChannelReadStatus.TEMPORARILY_DISCONNECTED,
            -> R.string.notification_channels_unavailable_disconnected
            NotificationChannelReadStatus.FAILED -> R.string.notification_channels_unavailable_failed
        },
    )


@Composable
private fun NotificationChannelSectionHeader(
    title: String,
    summary: String,
    showTopDivider: Boolean,
) {
    if (showTopDivider) {
        DetailDivider()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = MaterialTheme.spacing.large,
                end = MaterialTheme.spacing.large,
                top = MaterialTheme.spacing.medium,
                bottom = MaterialTheme.spacing.small,
            ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )
        if (summary.isNotBlank()) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun NotificationChannelRow(
    badge: String,
    title: String,
    summary: String,
    showDivider: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = MaterialTheme.spacing.large,
                vertical = MaterialTheme.spacing.medium,
            ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    softWrap = false,
                    maxLines = 1,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                softWrap = true,
                overflow = TextOverflow.Clip,
                maxLines = 8,
            )
        }
        if (summary.isNotBlank()) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                softWrap = true,
                overflow = TextOverflow.Clip,
                maxLines = 8,
            )
        }
    }
    if (showDivider) {
        DetailDivider()
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
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
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
        val switchModifier = if (!enabled && onClickWhenDisabled != null) {
            Modifier.clickable(onClick = onClickWhenDisabled)
        } else {
            Modifier
        }
        Box(modifier = switchModifier) {
            Switch(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                enabled = enabled,
            )
        }
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
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
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
    return Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault()))
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
        
        val annotatedText = remember(description) {
            AnnotatedString.fromHtml(
                htmlString = description,
            )
        }
        Text(
            text = annotatedText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
