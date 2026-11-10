package io.github.magisk317.mipush.feature.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.mipush.manager.application.ManagerApplication
import io.github.magisk317.uikit.surface.AppCard
import io.github.magisk317.uikit.surface.AppIcon
import io.github.magisk317.uikit.surface.AppIconButton
import io.github.magisk317.uikit.surface.AppIconImage
import io.github.magisk317.uikit.surface.DetailDivider
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import io.github.magisk317.uikit.theme.AppColorRole
import io.github.magisk317.uikit.theme.appColor
import io.github.magisk317.uikit.theme.spacing

@Composable
internal fun ApplicationDetailHeading(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        AppIconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            AppIcon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.app_detail_back),
            )
        }
        AppText(
            text = stringResource(R.string.app_detail_title),
            role = AppTextRole.Body,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = appColor(AppColorRole.OnSurface),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun ApplicationIdentityCard(info: ManagerApplication) {
    AppCard(color = appColor(AppColorRole.SurfaceContainerLow)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                AppIconImage(packageName = info.packageName, modifier = Modifier.size(52.dp))
                Column(modifier = Modifier.weight(1f)) {
                    AppText(
                        text = info.appName,
                        role = AppTextRole.Title,
                        fontSize = 22.sp,
                        color = appColor(AppColorRole.OnSurface),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    AppText(
                        text = info.packageName,
                        role = AppTextRole.BodySmall,
                        color = appColor(AppColorRole.OnSurfaceVariant),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                ApplicationDetailSummary(
                    label = stringResource(R.string.app_detail_service_status),
                    values = listOf(stringResource(
                        if (info.existServices) R.string.app_detail_service_ready else R.string.mipush_services_not_found,
                    )),
                    modifier = Modifier.weight(1f),
                )
                ApplicationDetailSummary(
                    label = stringResource(R.string.app_detail_registration_status),
                    values = listOf(stringResource(RegistrationStateStyle.registrationLabelResOf(info))),
                    modifier = Modifier.weight(1f),
                )
            }
            ApplicationDetailSummary(
                label = stringResource(R.string.app_detail_last_push),
                values = if (info.lastReceiveTimeMs > 0L) {
                    formatTime(info.lastReceiveTimeMs).split(' ', limit = 2)
                } else {
                    listOf(stringResource(R.string.app_detail_no_push_yet))
                },
                smallValue = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun ApplicationDetailActionRow(
    title: String,
    summary: String,
    actionLabel: String = title,
    enabled: Boolean = true,
    showDivider: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AppText(
                text = title,
                role = AppTextRole.Body,
                color = appColor(AppColorRole.OnSurface).copy(alpha = if (enabled) 1f else 0.38f),
            )
            if (summary.isNotBlank()) {
                AppText(
                    text = summary,
                    role = AppTextRole.BodySmall,
                    color = appColor(AppColorRole.OnSurfaceVariant).copy(alpha = if (enabled) 1f else 0.38f),
                )
            }
            if (actionLabel != title) {
                AppText(text = actionLabel, role = AppTextRole.BodySmall, color = appColor(AppColorRole.Primary))
            }
        }
        AppIcon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = appColor(AppColorRole.OnSurfaceVariant).copy(alpha = if (enabled) 1f else 0.38f),
        )
    }
    if (showDivider) DetailDivider()
}

@Composable
internal fun ApplicationDetailAdvancedCard(
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val expansionState = stringResource(if (expanded) R.string.app_detail_expanded else R.string.app_detail_collapsed)
    AppCard {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .semantics { stateDescription = expansionState }
                    .clickable(role = Role.Button, onClick = onToggle)
                    .heightIn(min = 64.dp)
                    .padding(MaterialTheme.spacing.large),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppText(
                        text = stringResource(R.string.app_detail_advanced),
                        role = AppTextRole.Body,
                        fontWeight = FontWeight.SemiBold,
                        color = appColor(AppColorRole.OnSurface),
                    )
                    AppText(
                        text = stringResource(R.string.app_detail_advanced_summary),
                        role = AppTextRole.BodySmall,
                        color = appColor(AppColorRole.OnSurfaceVariant),
                    )
                }
                AppIcon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = appColor(AppColorRole.OnSurfaceVariant),
                )
            }
            if (expanded) {
                DetailDivider()
                content()
            }
        }
    }
}


@Composable
private fun ApplicationDetailSummary(
    label: String,
    values: List<String>,
    modifier: Modifier = Modifier,
    smallValue: Boolean = false,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AppText(
            text = label,
            role = AppTextRole.Footnote,
            fontSize = 11.sp,
            color = appColor(AppColorRole.OnSurfaceVariant),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            values.forEach { value ->
                AppText(
                    text = value,
                    role = if (smallValue) AppTextRole.BodySmall else AppTextRole.Body,
                    fontSize = if (smallValue) 12.sp else 14.sp,
                    color = appColor(AppColorRole.OnSurface),
                )
            }
        }
    }
}

/** Page-local section proportions; shared typography and system font scaling stay intact. */
@Composable
internal fun ApplicationDetailSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppCard(modifier = modifier.fillMaxWidth(), color = appColor(AppColorRole.SurfaceContainerLow)) {
        Column {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AppText(
                    text = title,
                    role = AppTextRole.Body,
                    fontWeight = FontWeight.SemiBold,
                    color = appColor(AppColorRole.OnSurface),
                )
                if (!summary.isNullOrBlank()) {
                    AppText(text = summary, role = AppTextRole.BodySmall, color = appColor(AppColorRole.OnSurfaceVariant))
                }
            }
            DetailDivider()
            content()
        }
    }
}
