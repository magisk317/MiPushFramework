package top.trumeet.mipushframework.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import top.trumeet.ui.theme.spacing

enum class DialogActionStyle {
    Primary,
    Secondary,
    Danger,
}

data class DialogAction(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val style: DialogActionStyle = DialogActionStyle.Primary,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DialogActionRow(
    actions: List<DialogAction>,
    modifier: Modifier = Modifier,
) {
    if (actions.isEmpty()) return

    ButtonGroup(
        overflowIndicator = { menuState ->
            ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
        },
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        actions.forEach { action ->
            clickableItem(
                onClick = action.onClick,
                enabled = action.enabled,
                label = action.label,
                weight = 1f,
            )
        }
    }
}
