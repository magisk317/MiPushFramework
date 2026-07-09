@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.magisk317.mipush.feature.main.subpage

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.mipush.common.ACTION_PREF_CHANGED
import io.github.magisk317.mipush.feature.ui.theme.spacing
import io.github.magisk317.mipush.main.viewmodel.SettingsViewModel
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.uikit.preference.SectionCard
import io.github.magisk317.uikit.preference.StateSwitchItem as SettingsSwitchItem
import io.github.magisk317.uikit.surface.OverlayHeaderScaffold
import io.github.magisk317.uikit.surface.SectionColumn
import io.github.magisk317.uikit.surface.chromeTopAppBarColors
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun StatusBarIconSettingsPage(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val colorStatusBarIcon by viewModel.colorStatusBarIcon.collectAsStateWithLifecycle()
    val colorStatusBarIconGlobal by viewModel.colorStatusBarIconGlobal.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val showSwitchFeedback = rememberStatusBarIconSwitchFeedback(snackbarHostState)
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Page {
        Box(modifier = Modifier.fillMaxSize()) {
            OverlayHeaderScaffold(
                fallbackTopPadding = topInset + 64.dp,
                overlayModifier = Modifier
                    .fillMaxWidth(),
                overlay = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.pref_color_status_bar_icon_title)) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                            }
                        },
                        windowInsets = WindowInsets.statusBars,
                        colors = chromeTopAppBarColors(),
                    )
                },
                content = { listPadding ->
                    SectionColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        contentPadding = PaddingValues(
                            start = MaterialTheme.spacing.medium,
                            top = listPadding.calculateTopPadding() + MaterialTheme.spacing.small,
                            end = MaterialTheme.spacing.medium,
                            bottom = MaterialTheme.spacing.large,
                        ),
                    ) {
                        SectionCard(
                            title = stringResource(R.string.pref_color_status_bar_icon_title),
                            accordionMode = false,
                            sectionExpanded = true,
                            onExpandedChange = {},
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = MaterialTheme.spacing.small),
                                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                            ) {
                                val managedTitle = stringResource(R.string.pref_color_status_bar_icon_mipush_title)
                                SettingsSwitchItem(
                                    title = managedTitle,
                                    summary = stringResource(R.string.pref_color_status_bar_icon_mipush_summary),
                                    checked = colorStatusBarIcon,
                                    onCheckedChange = { enabled ->
                                        viewModel.setColorStatusBarIcon(enabled) {
                                            notifyStatusBarIconPreferenceChanged(context)
                                        }
                                        showSwitchFeedback(managedTitle, enabled)
                                    },
                                )

                                val globalTitle = stringResource(R.string.pref_color_status_bar_icon_global_title)
                                SettingsSwitchItem(
                                    title = globalTitle,
                                    summary = stringResource(R.string.pref_color_status_bar_icon_global_summary),
                                    checked = colorStatusBarIconGlobal,
                                    onCheckedChange = { enabled ->
                                        viewModel.setColorStatusBarIconGlobal(enabled) {
                                            notifyStatusBarIconPreferenceChanged(context)
                                        }
                                        showSwitchFeedback(globalTitle, enabled)
                                    },
                                )
                            }
                        }
                    }
                },
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

private fun notifyStatusBarIconPreferenceChanged(context: Context) {
    context.sendBroadcast(Intent(ACTION_PREF_CHANGED))
}

@Composable
private fun rememberStatusBarIconSwitchFeedback(
    snackbarHostState: SnackbarHostState,
): (String, Boolean) -> Unit {
    val scope = rememberCoroutineScope()
    val enabledTemplate = stringResource(R.string.settings_switch_enabled_feedback)
    val disabledTemplate = stringResource(R.string.settings_switch_disabled_feedback)
    return remember(snackbarHostState, scope, enabledTemplate, disabledTemplate) {
        { title, enabled ->
            val template = if (enabled) enabledTemplate else disabledTemplate
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(
                    message = String.format(Locale.getDefault(), template, title),
                    duration = SnackbarDuration.Short,
                )
            }
        }
    }
}
