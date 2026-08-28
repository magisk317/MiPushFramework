package io.github.magisk317.mipush.notification.policy

/**
 * Notification style classification for HyperIsland template selection.
 * Priority: Channel > Content Keywords > Package Name.
 */
enum class NotificationStyle {
    MESSAGE,
    GENERAL,
    BANNER,
    ALERT,
    PROMO,
    MEDIA,
    PROGRESS,
}

/**
 * Platform-neutral notification classifier.
 *
 * Classifies push notifications into styles for template selection based on
 * channel metadata, content keywords, and package name fallback.
 */
object NotificationClassifier {
    private val IM_PACKAGES = setOf(
        "com.tencent.mm",
        "com.tencent.mobileqq",
        "com.alibaba.android.rimet",
        "org.telegram.messenger",
        "com.whatsapp",
        "jp.naver.line.android",
        "com.discord",
    )

    private val MEDIA_PACKAGES = setOf(
        "com.netease.cloudmusic",
        "com.kugou.android",
        "com.tencent.qqmusic",
        "fm.qingting.qtradio",
        "com.ximalaya.ting.android",
        "com.tencent.karaoke",
    )

    private val PROGRESS_KEYWORDS = setOf(
        "下载", "上传", "download", "upload", "安装", "install",
        "更新", "update", "同步", "sync", "传输", "transfer", "进度", "progress",
    )

    private val ALERT_KEYWORDS = setOf(
        "倒计时", "countdown", "计时", "timer", "闹钟", "alarm",
        "提醒", "reminder", "定时", "预约", "待办",
    )

    private val PROMO_KEYWORDS = setOf(
        "优惠", "折扣", "促销", "限时", "秒杀", "抢购",
        "券", "红包", "满减", "特价", "¥", "￥", "$",
        "discount", "sale", "coupon", "off",
    )

    private val BANNER_KEYWORDS = setOf(
        "广告", "推广", "推荐", "精选", "热门", "头条",
        "ad", "promo", "featured",
    )

    private val MSG_CHANNEL_KEYWORDS = setOf("msg", "chat", "message", "消息", "聊天")
    private val MEDIA_CHANNEL_KEYWORDS = setOf("media", "music", "playback", "播放", "媒体")
    private val PROGRESS_CHANNEL_KEYWORDS = setOf("progress", "download", "下载", "进度")

    fun classify(
        title: String,
        content: String,
        packageName: String? = null,
        channelId: String? = null,
        channelName: String? = null,
    ): NotificationStyle {
        val text = "$title $content".lowercase()
        channelId?.let { classifyByChannel(it, channelName) }?.let { return it }
        classifyByContent(text)?.let { return it }
        packageName?.let { classifyByPackage(it) }?.let { return it }
        return NotificationStyle.GENERAL
    }

    private fun classifyByChannel(channelId: String, channelName: String?): NotificationStyle? {
        val id = channelId.lowercase()
        val name = channelName?.lowercase() ?: ""
        if (MSG_CHANNEL_KEYWORDS.any { id.contains(it) || name.contains(it) }) return NotificationStyle.MESSAGE
        if (MEDIA_CHANNEL_KEYWORDS.any { id.contains(it) || name.contains(it) }) return NotificationStyle.MEDIA
        if (PROGRESS_CHANNEL_KEYWORDS.any { id.contains(it) || name.contains(it) }) return NotificationStyle.PROGRESS
        return null
    }

    private fun classifyByContent(text: String): NotificationStyle? {
        if (PROGRESS_KEYWORDS.any { text.contains(it) }) return NotificationStyle.PROGRESS
        if (ALERT_KEYWORDS.any { text.contains(it) }) return NotificationStyle.ALERT
        if (PROMO_KEYWORDS.any { text.contains(it) }) return NotificationStyle.PROMO
        if (BANNER_KEYWORDS.any { text.contains(it) }) return NotificationStyle.BANNER
        return null
    }

    private fun classifyByPackage(packageName: String): NotificationStyle? {
        if (packageName in IM_PACKAGES) return NotificationStyle.MESSAGE
        if (packageName in MEDIA_PACKAGES) return NotificationStyle.MEDIA
        return null
    }
}
