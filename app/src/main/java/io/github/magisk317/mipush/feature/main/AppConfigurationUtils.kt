package io.github.magisk317.mipush.feature.main

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import io.github.magisk317.mipush.notification.NotificationManagerEx
import io.github.magisk317.mipush.runtime.R
import io.github.magisk317.mipush.notification.NotificationChannelManager
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.common.utils.NotificationUtils
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.platform.support.LegacyUiEntryPoints
import io.github.magisk317.mipush.runtime.core.store.entities.RegisteredApplication

class AppConfigurationUtils(
    private val context: Context,
    private val application: RegisteredApplication
) {
    fun shouldSuggestFakeApp(pkg: String): Boolean {
        return !isBlacklistApp(pkg) && Utils.isUserApplication(pkg)
    }

    fun isBlacklistApp(pkg: String): Boolean {
        return isBlacklistContaines(pkg) || isBlacklistMatches(pkg)
    }

    fun isBlacklistMatches(pkg: String): Boolean {
        val pkgsContains = context.resources.getStringArray(R.array.fake_blacklist_contains)
        for (p in pkgsContains) {
            if (pkg.contains(p)) {
                return true
            }
        }
        return false
    }

    fun isBlacklistContaines(pkg: String): Boolean {
        val pkgsEqual = context.resources.getStringArray(R.array.fake_blacklist_equals).toList()
        return pkgsEqual.contains(pkg)
    }

    fun gotoRecentEventsPage() {
        context.startActivity(
            LegacyUiEntryPoints.recentEventListIntent(
                context = context,
                packageName = application.packageName,
            ),
        )
    }

    fun gotoNotificationSettingPage() {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, Constants.SERVICE_APP_NAME)
        )
    }

    fun gotoNotificationChannelSettingPage(channel: NotificationChannel, configApp: String) {
        context.startActivity(
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, configApp)
                .putExtra(Settings.EXTRA_CHANNEL_ID, channel.id)
        )
    }

    fun copyToClipboard(channel: NotificationChannel) {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.setPrimaryClip(ClipData.newPlainText("channel_id", channel.id))
    }

    fun deleteNotificationChannel(channel: NotificationChannel) {
        NotificationManagerEx.deleteNotificationChannel(application.packageName, channel.id)
    }

    val notificationChannels: List<NotificationChannel>?
        get() = NotificationManagerEx.getNotificationChannels(application.packageName)?.filterNotNull()

    val configApp: String
        get() = if (NotificationManagerEx.isHooked) application.packageName else Constants.SERVICE_APP_NAME

    fun getNotificationCategoryName(group: NotificationChannelGroup): String {
        val suffix = if (group.id == null) "" else String.format(": %s (%s)", group.name, group.id)
        return context.getString(R.string.notification_channels) + suffix
    }

    val notificationChannelGroups: List<NotificationChannelGroup>
        get() {
        val mipushGroup = NotificationUtils.getGroupIdByPkg(application.packageName)
        val groups = NotificationManagerEx.getNotificationChannelGroups(application.packageName)?.filterNotNull()?.toMutableList() ?: mutableListOf()
        if (NotificationManagerEx.isHooked) {
            makeMIPushGroupToTopPositions(groups, mipushGroup)
        } else {
            removeAllNonMIPushGroup(groups, mipushGroup)
        }
        return groups
    }

    companion object {
        @JvmStatic
        fun removeAllNonMIPushGroup(groups: MutableList<NotificationChannelGroup>, mipushGroup: String) {
            groups.removeIf { group -> !TextUtils.equals(group.id, mipushGroup) }
        }

        @JvmStatic
        fun makeMIPushGroupToTopPositions(groups: MutableList<NotificationChannelGroup>, mipushGroup: String) {
            groups.sortWith { lhs, rhs ->
                if (TextUtils.equals(lhs.id, mipushGroup) || rhs.id == null) {
                    return@sortWith -1
                }
                if (TextUtils.equals(rhs.id, mipushGroup) || lhs.id == null) {
                    return@sortWith 1
                }
                lhs.id.compareTo(rhs.id)
            }
        }

        @JvmStatic
        fun getNotificationTitle(channel: NotificationChannel): CharSequence {
            var title: CharSequence = channel.name
            if (!NotificationChannelManager.isNotificationChannelEnabled(channel)) {
                title = "[disable]$title"
            }
            return title
        }

        @JvmStatic
        fun getNotificationSummary(channel: NotificationChannel): String {
            var summary = "id: " + channel.id
            val description = channel.description
            if (!TextUtils.isEmpty(description)) {
                summary += "\n$description"
            }
            return summary
        }
    }
}
