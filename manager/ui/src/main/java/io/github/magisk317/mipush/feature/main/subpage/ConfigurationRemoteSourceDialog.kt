@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.magisk317.mipush.feature.main.subpage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import io.github.magisk317.uikit.surface.AppAlertDialog
import io.github.magisk317.uikit.surface.DialogAction
import io.github.magisk317.uikit.surface.DialogActionRow
import io.github.magisk317.uikit.surface.DialogActionStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import io.github.magisk317.uikit.surface.AppTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.mipush.feature.ui.theme.spacing


@Composable
internal fun RemoteSourceDialog(
    repository: String,
    branch: String,
    accelerator: String,
    defaultRepository: String,
    defaultBranch: String,
    onRepositoryChange: (String) -> Unit,
    onBranchChange: (String) -> Unit,
    onAcceleratorChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onResetDefault: () -> Unit,
    onConfirm: () -> Unit,
) {
    // State-based fields re-seed whenever the parent pushes new values (initial
    // open, reset-to-default); edits are pushed up once on confirm.
    key(repository, branch, accelerator) {
        val repositoryState = rememberTextFieldState(repository)
        val branchState = rememberTextFieldState(branch)
        val acceleratorState = rememberTextFieldState(accelerator)
        AppAlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.config_remote_source_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                    AppTextField(
                        state = repositoryState,
                        label = stringResource(R.string.config_remote_repository_label),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                        singleLine = true,
                    )
                    AppTextField(
                        state = branchState,
                        label = stringResource(R.string.config_remote_branch_label),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                        singleLine = true,
                    )
                    AppTextField(
                        state = acceleratorState,
                        label = stringResource(R.string.config_remote_accelerator_label),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                        singleLine = true,
                    )
                    Text(
                        text = stringResource(
                            R.string.config_remote_source_default_hint,
                            "${defaultRepository}@${defaultBranch}",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                DialogActionRow(
                    actions = listOf(
                        DialogAction(
                            label = stringResource(R.string.action_reset_default),
                            onClick = onResetDefault,
                            style = DialogActionStyle.Secondary,
                        ),
                        DialogAction(
                            label = stringResource(android.R.string.cancel),
                            onClick = onDismiss,
                            style = DialogActionStyle.Secondary,
                        ),
                        DialogAction(
                            label = stringResource(android.R.string.ok),
                            onClick = {
                                onRepositoryChange(repositoryState.text.toString())
                                onBranchChange(branchState.text.toString())
                                onAcceleratorChange(acceleratorState.text.toString())
                                onConfirm()
                            },
                            style = DialogActionStyle.Primary,
                        ),
                    ),
                )
            },
        )
    }
}
