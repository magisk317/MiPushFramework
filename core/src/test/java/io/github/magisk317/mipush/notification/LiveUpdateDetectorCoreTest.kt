package io.github.magisk317.mipush.notification

import io.github.magisk317.mipush.notification.LiveUpdateDetectorCore.DetectionInput
import io.github.magisk317.mipush.notification.LiveUpdateDetectorCore.DetectionResult
import io.github.magisk317.mipush.notification.LiveUpdateDetectorCore.ProgressCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class LiveUpdateDetectorCoreTest {

    // --- detect() tests ---

    @Nested
    inner class DetectTests {

        @Test
        fun `returns NONE for empty input`() {
            val input = DetectionInput("", "", "com.example.app")
            val result = LiveUpdateDetectorCore.detect(input)
            assertEquals(DetectionResult.NONE, result)
        }

        @Test
        fun `returns NONE for blacklisted package`() {
            val input = DetectionInput(
                title = "骑手已接单 正在配送中",
                description = "预计10分钟送达",
                packageName = "com.tencent.mm"
            )
            val result = LiveUpdateDetectorCore.detect(input)
            assertEquals(DetectionResult.NONE, result)
        }

        @Test
        fun `detects delivery category with progress indicator`() {
            val input = DetectionInput(
                title = "骑手已取餐",
                description = "正在配送中 预计5分钟送达",
                packageName = "com.meituan.takeout"
            )
            val result = LiveUpdateDetectorCore.detect(input)
            assertTrue(result.isProgress)
            assertEquals(ProgressCategory.DELIVERY, result.category)
        }

        @Test
        fun `detects ride hailing category`() {
            val input = DetectionInput(
                title = "司机正在前往",
                description = "预计3分钟到达上车点",
                packageName = "com.sdu.didi.psnger"
            )
            val result = LiveUpdateDetectorCore.detect(input)
            assertTrue(result.isProgress)
            assertEquals(ProgressCategory.RIDE_HAILING, result.category)
        }

        @Test
        fun `detects logistics category`() {
            val input = DetectionInput(
                title = "快递运输中",
                description = "您的包裹正在运输中 预计明天到达",
                packageName = "com.sf.activity"
            )
            val result = LiveUpdateDetectorCore.detect(input)
            assertTrue(result.isProgress)
            assertEquals(ProgressCategory.LOGISTICS, result.category)
        }

        @Test
        fun `detects download category with percentage`() {
            val input = DetectionInput(
                title = "正在下载更新",
                description = "下载中 45%",
                packageName = "com.example.app"
            )
            val result = LiveUpdateDetectorCore.detect(input)
            assertTrue(result.isProgress)
            assertEquals(ProgressCategory.DOWNLOAD, result.category)
            assertEquals(45, result.progressPercent)
        }

        @Test
        fun `detects travel category`() {
            val input = DetectionInput(
                title = "航班即将起飞",
                description = "登机口已开放 预计15分钟后起飞",
                packageName = "com.example.travel"
            )
            val result = LiveUpdateDetectorCore.detect(input)
            assertTrue(result.isProgress)
            assertEquals(ProgressCategory.TRAVEL, result.category)
        }

        @Test
        fun `returns NONE when no active progress indicator`() {
            // Has category keywords but no progress indicator
            val input = DetectionInput(
                title = "骑手评价",
                description = "请对骑手服务进行评价",
                packageName = "com.meituan.takeout"
            )
            val result = LiveUpdateDetectorCore.detect(input)
            assertFalse(result.isProgress)
        }

        @Test
        fun `returns NONE when no category matches`() {
            val input = DetectionInput(
                title = "新消息",
                description = "正在处理中",
                packageName = "com.example.app"
            )
            val result = LiveUpdateDetectorCore.detect(input)
            // "正在" is a progress keyword but no category keywords match
            assertFalse(result.isProgress)
        }

        @Test
        fun `progressText is description when non-blank`() {
            val input = DetectionInput(
                title = "骑手已取餐",
                description = "正在配送中 预计5分钟送达",
                packageName = "com.meituan.takeout"
            )
            val result = LiveUpdateDetectorCore.detect(input)
            assertEquals("正在配送中 预计5分钟送达", result.progressText)
        }
    }

    // --- isPotentialLiveUpdate() tests ---

    @Nested
    inner class IsPotentialLiveUpdateTests {

        @Test
        fun `returns true for delivery with progress keywords`() {
            assertTrue(
                LiveUpdateDetectorCore.isPotentialLiveUpdate(
                    "骑手已接单",
                    "正在配送中"
                )
            )
        }

        @Test
        fun `returns false for no category match`() {
            assertFalse(
                LiveUpdateDetectorCore.isPotentialLiveUpdate(
                    "新消息通知",
                    "您有一条新消息"
                )
            )
        }

        @Test
        fun `returns false for category match without progress indicator`() {
            assertFalse(
                LiveUpdateDetectorCore.isPotentialLiveUpdate(
                    "骑手评价",
                    "请对骑手服务进行评价"
                )
            )
        }
    }

    // --- detectCategory() tests ---

    @Nested
    inner class DetectCategoryTests {

        @Test
        fun `returns UNKNOWN for empty text`() {
            assertEquals(ProgressCategory.UNKNOWN, LiveUpdateDetectorCore.detectCategory(""))
        }

        @Test
        fun `returns UNKNOWN for single short keyword match`() {
            // Single short keyword match gives score 1, threshold is 2
            assertEquals(ProgressCategory.UNKNOWN, LiveUpdateDetectorCore.detectCategory("骑手"))
        }

        @Test
        fun `returns DELIVERY for multiple delivery keywords`() {
            assertEquals(
                ProgressCategory.DELIVERY,
                LiveUpdateDetectorCore.detectCategory("骑手已取餐 正在配送")
            )
        }

        @Test
        fun `returns DOWNLOAD for download keywords`() {
            assertEquals(
                ProgressCategory.DOWNLOAD,
                LiveUpdateDetectorCore.detectCategory("正在下载 downloading update")
            )
        }

        @Test
        fun `case insensitive matching`() {
            assertEquals(
                ProgressCategory.LOGISTICS,
                LiveUpdateDetectorCore.detectCategory("PACKAGE SHIPPING transit")
            )
        }
    }

    // --- hasActiveProgressIndicator() tests ---

    @Nested
    inner class HasActiveProgressIndicatorTests {

        @Test
        fun `returns true for percentage`() {
            assertTrue(LiveUpdateDetectorCore.hasActiveProgressIndicator("下载 45%"))
        }

        @Test
        fun `returns true for fraction`() {
            assertTrue(LiveUpdateDetectorCore.hasActiveProgressIndicator("3/10 完成"))
        }

        @Test
        fun `returns true for progress keywords`() {
            assertTrue(LiveUpdateDetectorCore.hasActiveProgressIndicator("正在处理"))
        }

        @Test
        fun `returns true for time estimate`() {
            assertTrue(LiveUpdateDetectorCore.hasActiveProgressIndicator("约5分钟"))
        }

        @Test
        fun `returns true for distance estimate`() {
            assertTrue(LiveUpdateDetectorCore.hasActiveProgressIndicator("距您500米"))
        }

        @Test
        fun `returns false for plain text`() {
            assertFalse(LiveUpdateDetectorCore.hasActiveProgressIndicator("普通消息内容"))
        }
    }

    // --- extractProgressPercent() tests ---

    @Nested
    inner class ExtractProgressPercentTests {

        @Test
        fun `extracts percentage from text`() {
            assertEquals(75, LiveUpdateDetectorCore.extractProgressPercent("下载进度 75%"))
        }

        @Test
        fun `extracts fraction as percentage`() {
            assertEquals(50, LiveUpdateDetectorCore.extractProgressPercent("3/6 完成"))
        }

        @Test
        fun `clamps percentage to 0-100`() {
            assertEquals(100, LiveUpdateDetectorCore.extractProgressPercent("进度 150%"))
        }

        @Test
        fun `returns null when no progress found`() {
            assertNull(LiveUpdateDetectorCore.extractProgressPercent("没有进度信息"))
        }

        @Test
        fun `handles zero denominator in fraction`() {
            assertNull(LiveUpdateDetectorCore.extractProgressPercent("0/0 items"))
        }
    }

    // --- extractLabels() tests ---

    @Nested
    inner class ExtractLabelsTests {

        @Test
        fun `delivery labels`() {
            val (start, end, tracker) = LiveUpdateDetectorCore.extractLabels(
                ProgressCategory.DELIVERY, "骑手已取餐", "正在配送中。预计5分钟"
            )
            assertEquals("商家", start)
            assertEquals("目的地", end)
            assertNotNull(tracker)
        }

        @Test
        fun `ride hailing labels`() {
            val (start, end, tracker) = LiveUpdateDetectorCore.extractLabels(
                ProgressCategory.RIDE_HAILING, "司机接驾", "正在前往上车点"
            )
            assertEquals("上车点", start)
            assertEquals("目的地", end)
            assertNotNull(tracker)
        }

        @Test
        fun `unknown category returns null labels`() {
            val (start, end, tracker) = LiveUpdateDetectorCore.extractLabels(
                ProgressCategory.UNKNOWN, "title", "desc"
            )
            assertNull(start)
            assertNull(end)
            assertNull(tracker)
        }
    }

    // --- isPackageBlacklisted() tests ---

    @Nested
    inner class IsPackageBlacklistedTests {

        @Test
        fun `WeChat is blacklisted`() {
            assertTrue(LiveUpdateDetectorCore.isPackageBlacklisted("com.tencent.mm"))
        }

        @Test
        fun `QQ is blacklisted`() {
            assertTrue(LiveUpdateDetectorCore.isPackageBlacklisted("com.tencent.mobileqq"))
        }

        @Test
        fun `DingTalk is blacklisted`() {
            assertTrue(LiveUpdateDetectorCore.isPackageBlacklisted("com.alibaba.android.rimet"))
        }

        @Test
        fun `random package is not blacklisted`() {
            assertFalse(LiveUpdateDetectorCore.isPackageBlacklisted("com.example.app"))
        }
    }

    // --- ProgressCategory tests ---

    @Nested
    inner class ProgressCategoryTests {

        @Test
        fun `transport related categories`() {
            assertTrue(ProgressCategory.DELIVERY.isTransportRelated())
            assertTrue(ProgressCategory.RIDE_HAILING.isTransportRelated())
            assertTrue(ProgressCategory.LOGISTICS.isTransportRelated())
        }

        @Test
        fun `non transport related categories`() {
            assertFalse(ProgressCategory.DOWNLOAD.isTransportRelated())
            assertFalse(ProgressCategory.TRAVEL.isTransportRelated())
            assertFalse(ProgressCategory.UNKNOWN.isTransportRelated())
        }
    }
}
