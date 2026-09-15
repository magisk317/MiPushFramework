package io.github.magisk317.mipush.feature.main.subpage

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.magisk317.mipush.feature.ui.shell.PageScaffoldExpressive
import io.github.magisk317.mipush.manager.R

/** Expressive/Material chrome for [ThemeSettingsPage]. */
@Composable
internal fun ThemeSettingsExpressive(
    onBack: () -> Unit,
    body: @Composable (PaddingValues, Modifier) -> Unit,
) {
    PageScaffoldExpressive(
        title = stringResource(R.string.pref_theme_details_title),
        onBack = onBack,
        content = body,
    )
}
