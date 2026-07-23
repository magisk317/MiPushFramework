package io.github.magisk317.mipush.notification

import android.content.Context
import io.github.magisk317.mipush.common.island.IslandOptions

internal typealias MiPushIslandOptions = IslandOptions

internal object MiPushIslandPreferences {
    fun read(context: Context, packageName: String? = null): MiPushIslandOptions {
        return IslandOptionsSnapshotReader.read(context, packageName).options
    }
}
