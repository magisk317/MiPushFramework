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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.mipush.common.ACTION_PREF_CHANGED
import io.github.magisk317.uikit.theme.spacing
import io.github.magisk317.mipush.main.viewmodel.SettingsViewModel
import io.github.magisk317.mipush.manager.preferences.RuntimePreferenceWriteOutcome
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.uikit.common.AppSnackbarDuration
import io.github.magisk317.uikit.common.AppSnackbarHostState
import io.github.magisk317.uikit.preference.SectionCard
import io.github.magisk317.uikit.preference.StateSwitchItem as SettingsSwitchItem
import io.github.magisk317.uikit.surface.SectionColumn
import kotlinx.coroutines.Job
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun StatusBarIconSettingsPage(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val monochromeStatusBarIcon by viewModel.monochromeStatusBarIcon.collectAsStateWithLifecycle()
    val monochromeShadeIcon by viewModel.monochromeShadeIcon.collectAsStateWithLifecycle()
    val thirdPartyIconPack by viewModel.thirdPartyIconPack.collectAsStateWithLifecycle()
    val systemAppIconPack by viewModel.systemAppIconPack.collectAsStateWithLifecycle()
    val snackbarHostState = remember { AppSnackbarHostState() }
    val showSwitchFeedback = rememberStatusBarIconSwitchFeedback(snackbarHostState)
    val managedTitle = stringResource(R.string.pref_color_status_bar_icon_mipush_title)
    val shadeTitle = stringResource(R.string.pref_monochrome_shade_icon_title)
    val thirdPartyTitle = stringResource(R.string.pref_icon_pack_third_party_title)
    val systemAppTitle = stringResource(R.string.pref_icon_pack_system_app_title)

    val body: @Composable (PaddingValues, Modifier) -> Unit = { listPadding, scrollModifier ->
        StatusBarIconSettingsBody(
            listPadding = listPadding,
            scrollModifier = scrollModifier,
            monochromeStatusBarIcon = monochromeStatusBarIcon,
            monochromeShadeIcon = monochromeShadeIcon,
            thirdPartyIconPack = thirdPartyIconPack,
            systemAppIconPack = systemAppIconPack,
            managedTitle = managedTitle,
            shadeTitle = shadeTitle,
            thirdPartyTitle = thirdPartyTitle,
            systemAppTitle = systemAppTitle,
            onManagedToggle = { enabled ->
                applyStatusBarIconSwitch(
                    setter = viewModel::setMonochromeStatusBarIcon,
                    context = context,
                    title = managedTitle,
                    enabled = enabled,
                    notify = showSwitchFeedback,
                )
            },
            onShadeToggle = { enabled ->
                applyStatusBarIconSwitch(
                    setter = viewModel::setMonochromeShadeIcon,
                    context = context,
                    title = shadeTitle,
                    enabled = enabled,
                    notify = showSwitchFeedback,
                )
            },
            onThirdPartyToggle = { enabled ->
                applyStatusBarIconSwitch(
                    setter = viewModel::setThirdPartyIconPack,
                    context = context,
                    title = thirdPartyTitle,
                    enabled = enabled,
                    notify = showSwitchFeedback,
                )
            },
            onSystemAppToggle = { enabled ->
                applyStatusBarIconSwitch(
                    setter = viewModel::setSystemAppIconPack,
                    context = context,
                    title = systemAppTitle,
                    enabled = enabled,
                    notify = showSwitchFeedback,
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

        UiKitStyle.Expressive -> StatusBarIconSettingsMaterial(
            onBack = onBack,
            snackbarHostState = snackbarHostState,
            body = body,
        )
    }
}

@Composable
private fun StatusBarIconSettingsBody(
    listPadding: PaddingValues,
    scrollModifier: Modifier,
    monochromeStatusBarIcon: Boolean,
    monochromeShadeIcon: Boolean,
    thirdPartyIconPack: Boolean,
    systemAppIconPack: Boolean,
    managedTitle: String,
    shadeTitle: String,
    thirdPartyTitle: String,
    systemAppTitle: String,
    onManagedToggle: (Boolean) -> Unit,
    onShadeToggle: (Boolean) -> Unit,
    onThirdPartyToggle: (Boolean) -> Unit,
    onSystemAppToggle: (Boolean) -> Unit,
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
                    checked = monochromeStatusBarIcon,
                    onCheckedChange = onManagedToggle,
                )

                // The monochrome master switch owns everything below it: with it off the module
                // leaves both surfaces to MIUI's native rendering, so the sub-options have no
                // effect and are not offered. Stored values are kept for when it returns.
                if (monochromeStatusBarIcon) {
                    SettingsSwitchItem(
                        title = shadeTitle,
                        summary = stringResource(R.string.pref_monochrome_shade_icon_summary),
                        checked = monochromeShadeIcon,
                        onCheckedChange = onShadeToggle,
                    )

                    // Category scopes: an icon-pack substitution only happens inside the
                    // monochrome takeover, and only for apps the pack actually covers.
                    SettingsSwitchItem(
                        title = thirdPartyTitle,
                        summary = stringResource(R.string.pref_icon_pack_third_party_summary),
                        checked = thirdPartyIconPack,
                        onCheckedChange = onThirdPartyToggle,
                    )

                    SettingsSwitchItem(
                        title = systemAppTitle,
                        summary = stringResource(R.string.pref_icon_pack_system_app_summary),
                        checked = systemAppIconPack,
                        onCheckedChange = onSystemAppToggle,
                    )
                }
            }
        }
    }
}

private fun applyStatusBarIconSwitch(
    setter: (Boolean, ((RuntimePreferenceWriteOutcome) -> Unit)?) -> Job,
    context: Context,
    title: String,
    enabled: Boolean,
    notify: (String, Boolean, RuntimePreferenceWriteOutcome) -> Unit,
) {
    // The setter launches on the view-model scope and reports through the callback below, so the
    // Job is bound here to say that it is deliberately neither awaited nor cancelled.
    val updateJob = setter(enabled) { outcome ->
        // SystemUI's hook process caches the global options; the broadcast makes it re-read
        // immediately instead of waiting for the module's periodic refresh.
        if (outcome.isApplied) notifyStatusBarIconPreferenceChanged(context)
        notify(title, enabled, outcome)
    }
}

private fun notifyStatusBarIconPreferenceChanged(context: Context) {
    context.sendBroadcast(Intent(ACTION_PREF_CHANGED))
}

@Composable
private fun rememberStatusBarIconSwitchFeedback(
    snackbarHostState: AppSnackbarHostState,
): (String, Boolean, RuntimePreferenceWriteOutcome) -> Unit {
    val scope = rememberCoroutineScope()
    val enabledTemplate = stringResource(R.string.settings_switch_enabled_feedback)
    val disabledTemplate = stringResource(R.string.settings_switch_disabled_feedback)
    val rejectedTemplate = stringResource(R.string.settings_runtime_preference_rejected)
    val failedTemplate = stringResource(R.string.settings_runtime_preference_update_failed)
    return remember(
        snackbarHostState,
        scope,
        enabledTemplate,
        disabledTemplate,
        rejectedTemplate,
        failedTemplate,
    ) {
        { title, enabled, outcome ->
            val template = when (outcome) {
                RuntimePreferenceWriteOutcome.Rejected -> rejectedTemplate
                RuntimePreferenceWriteOutcome.Unavailable -> failedTemplate
                RuntimePreferenceWriteOutcome.Applied -> if (enabled) enabledTemplate else disabledTemplate
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
