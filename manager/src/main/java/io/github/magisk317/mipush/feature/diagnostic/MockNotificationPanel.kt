package io.github.magisk317.mipush.feature.diagnostic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.mipush.common.Constants

private enum class MockNotificationTab(val titleRes: Int) {
    NOTIFICATION_SHADE(R.string.mock_group_notification_shade),
    DYNAMIC_ISLAND(R.string.mock_group_dynamic_island),
}

private val notificationShadeKinds = listOf(
    MockNotificationKind.PLAIN,
    MockNotificationKind.BIG_TEXT,
    MockNotificationKind.BIG_PICTURE,
    MockNotificationKind.INBOX,
    MockNotificationKind.MESSAGING,
    MockNotificationKind.MEDIA,
    MockNotificationKind.PROGRESS,
    MockNotificationKind.HEADS_UP,
    MockNotificationKind.VOIP_INCOMING,
    MockNotificationKind.LIVE_UPDATE_DELIVERY,
)

private val focusTemplateKinds = listOf(
    MockNotificationKind.FOCUS_NOTIFICATION,
    MockNotificationKind.FOCUS_MESSAGE,
    MockNotificationKind.FOCUS_BANNER,
    MockNotificationKind.FOCUS_ALERT,
    MockNotificationKind.FOCUS_PROMO,
    MockNotificationKind.FOCUS_MEDIA,
    MockNotificationKind.FOCUS_PROGRESS,
)

@Composable
fun MockNotificationPanel(
    onDismiss: () -> Unit,
    onFire: (MockNotificationKind, String) -> Unit,
) {
    var targetPackage by remember { mutableStateOf(Constants.SERVICE_APP_NAME) }
    var showPackagePicker by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(MockNotificationTab.NOTIFICATION_SHADE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_mock_notification)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPackagePicker = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.mock_target_package),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = targetPackage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                MockNotificationTabSelector(
                    selectedTab = selectedTab,
                    onSelect = { selectedTab = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                when (selectedTab) {
                    MockNotificationTab.NOTIFICATION_SHADE -> {
                        MockNotificationKindList(
                            kinds = notificationShadeKinds,
                            targetPackage = targetPackage,
                            onDismiss = onDismiss,
                            onFire = onFire,
                        )
                    }

                    MockNotificationTab.DYNAMIC_ISLAND -> {
                        MockNotificationKindList(
                            kinds = listOf(MockNotificationKind.DYNAMIC_ISLAND),
                            targetPackage = targetPackage,
                            onDismiss = onDismiss,
                            onFire = onFire,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.mock_group_focus_templates),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                        MockNotificationKindList(
                            kinds = focusTemplateKinds,
                            targetPackage = targetPackage,
                            onDismiss = onDismiss,
                            onFire = onFire,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )

    if (showPackagePicker) {
        PackagePickerDialog(
            currentPackage = targetPackage,
            onSelect = { pkg ->
                targetPackage = pkg
                showPackagePicker = false
            },
            onDismiss = { showPackagePicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MockNotificationTabSelector(
    selectedTab: MockNotificationTab,
    onSelect: (MockNotificationTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = MockNotificationTab.entries
    SingleChoiceSegmentedButtonRow(
        modifier = modifier.fillMaxWidth(),
    ) {
        tabs.forEachIndexed { index, tab ->
            SegmentedButton(
                selected = tab == selectedTab,
                onClick = { onSelect(tab) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = tabs.size,
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp),
                icon = {},
                label = {
                    Text(
                        text = stringResource(tab.titleRes),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                },
            )
        }
    }
}

@Composable
private fun MockNotificationKindList(
    kinds: List<MockNotificationKind>,
    targetPackage: String,
    onDismiss: () -> Unit,
    onFire: (MockNotificationKind, String) -> Unit,
) {
    kinds.forEach { kind ->
        MockNotificationItem(kind = kind) {
            onFire(kind, targetPackage)
            onDismiss()
        }
    }
}

@Composable
private fun MockNotificationItem(
    kind: MockNotificationKind,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
    ) {
        Text(
            text = stringResource(kind.getLabelRes(context)),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(kind.getDescRes(context)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PackagePickerDialog(
    currentPackage: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val packages = remember {
        buildList {
            add(Constants.SERVICE_APP_NAME)
            add("com.jingdong.app.mall")
            add("com.sankuai.meituan")
            add("com.ss.android.ugc.aweme")
            add("com.tencent.mobileqq")
            add("com.eg.android.AlipayGphone")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mock_target_package)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                packages.forEach { pkg ->
                    Text(
                        text = pkg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (pkg == currentPackage) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(pkg) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
