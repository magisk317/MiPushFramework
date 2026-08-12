package io.github.magisk317.mipush.common.notification

/** Contract shared by the XMSF publisher and the SystemUI proxy for known broken click routes. */
object NotificationClickFallbackContract {
    const val USE_LAUNCHER_FALLBACK = "mipush.click_use_launcher_fallback"

    private val knownProblematicPackages = setOf(
        "com.tencent.mobileqq",
        "com.youku.phone",
        "com.tudou.android",
        "com.baidu.tieba",
        "com.taobao.idlefish",
    )

    fun shouldUseLauncherFallback(packageName: String?): Boolean =
        packageName != null && packageName in knownProblematicPackages
}
