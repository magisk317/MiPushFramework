package io.github.magisk317.mipush.hook.system

import android.app.Notification
import android.graphics.drawable.Icon
import android.os.Bundle
import io.github.magisk317.mipush.common.notification.NotificationContentSupport
import io.github.magisk317.mipush.hook.island.IslandDispatchContract
import io.github.magisk317.mipush.hook.island.IslandPreferences
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject

/**
 * Restores the MIUI focus contract for AMap's normal driving foreground notification.
 *
 * AMap already posts native focus notifications for its taxi, walk/ride, and UA paths.
 * Normal driving uses a different foreground-notification path, so it needs this narrow
 * compatibility bridge before NotificationManagerService parses its extras.
 */
internal object AmapNavigationFocusCompat {
    private const val AMAP_PACKAGE = "com.autonavi.minimap"
    private const val EXTRA_NAVIGATING = "com.autonavi.minimap.navigating"
    private const val FOCUS_PICTURES = "miui.focus.pics"
    private const val FOCUS_LARGE_PICTURE = "miui.focus.pic_large"
    private const val FOCUS_NAVIGATION_PICTURE = "miui.focus.pic_amap_navigation"

    fun attachIfEligible(
        packageName: String?,
        notification: Notification,
        focusAuthorizationBypassEnabled: Boolean = IslandPreferences.current().focusNotification,
    ): Boolean {
        if (!focusAuthorizationBypassEnabled || !shouldAttach(packageName, notification)) return false

        val extras = notification.extras
        val title = NotificationContentSupport.firstText(
            extras,
            Notification.EXTRA_TITLE,
            Notification.EXTRA_TITLE_BIG,
        ) ?: notification.tickerText?.toString()?.takeIf { it.isNotBlank() } ?: "AMap"
        val content = NotificationContentSupport.firstText(
            extras,
            Notification.EXTRA_TEXT,
            Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_SUB_TEXT,
        ) ?: title

        extras.putString(IslandDispatchContract.FOCUS_PARAM, buildFocusParam(title, content))
        resolvePicture(notification)?.let { picture ->
            extras.putBundle(
                FOCUS_PICTURES,
                Bundle().apply {
                    putParcelable(FOCUS_LARGE_PICTURE, picture)
                    putParcelable(FOCUS_NAVIGATION_PICTURE, picture)
                },
            )
        }
        return true
    }

    /**
     * NMS internal enqueue overloads keep the target package first, but their remaining
     * parameters differ between platform releases. Resolve the notification by type so the
     * foreground-service path is covered without binding the compatibility bridge to one ABI.
     */
    fun attachIfEligibleFromNmsArguments(
        arguments: Array<Any?>,
        focusAuthorizationBypassEnabled: Boolean = IslandPreferences.current().focusNotification,
    ): Boolean {
        val packageName = arguments.getOrNull(0) as? String
        val notification = arguments.firstOrNull { it is Notification } as? Notification ?: return false
        return attachIfEligible(packageName, notification, focusAuthorizationBypassEnabled)
    }

    fun attachIfEligibleFromForegroundServiceNmsArguments(
        arguments: Array<Any?>,
        focusAuthorizationBypassEnabled: Boolean = IslandPreferences.current().focusNotification,
    ): Boolean {
        // Current NMS internal overloads carry byForegroundService after userId (10 args)
        // or after postSilently (11 args).
        val byForegroundService = when (arguments.size) {
            10 -> arguments.getOrNull(8)
            11 -> arguments.getOrNull(9)
            else -> false
        }
        if (byForegroundService != true) return false
        return attachIfEligibleFromNmsArguments(arguments, focusAuthorizationBypassEnabled)
    }

    fun shouldAttach(packageName: String?, notification: Notification): Boolean {
        val extras = notification.extras
        return packageName == AMAP_PACKAGE &&
            notification.category == Notification.CATEGORY_NAVIGATION &&
            extras.getBoolean(EXTRA_NAVIGATING, false) &&
            !IslandDispatchContract.hasNativeFocusPayload(extras)
    }

    private fun buildFocusParam(title: String, content: String): String {
        val driveContent = parseDriveContent(title, content)
        return driveContent?.let(::buildDriveFocusParam)
            ?: buildStaticFocusParam(title, content)
    }

    private fun buildDriveFocusParam(content: DriveContent): String {
        return buildParamV2FocusPayload(
            baseTitle = content.notificationTitle,
            baseContent = content.nextTarget,
            compactLeftTitle = content.nextAction,
            compactRightTitle = content.distanceText,
            compactRightContent = DISTANCE_SUFFIX,
            highlightCompactRight = true,
        )
    }

    private fun buildStaticFocusParam(title: String, content: String): String {
        return buildParamV2FocusPayload(
            baseTitle = title,
            baseContent = content,
            compactLeftTitle = content,
            compactRightTitle = title,
            compactRightContent = null,
            highlightCompactRight = false,
        )
    }

    private fun buildParamV2FocusPayload(
        baseTitle: String,
        baseContent: String,
        compactLeftTitle: String,
        compactRightTitle: String,
        compactRightContent: String?,
        highlightCompactRight: Boolean,
    ): String {
        val sequence = FOCUS_SEQUENCE.incrementAndGet()
        val compactPicture = JSONObject()
            .put("type", 1)
            .put("pic", FOCUS_NAVIGATION_PICTURE)
        val compactLeft = JSONObject()
            .put("type", 1)
            .put("picInfo", compactPicture)
            .put("textInfo", JSONObject().put("title", compactLeftTitle))
        val compactRight = JSONObject()
            .put("showHighlightColor", highlightCompactRight)
            .put("title", compactRightTitle)
            .apply { compactRightContent?.let { put("content", it) } }
        val paramV2 = JSONObject()
            .put("baseInfo", JSONObject()
                .put("title", baseTitle)
                .put("content", baseContent)
                .put("showDivider", false)
                .put("type", 2))
            .put("param_island", JSONObject()
                .put("islandProperty", 1)
                .put("islandTimeout", NAVIGATION_ISLAND_TIMEOUT)
                .put("highlightColor", NAVIGATION_HIGHLIGHT_COLOR)
                .put("bigIslandArea", JSONObject()
                    .put("imageTextInfoLeft", compactLeft)
                    .put("textInfo", compactRight))
                .put("smallIslandArea", JSONObject()
                    .put("picInfo", JSONObject().put("type", 1))))
        putCommonFocusFields(paramV2, compactLeftTitle, sequence)
        return JSONObject().apply {
            put("param_v2", paramV2)
            putCommonFocusFields(this, compactLeftTitle, sequence)
        }.toString()
    }

    private fun putCommonFocusFields(target: JSONObject, ticker: String, sequence: Long) {
        target
            .put("ticker", ticker)
            .put("aodPic", FOCUS_NAVIGATION_PICTURE)
            .put("picInfo", JSONObject().put("type", 1))
            .put("enableFloat", false)
            .put("islandFirstFloat", true)
            .put("timeout", NAVIGATION_FOCUS_TIMEOUT)
            .put("sequence", sequence)
            .put("protocol", 1)
            .put("aodTitle", ticker)
            .put("updatable", true)
            .put("notifyId", NAVIGATION_NOTIFY_ID)
    }

    private fun parseDriveContent(title: String, content: String): DriveContent? {
        val match = DRIVE_TITLE_PATTERN.matchEntire(title.trim()) ?: return null
        val distanceText = match.groupValues[1].trim()
        val nextAction = match.groupValues.getOrNull(2).orEmpty().trim()
        if (distanceText.isEmpty()) return null
        val compactAction = nextAction.ifEmpty { NAVIGATING_COMPACT_TITLE }
        val target = content.trim()
        return DriveContent(
            distanceText = distanceText,
            nextAction = compactAction,
            nextTarget = target
                .takeUnless { it.isEmpty() || it == distanceText }
                ?: NAVIGATING_EXPANDED_CONTENT,
            notificationTitle = title.trim(),
        )
    }

    private fun resolvePicture(notification: Notification): Icon? {
        return notification.getLargeIcon() ?: notification.smallIcon
    }

    private data class DriveContent(
        val distanceText: String,
        val nextAction: String,
        val nextTarget: String,
        val notificationTitle: String,
    )

    private const val DISTANCE_SUFFIX = "\u540e"
    private const val NAVIGATION_HIGHLIGHT_COLOR = "#3F8CFF"
    private const val NAVIGATION_ISLAND_TIMEOUT = 14_400
    private const val NAVIGATION_FOCUS_TIMEOUT = 240
    private const val NAVIGATION_NOTIFY_ID = "com.autonavi.minimap99910001"
    private const val NAVIGATING_COMPACT_TITLE = "\u5bfc\u822a\u4e2d"
    private const val NAVIGATING_EXPANDED_CONTENT = "\u6b63\u5728\u5bfc\u822a"
    private const val MILLIS_PER_SECOND = 1_000L
    private val FOCUS_SEQUENCE = AtomicLong(System.currentTimeMillis() / MILLIS_PER_SECOND)
    private val DRIVE_TITLE_PATTERN = Regex(
        pattern = """^((?:\d{1,3}(?:,\d{3})+|\d+)(?:[.,]\d+)?\s*(?:\u516c\u91cc|\u5343\u7c73|\u516c\u5c3a|\u82f1\u5c3a|\u7c73|km|mi|ft|yd|m))(?:\s+(.+))?$""",
        option = RegexOption.IGNORE_CASE,
    )
}
