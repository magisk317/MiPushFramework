package io.github.magisk317.mipush.common.island

import android.content.Context

object IslandRendererPolicy {
    private const val HYPERISLAND_PACKAGE = "io.github.hyperisland"

    fun hyperIslandInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(HYPERISLAND_PACKAGE, 0)
        true
    }.getOrDefault(false)

    fun owner(context: Context, options: IslandOptions): String {
        return IslandVisualContract.ownerFor(options.rendererMode, hyperIslandInstalled(context))
    }

    fun delegatesToHyperIsland(context: Context, options: IslandOptions): Boolean {
        return IslandVisualContract.delegatesToHyperIsland(
            options.rendererMode,
            hyperIslandInstalled(context),
        )
    }

    fun mode(context: Context, options: IslandOptions): IslandRendererMode {
        return if (delegatesToHyperIsland(context, options)) {
            IslandRendererMode.HYPERISLAND
        } else {
            IslandRendererMode.MIPUSH
        }
    }
}
