package io.github.magisk317.mipush.common

/**
 * 通知样式分类 - 用于选择 HyperIsland 模板
 */
enum class NotificationStyle {
    /** IM/聊天消息 - 使用 ChatInfo 模板 */
    MESSAGE,
    /** 通用通知（新闻/系统） - 使用 IconTextInfo */
    GENERAL,
    /** 横幅通知（广告/推广） - 使用 IconTextInfo */
    BANNER,
    /** 提醒类（倒计时/闹钟） - 使用 HighlightInfo */
    ALERT,
    /** 促销类（价格/优惠） - 使用 HighlightInfoV3 */
    PROMO,
    /** 媒体类（音乐/播客） - 使用 CoverInfo */
    MEDIA,
    /** 进度类（下载/上传） - 使用 IconTextInfo + ProgressInfo */
    PROGRESS,
}
