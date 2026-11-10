package io.github.magisk317.mipush.feature.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.mipush.manager.application.ManagerApplication
import io.github.magisk317.uikit.surface.AppCircularProgressIndicator
import io.github.magisk317.uikit.surface.AppFilterChip
import io.github.magisk317.uikit.surface.AppPrimaryButton
import io.github.magisk317.uikit.surface.AppTextButton
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import io.github.magisk317.uikit.theme.AppColorRole
import io.github.magisk317.uikit.theme.appColor
import io.github.magisk317.uikit.theme.spacing

@Composable
internal fun ApplicationRegistrationCard(
    info: ManagerApplication,
    busy: Boolean,
    onSelect: (Int) -> Unit,
    onApprove: () -> Unit,
) {
    val selected = if (info.blocked) ManagerApplication.Type.DENY else info.type
    ApplicationDetailSectionCard(title = stringResource(R.string.app_detail_registration_policy)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = MaterialTheme.spacing.large,
                vertical = MaterialTheme.spacing.medium,
            ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                listOf(
                    ManagerApplication.Type.ALLOW to R.string.app_detail_registration_auto,
                    ManagerApplication.Type.ASK to R.string.app_detail_registration_ask,
                    ManagerApplication.Type.DENY to R.string.app_detail_registration_deny,
                ).forEach { (type, label) ->
                    AppFilterChip(
                        label = stringResource(label),
                        selected = selected == type,
                        enabled = !busy,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        onClick = { onSelect(type) },
                    )
                }
            }
            AppText(
                text = stringResource(
                    when (selected) {
                        ManagerApplication.Type.ASK -> R.string.app_detail_policy_ask_hint
                        ManagerApplication.Type.DENY -> R.string.app_detail_policy_deny_hint
                        else -> R.string.app_detail_policy_auto_hint
                    },
                ),
                role = AppTextRole.BodySmall,
                color = appColor(AppColorRole.OnSurfaceVariant),
            )
            if (busy) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                ) {
                    AppCircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    AppText(
                        text = stringResource(R.string.app_detail_registration_busy),
                        role = AppTextRole.BodySmall,
                        color = appColor(AppColorRole.OnSurfaceVariant),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (info.registrationPending && selected == ManagerApplication.Type.ASK) {
                AppText(
                    text = stringResource(R.string.app_detail_registration_pending),
                    role = AppTextRole.BodySmall,
                    color = appColor(AppColorRole.OnSurface),
                )
                AppPrimaryButton(
                    text = stringResource(R.string.app_detail_registration_approve),
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    onClick = onApprove,
                )
            }
            if (selected == ManagerApplication.Type.DENY) {
                // A failed cleanup can outlive the local registration marker. Keep the
                // idempotent retry visible without guessing the server or journal state.
                AppTextButton(
                    text = stringResource(R.string.app_detail_retry_revocation),
                    enabled = !busy,
                    modifier = Modifier.heightIn(min = 48.dp),
                    color = appColor(AppColorRole.Primary),
                    onClick = { onSelect(ManagerApplication.Type.DENY) },
                )
            }
        }
    }
}
