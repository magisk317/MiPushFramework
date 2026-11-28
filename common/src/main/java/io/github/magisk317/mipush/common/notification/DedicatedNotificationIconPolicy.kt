package io.github.magisk317.mipush.common.notification

internal object DedicatedNotificationIconPolicy {
    private const val MIN_GLYPH_DIMENSION = 16
    private const val MAX_GLYPH_DIMENSION = 2048
    private const val MAX_GLYPH_PIXELS = MAX_GLYPH_DIMENSION.toLong() * MAX_GLYPH_DIMENSION
    private const val RGB_CHANNEL_TOLERANCE = 12
    private const val FULL_ALPHA = 0xFF

    val candidateNames = listOf(
        "appicon_push",
        "push_icon",
        "notification_icon",
        "notification_small_icon",
        "small_icon",
        "status_bar_icon",
        "ic_notification",
    )

    fun isValidStatusGlyph(width: Int, height: Int, pixels: IntArray): Boolean {
        if (width < MIN_GLYPH_DIMENSION || height < MIN_GLYPH_DIMENSION ||
            width > MAX_GLYPH_DIMENSION || height > MAX_GLYPH_DIMENSION
        ) return false
        val pixelCount = width.toLong() * height
        if (pixelCount > MAX_GLYPH_PIXELS || pixels.size < pixelCount) return false
        var visible = 0
        for (pixel in pixels) {
            if (pixel ushr 24 == 0) continue
            visible++
            val red = (pixel ushr 16) and 0xFF
            val green = (pixel ushr 8) and 0xFF
            val blue = pixel and 0xFF
            if (kotlin.math.abs(red - green) > RGB_CHANNEL_TOLERANCE ||
                kotlin.math.abs(green - blue) > RGB_CHANNEL_TOLERANCE
            ) return false
        }
        if (visible == 0) return false
        return !hasOpaqueBacking(width, height, pixels)
    }

    private fun hasOpaqueBacking(width: Int, height: Int, pixels: IntArray): Boolean {
        fun opaque(x: Int, y: Int): Boolean = pixels[y * width + x] ushr 24 == FULL_ALPHA
        for (x in 0 until width) {
            if (!opaque(x, 0) || !opaque(x, height - 1)) return false
        }
        for (y in 0 until height) {
            if (!opaque(0, y) || !opaque(width - 1, y)) return false
        }
        return true
    }
}
