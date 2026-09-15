package io.github.magisk317.mipush.platform.support

/** Canonical components hosted by the standalone MiPush manager package. */
object ManagerComponentNames {
    const val PACKAGE = "io.github.magisk317.mipush"
    const val MAIN_ACTIVITY = "$PACKAGE.feature.main.MainActivity"
    const val APPLICATION_INFO_PAGE = "$PACKAGE.feature.main.ApplicationInfoPage"
    const val RECENT_EVENT_LIST_PAGE = "$PACKAGE.feature.main.RecentEventListPage"
    const val REQUEST_PERMISSION_PAGE = "$PACKAGE.feature.wizard.RequestPermissionPage"
    const val CONFIGURATIONS_PAGE = "$PACKAGE.feature.main.subpage.ConfigurationsPage"
}
