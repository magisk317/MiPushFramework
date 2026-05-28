package io.github.magisk317.mipush.feature.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xiaomi.xmsf.R
import io.github.magisk317.mipush.feature.ui.theme.spacing

@Composable
fun SettingsItem(
    title: String,
    summary: String? = null,
    content: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    io.github.magisk317.uikit.preference.Item(
        title = title,
        summary = summary.orEmpty(),
        enabled = enabled,
        trailingContent = content,
        onClick = onClick,
    )
}

@Composable
fun SettingsListItem(
    title: String,
    summary: String,
    values: Array<String>,
    selected: Int,
    onValueSelected: (Int) -> Unit,
    onValueSelectedWithPosition: ((index: Int, x: Float, y: Float) -> Unit)? = null,
) {
    var shouldShowDialog by remember { mutableStateOf(false) }
    SettingsDialogItem(
        title = title,
        summary = summary,
        confirmButton = {},
        content = {
            ItemLists(selected, values) { index, clickX, clickY ->
                if (onValueSelectedWithPosition != null) {
                    onValueSelectedWithPosition(index, clickX, clickY)
                } else {
                    onValueSelected(index)
                }
                shouldShowDialog = false
            }
        },
        actions = listOf(
            DialogAction(
                label = stringResource(android.R.string.cancel),
                onClick = { shouldShowDialog = false }
            )
        ),
        onClick = { shouldShowDialog = true },
        shouldShowDialog = shouldShowDialog,
        onDismiss = { shouldShowDialog = false }
    )
}

@Composable
fun SettingsDialogItem(
    title: String,
    summary: String,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    actions: List<DialogAction> = emptyList(),
    onClick: () -> Unit,
    shouldShowDialog: Boolean,
    onDismiss: () -> Unit,
    isDragging: Boolean = false,
    content: @Composable () -> Unit
) {
    SettingsItem(
        title = title,
        summary = summary,
        onClick = onClick,
        content = {
            SettingsDialog(
                title = title,
                shouldShowDialog = shouldShowDialog,
                onDismiss = onDismiss,
                confirmButton = confirmButton,
                dismissButton = dismissButton,
                actions = actions,
                isDragging = isDragging,
                content = content
            )
        }
    )
}

@Composable
fun SettingsDialog(
    title: String,
    shouldShowDialog: Boolean,
    onDismiss: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    actions: List<DialogAction> = emptyList(),
    isDragging: Boolean = false,
    content: @Composable () -> Unit
) {
    if (!shouldShowDialog) return

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogWindowProvider = androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider
        androidx.compose.runtime.SideEffect {
            dialogWindowProvider?.window?.let { window ->
                window.setDimAmount(if (isDragging) 0f else 0.5f)
                window.setBackgroundDrawableResource(android.R.color.transparent)
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Surface(
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = if (isDragging) 0.dp else 6.dp,
                color = if (isDragging) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .width(androidx.compose.ui.unit.Dp.Unspecified)
                    .widthIn(min = 280.dp, max = 560.dp)
                    .heightIn(max = 560.dp)
                    .padding(MaterialTheme.spacing.superLarge)
            ) {
                Column(
                    modifier = Modifier.padding(MaterialTheme.spacing.superLarge)
                ) {
                    if (!isDragging) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                    
                    Box(modifier = Modifier.weight(1f, fill = false)) {
                        content()
                    }

                    if (!isDragging) {
                        Spacer(modifier = Modifier.height(MaterialTheme.spacing.superLarge))

                        if (actions.isNotEmpty()) {
                            DialogActionRow(actions = actions)
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
                            ) {
                                dismissButton?.invoke()
                                Spacer(modifier = Modifier.width(MaterialTheme.spacing.small))
                                confirmButton()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemLists(
    selected: Int,
    values: Array<String>,
    onSelect: (Int, Float, Float) -> Unit
) {
    LazyColumn {
        itemsIndexed(values) { index, item ->
            var rowOffset by remember { mutableStateOf(Offset.Zero) }
            Row(
                Modifier
                    .onGloballyPositioned { coordinates ->
                        rowOffset = coordinates.localToRoot(Offset.Zero)
                    }
                    .clickable(role = Role.RadioButton) {
                        // Use row center as reveal origin for theme transition.
                        onSelect(index, rowOffset.x + 24f, rowOffset.y + 24f)
                    }
                    .fillMaxWidth()
                    .padding(top = MaterialTheme.spacing.small, bottom = MaterialTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(index == selected, onClick = null)
                Spacer(modifier = Modifier.width(MaterialTheme.spacing.extraSmall))
                Text(text = item)
            }
        }
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
             // Reduce horizontal padding as ListItem has its own padding, but keep top/bottom for separation
            .padding(vertical = MaterialTheme.spacing.small)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.large,
                vertical = MaterialTheme.spacing.small
            )
        )
        content()
    }
}

@Composable
fun SettingsSwitchItem(
    title: String,
    summary: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    io.github.magisk317.uikit.preference.StateSwitchItem(
        title = title,
        summary = summary.orEmpty(),
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
    )
}

// ItemInfo is no longer needed with ListItem

@Preview(showBackground = true)
@Composable
fun InfoDialogPreview() {
    SettingsDialog(
        title = stringResource(R.string.settings_mock_notification),
        shouldShowDialog = true,
        {}, {}
    ) {
        ItemLists(
            selected = 0,
            values = arrayOf("Option A", "Option B"),
            onSelect = { _, _, _ -> }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsItemPreview() {
    SettingsSwitchItem(
        title = stringResource(R.string.settings_start_foreground_service),
        summary = stringResource(R.string.settings_start_foreground_service_summary),
        checked = false,
        enabled = false,
        onCheckedChange = { }
    )
}

@Preview(showBackground = true)
@Composable
fun SingleLineSettingsItemPreview() {
    SettingsItem(
        title = stringResource(R.string.settings_start_foreground_service)
    ) {}
}
