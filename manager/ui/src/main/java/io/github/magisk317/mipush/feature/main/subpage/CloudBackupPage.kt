package io.github.magisk317.mipush.feature.main.subpage

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.mipush.main.viewmodel.CloudBackupViewModel
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.uikit.backup.CloudBackupPanel
import io.github.magisk317.uikit.common.AppSnackbarHostState
import io.github.magisk317.uikit.foundation.LocalSnackbarHostState
import io.github.magisk317.uikit.surface.ConfirmActionDialog
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle

/**
 * Cloud backup settings page: hosts the ui-kit [CloudBackupPanel] and adds the
 * host-owned restore confirmation. All provider logic lives in
 * [CloudBackupViewModel]; this composable only maps user gestures to it.
 */
@Composable
fun CloudBackupPage(
    viewModel: CloudBackupViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { AppSnackbarHostState() }
    var pendingRestoreId by remember { mutableStateOf<String?>(null) }

    val body: @Composable (PaddingValues, Modifier) -> Unit = { listPadding, _ ->
        CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
            CloudBackupPanel(
                state = state,
                actions = viewModel.actions.copy(
                    onRestoreBackup = { backupId -> pendingRestoreId = backupId },
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = listPadding.calculateTopPadding()),
            )
        }
    }

    when (currentUiKitStyle()) {
        UiKitStyle.Miuix -> CloudBackupMiuix(
            onBack = onBack,
            snackbarHostState = snackbarHostState,
            body = body,
        )

        UiKitStyle.Expressive -> CloudBackupMaterial(
            onBack = onBack,
            snackbarHostState = snackbarHostState,
            body = body,
        )
    }

    pendingRestoreId?.let { backupId ->
        ConfirmActionDialog(
            title = stringResource(R.string.cloud_backup_restore_confirm_title),
            message = stringResource(R.string.cloud_backup_restore_confirm_message),
            confirmText = stringResource(R.string.cloud_backup_restore_confirm_action),
            onDismissRequest = { pendingRestoreId = null },
            onConfirm = {
                pendingRestoreId = null
                viewModel.restoreBackup(backupId)
            },
        )
    }
}
