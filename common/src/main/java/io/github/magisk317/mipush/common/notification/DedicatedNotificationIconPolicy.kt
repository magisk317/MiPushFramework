package io.github.magisk317.mipush.common.notification

internal object DedicatedNotificationIconPolicy {
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
        if (width < 16 || height < 16 || width > 2048 || height > 2048) return false
        val pixelCount = width.toLong() * height
        if (pixelCount > 2048L * 2048L || pixels.size < pixelCount) return false
        var visible = 0
        for (pixel in pixels) {
            if (pixel ushr 24 == 0) continue
            visible++
            val red = (pixel ushr 16) and 0xFF
            val green = (pixel ushr 8) and 0xFF
            val blue = pixel and 0xFF
            if (kotlin.math.abs(red - green) > 12 || kotlin.math.abs(green - blue) > 12) return false
        }
        if (visible == 0) return false
        return !hasOpaqueBacking(width, height, pixels)
    }

    private fun hasOpaqueBacking(width: Int, height: Int, pixels: IntArray): Boolean {
        fun opaque(x: Int, y: Int): Boolean = pixels[y * width + x] ushr 24 == 0xFF
        for (x in 0 until width) {
            if (!opaque(x, 0) || !opaque(x, height - 1)) return false
        }
        for (y in 0 until height) {
            if (!opaque(0, y) || !opaque(width - 1, y)) return false
        }
        return true
    }
}
