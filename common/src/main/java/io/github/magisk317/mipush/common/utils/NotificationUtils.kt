package io.github.magisk317.mipush.common.utils

import androidx.annotation.NonNull

/**
 * @author Trumeet
 * @date 2018/1/30
 */
object NotificationUtils {
    @JvmStatic
    fun getChannelIdByPkg(@NonNull packageName: String): String {
        // update version 2
        return "ch_$packageName"
    }

    @JvmStatic
    fun getGroupIdByPkg(@NonNull packageName: String): String {
        return "gp_$packageName"
    }

    @JvmStatic
    fun isMiPushManagedChannelId(@NonNull packageName: String, channelId: String?): Boolean {
        if (channelId.isNullOrEmpty()) return false
        val prefix = getChannelIdByPkg(packageName)
        return channelId == prefix || channelId.startsWith(prefix + "_")
    }

    @JvmStatic
    fun isMiPushManagedGroupId(@NonNull packageName: String, groupId: String?): Boolean {
        if (groupId.isNullOrEmpty()) return false
        return groupId == getGroupIdByPkg(packageName)
    }
}
