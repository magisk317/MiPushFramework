package top.trumeet.common.utils

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
    fun getPackageName(@NonNull groupOrChannel: String): String {
        return groupOrChannel.substring(3)
    }
}
