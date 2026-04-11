package io.github.magisk317.mipush.platform.support

import com.xiaomi.xmsf.runtime.PushRuntimeComponents

object LegacyComponentNames {
    const val MAIN_ACTIVITY = "top.trumeet.mipushframework.main.MainActivity"
    const val APPLICATION_INFO_PAGE = "top.trumeet.mipushframework.main.ApplicationInfoPage"
    const val HELP_PAGE = "top.trumeet.mipushframework.main.HelpPage"
    const val RECENT_EVENT_LIST_PAGE = "top.trumeet.mipushframework.main.RecentEventListPage"
    const val REQUEST_PERMISSION_PAGE = "top.trumeet.mipushframework.wizard.RequestPermissionPage"
    const val WELCOME_ACTIVITY = "top.trumeet.mipushframework.wizard.WelcomeActivity"
    const val DETECTION_SERVICE = "top.trumeet.common.ita.DetectionService"

    val manifestActivities = setOf(
        MAIN_ACTIVITY,
        APPLICATION_INFO_PAGE,
        HELP_PAGE,
        RECENT_EVENT_LIST_PAGE,
        REQUEST_PERMISSION_PAGE,
        WELCOME_ACTIVITY,
    )

    val manifestServices = setOf(
        PushRuntimeComponents.BRIDGE_SERVICE_CLASS,
        PushRuntimeComponents.LEGACY_MAIN_SERVICE_CLASS,
        DETECTION_SERVICE,
    )
}
