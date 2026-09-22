@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.magisk317.mipush.feature.main.subpage

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magisk317.uikit.surface.PageScaffoldExpressive
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.uikit.surface.AppCircularProgressIndicator
import io.github.magisk317.uikit.surface.AppIconButton

/** Expressive/Material chrome for the connection-status page. */
@Composable
internal fun ConnectionStatusExpressive(
    onBack: () -> Unit,
    isReconnecting: Boolean,
    isRefreshing: Boolean,
    onReconnect: () -> Unit,
    onRefresh: () -> Unit,
    body: @Composable (PaddingValues, Modifier) -> Unit,
) {
    PageScaffoldExpressive(
        title = stringResource(R.string.connection_status_title),
        onBack = onBack,
        actions = {
            AppIconButton(
                onClick = onReconnect,
                enabled = !isReconnecting,
            ) {
                if (isReconnecting) {
                    AppCircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = stringResource(R.string.connection_status_force_reconnect),
                    )
                }
            }
            AppIconButton(
                onClick = onRefresh,
                enabled = !isRefreshing,
            ) {
                if (isRefreshing) {
                    AppCircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.connection_status_refresh),
                    )
                }
            }
        },
        content = body,
    )
}
