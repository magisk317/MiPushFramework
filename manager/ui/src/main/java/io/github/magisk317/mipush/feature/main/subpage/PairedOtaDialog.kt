package io.github.magisk317.mipush.feature.main.subpage

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.uikit.surface.AppAlertDialog
import io.github.magisk317.uikit.surface.AppLinearProgressIndicator
import io.github.magisk317.uikit.surface.DialogAction
import io.github.magisk317.uikit.surface.DialogActionRow
import io.github.magisk317.uikit.surface.DialogActionStyle
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import io.github.magisk317.uikit.theme.AppColorRole
import io.github.magisk317.uikit.theme.appColor
import io.github.magisk317.uikit.update.AppUpdateController
import io.github.magisk317.uikit.update.UpdateUiState
import kotlinx.coroutines.launch

private enum class PairedRowState {
    Waiting,
    Checking,
    Available,
    Downloading,
    WaitingForInstall,
    WaitingForSystemConfirmation,
    Installing,
    Installed,
    Failed,
}
private data class PairedRowModel(
    val state: PairedRowState,
    val progress: Int? = null,
    val version: String? = null,
    val detail: String? = null,
)

@Composable
internal fun PairedOtaDialog(
    coordinator: VersionUpdateCoordinator,
    controller: AppUpdateController,
    context: Context,
) {
    val visible by coordinator.pairedVisible.collectAsState()
    if (!visible) return

    val started by coordinator.pairedStarted.collectAsState()
    val managerCompleted by coordinator.managerCompleted.collectAsState()
    val managerInstallStarted by coordinator.managerInstallStarted.collectAsState()
    val managerHint by coordinator.managerHint.collectAsState()
    val runtimeHint by coordinator.runtimeHint.collectAsState()
    val managerState by controller.state.collectAsState()
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val managerRow = managerRowModel(
        managerState = managerState,
        managerHint = managerHint,
        started = started,
        completed = managerCompleted,
        installStarted = managerInstallStarted,
    )
    val runtimeRow = runtimeRowModel(runtimeHint, started)

    DisposableEffect(lifecycleOwner, coordinator) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) coordinator.onHostResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(started, managerState, managerCompleted) {
        if (started && !managerCompleted && managerState is UpdateUiState.ReadyToInstall) {
            coordinator.installManager(context)
        }
    }

    val terminal = managerRow.state == PairedRowState.Installed &&
        runtimeRow.state == PairedRowState.Installed
    val failed = managerRow.state == PairedRowState.Failed || runtimeRow.state == PairedRowState.Failed
    val canStart = !started && (managerRow.state == PairedRowState.Available ||
        runtimeRow.state == PairedRowState.Available)
    val managerReadyToInstall = started && !managerCompleted &&
        managerState is UpdateUiState.ReadyToInstall && !managerInstallStarted
    val retryRuntime = failed && coordinator.canRetryPairedRuntime
    val retryManager = failed && started && !managerCompleted &&
        managerState is UpdateUiState.Failed

    AppAlertDialog(
        onDismissRequest = {
            if (terminal || failed) coordinator.dismissPaired()
        },
        properties = DialogProperties(
            dismissOnBackPress = terminal || failed,
            dismissOnClickOutside = false,
        ),
        title = {
            AppText(
                text = stringResource(R.string.paired_ota_title),
                role = AppTextRole.Title,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                PairedOtaRow(
                    title = stringResource(R.string.paired_ota_manager),
                    model = managerRow,
                )
                PairedOtaRow(
                    title = stringResource(R.string.paired_ota_runtime),
                    model = runtimeRow,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            val actions = when {
                canStart -> listOf(
                    DialogAction(
                        label = stringResource(R.string.paired_ota_start),
                        onClick = { scope.launch { coordinator.startPairedUpdate() } },
                        style = DialogActionStyle.Primary,
                    ),
                )
                managerReadyToInstall -> listOf(
                    DialogAction(
                        label = stringResource(R.string.paired_ota_install_manager),
                        onClick = { coordinator.installManager(context) },
                        style = DialogActionStyle.Primary,
                    ),
                )
                retryRuntime -> listOf(
                    DialogAction(
                        label = stringResource(R.string.paired_ota_retry_runtime),
                        onClick = { scope.launch { coordinator.retryRuntime() } },
                        style = DialogActionStyle.Primary,
                    ),
                )
                retryManager -> listOf(
                    DialogAction(
                        label = stringResource(R.string.paired_ota_retry_manager),
                        onClick = coordinator::retryManager,
                        style = DialogActionStyle.Primary,
                    ),
                )
                terminal || failed -> listOf(
                    DialogAction(
                        label = stringResource(android.R.string.ok),
                        onClick = coordinator::dismissPaired,
                        style = DialogActionStyle.Primary,
                    ),
                )
                else -> emptyList()
            }
            if (actions.isNotEmpty()) DialogActionRow(actions)
        },
    )
}

@Composable
private fun PairedOtaRow(title: String, model: PairedRowModel) {
    val (label, color) = when (model.state) {
        PairedRowState.Waiting -> R.string.paired_ota_status_waiting to AppColorRole.OnSurfaceVariant
        PairedRowState.Checking -> R.string.paired_ota_status_checking to AppColorRole.Primary
        PairedRowState.Available -> R.string.paired_ota_status_available to AppColorRole.Primary
        PairedRowState.Downloading -> R.string.paired_ota_status_downloading to AppColorRole.Primary
        PairedRowState.WaitingForInstall -> R.string.paired_ota_status_waiting_install to AppColorRole.Tertiary
        PairedRowState.WaitingForSystemConfirmation ->
            R.string.paired_ota_status_waiting_system to AppColorRole.Tertiary
        PairedRowState.Installing -> R.string.paired_ota_status_installing to AppColorRole.Primary
        PairedRowState.Installed -> R.string.paired_ota_status_installed to AppColorRole.Primary
        PairedRowState.Failed -> R.string.paired_ota_status_failed to AppColorRole.Error
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(appColor(color)),
            )
            AppText(text = title, role = AppTextRole.Subtitle, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.weight(1f))
            AppText(
                text = stringResource(label),
                role = AppTextRole.BodySmall,
                color = appColor(color),
            )
        }
        when {
            model.progress != null -> {
                AppLinearProgressIndicator(
                    progress = model.progress / 100f,
                    modifier = Modifier.fillMaxWidth().height(7.dp),
                )
                AppText(
                    text = "${model.progress}%",
                    role = AppTextRole.BodySmall,
                    color = appColor(AppColorRole.OnSurfaceVariant),
                )
            }
            model.state == PairedRowState.Checking ||
                model.state == PairedRowState.Installing -> {
                AppLinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(7.dp))
            }
        }
        model.detail?.let {
            AppText(text = it, role = AppTextRole.BodySmall, color = appColor(AppColorRole.Error))
        }
        model.version?.let {
            AppText(
                text = stringResource(R.string.paired_ota_version, it),
                role = AppTextRole.BodySmall,
                color = appColor(AppColorRole.OnSurfaceVariant),
            )
        }
    }
}

private fun managerRowModel(
    managerState: UpdateUiState,
    managerHint: VersionRowHint,
    started: Boolean,
    completed: Boolean,
    installStarted: Boolean,
): PairedRowModel = when {
    completed -> PairedRowModel(PairedRowState.Installed)
    !started && managerHint is VersionRowHint.UpToDate -> PairedRowModel(PairedRowState.Installed)
    managerState is UpdateUiState.Checking -> PairedRowModel(PairedRowState.Checking)
    managerState is UpdateUiState.Available -> PairedRowModel(PairedRowState.Available)
    managerState is UpdateUiState.Downloading -> PairedRowModel(
        PairedRowState.Downloading,
        (managerState.progress.takeIf { it >= 0f }?.times(100f))?.toInt(),
    )
    managerState is UpdateUiState.ReadyToInstall && installStarted -> PairedRowModel(PairedRowState.Installing)
    managerState is UpdateUiState.ReadyToInstall -> PairedRowModel(PairedRowState.WaitingForInstall)
    managerState is UpdateUiState.Failed -> PairedRowModel(
        PairedRowState.Failed,
        detail = managerState.reason,
    )
    managerHint is VersionRowHint.Available -> PairedRowModel(
        PairedRowState.Available,
        version = managerHint.latestVersionName,
    )
    managerHint is VersionRowHint.UpToDate -> PairedRowModel(PairedRowState.Installed)
    started -> PairedRowModel(PairedRowState.Installing)
    else -> PairedRowModel(PairedRowState.Waiting)
}

private fun runtimeRowModel(hint: VersionRowHint, started: Boolean): PairedRowModel = when (hint) {
    VersionRowHint.Hidden -> PairedRowModel(PairedRowState.Waiting)
    VersionRowHint.Checking -> PairedRowModel(PairedRowState.Checking)
    VersionRowHint.UpToDate -> PairedRowModel(PairedRowState.Installed)
    is VersionRowHint.Available -> PairedRowModel(
        PairedRowState.Available,
        version = hint.latestVersionName,
    )
    is VersionRowHint.Updating -> PairedRowModel(PairedRowState.Downloading, hint.percent)
    VersionRowHint.WaitingForSystemConfirmation ->
        PairedRowModel(PairedRowState.WaitingForSystemConfirmation)
    VersionRowHint.Installed -> PairedRowModel(PairedRowState.Installed)
    is VersionRowHint.Failed -> PairedRowModel(PairedRowState.Failed, detail = hint.message)
    VersionRowHint.Unsupported -> PairedRowModel(
        PairedRowState.Failed,
        detail = "Runtime update is unavailable",
    )
}
