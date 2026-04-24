package io.github.magisk317.mipush.platform.support
import io.github.magisk317.mipush.common.Constants


object LegacyComponentNames {
    const val MAIN_ACTIVITY = "io.github.magisk317.mipush.feature.main.MainActivity"
    const val APPLICATION_INFO_PAGE = "io.github.magisk317.mipush.feature.main.ApplicationInfoPage"
    const val HELP_PAGE = "io.github.magisk317.mipush.feature.main.HelpPage"
    const val RECENT_EVENT_LIST_PAGE = "io.github.magisk317.mipush.feature.main.RecentEventListPage"
    const val REQUEST_PERMISSION_PAGE = "io.github.magisk317.mipush.feature.wizard.RequestPermissionPage"
    const val WELCOME_ACTIVITY = "io.github.magisk317.mipush.feature.wizard.WelcomeActivity"
    const val DETECTION_SERVICE = "io.github.magisk317.mipush.platform.activity.DetectionService"

    // Component Extras & Flags
    const val EXTRA_START_TAB = "extra_start_tab"
    const val START_TAB_SETTINGS = "settings"
    const val EXTRA_START_ROUTE = "extra_start_route"

    const val EXTRA_PACKAGE_NAME = "EXTRA_PACKAGE_NAME"
    const val EXTRA_IGNORE_NOT_REGISTERED = "EXTRA_IGNORE_NOT_REGISTERED"

    const val EXTRA_RECHECK_ONLY = "extra_recheck_only"

    val manifestActivities = setOf(
        MAIN_ACTIVITY,
        APPLICATION_INFO_PAGE,
        HELP_PAGE,
        RECENT_EVENT_LIST_PAGE,
        REQUEST_PERMISSION_PAGE,
        WELCOME_ACTIVITY,
    )

    val manifestServices = setOf(
        Constants.BRIDGE_SERVICE_CLASS,
        Constants.XM_PUSH_SERVICE_CLASS,
        DETECTION_SERVICE,
    )
}
