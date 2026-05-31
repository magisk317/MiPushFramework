package io.github.magisk317.mipush.common

/**
 * 通知分类器 - 根据推送元数据自动分类，用于选择不同的 HyperIsland 模板
 *
 * 优先级：Channel > Content Keywords > Package Name
 * 内容关键词优先于包名，避免综合性 App（如支付宝）的通知被错误归类为 IM。
 */
object NotificationClassifier {

    // ==================== 包名集合 ====================

    /** 已知 IM 类 app（仅作为兜底，不覆盖内容关键词匹配） */
    private val IM_PACKAGES = setOf(
        "com.tencent.mm",               // 微信
        "com.tencent.mobileqq",         // QQ
        "com.alibaba.android.rimet",    // 钉钉
        "org.telegram.messenger",       // Telegram
        "com.whatsapp",                 // WhatsApp
        "jp.naver.line.android",        // LINE
        "com.discord",                  // Discord
    )

    /** 已知媒体类 app */
    private val MEDIA_PACKAGES = setOf(
        "com.netease.cloudmusic",       // 网易云音乐
        "com.kugou.android",            // 酷狗
        "com.tencent.qqmusic",          // QQ音乐
        "fm.qingting.qtradio",          // 蜻蜓FM
        "com.ximalaya.ting.android",    // 喜马拉雅
        "com.tencent.karaoke",          // 全民K歌
    )

    // ==================== 关键词集合 ====================

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

    // ==================== Channel 关键词 ====================

    private val MSG_CHANNEL_KEYWORDS = setOf("msg", "chat", "message", "消息", "聊天")
    private val MEDIA_CHANNEL_KEYWORDS = setOf("media", "music", "playback", "播放", "媒体")
    private val PROGRESS_CHANNEL_KEYWORDS = setOf("progress", "download", "下载", "进度")

    // ==================== 分类逻辑 ====================

    /**
     * 根据推送元数据分类通知样式
     *
     * @param title 通知标题
     * @param content 通知内容
     * @param packageName 发送方包名
     * @param channelId 通知渠道 ID
     * @param channelName 通知渠道名称
     */
    fun classify(
        title: String,
        content: String,
        packageName: String? = null,
        channelId: String? = null,
        channelName: String? = null,
    ): NotificationStyle {
        val text = "$title $content".lowercase()

        // 1. Channel 判断（优先级最高 - 系统级明确分类）
        channelId?.let { classifyByChannel(it, channelName) }?.let { return it }

        // 2. 内容关键词判断（优先于包名 - 避免综合性 App 误判）
        classifyByContent(text)?.let { return it }

        // 3. 包名兜底（仅当内容无特征时使用）
        packageName?.let { classifyByPackage(it) }?.let { return it }

        // 4. 默认通用通知
        return NotificationStyle.GENERAL
    }

    private fun classifyByChannel(channelId: String, channelName: String?): NotificationStyle? {
        val id = channelId.lowercase()
        val name = channelName?.lowercase() ?: ""

        if (MSG_CHANNEL_KEYWORDS.any { id.contains(it) || name.contains(it) }) {
            return NotificationStyle.MESSAGE
        }
        if (MEDIA_CHANNEL_KEYWORDS.any { id.contains(it) || name.contains(it) }) {
            return NotificationStyle.MEDIA
        }
        if (PROGRESS_CHANNEL_KEYWORDS.any { id.contains(it) || name.contains(it) }) {
            return NotificationStyle.PROGRESS
        }
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
