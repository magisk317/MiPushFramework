package io.github.magisk317.mipush.feature.main.subpage

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.mipush.common.ACTION_PREF_CHANGED
import io.github.magisk317.uikit.theme.spacing
import io.github.magisk317.mipush.main.viewmodel.SettingsViewModel
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.uikit.common.AppSnackbarDuration
import io.github.magisk317.uikit.common.AppSnackbarHostState
import io.github.magisk317.uikit.preference.SectionCard
import io.github.magisk317.uikit.preference.StateSwitchItem as SettingsSwitchItem
import io.github.magisk317.uikit.surface.AppAlertDialog
import io.github.magisk317.uikit.surface.AppTextButton
import io.github.magisk317.uikit.surface.SectionColumn
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle
import androidx.compose.material3.Text
import java.util.Locale
import kotlinx.coroutines.launch

private data class PendingStatusBarToggle(
    val managed: Boolean? = null,
    val global: Boolean? = null,
    val title: String,
    val enabled: Boolean,
)

@Composable
fun StatusBarIconSettingsPage(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val colorStatusBarIcon by viewModel.colorStatusBarIcon.collectAsStateWithLifecycle()
    val colorStatusBarIconGlobal by viewModel.colorStatusBarIconGlobal.collectAsStateWithLifecycle()
    val snackbarHostState = remember { AppSnackbarHostState() }
    val showSwitchFeedback = rememberStatusBarIconSwitchFeedback(snackbarHostState)
    val scope = rememberCoroutineScope()
    val rebootFailedMessage = stringResource(R.string.pref_color_status_bar_icon_reboot_failed)
    val managedTitle = stringResource(R.string.pref_color_status_bar_icon_mipush_title)
    val globalTitle = stringResource(R.string.pref_color_status_bar_icon_global_title)
    var pendingToggle by remember { mutableStateOf<PendingStatusBarToggle?>(null) }

    val body: @Composable (PaddingValues, Modifier) -> Unit = { listPadding, scrollModifier ->
        StatusBarIconSettingsBody(
            listPadding = listPadding,
            scrollModifier = scrollModifier,
            colorStatusBarIcon = colorStatusBarIcon,
            colorStatusBarIconGlobal = colorStatusBarIconGlobal,
            managedTitle = managedTitle,
            globalTitle = globalTitle,
            onManagedToggle = { enabled ->
                pendingToggle = PendingStatusBarToggle(
                    managed = enabled,
                    title = managedTitle,
                    enabled = enabled,
                )
            },
            onGlobalToggle = { enabled ->
                pendingToggle = PendingStatusBarToggle(
                    global = enabled,
                    title = globalTitle,
                    enabled = enabled,
                )
            },
        )
    }

    when (currentUiKitStyle()) {
        UiKitStyle.Miuix -> StatusBarIconSettingsMiuix(
            onBack = onBack,
            snackbarHostState = snackbarHostState,
            body = body,
        )

        UiKitStyle.Expressive -> StatusBarIconSettingsExpressive(
            onBack = onBack,
            snackbarHostState = snackbarHostState,
            body = body,
        )
    }

    val pending = pendingToggle
    if (pending != null) {
        AppAlertDialog(
            onDismissRequest = { pendingToggle = null },
            title = { Text(stringResource(R.string.pref_color_status_bar_icon_restart_title)) },
            text = { Text(stringResource(R.string.pref_color_status_bar_icon_restart_message)) },
            confirmButton = {
                AppTextButton(
                    text = stringResource(R.string.pref_color_status_bar_icon_restart_confirm),
                    onClick = {
                        val target = pending
                        pendingToggle = null
                        viewModel.applyColorStatusBarIconWithRestart(
                            managed = target.managed,
                            global = target.global,
                            onPrepared = { success ->
                                if (success) notifyStatusBarIconPreferenceChanged(context)
                                showSwitchFeedback(target.title, target.enabled, success)
                            },
                            onRebootFailed = {
                                scope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    snackbarHostState.showSnackbar(rebootFailedMessage)
                                }
                            },
                        )
                    },
                )
            },
            dismissButton = {
                AppTextButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = { pendingToggle = null },
                )
            },
        )
    }
}

@Composable
private fun StatusBarIconSettingsBody(
    listPadding: PaddingValues,
    scrollModifier: Modifier,
    colorStatusBarIcon: Boolean,
    colorStatusBarIconGlobal: Boolean,
    managedTitle: String,
    globalTitle: String,
    onManagedToggle: (Boolean) -> Unit,
    onGlobalToggle: (Boolean) -> Unit,
) {
    val scrollState = rememberScrollState()
    SectionColumn(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .then(scrollModifier),
        contentPadding = PaddingValues(
            start = MaterialTheme.spacing.medium,
            top = listPadding.calculateTopPadding() + MaterialTheme.spacing.small,
            end = MaterialTheme.spacing.medium,
            bottom = MaterialTheme.spacing.large,
        ),
    ) {
        SectionCard(
            title = stringResource(R.string.pref_color_status_bar_icon_title),
            accordionMode = false,
            sectionExpanded = true,
            onExpandedChange = {},
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = MaterialTheme.spacing.small),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                SettingsSwitchItem(
                    title = managedTitle,
                    summary = stringResource(R.string.pref_color_status_bar_icon_mipush_summary),
                    checked = colorStatusBarIcon,
                    onCheckedChange = onManagedToggle,
                )

                if (!colorStatusBarIcon) {
                    SettingsSwitchItem(
                        title = globalTitle,
                        summary = stringResource(R.string.pref_color_status_bar_icon_global_summary),
                        checked = colorStatusBarIconGlobal,
                        onCheckedChange = onGlobalToggle,
                    )
                }
            }
        }
    }
}

private fun notifyStatusBarIconPreferenceChanged(context: Context) {
    context.sendBroadcast(Intent(ACTION_PREF_CHANGED))
}

@Composable
private fun rememberStatusBarIconSwitchFeedback(
    snackbarHostState: AppSnackbarHostState,
): (String, Boolean, Boolean) -> Unit {
    val scope = rememberCoroutineScope()
    val enabledTemplate = stringResource(R.string.settings_switch_enabled_feedback)
    val disabledTemplate = stringResource(R.string.settings_switch_disabled_feedback)
    val failedTemplate = stringResource(R.string.settings_runtime_preference_update_failed)
    return remember(snackbarHostState, scope, enabledTemplate, disabledTemplate, failedTemplate) {
        { title, enabled, success ->
            val template = when {
                !success -> failedTemplate
                enabled -> enabledTemplate
                else -> disabledTemplate
            }
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
