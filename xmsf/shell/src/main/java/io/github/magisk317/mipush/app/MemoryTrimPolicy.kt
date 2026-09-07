package io.github.magisk317.mipush.app

import android.content.ComponentCallbacks2

/**
 * Maps Android memory callbacks to the smallest useful cache eviction scope.
 *
 * UI-hidden and moderate running pressure release bitmap-backed accelerators because bitmaps are
 * the largest cache values in this process. Background or critical pressure also releases small
 * metadata accelerators. Unknown callback levels are ignored so a future platform signal cannot
 * accidentally clear runtime state.
 */
internal object MemoryTrimPolicy {
    enum class Action {
        NONE,
        CLEAR_BITMAPS,
        CLEAR_ALL,
    }

    @Suppress("DEPRECATION")
    fun actionFor(level: Int): Action = when (level) {
        ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> Action.CLEAR_BITMAPS

        ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
        ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
        ComponentCallbacks2.TRIM_MEMORY_MODERATE,
        ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> Action.CLEAR_ALL

        else -> Action.NONE
    }
}
