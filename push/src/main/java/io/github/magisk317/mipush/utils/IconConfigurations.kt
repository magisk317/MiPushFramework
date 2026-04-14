package io.github.magisk317.mipush.utils

import android.graphics.Bitmap

/**
 * Compatibility wrapper for legacy IconConfigurations.
 */
class IconConfigurations {
    class IconConfig {
        var isEnabled: Boolean? = true
        var isEnabledAll: Boolean? = true
        
        fun bitmap(): Bitmap? = null
        fun color(): Int = 0
    }

    fun get(packageName: String): IconConfig? {
        return null
    }

    fun init(context: android.content.Context, directory: android.net.Uri?) {
        // Bridging logic
    }
}
