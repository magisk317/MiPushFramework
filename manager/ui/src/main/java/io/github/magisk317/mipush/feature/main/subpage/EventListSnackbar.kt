package io.github.magisk317.mipush.feature.main.subpage

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import io.github.magisk317.uikit.common.AppSnackbarHost
import io.github.magisk317.uikit.common.AppSnackbarHostState
import io.github.magisk317.mipush.feature.ui.theme.spacing

/** Shared event-tab snackbar host used by both Miuix and Expressive chrome. */
@Composable
internal fun EventTabSnackbarHost(
    snackbarHostState: AppSnackbarHostState,
    contentBottomPadding: Dp,
) {
    AppSnackbarHost(
        hostState = snackbarHostState,
        bottomPadding = contentBottomPadding +
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
            MaterialTheme.spacing.medium,
        modifier = Modifier.padding(
            start = MaterialTheme.spacing.medium,
            end = MaterialTheme.spacing.medium,
        ),
        snackbar = { data -> DeleteCountdownSnackbar(data) },
    )
}
