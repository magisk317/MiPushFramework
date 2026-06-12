package io.github.magisk317.mipush.platform.support

import io.github.magisk317.mipush.common.Constants

object LegacyComponentNames {
    const val SERVICE_PACKAGE = "com.xiaomi.xmsf"
    const val BRIDGE_SERVICE_CLASS = "com.xiaomi.xmsf.push.service.XMPushService"
    const val LEGACY_MAIN_SERVICE_CLASS = "com.xiaomi.push.service.XMPushService"

    const val MAIN_ACTIVITY = "io.github.magisk317.mipush.feature.main.MainActivity"
    const val APPLICATION_INFO_PAGE = "io.github.magisk317.mipush.feature.main.ApplicationInfoPage"
    const val HELP_PAGE = "io.github.magisk317.mipush.feature.main.HelpPage"
    const val RECENT_EVENT_LIST_PAGE = "io.github.magisk317.mipush.feature.main.RecentEventListPage"
    const val REQUEST_PERMISSION_PAGE = "io.github.magisk317.mipush.feature.wizard.RequestPermissionPage"
    const val WELCOME_ACTIVITY = "io.github.magisk317.mipush.feature.wizard.WelcomeActivity"

    val manifestActivities = setOf(
        MAIN_ACTIVITY,
        APPLICATION_INFO_PAGE,
        HELP_PAGE,
        RECENT_EVENT_LIST_PAGE,
        REQUEST_PERMISSION_PAGE,
        WELCOME_ACTIVITY,
    )

    val manifestServices = setOf(
        BRIDGE_SERVICE_CLASS,
        LEGACY_MAIN_SERVICE_CLASS,
        Constants.KEEPALIVE_ACCESSIBILITY_SERVICE_CLASS,
    )
}
