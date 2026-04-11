package io.github.magisk317.mipush.feature.main

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.imageResource
import androidx.core.graphics.drawable.toBitmap
import java.util.concurrent.ConcurrentHashMap

import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApplicationIconCache @Inject constructor(@param:ApplicationContext val context: Context) {
    // No-arg fallback for legacy Singleton access.
    constructor() : this(io.github.magisk317.mipush.common.utils.Utils.getApplication()!!)

    init {
        try {
            io.github.magisk317.mipush.common.utils.Singleton.reset(this)
        } catch (_: Throwable) {}
    }

    val defaultAppIcon by lazy {
        BitmapPainter(
            ImageBitmap.imageResource(
                context.resources, android.R.mipmap.sym_def_app_icon
            )
        )
    }
    private val iconCache = ConcurrentHashMap<String, Painter>()

    fun get(packageName: String): Painter? {
        return iconCache[packageName]
    }

    fun cache(packageName: String): Painter {
        val icon = getAppIcon(packageName)
        iconCache[packageName] = icon
        return icon
    }

    private fun getAppIcon(packageName: String): BitmapPainter {
        try {
            val applicationIcon = context.packageManager.getApplicationIcon(packageName)
            return BitmapPainter(applicationIcon.toBitmap().asImageBitmap())
        } catch (_: PackageManager.NameNotFoundException) {
            return defaultAppIcon
        }
    }

}
