package io.github.magisk317.mipush.feature.main

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.magisk317.mipush.common.compat.PackageManagerCompatBridge
import io.github.magisk317.mipush.compat.RegistrationStateCompat
import io.github.magisk317.mipush.compat.RegistrationStateStore
import io.github.magisk317.mipush.utils.RegistrationHelper
import io.github.magisk317.mipush.config.ConfigNavigationHelper
import io.github.magisk317.mipush.platform.support.AppRootAccessFacade
import io.github.magisk317.mipush.platform.support.PermissionUtils
import com.xiaomi.xmsf.BuildConfig
import com.xiaomi.xmsf.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.magisk317.mipush.common.utils.Utils
import kotlinx.coroutines.runBlocking
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import io.github.magisk317.mipush.runtime.store.entities.Event
import io.github.magisk317.mipush.runtime.store.entities.RegisteredApplication
import io.github.magisk317.mipush.runtime.store.entities.RegisteredApplication.RegisteredType
import io.github.magisk317.mipush.feature.ui.component.AppIcon
import io.github.magisk317.mipush.feature.ui.component.ActionStrip
import io.github.magisk317.mipush.feature.ui.component.DetailDivider
import io.github.magisk317.mipush.feature.ui.component.DetailSectionCard
import io.github.magisk317.mipush.feature.ui.component.DialogAction
import io.github.magisk317.mipush.feature.ui.component.DialogActionRow
import io.github.magisk317.mipush.feature.ui.component.LabelValueBlock
import io.github.magisk317.mipush.feature.ui.component.MarkdownView
import io.github.magisk317.mipush.feature.ui.component.MetricGrid
import io.github.magisk317.mipush.feature.ui.component.MetricSpec
import io.github.magisk317.mipush.feature.ui.component.SectionColumn
import io.github.magisk317.mipush.feature.ui.component.SettingsDialogItem
import io.github.magisk317.mipush.feature.ui.component.SettingsItem
import io.github.magisk317.mipush.feature.main.AppConfigurationUtils
import io.github.magisk317.mipush.feature.main.AppRegistrationDiagnostics
import io.github.magisk317.mipush.feature.main.AppRegistrationDiagnosticsHelper
import io.github.magisk317.mipush.feature.main.RegistrationStateStyle
import io.github.magisk317.mipush.feature.main.subpage.ApplicationPageOperation
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
                application.appName = io.github.magisk317.mipush.platform.support.Global.applicationNameCache()
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
            if (application != null) {
                refreshTransientAppState(application)
            }
            return application
        }
        return null
    }

    private fun refreshTransientAppState(application: RegisteredApplication) {
        application.lastReceiveTime = Date(Utils.getLastReceiveTime(application.packageName) ?: 0L)

        val packageInfo = runCatching {
            PackageManagerCompatBridge.getPackageInfo(
                packageManager,
                application.packageName,
                PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS,
            )
        }.getOrNull()

        if (packageInfo == null) {
            application.existServices = false
            application.registrationTypeReason = "package_not_found"
            return
        }

        val checker = ApplicationPageOperation.getMiPushManifestChecker()
        application.existServices = ApplicationPageOperation.hasMiPushServices(
            checker = checker,
            info = packageInfo,
        )

        val serviceNames = packageInfo.services?.mapNotNull { it.name }?.toSet() ?: emptySet()
        val receiverNames = packageInfo.receivers?.mapNotNull { it.name }?.toSet() ?: emptySet()
        application.registrationTypeReason = RegistrationHelper.classifyDisplayTypeReason(
            serviceNames = serviceNames,
            receiverNames = receiverNames,
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
        RegistrationDiagnosticsCard()
        RegistrationActionsCard()
        TipsCard()
        ActivitySectionCard(snackbarHostState)
        NotificationSection()
    }

    @Composable
    private fun ApplicationInfoHeader(snackbarHostState: SnackbarHostState) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val configNavigationHelper = remember { ConfigNavigationHelper() }
        val diagnostics by androidx.compose.runtime.produceState<AppRegistrationDiagnostics?>(
            initialValue = null,
            key1 = applicationInfo.packageName,
        ) {
            value = withContext(Dispatchers.IO) {
                AppRegistrationDiagnosticsHelper.load(
                    context = context,
                    packageName = applicationInfo.packageName,
                    registeredType = applicationInfo.registeredType,
                )
            }
        }
        val resolvedTypeReason = remember(applicationInfo.registrationTypeReason, diagnostics?.displayTypeReason) {
            when {
                applicationInfo.registrationTypeReason.isNotBlank() &&
                    applicationInfo.registrationTypeReason != "unknown" -> applicationInfo.registrationTypeReason
                !diagnostics?.displayTypeReason.isNullOrBlank() &&
                    diagnostics?.displayTypeReason != "unknown" -> diagnostics?.displayTypeReason.orEmpty()
                else -> applicationInfo.registrationTypeReason.ifBlank { "unknown" }
            }
        }
        val typeLabel = registrationTypeShortLabel(resolvedTypeReason)
        val serviceState = if (applicationInfo.existServices) {
            stringResource(R.string.app_detail_service_ready)
        } else {
            stringResource(R.string.mipush_services_not_found)
        }
        val registrationValue = stringResource(RegistrationStateStyle.registrationLabelResOf(applicationInfo))
        val lastPush = formatTime(applicationInfo.lastReceiveTime.time)

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
                            value = typeLabel,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            accent = MaterialTheme.colorScheme.secondary,
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
                                    configNavigationHelper.openForPackage(applicationInfo.packageName)
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
            val hasRoot = withContext(Dispatchers.IO) {
                PermissionUtils.refreshRootAccessIfGranted()
            }
            if (!hasRoot) {
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.force_register_requires_root),
                    duration = SnackbarDuration.Short,
                )
                return@launch
            }

            val plan = withContext(Dispatchers.IO) {
                RegistrationHelper.inspectForceRegisterPlan(packageName)
            }
            if (!plan.supportsServiceDispatch && !plan.supportsReceiverFallback) {
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.force_register_unavailable),
                    duration = SnackbarDuration.Short,
                )
                return@launch
            }

            withContext(Dispatchers.IO) {
                stopTargetAppBestEffort(packageName)
            }
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent == null) {
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.force_register_failed),
                    duration = SnackbarDuration.Short,
                )
                return@launch
            }
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (runCatching { context.startActivity(launchIntent) }.isFailure) {
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.force_register_failed),
                    duration = SnackbarDuration.Short,
                )
                return@launch
            }

            kotlinx.coroutines.delay(500)
            forceRegisterWithFeedback(context, packageName, snackbarHostState)
        }
    }

    private fun stopTargetAppBestEffort(packageName: String) {
        runCatching {
            AppRootAccessFacade.runRootCommand("am force-stop $packageName")
        }
    }

    private suspend fun forceRegisterWithFeedback(
        context: Context,
        packageName: String,
        snackbarHostState: SnackbarHostState,
    ) {
        val message = withContext(Dispatchers.IO) {
            if (!PermissionUtils.refreshRootAccessIfGranted()) {
                return@withContext context.getString(R.string.force_register_requires_root)
            }
            val result = runCatching {
                RegistrationHelper.tryForceRegister(packageName)
            }
            if (result.getOrDefault(false)) {
                return@withContext context.getString(R.string.force_register_sent)
            }
            val cause = result.exceptionOrNull()
            if (cause is NoClassDefFoundError || cause is ClassNotFoundException || cause is UnsupportedOperationException) {
                return@withContext context.getString(R.string.force_register_unavailable)
            }
            if (runCatching { RegistrationHelper.tryForceRegisterFallback(packageName) }.getOrDefault(false)) {
                context.getString(R.string.force_register_sent)
            } else {
                context.getString(R.string.force_register_failed)
            }
        }
        snackbarHostState.showSnackbar(
            message = message,
            duration = SnackbarDuration.Short,
        )
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
                value = registrationTypeShortLabel(
                    applicationInfo.registrationTypeReason.ifBlank { info.displayTypeReason },
                ),
            )
            AppDetailValueRow(
                label = stringResource(R.string.registration_diagnostics_local_state),
                value = stringResource(
                    R.string.registration_diagnostics_local_state_value,
                    info.hasLocalRegistration.toFlagValue(),
                    info.regSecCount,
                    formatTime(info.lastReceiveTime),
                ),
            )
            AppDetailValueRow(
                label = stringResource(R.string.registration_diagnostics_recent_event),
                value = formatRecentRegistrationEvent(info),
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
        val resources = LocalResources.current
        val scope = rememberCoroutineScope()
        val launchObserveTimeoutMessage = stringResource(R.string.registration_action_launch_observe_timeout)

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
                            resources.getString(
                                R.string.registration_action_launch_observe_result,
                                resources.getString(registrationInferenceLabelRes(diagnostics.inferenceReason)),
                            )
                        } else {
                            launchObserveTimeoutMessage
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
    private fun ActivitySectionCard(snackbarHostState: SnackbarHostState) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val notificationOnRegisterDisabledMessage = stringResource(
            R.string.notification_on_register_global_disabled_hint,
        )
        val globalEnabled = remember {
            runBlocking {
                io.github.magisk317.mipush.platform.support.Global.configCenter()
                    .isNotificationOnRegisterAsync()
            }
        }
        var checked by remember { mutableStateOf(applicationInfo.notificationOnRegister) }
        var blocked by remember { mutableStateOf(applicationInfo.blocked) }

        DetailSectionCard(
            title = stringResource(R.string.app_detail_activity_and_behavior),
        ) {
            SettingSwitchRow(
                title = stringResource(R.string.app_detail_block),
                summary = stringResource(R.string.app_detail_block_summary),
                checked = blocked,
                showDivider = true,
            ) {
                blocked = it
                applicationInfo.blocked = blocked
                RegisteredApplicationDb.update(applicationInfo)
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

            SettingSwitchRow(
                title = stringResource(R.string.permission_notification_on_register),
                summary = stringResource(R.string.permission_summary_notification_on_register),
                checked = checked,
                enabled = globalEnabled && !blocked,
                showDivider = false,
                onClickWhenDisabled = {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = notificationOnRegisterDisabledMessage,
                            duration = SnackbarDuration.Short,
                        )
                    }
                },
            ) {
                checked = it
                applicationInfo.notificationOnRegister = checked
                RegisteredApplicationDb.update(applicationInfo)
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

@Composable
private fun formatRecentRegistrationEvent(diagnostics: AppRegistrationDiagnostics): String {
    val label = when (diagnostics.latestRegistrationEventType) {
        Event.Type.Registration -> stringResource(R.string.registration_event_registration)
        Event.Type.RegistrationResult -> if (diagnostics.latestRegistrationEventResult == Event.ResultType.OK) {
            stringResource(R.string.registration_event_registration_result_ok)
        } else {
            stringResource(R.string.registration_event_registration_result_failed)
        }
        Event.Type.UnRegistration -> stringResource(R.string.registration_event_unregistration)
        else -> stringResource(R.string.registration_event_none)
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

private fun registrationTypeShortLabel(reason: String): String {
    return reason.replace('_', '-')
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
