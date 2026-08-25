package io.github.magisk317.mipush.feature.main.subpage

import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.mipush.main.viewmodel.XmppServerSaveResult
import io.github.magisk317.mipush.main.viewmodel.XmppServerUiState
import io.github.magisk317.mipush.main.viewmodel.XmppServerViewModel
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.uikit.surface.AppAlertDialog
import io.github.magisk317.uikit.surface.DialogAction
import io.github.magisk317.uikit.surface.DialogActionRow
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun XmppServerEditor(
    viewModel: XmppServerViewModel = koinViewModel(),
    entry: @Composable (XmppServerUiState, onEdit: () -> Unit) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val savedMessage = stringResource(R.string.settings_XMPP_server_saved)
    val failedMessage = stringResource(R.string.settings_XMPP_server_save_failed)

    LaunchedEffect(uiState.saveResult) {
        val result = uiState.saveResult ?: return@LaunchedEffect
        val message = when (result) {
            XmppServerSaveResult.SAVED -> {
                showDialog = false
                savedMessage
            }
            XmppServerSaveResult.FAILED -> failedMessage
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.consumeSaveResult()
    }

    entry(uiState) {
        if (uiState.isLoaded && !uiState.isSaving) {
            showDialog = true
        }
    }

    if (showDialog) {
        XmppServerDialog(
            currentServer = uiState.configuredServer,
            serverHint = stringResource(R.string.settings_XMPP_server_hint),
            isSaving = uiState.isSaving || !uiState.isLoaded,
            onDismissRequest = {
                if (!uiState.isSaving) showDialog = false
            },
            onConfirm = viewModel::save,
        )
    }
}

@Composable
internal fun XmppServerDialog(
    currentServer: String?,
    serverHint: String,
    isSaving: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(currentServer.orEmpty()) }

    AppAlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.settings_XMPP_server)) },
        text = {
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(serverHint) },
                singleLine = true,
                enabled = !isSaving,
            )
        },
        confirmButton = {},
        dismissButton = {
            DialogActionRow(
                actions = listOf(
                    DialogAction(
                        label = stringResource(android.R.string.cancel),
                        onClick = onDismissRequest,
                        enabled = !isSaving,
                    ),
                    DialogAction(
                        label = stringResource(android.R.string.ok),
                        onClick = { onConfirm(text) },
                        enabled = !isSaving,
                    ),
                ),
            )
        },
    )
}
