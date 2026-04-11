package io.github.magisk317.mipush.platform.support

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.magisk317.mipush.feature.main.ApplicationInfoPage
import io.github.magisk317.mipush.feature.main.MainActivity
import io.github.magisk317.mipush.feature.wizard.RequestPermissionPage

internal object LegacyUiEntryPoints {
    fun mainActivityIntent(
        context: Context,
        startRoute: String? = null,
        startTab: String? = null,
    ): Intent {
        return Intent().setClassName(context, LegacyComponentNames.MAIN_ACTIVITY).apply {
            if (!startRoute.isNullOrBlank()) {
                putExtra(MainActivity.EXTRA_START_ROUTE, startRoute)
            }
            if (!startTab.isNullOrBlank()) {
                putExtra(MainActivity.EXTRA_START_TAB, startTab)
            }
        }
    }

    fun applicationInfoIntent(
        context: Context,
        packageName: String,
        ignoreNotRegistered: Boolean = false,
    ): Intent {
        return Intent().setClassName(context, LegacyComponentNames.APPLICATION_INFO_PAGE)
            .putExtra(ApplicationInfoPage.EXTRA_PACKAGE_NAME, packageName)
            .putExtra(ApplicationInfoPage.EXTRA_IGNORE_NOT_REGISTERED, ignoreNotRegistered)
    }

    fun helpPageIntent(context: Context): Intent {
        return Intent().setClassName(context, LegacyComponentNames.HELP_PAGE)
    }

    fun recentEventListIntent(
        context: Context,
        packageName: String,
    ): Intent {
        return Intent().setClassName(context, LegacyComponentNames.RECENT_EVENT_LIST_PAGE)
            .setData(Uri.parse(packageName))
    }

    fun requestPermissionIntent(
        context: Context,
        recheckOnly: Boolean = false,
    ): Intent {
        return Intent().setClassName(context, LegacyComponentNames.REQUEST_PERMISSION_PAGE)
            .putExtra(RequestPermissionPage.EXTRA_RECHECK_ONLY, recheckOnly)
    }
}
