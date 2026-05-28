package io.github.magisk317.mipush.platform.support

import io.github.magisk317.mipush.runtime.core.PushRuntimeComponents

object LegacyComponentNames {
    const val MAIN_ACTIVITY = "io.github.magisk317.mipush.feature.main.MainActivity"
    const val APPLICATION_INFO_PAGE = "io.github.magisk317.mipush.feature.main.ApplicationInfoPage"
    const val HELP_PAGE = "io.github.magisk317.mipush.feature.main.HelpPage"
    const val RECENT_EVENT_LIST_PAGE = "io.github.magisk317.mipush.feature.main.RecentEventListPage"
    const val REQUEST_PERMISSION_PAGE = "io.github.magisk317.mipush.feature.wizard.RequestPermissionPage"
    const val WELCOME_ACTIVITY = "io.github.magisk317.mipush.feature.wizard.WelcomeActivity"
    const val DETECTION_SERVICE = "io.github.magisk317.mipush.platform.activity.DetectionService"

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
