@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.magisk317.mipush.feature.main.subpage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import io.github.magisk317.uikit.surface.AppAlertDialog
import io.github.magisk317.uikit.preference.AppRadioButton
import io.github.magisk317.uikit.common.AppSnackbarHostState
import androidx.compose.material3.Text
import io.github.magisk317.uikit.surface.AppTextButton
import io.github.magisk317.uikit.surface.AppTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.mipush.common.FREEZE_REFREEZE_POLICY_NEVER
import io.github.magisk317.mipush.common.FREEZE_REFREEZE_POLICY_SCREEN_OFF
import io.github.magisk317.mipush.common.FREEZE_REFREEZE_POLICY_TIMED
import io.github.magisk317.mipush.common.FREEZE_REFREEZE_POLICY_TASK_REMOVED
import io.github.magisk317.mipush.main.viewmodel.SettingsViewModel
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.uikit.preference.Item as SettingsItem
import io.github.magisk317.uikit.preference.StateSwitchItem as SettingsSwitchItem
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

private const val MAX_FREEZE_REFREEZE_DELAY_MINUTES = 120

private data class PolicyOption(val value: Int, val labelRes: Int)

private val POLICY_OPTIONS = listOf(
    PolicyOption(FREEZE_REFREEZE_POLICY_NEVER, R.string.pref_freeze_policy_never),
    PolicyOption(FREEZE_REFREEZE_POLICY_SCREEN_OFF, R.string.pref_freeze_policy_screen_off),
    PolicyOption(FREEZE_REFREEZE_POLICY_TIMED, R.string.pref_freeze_policy_timed),
    PolicyOption(FREEZE_REFREEZE_POLICY_TASK_REMOVED, R.string.pref_freeze_policy_task_removed),
)

@Composable
internal fun FreezeBlock(viewModel: SettingsViewModel, snackbarHostState: AppSnackbarHostState) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val freezeEnabled by viewModel.freezeEnabled.collectAsStateWithLifecycle()
    val freezeRefreezePolicy by viewModel.freezeRefreezePolicy.collectAsStateWithLifecycle()
    val freezeRefreezeDelayMinutes by viewModel.freezeRefreezeDelayMinutes.collectAsStateWithLifecycle()
    val showSwitchFeedback = rememberSwitchFeedback(snackbarHostState)
    var showPolicyDialog by remember { mutableStateOf(false) }
    var pendingPolicy by remember { mutableStateOf(FREEZE_REFREEZE_POLICY_SCREEN_OFF) }
    var showDelayDialog by remember { mutableStateOf(false) }
    val delayInputState = remember(freezeRefreezeDelayMinutes) { TextFieldState(freezeRefreezeDelayMinutes.toString()) }
    val delayError = stringResource(R.string.pref_freeze_refreeze_delay_error)

    val freezeEnabledTitle = stringResource(R.string.pref_freeze_enabled_title)
    SettingsSwitchItem(
        title = freezeEnabledTitle,
        summary = stringResource(R.string.pref_freeze_enabled_summary),
        checked = freezeEnabled,
    ) { enabled ->
        viewModel.setFreezeEnabled(enabled) { success ->
            if (success) notifyPrefChanged(context)
            showSwitchFeedback(freezeEnabledTitle, enabled, success)
        }
    }

    if (freezeEnabled) {
        val policyLabels = POLICY_OPTIONS.map { stringResource(it.labelRes) }
        val selectedPolicyIndex = POLICY_OPTIONS.indexOfFirst { it.value == freezeRefreezePolicy }.coerceAtLeast(0)
        val policyTitle = stringResource(R.string.pref_freeze_refreeze_policy_title)
        fun applyPolicy(index: Int) {
            val newPolicy = POLICY_OPTIONS[index].value
            viewModel.setFreezeRefreezePolicy(newPolicy) { success ->
                if (success) notifyPrefChanged(context)
                showSwitchFeedback(policyTitle, true, success)
            }
        }
        when (currentUiKitStyle()) {
            UiKitStyle.Miuix -> OverlayDropdownPreference(
                title = policyTitle,
                summary = policyLabels[selectedPolicyIndex],
                items = policyLabels,
                selectedIndex = selectedPolicyIndex,
                onSelectedIndexChange = ::applyPolicy,
            )

            UiKitStyle.Expressive -> SettingsItem(
                title = policyTitle,
                summary = policyLabels[selectedPolicyIndex],
            ) {
                pendingPolicy = POLICY_OPTIONS[selectedPolicyIndex].value
                showPolicyDialog = true
            }
        }

        if (freezeRefreezePolicy == FREEZE_REFREEZE_POLICY_TIMED) {
            SettingsItem(
                title = stringResource(R.string.pref_freeze_refreeze_delay_title),
                summary = stringResource(
                    R.string.pref_freeze_refreeze_delay_summary,
                    freezeRefreezeDelayMinutes,
                ),
            ) {
                delayInputState.setTextAndPlaceCursorAtEnd(freezeRefreezeDelayMinutes.toString())
                showDelayDialog = true
            }
        }
    }

    if (showPolicyDialog) {
        AppAlertDialog(
            onDismissRequest = { showPolicyDialog = false },
            title = { Text(stringResource(R.string.pref_freeze_refreeze_policy_title)) },
            text = {
                Column {
                    listOf(
                        FREEZE_REFREEZE_POLICY_NEVER to R.string.pref_freeze_policy_never,
                        FREEZE_REFREEZE_POLICY_SCREEN_OFF to R.string.pref_freeze_policy_screen_off,
                        FREEZE_REFREEZE_POLICY_TIMED to R.string.pref_freeze_policy_timed,
                        FREEZE_REFREEZE_POLICY_TASK_REMOVED to R.string.pref_freeze_policy_task_removed,
                    ).forEach { (value, labelRes) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { pendingPolicy = value },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppRadioButton(
                                selected = pendingPolicy == value,
                                onClick = { pendingPolicy = value },
                            )
                            Text(stringResource(labelRes))
                        }
                    }
                }
            },
            confirmButton = {
                AppTextButton(
                    text = stringResource(android.R.string.ok),
                    onClick = {
                        viewModel.setFreezeRefreezePolicy(pendingPolicy) { success ->
                            if (success) notifyPrefChanged(context)
                            showSwitchFeedback(
                                context.getString(R.string.pref_freeze_refreeze_policy_title),
                                true,
                                success,
                            )
                        }
                        showPolicyDialog = false
                    },
                )
            },
            dismissButton = {
                AppTextButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = { showPolicyDialog = false },
                )
            },
        )
    }

    if (showDelayDialog) {
        AppAlertDialog(
            onDismissRequest = { showDelayDialog = false },
            title = { Text(stringResource(R.string.pref_freeze_refreeze_delay_title)) },
            text = {
                AppTextField(
                    state = delayInputState,
                    supportingText = { Text(stringResource(R.string.pref_freeze_refreeze_delay_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                AppTextButton(
                    text = stringResource(android.R.string.ok),
                    onClick = {
                        val minutes = delayInputState.text.toString().toIntOrNull()
                        if (minutes == null || minutes < 1 || minutes > MAX_FREEZE_REFREEZE_DELAY_MINUTES) {
                            scope.launch {
                                snackbarHostState.showSnackbar(delayError)
                            }
                        } else {
                            viewModel.setFreezeRefreezeDelayMinutes(minutes) { success ->
                                if (success) notifyPrefChanged(context)
                                showSwitchFeedback(
                                    context.getString(R.string.pref_freeze_refreeze_delay_title),
                                    true,
                                    success,
                                )
                            }
                            showDelayDialog = false
                        }
                    },
                )
            },
            dismissButton = {
                AppTextButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = { showDelayDialog = false },
                )
            },
        )
    }
}

private fun policySummaryRes(policy: Int): Int = when (policy) {
    FREEZE_REFREEZE_POLICY_NEVER -> R.string.pref_freeze_policy_never
    FREEZE_REFREEZE_POLICY_SCREEN_OFF -> R.string.pref_freeze_policy_screen_off
    FREEZE_REFREEZE_POLICY_TIMED -> R.string.pref_freeze_policy_timed
    FREEZE_REFREEZE_POLICY_TASK_REMOVED -> R.string.pref_freeze_policy_task_removed
    else -> R.string.pref_freeze_policy_screen_off
}
