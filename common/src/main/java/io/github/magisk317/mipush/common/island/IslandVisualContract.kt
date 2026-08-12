package io.github.magisk317.mipush.common.island

/** Extras shared by XMSF, the dispatcher proxy and the SystemUI renderer. */
object IslandVisualContract {
    const val VERSION = 1

    const val OWNER_KEY = "hyperisland.owner"
    const val MIPUSH_OWNER = "io.github.magisk317.mipush"
    const val HYPERISLAND_OWNER = "io.github.hyperisland"

    const val VISUAL_VERSION_KEY = "mipush_island_visual_version"
    const val VISUAL_MARKER_KEY = "mipush_island_visual_marker"
    const val VISUAL_MODE_KEY = "mipush_island_visual_mode"
    const val HIGHLIGHT_COLOR_KEY = "hyperisland_dynamic_highlight_color"
    const val GLOW_COLOR_KEY = "hyperisland_dynamic_glow_color"
    const val ISLAND_GLOW_COLOR_KEY = "hyperisland_island_outer_glow_color"
    const val FOCUS_GLOW_COLOR_KEY = "hyperisland_focus_out_effect_color"

    const val VISUAL_MARKER = "mipush-island-visual"

    /** Resolves an explicit or automatic preference to an actually available renderer. */
    fun effectiveMode(mode: IslandRendererMode, hyperIslandInstalled: Boolean): IslandRendererMode =
        when (mode) {
            IslandRendererMode.MIPUSH -> IslandRendererMode.MIPUSH
            IslandRendererMode.HYPERISLAND ->
                if (hyperIslandInstalled) IslandRendererMode.HYPERISLAND else IslandRendererMode.MIPUSH
            IslandRendererMode.AUTO ->
                if (hyperIslandInstalled) IslandRendererMode.HYPERISLAND else IslandRendererMode.MIPUSH
        }

    fun ownerFor(mode: IslandRendererMode, hyperIslandInstalled: Boolean): String = when (
        effectiveMode(mode, hyperIslandInstalled)
    ) {
        IslandRendererMode.HYPERISLAND -> HYPERISLAND_OWNER
        IslandRendererMode.MIPUSH,
        IslandRendererMode.AUTO -> MIPUSH_OWNER
    }

    fun delegatesToHyperIsland(mode: IslandRendererMode, hyperIslandInstalled: Boolean): Boolean =
        effectiveMode(mode, hyperIslandInstalled) == IslandRendererMode.HYPERISLAND
}
