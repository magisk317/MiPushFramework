@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.magisk317.mipush.feature.main.subpage

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.magisk317.uikit.surface.AppIconButton
import io.github.magisk317.uikit.surface.AppTopBar
import androidx.compose.material3.Icon

/** Expressive/Material chrome for the configuration editor screen. */
@Composable
internal fun ConfigurationEditorExpressive(
    path: String,
    onBack: () -> Unit,
    body: @Composable (PaddingValues, Modifier) -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopBar(
                title = path,
                navigationIcon = {
                    AppIconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(android.R.string.cancel),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        body(PaddingValues(top = innerPadding.calculateTopPadding()), Modifier)
    }
}
