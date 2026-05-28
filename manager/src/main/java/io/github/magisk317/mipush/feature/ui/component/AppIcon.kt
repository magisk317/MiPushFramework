package io.github.magisk317.mipush.feature.ui.component

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.magisk317.mipush.app.di.AppDependencies
import io.github.magisk317.mipush.feature.main.ApplicationIconCache

@Composable
fun AppIcon(packageName: String, appName: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val iconCache = remember(context) {
        if (isPreview) null
        else AppDependencies.get<ApplicationIconCache>(context)
    }

    var icon by remember(packageName) {
        mutableStateOf(
            if (isPreview || iconCache == null) null
            else iconCache.get(packageName)
        )
    }

    if (!isPreview && iconCache != null && icon == null) {
        LaunchedEffect(packageName) {
            withContext(Dispatchers.IO) {
                icon = iconCache.cache(packageName)
            }
        }
    }

    if (icon != null) {
        Image(icon!!, appName, modifier = modifier)
    } else {
        // Fallback or placeholder
        Image(
            painter = painterResource(id = android.R.mipmap.sym_def_app_icon),
            contentDescription = appName,
            modifier = modifier
        )
    }
}
