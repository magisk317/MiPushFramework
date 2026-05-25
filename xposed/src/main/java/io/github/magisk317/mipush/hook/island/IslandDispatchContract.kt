package io.github.magisk317.mipush.hook.island

object IslandDispatchContract {
    const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    const val ACTION_SHOW = "io.github.magisk317.mipush.action.SHOW_ISLAND"
    const val ACTION_CANCEL = "io.github.magisk317.mipush.action.CANCEL_ISLAND"
    const val EXTRA_NOTIFICATION_ID = "notification_id"

    const val CHANNEL_ID = "mipush_island_dispatcher"
    const val CHANNEL_NAME = "MiPush Island"
    const val DEFAULT_NOTIFICATION_ID = 0x4d495049

    const val FOCUS_PARAM = "miui.focus.param"
    const val SOURCE_PACKAGE = "hyperisland_source_pkg"
    const val SOURCE_CHANNEL = "hyperisland_source_channel"
    const val OWNER = "hyperisland.owner"
    const val OWNER_MARKER = "io.github.magisk317.mipush"
    const val PROCESSED = "mipush_island_processed"
}
