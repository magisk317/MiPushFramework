@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.magisk317.mipush.feature.main.subpage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import kotlinx.coroutines.launch

private const val MAX_FREEZE_REFREEZE_DELAY_MINUTES = 120

@Composable
internal fun FreezeBlock(viewModel: SettingsViewModel, snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val freezeEnabled by viewModel.freezeEnabled.collectAsStateWithLifecycle()
    val freezeRefreezePolicy by viewModel.freezeRefreezePolicy.collectAsStateWithLifecycle()
    val freezeRefreezeDelayMinutes by viewModel.freezeRefreezeDelayMinutes.collectAsStateWithLifecycle()
    val showSwitchFeedback = rememberSwitchFeedback(snackbarHostState)
    var showPolicyDialog by remember { mutableStateOf(false) }
    var pendingPolicy by remember { mutableStateOf(FREEZE_REFREEZE_POLICY_SCREEN_OFF) }
    var showDelayDialog by remember { mutableStateOf(false) }
    var delayInput by remember(freezeRefreezeDelayMinutes) { mutableStateOf(freezeRefreezeDelayMinutes.toString()) }
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
        SettingsItem(
            title = stringResource(R.string.pref_freeze_refreeze_policy_title),
            summary = stringResource(policySummaryRes(freezeRefreezePolicy)),
        ) {
            pendingPolicy = freezeRefreezePolicy
            showPolicyDialog = true
        }

        if (freezeRefreezePolicy == FREEZE_REFREEZE_POLICY_TIMED) {
            SettingsItem(
                title = stringResource(R.string.pref_freeze_refreeze_delay_title),
                summary = stringResource(
                    R.string.pref_freeze_refreeze_delay_summary,
                    freezeRefreezeDelayMinutes,
                ),
            ) {
                delayInput = freezeRefreezeDelayMinutes.toString()
                showDelayDialog = true
            }
        }
    }

    if (showPolicyDialog) {
        AlertDialog(
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
                            RadioButton(
                                selected = pendingPolicy == value,
                                onClick = { pendingPolicy = value },
                            )
                            Text(stringResource(labelRes))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
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
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPolicyDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    if (showDelayDialog) {
        AlertDialog(
            onDismissRequest = { showDelayDialog = false },
            title = { Text(stringResource(R.string.pref_freeze_refreeze_delay_title)) },
            text = {
                TextField(
                    value = delayInput,
                    onValueChange = { value ->
                        delayInput = value.filter { it.isDigit() }
                    },
                    supportingText = { Text(stringResource(R.string.pref_freeze_refreeze_delay_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val minutes = delayInput.toIntOrNull()
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
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelayDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
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