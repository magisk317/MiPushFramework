package io.github.magisk317.mipush.feature.main.subpage

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magisk317.mipush.feature.ui.shell.PageScaffoldMiuix
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.uikit.common.AppSnackbarHost
import io.github.magisk317.uikit.common.AppSnackbarHostState

/** Miuix chrome for [StatusBarIconSettingsPage]. */
@Composable
internal fun StatusBarIconSettingsMiuix(
    onBack: () -> Unit,
    snackbarHostState: AppSnackbarHostState,
    body: @Composable (PaddingValues, Modifier) -> Unit,
) {
    PageScaffoldMiuix(
        title = stringResource(R.string.pref_color_status_bar_icon_title),
        onBack = onBack,
        snackbarHost = {
            AppSnackbarHost(
                hostState = snackbarHostState,
                bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp,
            )
        },
        content = body,
    )
}
