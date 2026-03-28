package top.trumeet.mipushframework.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import top.trumeet.ui.theme.spacing

@Composable
fun ExpressiveHeroCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    io.github.magisk317.uikit.surface.ExpressiveHeroCard(
        title = title,
        subtitle = null,
        modifier = modifier,
        content = content,
    )
}

@Composable
fun ExpressiveSectionCard(
    title: String,
    summary: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    io.github.magisk317.uikit.surface.ExpressiveSectionCard(
        title = title,
        summary = summary,
        modifier = modifier,
        content = content,
    )
}

@Composable
fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
) {
    io.github.magisk317.uikit.surface.MetricCard(
        label = label,
        value = value,
        modifier = modifier,
        accent = accent,
    )
}

@Composable
fun InfoPill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer,
) {
    io.github.magisk317.uikit.surface.InfoPill(
        text = text,
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
    )
}

@Composable
fun LabelValueBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    io.github.magisk317.uikit.surface.LabelValueBlock(
        label = label,
        value = value,
        modifier = modifier,
    )
}

@Composable
fun ActionStrip(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    io.github.magisk317.uikit.surface.ActionStrip(
        modifier = modifier,
        content = content,
    )
}

typealias MetricSpec = io.github.magisk317.uikit.surface.MetricSpec

@Composable
fun FeatureEntryCard(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    io.github.magisk317.uikit.surface.FeatureEntryCard(
        title = title,
        summary = summary,
        modifier = modifier,
        onClick = onClick,
    )
}

@Composable
fun MetricGrid(
    metrics: List<MetricSpec>,
    modifier: Modifier = Modifier,
) {
    io.github.magisk317.uikit.surface.MetricGrid(
        metrics = metrics,
        modifier = modifier,
    )
}

@Composable
fun DetailSectionCard(
    title: String,
    summary: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    io.github.magisk317.uikit.surface.DetailSectionCard(
        title = title,
        summary = summary,
        modifier = modifier,
        content = content,
    )
}

@Composable
fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    leadingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    io.github.magisk317.uikit.surface.DetailRow(
        label = label,
        value = value,
        modifier = modifier,
        leadingContent = leadingContent,
        onClick = onClick,
    )
}

@Composable
fun DetailDivider() {
    io.github.magisk317.uikit.surface.DetailDivider()
}

@Composable
fun SectionColumn(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    content: @Composable () -> Unit,
) {
    io.github.magisk317.uikit.surface.SectionColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

@Composable
fun OverlayHeaderScaffold(
    modifier: Modifier = Modifier,
    fallbackTopPadding: Dp,
    bottomPadding: Dp = 0.dp,
    overlayModifier: Modifier = Modifier,
    overlay: @Composable ColumnScope.() -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    io.github.magisk317.uikit.surface.OverlayHeaderScaffold(
        modifier = modifier,
        fallbackTopPadding = fallbackTopPadding,
        bottomPadding = bottomPadding,
        overlayModifier = overlayModifier,
        overlay = overlay,
        content = content,
    )
}

@Composable
fun OverlayHeaderPanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    io.github.magisk317.uikit.surface.OverlayHeaderPanel(
        modifier = modifier,
        title = title,
        subtitle = null,
        actions = actions,
        content = content,
    )
}

@Composable
fun SearchWorkspaceScaffold(
    fallbackTopPadding: Dp,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    overlayModifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    searchField: @Composable ColumnScope.() -> Unit,
    supportingContent: @Composable ColumnScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    io.github.magisk317.uikit.surface.SearchWorkspaceScaffold(
        fallbackTopPadding = fallbackTopPadding,
        modifier = modifier,
        bottomPadding = bottomPadding,
        overlayModifier = overlayModifier,
        title = title,
        subtitle = null,
        actions = actions,
        searchField = searchField,
        supportingContent = supportingContent,
        content = content,
    )
}

@Composable
fun WorkspaceListItem(
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Transparent,
    onClick: (() -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    io.github.magisk317.uikit.surface.WorkspaceListItem(
        modifier = modifier,
        containerColor = containerColor,
        onClick = onClick,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        content = content,
    )
}

@Composable
fun WorkspaceEmptyState(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
) {
    io.github.magisk317.uikit.surface.WorkspaceEmptyState(
        title = title,
        summary = summary,
        modifier = modifier,
        icon = icon,
    )
}

@Composable
fun WorkspaceTrailingIcon(
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    io.github.magisk317.uikit.surface.WorkspaceTrailingIcon(
        imageVector = imageVector,
        modifier = modifier,
        tint = tint,
    )
}
