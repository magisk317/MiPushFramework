package io.github.magisk317.mipush.feature.main.subpage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.mipush.main.viewmodel.SettingsViewModel
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.uikit.common.AppSnackbarHostState
import io.github.magisk317.uikit.preference.StateSwitchItem as SettingsSwitchItem

/**
 * Frozen apps keep receiving notifications; tapping one routes through XMSF, which re-enables the
 * package before pulling it up. Whether it gets frozen again is left to the platform and to
 * whichever keep-alive module the user pairs with this one.
 */
@Composable
internal fun FreezeBlock(viewModel: SettingsViewModel, snackbarHostState: AppSnackbarHostState) {
    val context = LocalContext.current
    val freezeEnabled by viewModel.freezeEnabled.collectAsStateWithLifecycle()
    val showSwitchFeedback = rememberSwitchFeedback(snackbarHostState)

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
}
