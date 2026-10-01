package io.github.magisk317.mipush.feature.main.subpage

import io.github.magisk317.uikit.surface.AppSurface
import io.github.magisk317.uikit.theme.AppColorRole
import io.github.magisk317.uikit.theme.appColor
import androidx.compose.runtime.Composable

@Composable
fun Page(content: @Composable () -> Unit) {
    AppSurface(color = appColor(AppColorRole.Background), content = content)
}
