package io.github.magisk317.mipush.notification

import android.content.Context
import io.github.magisk317.mipush.common.island.IslandOptions

typealias MiPushIslandOptions = IslandOptions

object MiPushIslandPreferences {
    fun read(
        context: Context,
        packageName: String? = null,
        userId: Int? = null,
    ): MiPushIslandOptions {
        return IslandOptionsSnapshotReader.read(context, packageName, userId).options
    }
}
