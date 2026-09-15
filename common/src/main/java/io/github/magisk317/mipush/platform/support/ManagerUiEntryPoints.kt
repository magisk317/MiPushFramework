package io.github.magisk317.mipush.platform.support

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/** Cross-process intents targeting canonical activities in the MiPush manager package. */
object ManagerUiEntryPoints {
    private const val EXTRA_START_ROUTE = "extra_start_route"
    private const val EXTRA_START_TAB = "extra_start_tab"
    private const val EXTRA_PACKAGE_NAME = "EXTRA_PACKAGE_NAME"
    private const val EXTRA_IGNORE_NOT_REGISTERED = "EXTRA_IGNORE_NOT_REGISTERED"
    private const val EXTRA_RECHECK_ONLY = "extra_recheck_only"
    private const val EXTRA_INITIAL_QUERY = "extra_initial_query"
    private const val EXTRA_INITIAL_PATH = "extra_initial_path"

    fun managerUiPackage(context: Context): String {
        return if (hasLocalManagerUi(context)) context.packageName else ManagerComponentNames.PACKAGE
    }

    private fun hasLocalManagerUi(context: Context): Boolean {
        return runCatching {
            Class.forName(ManagerComponentNames.MAIN_ACTIVITY, false, context.classLoader)
            true
        }.getOrDefault(false)
    }

    private fun managerUiIntent(context: Context, className: String): Intent =
        Intent().setClassName(managerUiPackage(context), className)

    fun mainActivityIntent(
        context: Context,
        startRoute: String? = null,
        startTab: String? = null,
    ): Intent {
        return managerUiIntent(context, ManagerComponentNames.MAIN_ACTIVITY).apply {
            if (!startRoute.isNullOrBlank()) putExtra(EXTRA_START_ROUTE, startRoute)
            if (!startTab.isNullOrBlank()) putExtra(EXTRA_START_TAB, startTab)
        }
    }

    fun applicationInfoIntent(
        context: Context,
        packageName: String,
        ignoreNotRegistered: Boolean = false,
    ): Intent {
        return managerUiIntent(context, ManagerComponentNames.APPLICATION_INFO_PAGE)
            .putExtra(EXTRA_PACKAGE_NAME, packageName)
            .putExtra(EXTRA_IGNORE_NOT_REGISTERED, ignoreNotRegistered)
    }

    fun recentEventListIntent(context: Context, packageName: String): Intent {
        return managerUiIntent(context, ManagerComponentNames.RECENT_EVENT_LIST_PAGE)
            .setData(packageName.toUri())
    }

    fun configurationsIntent(
        context: Context,
        initialQuery: String? = null,
        initialPath: String? = null,
    ): Intent {
        return managerUiIntent(context, ManagerComponentNames.CONFIGURATIONS_PAGE).apply {
            if (!initialQuery.isNullOrBlank()) putExtra(EXTRA_INITIAL_QUERY, initialQuery)
            if (!initialPath.isNullOrBlank()) putExtra(EXTRA_INITIAL_PATH, initialPath)
        }
    }

    fun requestPermissionIntent(
        context: Context,
        recheckOnly: Boolean = false,
    ): Intent {
        return managerUiIntent(context, ManagerComponentNames.REQUEST_PERMISSION_PAGE)
            .putExtra(EXTRA_RECHECK_ONLY, recheckOnly)
    }
}
