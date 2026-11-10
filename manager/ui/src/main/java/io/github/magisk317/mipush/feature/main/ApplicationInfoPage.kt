package io.github.magisk317.mipush.feature.main

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.magisk317.mipush.common.utils.logW
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import io.github.magisk317.uikit.scroll.uiKitScrollEndHaptic
import io.github.magisk317.uikit.surface.AppAlertDialog
import io.github.magisk317.uikit.surface.AppCard
import androidx.compose.material3.MaterialTheme
import io.github.magisk317.uikit.common.AppSnackbarDuration
import io.github.magisk317.uikit.common.AppSnackbarHostState
import io.github.magisk317.uikit.common.AppSnackbarHost
import io.github.magisk317.uikit.surface.AppSurface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import io.github.magisk317.uikit.surface.DialogAction
import io.github.magisk317.uikit.surface.DialogActionRow
import io.github.magisk317.uikit.surface.DialogActionStyle
import io.github.magisk317.uikit.surface.SectionColumn
import io.github.magisk317.mipush.feature.ui.theme.Theme
import io.github.magisk317.uikit.theme.spacing
import java.util.Locale
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import io.github.magisk317.uikit.theme.applyEdgeToEdge
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import io.github.magisk317.uikit.theme.AppColorRole
import io.github.magisk317.uikit.theme.appColor
import io.github.magisk317.mipush.common.logging.CompanionLogSupport

open class ApplicationInfoPage : ComponentActivity() {
    companion object : CompanionLogSupport {
        override val logTag = "ApplicationInfoPage"
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
            setContent { SettingsApp() }
        }
    }

    @Composable
    fun SettingsApp() {
        val snackbarHostState = remember { AppSnackbarHostState() }

        Theme {
            AppSurface(
                modifier = Modifier.fillMaxSize(),
                color = appColor(AppColorRole.Background),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    SectionColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
                            .verticalScroll(rememberScrollState())
                            .uiKitScrollEndHaptic()
                            .padding(horizontal = MaterialTheme.spacing.medium),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            top = MaterialTheme.spacing.medium,
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
        val currentInfo by infoViewModel.applicationInfo.collectAsStateWithLifecycle()
        val context = LocalContext.current
        ApplicationDetailHeading(onBack = ::finish)
        ApplicationIdentityCard(info = currentInfo ?: applicationInfo)
        RegistrationPolicyCard(snackbarHostState)
        NotificationBehaviorSection(snackbarHostState)
        IslandDisplaySection(snackbarHostState)
        AdvancedSection(snackbarHostState)
        AppCard {
            ApplicationDetailActionRow(
                title = stringResource(R.string.app_detail_open_system_settings),
                summary = stringResource(R.string.app_detail_system_settings_summary),
                onClick = { openSystemAppInfo(context) },
            )
        }
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

    private fun openSystemAppInfo(context: Context) {
        val uri = Uri.fromParts("package", applicationInfo.packageName, null)
        // The details screen is a Settings activity, which an OEM build can filter out.
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(uri)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { error ->
            logW("app detail settings launch failed: ${error.javaClass.simpleName}")
        }
    }

    @Composable
    private fun AdvancedSection(snackbarHostState: AppSnackbarHostState) {
        val currentInfo by infoViewModel.applicationInfo.collectAsStateWithLifecycle()
        val busy by infoViewModel.registrationBusy.collectAsStateWithLifecycle()
        val info = currentInfo ?: applicationInfo
        val scope = rememberCoroutineScope()
        var expanded by rememberSaveable { mutableStateOf(false) }
        ApplicationDetailAdvancedCard(expanded = expanded, onToggle = { expanded = !expanded }) {
            ApplicationDetailActionRow(
                title = stringResource(R.string.app_detail_repair_registration),
                summary = stringResource(R.string.app_detail_repair_registration_summary),
                enabled = !busy,
                showDivider = true,
            ) {
                scope.launch {
                    val feedback = infoViewModel.launchTargetAppAndForceRegister(info.packageName)
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(feedback, duration = AppSnackbarDuration.Short)
                }
            }
            ApplicationDetailActionRow(
                title = stringResource(R.string.recent_activity_view),
                summary = stringResource(R.string.app_detail_recent_activity_summary),
                onClick = appConfigurationUtils::gotoRecentEventsPage,
            )
            RegistrationGuidance(info)
        }
    }

    @Composable
    private fun RegistrationGuidance(info: ManagerApplication) {
        if (info.blocked || info.type == ManagerApplication.Type.DENY) return
        if (info.registeredType != ManagerApplication.RegisteredType.NOT_REGISTERED &&
            info.registeredType != ManagerApplication.RegisteredType.UNREGISTERED
        ) return
        val diagnostics by infoViewModel.diagnostics.collectAsStateWithLifecycle()
        val shouldSuggestResetprop = appConfigurationUtils.shouldSuggestFakeApp(info.packageName) &&
            diagnostics?.inferenceReason in setOf(
                "unregistered_after_attempt", "registration_result_failed", "local_state_stale",
                "has_secret_but_no_local_reg",
            )
        val description = stringResource(
            if (info.registeredType == ManagerApplication.RegisteredType.UNREGISTERED) {
                R.string.status_app_registered_error_desc
            } else if (shouldSuggestResetprop) {
                R.string.status_app_not_registered_detail_with_fake_suggest
            } else {
                R.string.status_app_not_registered_detail_without_fake_suggest
            },
        )
        Tips(description = description)
    }

    @Composable
    private fun RegistrationPolicyCard(snackbarHostState: AppSnackbarHostState) {
        val currentInfo by infoViewModel.applicationInfo.collectAsStateWithLifecycle()
        val busy by infoViewModel.registrationBusy.collectAsStateWithLifecycle()
        val info = currentInfo ?: applicationInfo
        val scope = rememberCoroutineScope()
        var showDenyDialog by remember { mutableStateOf(false) }
        fun runAction(action: suspend () -> String) {
            scope.launch {
                val message = action()
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(message, duration = AppSnackbarDuration.Short)
            }
        }
        ApplicationRegistrationCard(
            info = info,
            busy = busy,
            onSelect = { type ->
                if (type == ManagerApplication.Type.DENY &&
                    info.registeredType == ManagerApplication.RegisteredType.REGISTERED
                ) {
                    showDenyDialog = true
                } else {
                    runAction { infoViewModel.setRegistrationPolicy(info.packageName, type) }
                }
            },
            onApprove = { runAction { infoViewModel.approvePendingRegistration(info.packageName) } },
        )
        if (showDenyDialog) {
            AppAlertDialog(
                onDismissRequest = { showDenyDialog = false },
                title = {
                    AppText(text = stringResource(R.string.app_detail_deny_confirm_title), role = AppTextRole.Title)
                },
                text = {
                    AppText(
                        text = stringResource(R.string.app_detail_deny_confirm_summary),
                        color = appColor(AppColorRole.OnSurfaceVariant),
                    )
                },
                confirmButton = {
                    DialogActionRow(
                        actions = listOf(
                            DialogAction(
                                label = stringResource(R.string.app_detail_registration_deny),
                                enabled = !busy,
                                style = DialogActionStyle.Danger,
                                onClick = {
                                    showDenyDialog = false
                                    runAction {
                                        infoViewModel.setRegistrationPolicy(info.packageName, ManagerApplication.Type.DENY)
                                    }
                                },
                            ),
                            DialogAction(
                                label = stringResource(R.string.app_detail_unregister_cancel),
                                style = DialogActionStyle.Secondary,
                                onClick = { showDenyDialog = false },
                            ),
                        ),
                    )
                },
            )
        }
    }

    @Composable
    private fun NotificationBehaviorSection(snackbarHostState: AppSnackbarHostState) {
        val showSwitchFeedback = rememberSwitchFeedback(snackbarHostState)
        val currentInfo by infoViewModel.applicationInfo.collectAsStateWithLifecycle()
        val busy by infoViewModel.registrationBusy.collectAsStateWithLifecycle()
        val info = currentInfo ?: applicationInfo
        val enabled = !info.blocked && info.type != ManagerApplication.Type.DENY && !busy
        ApplicationDetailSectionCard(title = stringResource(R.string.app_detail_notification_behavior)) {
            val suppressTitle = stringResource(R.string.app_detail_suppress_when_disabled)
            SettingSwitchRow(
                title = suppressTitle,
                summary = stringResource(R.string.app_detail_suppress_when_disabled_summary),
                checked = info.suppressWhenDisabled,
                enabled = enabled,
                showDivider = true,
            ) { checked ->
                infoViewModel.updateSuppressWhenDisabled(checked)
                showSwitchFeedback(suppressTitle, checked)
            }
            val clickFallbackTitle = stringResource(R.string.app_detail_click_fallback)
            SettingSwitchRow(
                title = clickFallbackTitle,
                summary = stringResource(R.string.app_detail_click_fallback_summary),
                checked = info.clickFallbackEnabled,
                enabled = enabled,
                showDivider = true,
            ) { checked ->
                infoViewModel.updateClickFallbackEnabled(checked)
                showSwitchFeedback(clickFallbackTitle, checked)
            }
            val countLimitExemptTitle = stringResource(R.string.app_detail_count_limit_exempt)
            SettingSwitchRow(
                title = countLimitExemptTitle,
                summary = stringResource(R.string.app_detail_count_limit_exempt_summary),
                checked = info.countLimitExempt,
                enabled = enabled,
            ) { checked ->
                infoViewModel.updateCountLimitExempt(checked)
                showSwitchFeedback(countLimitExemptTitle, checked)
            }
        }
    }

    @Composable
    private fun IslandDisplaySection(snackbarHostState: AppSnackbarHostState) {
        val showSwitchFeedback = rememberSwitchFeedback(snackbarHostState)
        val currentInfo by infoViewModel.applicationInfo.collectAsStateWithLifecycle()
        val busy by infoViewModel.registrationBusy.collectAsStateWithLifecycle()
        val info = currentInfo ?: applicationInfo
        val blocked = info.blocked || info.type == ManagerApplication.Type.DENY
        val islandEnabled = info.islandEnabled
        val islandFocusNotification = info.islandFocusNotification

        ApplicationDetailSectionCard(
            title = stringResource(R.string.app_detail_island_controls),
            summary = stringResource(R.string.app_detail_island_controls_summary),
        ) {
            val islandEnabledTitle = stringResource(R.string.app_detail_island_enabled)
            SettingSwitchRow(
                title = islandEnabledTitle,
                summary = stringResource(R.string.app_detail_island_enabled_summary),
                checked = islandEnabled,
                enabled = !blocked && !busy,
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
                enabled = !blocked && !busy && islandEnabled,
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

        ApplicationDetailSectionCard(
            title = stringResource(R.string.app_detail_notifications),
            summary = stringResource(R.string.settings_manage_app_notifications_summary),
        ) {
            if (isPreview) {
                ApplicationDetailActionRow(
                    title = stringResource(R.string.settings_manage_app_notifications),
                    summary = stringResource(R.string.settings_manage_app_notifications_summary),
                    actionLabel = stringResource(R.string.settings_manage_app_notifications),
                ) {
                    appConfigurationUtils.gotoNotificationSettingPage(isHooked)
                }
                return@ApplicationDetailSectionCard
            }

            if (channelState.isLoading) {
                NotificationChannelsLoadingRow(showDivider = snapshot != null)
                if (snapshot == null) return@ApplicationDetailSectionCard
            }

            channelState.unavailableStatus?.let { status ->
                ApplicationDetailActionRow(
                    title = stringResource(R.string.notification_channels_unavailable_title),
                    summary = notificationChannelsUnavailableMessage(status),
                    actionLabel = stringResource(R.string.retry),
                    showDivider = snapshot != null,
                    onClick = infoViewModel::refreshNotificationChannels,
                )
                if (snapshot == null) return@ApplicationDetailSectionCard
            }

            if (snapshot == null) {
                NotificationChannelsLoadingRow(showDivider = false)
                return@ApplicationDetailSectionCard
            }

            when (notificationChannelContentKind(snapshot, sections)) {
                NotificationChannelContentKind.EMPTY -> {
                    ApplicationDetailActionRow(
                        title = stringResource(R.string.notification_channels_empty_title),
                        summary = stringResource(R.string.notification_channels_empty_summary),
                        actionLabel = stringResource(R.string.settings_manage_app_notifications),
                    ) {
                        appConfigurationUtils.gotoNotificationSettingPage(isHooked)
                    }
                    return@ApplicationDetailSectionCard
                }
                NotificationChannelContentKind.HIDDEN -> {
                    ApplicationDetailActionRow(
                        title = stringResource(R.string.notification_channels_native_only_title),
                        summary = stringResource(R.string.notification_channels_native_only_summary),
                        actionLabel = stringResource(R.string.settings_manage_app_notifications),
                    ) {
                        appConfigurationUtils.gotoTargetNotificationSettingPage()
                    }
                    return@ApplicationDetailSectionCard
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
                                        AppText(
                                            text = channelTitle,
                                            color = appColor(AppColorRole.OnSurface),
                                            softWrap = true,
                                            overflow = TextOverflow.Clip,
                                        )
                                        if (!channel.enabled) {
                                            NotificationChannelBadge(
                                                text = disabledBadge,
                                                containerColor = appColor(AppColorRole.ErrorContainer),
                                                contentColor = appColor(AppColorRole.OnErrorContainer),
                                            )
                                        }
                                    }
                                }
                            },
                            text = {
                                AppText(
                                    text = summary,
                                    color = appColor(AppColorRole.OnSurfaceVariant),
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
