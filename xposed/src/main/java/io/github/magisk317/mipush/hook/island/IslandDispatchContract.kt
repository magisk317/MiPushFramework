package io.github.magisk317.mipush.hook.island

import android.os.Bundle
import io.github.magisk317.mipush.common.island.IslandVisualContract

object IslandDispatchContract {
    const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    const val ACTION_SHOW = "io.github.magisk317.mipush.action.SHOW_ISLAND"
    const val ACTION_CANCEL = "io.github.magisk317.mipush.action.CANCEL_ISLAND"
    const val EXTRA_NOTIFICATION_ID = "notification_id"
    const val EXTRA_USER_ID = "user_id"

    const val CHANNEL_ID = "mipush_island_dispatcher"
    const val CHANNEL_NAME = "MiPush Island"
    const val DEFAULT_NOTIFICATION_ID = 0x4d495049

    const val FOCUS_PARAM = "miui.focus.param"
    const val FOCUS_REMOTE_VIEW = "miui.focus.rv"
    const val SOURCE_PACKAGE = "hyperisland_source_pkg"
    const val SOURCE_CHANNEL = "hyperisland_source_channel"
    const val OWNER = IslandVisualContract.OWNER_KEY
    const val OWNER_MARKER = IslandVisualContract.MIPUSH_OWNER
    const val PROCESSED = "mipush_island_processed"

    const val VISUAL_VERSION = IslandVisualContract.VISUAL_VERSION_KEY
    const val VISUAL_MARKER = IslandVisualContract.VISUAL_MARKER_KEY
    const val VISUAL_MODE = IslandVisualContract.VISUAL_MODE_KEY
    const val HIGHLIGHT_COLOR = IslandVisualContract.HIGHLIGHT_COLOR_KEY
    const val GLOW_COLOR = IslandVisualContract.GLOW_COLOR_KEY
    const val ISLAND_GLOW_COLOR = IslandVisualContract.ISLAND_GLOW_COLOR_KEY
    const val FOCUS_GLOW_COLOR = IslandVisualContract.FOCUS_GLOW_COLOR_KEY

    fun hasNativeFocusPayload(extras: Bundle): Boolean =
        extras.containsKey(FOCUS_PARAM) || extras.containsKey(FOCUS_REMOTE_VIEW)
}
