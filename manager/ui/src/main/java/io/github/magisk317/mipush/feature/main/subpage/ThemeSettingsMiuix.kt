package io.github.magisk317.mipush.feature.main.subpage

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.magisk317.mipush.feature.ui.shell.PageScaffoldMiuix
import io.github.magisk317.mipush.manager.R

/** Miuix chrome for [ThemeSettingsPage]. */
@Composable
internal fun ThemeSettingsMiuix(
    onBack: () -> Unit,
    body: @Composable (PaddingValues, Modifier) -> Unit,
) {
    PageScaffoldMiuix(
        title = stringResource(R.string.pref_theme_details_title),
        onBack = onBack,
        content = body,
    )
}
