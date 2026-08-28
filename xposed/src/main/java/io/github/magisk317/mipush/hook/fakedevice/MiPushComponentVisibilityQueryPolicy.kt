package io.github.magisk317.mipush.hook.fakedevice

internal object MiPushComponentVisibilityQueryPolicy {
    const val XMSF_PACKAGE = "com.xiaomi.xmsf"
    const val MIPUSH_RECEIVE_ACTION = "com.xiaomi.mipush.RECEIVE_MESSAGE"
    const val MIPUSH_MIUI_RECEIVE_ACTION = "com.xiaomi.mipush.miui.RECEIVE_MESSAGE"
    const val MIPUSH_MIUI_CLICK_ACTION = "com.xiaomi.mipush.miui.CLICK_MESSAGE"
    const val MIPUSH_PING_ACTION = "com.xiaomi.push.PING_TIMER"
    const val CHANNEL_AUTHORITY = "com.xiaomi.xmsf.provider.CHANNEL"
    const val PUSH_SUPPORT_AUTHORITY = "com.xiaomi.push.provider.PUSH_SUPPORT"
    const val PUSH_COMMON_AUTHORITY = "com.xiaomi.push.provider.PUSH_COMMON"
    const val PUSH_PROFILE_AUTHORITY = "com.xiaomi.push.provider.profile"

    private val receiverActions = setOf(
        MIPUSH_RECEIVE_ACTION,
        MIPUSH_MIUI_RECEIVE_ACTION,
        MIPUSH_PING_ACTION,
    )
    private val xmsfAuthorities = setOf(
        CHANNEL_AUTHORITY,
        PUSH_SUPPORT_AUTHORITY,
        PUSH_COMMON_AUTHORITY,
        PUSH_PROFILE_AUTHORITY,
    )

    fun isMiPushReceiverQuery(
        action: String?,
        targetPackage: String?,
        ownPackage: String,
    ): Boolean = action in receiverActions &&
        (targetPackage == null || targetPackage == ownPackage)

    fun isMiPushServiceQuery(
        action: String?,
        targetPackage: String?,
        componentPackage: String?,
        ownPackage: String,
    ): Boolean {
        if (targetPackage == XMSF_PACKAGE || componentPackage == XMSF_PACKAGE) return true
        if (targetPackage != null && targetPackage != ownPackage) return false
        return action == MIPUSH_MIUI_CLICK_ACTION
    }

    fun isMiPushActivityQuery(
        action: String?,
        targetPackage: String?,
        ownPackage: String,
    ): Boolean {
        if (targetPackage != null && targetPackage != ownPackage && targetPackage != XMSF_PACKAGE) {
            return false
        }
        val normalizedAction = action.orEmpty()
        return normalizedAction.startsWith("com.xiaomi.mipush") ||
            normalizedAction.startsWith("com.xiaomi.push")
    }

    fun shouldPatchInstallerQuery(queryPackage: String?, ownPackage: String): Boolean =
        queryPackage == XMSF_PACKAGE || queryPackage == ownPackage

    fun shouldPatchProviderQuery(processName: String?): Boolean =
        processName == null || processName == XMSF_PACKAGE

    fun isMiPushProviderAuthority(authority: String?): Boolean =
        authority != null && authority in xmsfAuthorities
}
