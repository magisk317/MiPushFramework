package io.github.magisk317.mipush.feature.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import io.github.magisk317.uikit.surface.AppCard
import io.github.magisk317.uikit.surface.AppCircularProgressIndicator
import io.github.magisk317.uikit.surface.AppBadge
import io.github.magisk317.uikit.surface.AppPrimaryButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.magisk317.mipush.manager.application.ManagerApplication
import io.github.magisk317.mipush.manager.notification.NotificationChannelReadStatus
import io.github.magisk317.mipush.manager.notification.NotificationChannelSnapshot
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.uikit.preference.AppSwitch
import io.github.magisk317.uikit.surface.DetailDivider
import io.github.magisk317.uikit.theme.spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class NotificationChannelContentKind {
    EMPTY,
    HIDDEN,
    VISIBLE,
}

internal fun notificationChannelContentKind(
    snapshot: NotificationChannelSnapshot,
    sections: List<NotificationChannelSection>,
): NotificationChannelContentKind = when {
    snapshot.channels.isEmpty() -> NotificationChannelContentKind.EMPTY
    sections.isEmpty() -> NotificationChannelContentKind.HIDDEN
    else -> NotificationChannelContentKind.VISIBLE
}

@Composable
internal fun NotificationChannelsLoadingRow(showDivider: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppCircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
        )
        Text(
            text = stringResource(R.string.notification_channels_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
    if (showDivider) {
        DetailDivider()
    }
}

@Composable
internal fun notificationChannelsUnavailableMessage(status: NotificationChannelReadStatus): String =
    stringResource(
        when (status) {
            NotificationChannelReadStatus.RUNTIME_MISSING ->
                R.string.notification_channels_unavailable_runtime_missing
            NotificationChannelReadStatus.PERMISSION_DENIED ->
                R.string.notification_channels_unavailable_permission_denied
            NotificationChannelReadStatus.BINDING ->
                R.string.notification_channels_unavailable_binding
            NotificationChannelReadStatus.INCOMPATIBLE,
            NotificationChannelReadStatus.UNSUPPORTED,
            -> R.string.notification_channels_unavailable_incompatible
            NotificationChannelReadStatus.DISCONNECTED,
            NotificationChannelReadStatus.TIMED_OUT,
            NotificationChannelReadStatus.TEMPORARILY_DISCONNECTED,
            -> R.string.notification_channels_unavailable_disconnected
            NotificationChannelReadStatus.FAILED -> R.string.notification_channels_unavailable_failed
        },
    )


@Composable
internal fun NotificationChannelSectionHeader(
    title: String,
    summary: String,
    showTopDivider: Boolean,
) {
    if (showTopDivider) {
        DetailDivider()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f),
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.spacing.large,
                    end = MaterialTheme.spacing.large,
                    top = MaterialTheme.spacing.medium,
                    bottom = MaterialTheme.spacing.small,
                ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
            if (summary.isNotBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
internal fun NotificationChannelRow(
    badge: String,
    disabledBadge: String,
    enabled: Boolean,
    title: String,
    summary: String,
    showDivider: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = MaterialTheme.spacing.large,
                vertical = MaterialTheme.spacing.medium,
            ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NotificationChannelBadge(text = badge)
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f, fill = false),
                softWrap = true,
                overflow = TextOverflow.Clip,
                maxLines = 8,
            )
            if (!enabled) {
                NotificationChannelBadge(
                    text = disabledBadge,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        if (summary.isNotBlank()) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                softWrap = true,
                overflow = TextOverflow.Clip,
                maxLines = 8,
            )
        }
    }
    if (showDivider) {
        DetailDivider()
    }
}

@Composable
internal fun NotificationChannelBadge(
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    AppBadge(
        text = text,
        containerColor = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
internal fun ActionSummaryRow(
    title: String,
    summary: String,
    actionLabel: String,
    enabled: Boolean = true,
    showDivider: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(MaterialTheme.spacing.medium))
        AppPrimaryButton(
            onClick = onClick,
            enabled = enabled,
        ) {
            Text(actionLabel)
        }
    }
    if (showDivider) {
        DetailDivider()
    }
}

@Composable
internal fun SettingSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean = true,
    showDivider: Boolean = false,
    onClickWhenDisabled: (() -> Unit)? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (!enabled && onClickWhenDisabled != null) {
                    Modifier.clickable { onClickWhenDisabled() }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
        }
        Spacer(Modifier.width(MaterialTheme.spacing.medium))
        val switchModifier = if (!enabled && onClickWhenDisabled != null) {
            Modifier.clickable(onClick = onClickWhenDisabled)
        } else {
            Modifier
        }
        Box(modifier = switchModifier) {
            AppSwitch(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                enabled = enabled,
            )
        }
    }
    if (showDivider) {
        DetailDivider()
    }
}

@Composable
internal fun HeaderMetricCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    AppCard(
        modifier = modifier,
        color = if (io.github.magisk317.uikit.theme.currentUiKitStyle() == io.github.magisk317.uikit.theme.UiKitStyle.Miuix) {
            top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(accent.copy(alpha = 0.14f))
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
            )
        }
    }
}

internal fun formatTime(time: Long?): String {
    if (time == null || time <= 0L) return "-"
    return Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault()))
}

@Composable
internal fun Tips(description: String) {
    Row(
        modifier = Modifier.padding(MaterialTheme.spacing.large),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    color = MaterialTheme.colorScheme.error,
                    shape = RoundedCornerShape(999.dp),
                ),
        )
        Spacer(Modifier.width(MaterialTheme.spacing.medium))

        val annotatedText = remember(description) {
            AnnotatedString.fromHtml(
                htmlString = description,
            )
        }
        Text(
            text = annotatedText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsPreview() {
    val context = LocalContext.current
    Utils.context = context

    val app = ManagerApplication(
        packageName = Constants.SERVICE_APP_NAME,
        appName = "test app",
    )
    val page = ApplicationInfoPage()
    page.init(app)
    page.SettingsApp()
}
