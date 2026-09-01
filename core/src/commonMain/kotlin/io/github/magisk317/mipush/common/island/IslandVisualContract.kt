package io.github.magisk317.mipush.common.island

/** Extras shared by XMSF, the dispatcher proxy and the MiPush-owned SystemUI renderer. */
object IslandVisualContract {
    const val VERSION = 1
    const val OWNER_KEY = "mipush_island_owner"
    const val MIPUSH_OWNER = "io.github.magisk317.mipush"

    const val VISUAL_VERSION_KEY = "mipush_island_visual_version"
    const val VISUAL_MARKER_KEY = "mipush_island_visual_marker"
    const val HIGHLIGHT_COLOR_KEY = "mipush_island_highlight_color"
    const val GLOW_COLOR_KEY = "mipush_island_glow_color"
    const val ISLAND_GLOW_COLOR_KEY = "mipush_island_outer_glow_color"
    const val FOCUS_GLOW_COLOR_KEY = "mipush_island_focus_glow_color"

    const val VISUAL_MARKER = "mipush-island-visual"
}
