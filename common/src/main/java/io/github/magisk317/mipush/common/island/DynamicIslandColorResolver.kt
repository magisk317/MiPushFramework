package io.github.magisk317.mipush.common.island

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import kotlin.math.max

/** Small, allocation-bounded dominant-color resolver for notification island accents. */
object DynamicIslandColorResolver {
    private const val SAMPLE_SIZE = 48
    private const val ALPHA_THRESHOLD = 40
    private const val SATURATION_THRESHOLD = 0.12f
    private const val VALUE_THRESHOLD = 0.12f
    private const val COLOR_BUCKET_SIZE = 32
    private const val SCORE_OFFSET = 0.25f
    private const val MIN_ACCENT_SATURATION = 0.52f
    private const val MIN_ACCENT_VALUE = 0.55f
    private const val MAX_ACCENT_VALUE = 0.92f
    private const val MAX_DRAWABLE_SIZE = 256

    fun resolve(
        context: Context,
        packageName: String?,
        notificationIcon: Icon? = null,
        largeIcon: Bitmap? = null,
    ): String? {
        val bitmap = largeIcon ?: notificationIcon?.let { iconToBitmap(context, it) }
            ?: packageName?.let { packageIconToBitmap(context, it) }
            ?: return null
        return resolveBitmap(bitmap)
    }

    fun resolveBitmap(bitmap: Bitmap): String? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        val scaled = scaleForSampling(bitmap)
        val buckets = HashMap<Int, ColorBucket>()
        val pixels = IntArray(scaled.width * scaled.height)
        scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        pixels.forEach { pixel ->
            val alpha = Color.alpha(pixel)
            if (alpha < ALPHA_THRESHOLD) return@forEach
            val red = Color.red(pixel)
            val green = Color.green(pixel)
            val blue = Color.blue(pixel)
            val hsv = FloatArray(3)
            Color.colorToHSV(pixel, hsv)
            if (hsv[1] < SATURATION_THRESHOLD || hsv[2] < VALUE_THRESHOLD) return@forEach
            val key = ((red / COLOR_BUCKET_SIZE) shl 16) or
                ((green / COLOR_BUCKET_SIZE) shl 8) or
                (blue / COLOR_BUCKET_SIZE)
            val score = (SCORE_OFFSET + hsv[1]) * (SCORE_OFFSET + hsv[2]) * (alpha / 255f)
            val bucket = buckets.getOrPut(key) { ColorBucket() }
            bucket.red += red * score
            bucket.green += green * score
            bucket.blue += blue * score
            bucket.weight += score
        }
        if (scaled !== bitmap) scaled.recycle()
        val selected = buckets.values.maxByOrNull { it.weight } ?: return null
        if (selected.weight <= 0f) return null
        val red = (selected.red / selected.weight).toInt().coerceIn(0, 255)
        val green = (selected.green / selected.weight).toInt().coerceIn(0, 255)
        val blue = (selected.blue / selected.weight).toInt().coerceIn(0, 255)
        val hsv = FloatArray(3)
        Color.RGBToHSV(red, green, blue, hsv)
        hsv[1] = max(hsv[1], MIN_ACCENT_SATURATION).coerceAtMost(1f)
        hsv[2] = hsv[2].coerceIn(MIN_ACCENT_VALUE, MAX_ACCENT_VALUE)
        return String.format("#%08X", Color.HSVToColor(hsv))
    }

    private fun iconToBitmap(context: Context, icon: Icon): Bitmap? = runCatching {
        drawableToBitmap(icon.loadDrawable(context))
    }.getOrNull()

    private fun packageIconToBitmap(context: Context, packageName: String): Bitmap? = runCatching {
        drawableToBitmap(context.packageManager.getApplicationIcon(packageName))
    }.getOrNull()

    private fun drawableToBitmap(drawable: Drawable?): Bitmap? {
        drawable ?: return null
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: SAMPLE_SIZE
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: SAMPLE_SIZE
        return Bitmap.createBitmap(
            width.coerceAtMost(MAX_DRAWABLE_SIZE),
            height.coerceAtMost(MAX_DRAWABLE_SIZE),
            Bitmap.Config.ARGB_8888,
        ).also {
            val canvas = Canvas(it)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
        }
    }

    private fun scaleForSampling(bitmap: Bitmap): Bitmap {
        if (bitmap.width <= SAMPLE_SIZE && bitmap.height <= SAMPLE_SIZE) return bitmap
        val scale = minOf(SAMPLE_SIZE.toFloat() / bitmap.width, SAMPLE_SIZE.toFloat() / bitmap.height)
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    private data class ColorBucket(
        var red: Float = 0f,
        var green: Float = 0f,
        var blue: Float = 0f,
        var weight: Float = 0f,
    )
}
