package io.github.magisk317.mipush.feature.main

import io.github.magisk317.mipush.common.R as CommonR
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.manager.application.ManagerApplication
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.manager.notification.NotificationChannelGroupSummary
import io.github.magisk317.mipush.manager.notification.NotificationChannelSnapshot
import io.github.magisk317.mipush.manager.notification.NotificationChannelSummary

class AppConfigurationUtils(
    private val context: Context,
    private val application: ManagerApplication,
) {
    fun shouldSuggestFakeApp(pkg: String): Boolean {
        return !isBlacklistApp(pkg) && Utils.isUserApplication(pkg)
    }

    fun isBlacklistApp(pkg: String): Boolean {
        return isBlacklistContaines(pkg) || isBlacklistMatches(pkg)
    }

    fun isBlacklistMatches(pkg: String): Boolean {
        val pkgsContains = context.resources.getStringArray(CommonR.array.fake_blacklist_contains)
        for (p in pkgsContains) {
            if (pkg.contains(p)) {
                return true
            }
        }
        return false
    }

    fun isBlacklistContaines(pkg: String): Boolean {
        val pkgsEqual = context.resources.getStringArray(CommonR.array.fake_blacklist_equals).toList()
        return pkgsEqual.contains(pkg)
    }

    fun gotoRecentEventsPage() {
        startSettingsScreen(
            intent = Intent(context, RecentEventListPage::class.java)
                .setData(application.packageName.toUri()),
            fallbackPackage = application.packageName,
            label = "recent_events",
        )
    }

    fun gotoNotificationSettingPage(isHooked: Boolean) {
        startSettingsScreen(
            intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, configApp(isHooked)),
            fallbackPackage = application.packageName,
            label = "app_notification_settings",
        )
    }

    fun gotoTargetNotificationSettingPage() {
        startSettingsScreen(
            intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, application.packageName),
            fallbackPackage = application.packageName,
            label = "target_notification_settings",
        )
    }

    fun gotoNotificationChannelSettingPage(channel: NotificationChannelSummary, isHooked: Boolean) {
        startSettingsScreen(
            intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, configAppFor(channel, isHooked))
                .putExtra(Settings.EXTRA_CHANNEL_ID, channel.id),
            fallbackPackage = configAppFor(channel, isHooked),
            label = "channel_notification_settings",
        )
    }

    /**
     * Open a Settings screen without letting a missing handler crash the manager.
     *
     * Settings activities are absent on some ROMs and can be filtered out by an OEM build, so
     * [Context.startActivity] can throw `ActivityNotFoundException`. Degrade to the app detail
     * page and, if that is unavailable too, log instead of taking the process down.
     */
    private fun startSettingsScreen(intent: Intent, fallbackPackage: String, label: String) {
        runCatching { context.startActivity(intent) }
            .onFailure { error ->
                logW("$label launch failed: ${error.javaClass.simpleName}")
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData("package:$fallbackPackage".toUri()),
                    )
                }.onFailure { fallbackError ->
                    logW("$label fallback launch failed: ${fallbackError.javaClass.simpleName}")
                }
            }
    }

    fun copyToClipboard(channel: NotificationChannelSummary) {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.setPrimaryClip(ClipData.newPlainText("channel_id", channel.id))
    }

    /**
     * Settings deep-link package for this app's notification owner.
     * Hooked / target-provisioned channels live under the target package; otherwise under XMSF.
     */
    fun configApp(isHooked: Boolean): String =
        if (isHooked) application.packageName else Constants.SERVICE_APP_NAME

    fun configAppFor(channel: NotificationChannelSummary, isHooked: Boolean): String {
        // Unhooked MiPush-managed channels live under XMSF; everything else under target package.
        return if (channel.managedByMiPush && !isHooked) {
            Constants.SERVICE_APP_NAME
        } else {
            application.packageName
        }
    }

    companion object {
        /** Build the product's MiPush/native sections from the Binder-owned domain snapshot. */
        fun notificationChannelSections(snapshot: NotificationChannelSnapshot): List<NotificationChannelSection> {
            val groupById = snapshot.groups.associateBy { it.id }
            val mipushChannels = snapshot.channels.filter(NotificationChannelSummary::managedByMiPush)
            val nativeChannels = snapshot.channels.filterNot(NotificationChannelSummary::managedByMiPush)
            val sections = mutableListOf<NotificationChannelSection>()

            val mipushGroup = snapshot.groups.firstOrNull(NotificationChannelGroupSummary::managedByMiPush)
            if (mipushChannels.isNotEmpty() || mipushGroup != null) {
                sections += NotificationChannelSection(
                    kind = NotificationChannelSectionKind.MIPUSH,
                    group = mipushGroup,
                    channels = mipushChannels.sortedBy { it.id },
                )
            }

            nativeChannels.groupBy { it.groupId }.forEach { (groupId, groupChannels) ->
                sections += NotificationChannelSection(
                    kind = NotificationChannelSectionKind.NATIVE,
                    group = groupId?.let(groupById::get),
                    channels = groupChannels.sortedBy { it.id },
                )
            }

            return sections
        }

        @JvmStatic
        fun getNotificationTitle(channel: NotificationChannelSummary): String =
            channel.name.trim().ifEmpty { channel.id }

        @JvmStatic
        fun getNotificationSummary(channel: NotificationChannelSummary): String {
            var summary = "id: " + channel.id
            val description = channel.description
            if (!description.isNullOrEmpty()) {
                summary += "\n$description"
            }
            return summary
        }
    }
}

enum class NotificationChannelSectionKind {
    MIPUSH,
    NATIVE,
}

data class NotificationChannelSection(
    val kind: NotificationChannelSectionKind,
    val group: NotificationChannelGroupSummary?,
    val channels: List<NotificationChannelSummary>,
)
