package io.github.magisk317.mipush.common.utils

/**
 * @author Trumeet
 * @date 2018/1/30
 */
object NotificationUtils {
    @JvmStatic
    fun getChannelIdByPkg(packageName: String): String {
        // update version 2
        return "ch_$packageName"
    }

    @JvmStatic
    fun getGroupIdByPkg(packageName: String): String {
        return "gp_$packageName"
    }

    @JvmStatic
    fun isMiPushManagedChannelId(packageName: String, channelId: String?): Boolean {
        if (channelId.isNullOrEmpty()) return false
        val legacyPrefix = getChannelIdByPkg(packageName)
        val stockOldPrefix = "mipush_${packageName}_"
        val stockNewPrefix = "mipush|$packageName|"
        return channelId == legacyPrefix ||
            channelId.startsWith(legacyPrefix + "_") ||
            channelId.startsWith(stockOldPrefix) ||
            channelId.startsWith(stockNewPrefix)
    }

    @JvmStatic
    fun isMiPushManagedGroupId(packageName: String, groupId: String?): Boolean {
        if (groupId.isNullOrEmpty()) return false
        return groupId == getGroupIdByPkg(packageName)
    }
}
