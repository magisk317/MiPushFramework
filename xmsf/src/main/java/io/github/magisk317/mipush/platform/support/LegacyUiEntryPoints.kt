package io.github.magisk317.mipush.platform.support

import android.content.Context
import android.content.Intent
import android.net.Uri

object LegacyUiEntryPoints {
    // Intent extra keys — mirrored from Activity companions to avoid cross-module class references.
    private const val EXTRA_START_ROUTE = "extra_start_route"
    private const val EXTRA_START_TAB = "extra_start_tab"
    private const val EXTRA_PACKAGE_NAME = "EXTRA_PACKAGE_NAME"
    private const val EXTRA_IGNORE_NOT_REGISTERED = "EXTRA_IGNORE_NOT_REGISTERED"
    private const val EXTRA_RECHECK_ONLY = "extra_recheck_only"

    fun mainActivityIntent(
        context: Context,
        startRoute: String? = null,
        startTab: String? = null,
    ): Intent {
        return Intent().setClassName(context, LegacyComponentNames.MAIN_ACTIVITY).apply {
            if (!startRoute.isNullOrBlank()) {
                putExtra(EXTRA_START_ROUTE, startRoute)
            }
            if (!startTab.isNullOrBlank()) {
                putExtra(EXTRA_START_TAB, startTab)
            }
        }
    }

    fun applicationInfoIntent(
        context: Context,
        packageName: String,
        ignoreNotRegistered: Boolean = false,
    ): Intent {
        return Intent().setClassName(context, LegacyComponentNames.APPLICATION_INFO_PAGE)
            .putExtra(EXTRA_PACKAGE_NAME, packageName)
            .putExtra(EXTRA_IGNORE_NOT_REGISTERED, ignoreNotRegistered)
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
            .putExtra(EXTRA_RECHECK_ONLY, recheckOnly)
    }
}
