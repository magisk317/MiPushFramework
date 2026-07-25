package io.github.magisk317.mipush.feature.main

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.common.manager.ManagerApplication
import io.github.magisk317.mipush.common.manager.ManagerNotificationGateway
import io.github.magisk317.mipush.common.utils.NotificationUtils
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.manager.R

class AppConfigurationUtils(
    private val context: Context,
    private val application: ManagerApplication,
    private val notificationGateway: ManagerNotificationGateway,
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
            Intent(context, RecentEventListPage::class.java)
                .setData(Uri.parse(application.packageName)),
        )
    }

    fun gotoNotificationSettingPage() {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, configApp)
        )
    }

    fun gotoNotificationChannelSettingPage(channel: NotificationChannel, configApp: String = this.configAppFor(channel)) {
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
        notificationGateway.deleteNotificationChannel(application.packageName, channel.id)
    }

    val notificationChannels: List<NotificationChannel>
        get() = notificationGateway.getNotificationChannels(application.packageName)

    /**
     * Settings deep-link package for this app's notification owner.
     * Hooked / target-provisioned channels live under the target package; otherwise under XMSF.
     */
    val configApp: String
        get() = if (notificationGateway.isHooked) {
            application.packageName
        } else {
            Constants.SERVICE_APP_NAME
        }

    fun configAppFor(channel: NotificationChannel): String {
        // Unhooked MiPush-managed channels live under XMSF; everything else under target package.
        val managed = NotificationUtils.isMiPushManagedChannelId(application.packageName, channel.id)
        return if (managed && !notificationGateway.isHooked) {
            Constants.SERVICE_APP_NAME
        } else {
            application.packageName
        }
    }

    fun getNotificationCategoryName(group: NotificationChannelGroup): String {
        val suffix = if (group.id == null) "" else String.format(": %s (%s)", group.name, group.id)
        return context.getString(R.string.notification_channels) + suffix
    }

    fun isMiPushManagedChannel(channel: NotificationChannel): Boolean {
        return NotificationUtils.isMiPushManagedChannelId(application.packageName, channel.id) ||
            NotificationUtils.isMiPushManagedGroupId(application.packageName, channel.group)
    }

    fun isMiPushManagedGroup(group: NotificationChannelGroup): Boolean {
        return NotificationUtils.isMiPushManagedGroupId(application.packageName, group.id)
    }

    /**
     * Product model C: dual sections.
     * - MiPush: managed group / ch_$pkg* ids
     * - Native: remaining target-app channels (hooked / identity-capable paths)
     */
    fun notificationChannelSections(): List<NotificationChannelSection> {
        val mipushGroupId = NotificationUtils.getGroupIdByPkg(application.packageName)
        val groups = notificationGateway.getNotificationChannelGroups(application.packageName)
        val channels = notificationChannels
        val groupById = groups.associateBy { it.id }

        val mipushChannels = channels.filter { isMiPushManagedChannel(it) }
        val nativeChannels = channels.filterNot { isMiPushManagedChannel(it) }

        val sections = mutableListOf<NotificationChannelSection>()

        val mipushGroup = groupById[mipushGroupId]
        if (mipushChannels.isNotEmpty() || mipushGroup != null) {
            sections += NotificationChannelSection(
                kind = NotificationChannelSectionKind.MIPUSH,
                title = context.getString(R.string.notification_channels_section_mipush),
                summary = context.getString(R.string.notification_channels_section_mipush_summary),
                group = mipushGroup,
                channels = mipushChannels.sortedBy { it.id },
            )
        }

        if (notificationGateway.isHooked && nativeChannels.isNotEmpty()) {
            val nativeByGroup = nativeChannels.groupBy { it.group }
            nativeByGroup.forEach { (groupId, groupChannels) ->
                val group = groupId?.let { groupById[it] }
                val title = if (group != null) {
                    context.getString(R.string.notification_channels_section_native) +
                        String.format(": %s (%s)", group.name, group.id)
                } else {
                    context.getString(R.string.notification_channels_section_native)
                }
                sections += NotificationChannelSection(
                    kind = NotificationChannelSectionKind.NATIVE,
                    title = title,
                    summary = context.getString(R.string.notification_channels_section_native_summary),
                    group = group,
                    channels = groupChannels.sortedBy { it.id },
                )
            }
        } else if (!notificationGateway.isHooked) {
            // Unhooked: only show MiPush-managed surface; native app channels are not owned here.
        }

        return sections
    }

    val notificationChannelGroups: List<NotificationChannelGroup>
        get() {
            val mipushGroup = NotificationUtils.getGroupIdByPkg(application.packageName)
            val groups = notificationGateway.getNotificationChannelGroups(application.packageName).toMutableList()
            if (notificationGateway.isHooked) {
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
                compareNotificationChannelGroupIds(lhs.id, rhs.id, mipushGroup)
            }
        }

        internal fun compareNotificationChannelGroupIds(
            lhsId: String?,
            rhsId: String?,
            mipushGroup: String,
        ): Int {
            val priorityCompare = compareValues(
                notificationChannelGroupPriority(lhsId, mipushGroup),
                notificationChannelGroupPriority(rhsId, mipushGroup),
            )
            if (priorityCompare != 0) return priorityCompare
            return compareValues(lhsId.orEmpty(), rhsId.orEmpty())
        }

        private fun notificationChannelGroupPriority(id: String?, mipushGroup: String): Int {
            return when {
                id == mipushGroup -> 0
                id == null -> 2
                else -> 1
            }
        }

        @JvmStatic
        fun getNotificationTitle(
            channel: NotificationChannel,
            notificationGateway: ManagerNotificationGateway,
        ): CharSequence {
            var title: CharSequence = channel.name
            if (!notificationGateway.isNotificationChannelEnabled(channel)) {
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

enum class NotificationChannelSectionKind {
    MIPUSH,
    NATIVE,
}

data class NotificationChannelSection(
    val kind: NotificationChannelSectionKind,
    val title: String,
    val summary: String,
    val group: NotificationChannelGroup?,
    val channels: List<NotificationChannel>,
)
