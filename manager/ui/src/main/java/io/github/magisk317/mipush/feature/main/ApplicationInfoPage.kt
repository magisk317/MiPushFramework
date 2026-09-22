package io.github.magisk317.mipush.feature.main

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.foundation.verticalScroll
import io.github.magisk317.uikit.scroll.uiKitScrollEndHaptic
import io.github.magisk317.uikit.surface.AppAlertDialog
import io.github.magisk317.uikit.surface.AppCard
import io.github.magisk317.uikit.surface.AppPrimaryButton
import androidx.compose.material3.MaterialTheme
import io.github.magisk317.uikit.surface.AppSecondaryButton
import io.github.magisk317.uikit.common.AppSnackbarDuration
import io.github.magisk317.uikit.common.AppSnackbarHostState
import io.github.magisk317.uikit.common.AppSnackbarHost
import io.github.magisk317.uikit.surface.AppSurface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.magisk317.mipush.manager.application.ManagerApplication
import io.github.magisk317.mipush.main.viewmodel.ApplicationInfoViewModel
import io.github.magisk317.mipush.manager.application.ApplicationReadResult
import io.github.magisk317.mipush.manager.application.RemoteApplicationDetailSource
import io.github.magisk317.mipush.manager.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.magisk317.uikit.surface.AppIconImage
import io.github.magisk317.uikit.surface.DialogAction
import io.github.magisk317.uikit.surface.DialogActionRow
import io.github.magisk317.uikit.surface.DetailSectionCard
import io.github.magisk317.uikit.surface.SectionColumn
import io.github.magisk317.mipush.feature.ui.theme.Theme
import io.github.magisk317.mipush.feature.ui.theme.spacing
import java.util.Locale
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import io.github.magisk317.uikit.theme.applyEdgeToEdge

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
        super.onCreate(savedInstanceState)
        applyEdgeToEdge(this)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        if (packageName.isNullOrBlank()) {
            finish()
            return
        }
        val ignoreNotRegistered = intent.getBooleanExtra(EXTRA_IGNORE_NOT_REGISTERED, false)
        lifecycleScope.launch {
            val app = withContext(Dispatchers.IO) {
                when (val result = applicationSource.load(packageName, ignoreNotRegistered)) {
                    is ApplicationReadResult.Available -> result.value
                    is ApplicationReadResult.Unavailable -> null
                }
            }
            if (app == null || isFinishing || isDestroyed) {
                finish()
                return@launch
            }
            init(app)
            infoViewModel.setApplicationInfo(info = app, ignoreNotRegistered = ignoreNotRegistered)
            appConfigurationUtils = AppConfigurationUtils(this@ApplicationInfoPage, app)
            setContent {
                Theme() {
                    SettingsApp()
                }
            }
        }
    }

    @Composable
    fun SettingsApp() {
        val snackbarHostState = remember { AppSnackbarHostState() }

        Theme {
            AppSurface(
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
                            .uiKitScrollEndHaptic()
                            .padding(horizontal = MaterialTheme.spacing.medium),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            top = topInset + MaterialTheme.spacing.medium,
                            bottom = bottomInset + MaterialTheme.spacing.medium,
                        ),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                    ) {
                        SettingsScreen(snackbarHostState)
                    }
                    AppSnackbarHost(
                        hostState = snackbarHostState,
                        bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp,
                    )
                }
            }
        }
    }

    @Composable
    fun SettingsScreen(snackbarHostState: AppSnackbarHostState) {
        ApplicationInfoHeader(snackbarHostState)
        TipsCard()
        ActivitySectionCard(snackbarHostState)
        IslandDisplaySection(snackbarHostState)
        NotificationSection(snackbarHostState)
    }

    @Composable
    private fun rememberSwitchFeedback(snackbarHostState: AppSnackbarHostState): (String, Boolean) -> Unit {
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
                        duration = AppSnackbarDuration.Short,
                    )
                }
            }
        }
    }

    @Composable
    private fun ApplicationInfoHeader(snackbarHostState: AppSnackbarHostState) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val isZygiskEnabledForApp by infoViewModel.isZygiskEnabledForApp.collectAsStateWithLifecycle()
        val zygiskStateLabel = when (isZygiskEnabledForApp) {
            true -> stringResource(R.string.zygisk_enabled)
            false -> stringResource(R.string.zygisk_disabled)
            null -> stringResource(R.string.zygisk_unavailable)
        }
        val serviceState = if (applicationInfo.existServices) {
            stringResource(R.string.app_detail_service_ready)
        } else {
            stringResource(R.string.mipush_services_not_found)
        }
        val registrationValue = stringResource(RegistrationStateStyle.registrationLabelResOf(applicationInfo))
        val lastPush = formatTime(applicationInfo.lastReceiveTimeMs)

        AppCard(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
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
                            accent = if (isZygiskEnabledForApp == true) {
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
                        AppPrimaryButton(
                            onClick = {
                                scope.launch {
                                    val feedback = infoViewModel.launchTargetAppAndForceRegister(
                                        applicationInfo.packageName,
                                        applicationInfo.registeredType,
                                    )
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    snackbarHostState.showSnackbar(
                                        message = feedback,
                                        duration = AppSnackbarDuration.Short,
                                    )
                                }
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.app_detail_force_register),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        AppSecondaryButton(
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
    private fun ActivitySectionCard(snackbarHostState: AppSnackbarHostState) {
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
                checked = isZygiskEnabledForApp == true && !blocked,
                enabled = isZygiskConfigurableForApp && !blocked && isZygiskEnabledForApp != null,
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

            val clickFallbackTitle = stringResource(R.string.app_detail_click_fallback)
            SettingSwitchRow(
                title = clickFallbackTitle,
                summary = stringResource(R.string.app_detail_click_fallback_summary),
                checked = currentInfo?.clickFallbackEnabled ?: applicationInfo.clickFallbackEnabled,
                enabled = !blocked,
                showDivider = true,
            ) { enabled ->
                infoViewModel.updateClickFallbackEnabled(enabled)
                showSwitchFeedback(clickFallbackTitle, enabled)
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
                            duration = AppSnackbarDuration.Short,
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
    private fun IslandDisplaySection(snackbarHostState: AppSnackbarHostState) {
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
    private fun NotificationSection(snackbarHostState: AppSnackbarHostState) {
        val scope = rememberCoroutineScope()
        val deleteFailedMessage = stringResource(R.string.notification_channels_delete_failed)
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
                    val disabledBadge = stringResource(R.string.notification_channels_disabled_badge)
                    val channelTitle = AppConfigurationUtils.getNotificationTitle(channel)
                    val summary = AppConfigurationUtils.getNotificationSummary(channel)
                    NotificationChannelRow(
                        badge = badge,
                        disabledBadge = disabledBadge,
                        enabled = channel.enabled,
                        title = channelTitle,
                        summary = summary,
                        showDivider = channelIndex < section.channels.lastIndex,
                        onClick = { shouldShowDialog = true },
                    )
                    if (shouldShowDialog) {
                        AppAlertDialog(
                            onDismissRequest = { shouldShowDialog = false },
                            title = {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        NotificationChannelBadge(text = badge)
                                        Text(
                                            text = channelTitle,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            softWrap = true,
                                            overflow = TextOverflow.Clip,
                                        )
                                        if (!channel.enabled) {
                                            NotificationChannelBadge(
                                                text = disabledBadge,
                                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                            )
                                        }
                                    }
                                }
                            },
                            text = {
                                Text(
                                    text = summary,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                                infoViewModel.deleteNotificationChannel(channel.id) { deleted ->
                                                    if (!deleted) {
                                                        scope.launch {
                                                            snackbarHostState.showSnackbar(
                                                                deleteFailedMessage,
                                                                duration = AppSnackbarDuration.Short,
                                                            )
                                                        }
                                                    }
                                                }
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
