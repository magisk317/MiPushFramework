@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.magisk317.mipush.feature.main.subpage

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.main.viewmodel.SettingsViewModel
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.uikit.preference.StateSwitchItem as SettingsSwitchItem
import kotlinx.coroutines.launch

@Composable
internal fun KeepAliveBlock(viewModel: SettingsViewModel, snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val isStartForeground by viewModel.isStartForeground.collectAsStateWithLifecycle()
    val keepAliveOomAdj by viewModel.keepAliveOomAdj.collectAsStateWithLifecycle()
    val keepAliveAntiKill by viewModel.keepAliveAntiKill.collectAsStateWithLifecycle()
    val keepAliveStandbyBypass by viewModel.keepAliveStandbyBypass.collectAsStateWithLifecycle()
    val keepAliveDozeBypass by viewModel.keepAliveDozeBypass.collectAsStateWithLifecycle()
    val showSwitchFeedback = rememberSwitchFeedback(snackbarHostState)
    val lifecycleOwner = LocalLifecycleOwner.current
    var accessibilityStatusRefresh by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityStatusRefresh += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val keepAliveAccessibilityServiceEnabled = remember(context, accessibilityStatusRefresh) {
        isKeepAliveAccessibilityServiceEnabled(context)
    }
    val activityIntentNotFoundMessage = stringResource(R.string.activity_intent_not_found)

    val startForegroundTitle = stringResource(R.string.settings_start_foreground_service)
    SettingsSwitchItem(
        title = startForegroundTitle,
        summary = stringResource(R.string.settings_start_foreground_service_summary),
        checked = isStartForeground,
    ) { enabled ->
        viewModel.setStartForeground(enabled) { success ->
            showSwitchFeedback(startForegroundTitle, enabled, success)
        }
    }

    if (isStartForeground) {
        val keepAliveOomAdjTitle = stringResource(R.string.pref_keepalive_oom_adj_title)
        SettingsSwitchItem(
            title = keepAliveOomAdjTitle,
            summary = stringResource(R.string.pref_keepalive_oom_adj_summary),
            checked = keepAliveOomAdj,
        ) { enabled ->
            viewModel.setKeepAliveOomAdj(enabled) { success ->
                showSwitchFeedback(keepAliveOomAdjTitle, enabled, success)
            }
        }

        val keepAliveAntiKillTitle = stringResource(R.string.pref_keepalive_anti_kill_title)
        SettingsSwitchItem(
            title = keepAliveAntiKillTitle,
            summary = stringResource(R.string.pref_keepalive_anti_kill_summary),
            checked = keepAliveAntiKill,
        ) { enabled ->
            viewModel.setKeepAliveAntiKill(enabled) { success ->
                showSwitchFeedback(keepAliveAntiKillTitle, enabled, success)
            }
        }

        val keepAliveStandbyBypassTitle = stringResource(R.string.pref_keepalive_standby_bypass_title)
        SettingsSwitchItem(
            title = keepAliveStandbyBypassTitle,
            summary = stringResource(R.string.pref_keepalive_standby_bypass_summary),
            checked = keepAliveStandbyBypass,
        ) { enabled ->
            viewModel.setKeepAliveStandbyBypass(enabled) { success ->
                showSwitchFeedback(keepAliveStandbyBypassTitle, enabled, success)
            }
        }

        val keepAliveDozeBypassTitle = stringResource(R.string.pref_keepalive_doze_bypass_title)
        SettingsSwitchItem(
            title = keepAliveDozeBypassTitle,
            summary = stringResource(R.string.pref_keepalive_doze_bypass_summary),
            checked = keepAliveDozeBypass,
        ) { enabled ->
            viewModel.setKeepAliveDozeBypass(enabled) { success ->
                showSwitchFeedback(keepAliveDozeBypassTitle, enabled, success)
            }
        }

        SettingsSwitchItem(
            title = stringResource(R.string.pref_keepalive_dedicated_service_title),
            summary = stringResource(
                if (keepAliveAccessibilityServiceEnabled) {
                    R.string.pref_keepalive_dedicated_service_enabled_summary
                } else {
                    R.string.pref_keepalive_dedicated_service_disabled_summary
                }
            ),
            checked = keepAliveAccessibilityServiceEnabled,
        ) { enabled ->
            scope.launch {
                val success = toggleAccessibilityServiceViaRoot(context, enabled)
                if (success) {
                    accessibilityStatusRefresh += 1
                } else {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    runCatching {
                        context.startActivity(intent)
                    }.onFailure {
                        snackbarHostState.showSnackbar(
                            message = activityIntentNotFoundMessage,
                            duration = SnackbarDuration.Short,
                        )
                    }
                }
            }
        }
    }
}

internal fun isKeepAliveAccessibilityServiceEnabled(context: Context): Boolean {
    val accessibilityEnabled = runCatching {
        Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
    }.getOrDefault(0)
    if (accessibilityEnabled != 1) {
        return false
    }

    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    return enabledServices.split(':').any { service ->
        val component = ComponentName.unflattenFromString(service) ?: return@any false
        component.packageName == Constants.SERVICE_APP_NAME &&
            component.className == Constants.KEEPALIVE_ACCESSIBILITY_SERVICE_CLASS
    }
}
