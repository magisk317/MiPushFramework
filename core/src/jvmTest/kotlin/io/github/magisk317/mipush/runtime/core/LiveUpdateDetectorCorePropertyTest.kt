package io.github.magisk317.mipush.notification

import io.github.magisk317.mipush.notification.LiveUpdateDetectorCore.DetectionInput
import io.github.magisk317.mipush.notification.LiveUpdateDetectorCore.ProgressCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Property-based tests for LiveUpdateDetectorCore.
 *
 * **Validates: Requirements 7.6, 8.4**
 *
 * Property 3: Classification correctness —
 * For any `DetectionInput` with only stdlib-compatible content,
 * `LiveUpdateDetectorCore.detect()` returns deterministic results.
 */
class LiveUpdateDetectorCorePropertyTest {

    companion object {
        /** Sample title fragments for generating random inputs */
        private val TITLE_FRAGMENTS = listOf(
            "骑手已接单", "司机正在前往", "快递运输中", "正在下载更新", "航班即将起飞",
            "新消息", "系统通知", "骑手已取餐", "包裹派送中", "下载完成",
            "配送中", "预计到达", "登机口", "更新可用", "订单已完成",
            "", "hello", "test notification", "重要提醒", "活动通知"
        )

        /** Sample description fragments for generating random inputs */
        private val DESCRIPTION_FRAGMENTS = listOf(
            "正在配送中 预计5分钟送达", "预计3分钟到达上车点", "您的包裹正在运输中",
            "下载中 45%", "登机口已开放 预计15分钟后起飞", "请对骑手服务进行评价",
            "3/10 完成", "距您500米", "约5分钟", "普通消息内容",
            "", "no progress here", "processing", "正在处理中", "已完成"
        )

        /** Sample package names for generating random inputs */
        private val PACKAGE_NAMES = listOf(
            "com.meituan.takeout", "com.sdu.didi.psnger", "com.sf.activity",
            "com.example.app", "com.tencent.mm", "com.tencent.mobileqq",
            "com.alibaba.android.rimet", "com.example.travel", "org.test.pkg",
            "io.github.test", "com.random.app"
        )
    }

    /**
     * Property 3: Determinism — for any randomly generated DetectionInput,
     * calling `detect()` multiple times with the same input always produces
     * the identical DetectionResult.
     *
     * **Validates: Requirements 7.6**
     */
    @RepeatedTest(100)
    fun `detect returns deterministic results for same input`() {
        val seed = Random.nextLong()
        val rng = Random(seed)

        val input = generateRandomInput(rng)

        val result1 = LiveUpdateDetectorCore.detect(input)
        val result2 = LiveUpdateDetectorCore.detect(input)
        val result3 = LiveUpdateDetectorCore.detect(input)

        assertEquals(
            result1,
            result2,
            "Determinism violated (run 1 vs 2) for seed=$seed, input=$input"
        )
        assertEquals(
            result2,
            result3,
            "Determinism violated (run 2 vs 3) for seed=$seed, input=$input"
        )
    }

    /**
     * Property 3 (extended): Determinism holds for a batch of randomly generated inputs.
     * Running detect() on the same sequence of inputs always yields identical results.
     *
     * **Validates: Requirements 7.6**
     */
    @RepeatedTest(50)
    fun `detect is deterministic across a batch of random inputs`() {
        val seed = Random.nextLong()
        val rng = Random(seed)

        val batchSize = rng.nextInt(10, 30)
        val inputs = (1..batchSize).map { generateRandomInput(rng) }

        // First pass
        val firstResults = inputs.map { LiveUpdateDetectorCore.detect(it) }

        // Second pass — same inputs, same order
        val secondResults = inputs.map { LiveUpdateDetectorCore.detect(it) }

        assertEquals(
            firstResults,
            secondResults,
            "Determinism violated across batch for seed=$seed"
        )
    }

    /**
     * Property 3 (structural): The DetectionResult always has a valid category
     * and consistent isProgress flag. When isProgress is false, category must be UNKNOWN.
     *
     * **Validates: Requirements 7.6**
     */
    @RepeatedTest(100)
    fun `detect result has consistent structure`() {
        val seed = Random.nextLong()
        val rng = Random(seed)

        val input = generateRandomInput(rng)
        val result = LiveUpdateDetectorCore.detect(input)

        assertNotNull(result.category, "Category should never be null for seed=$seed")

        if (!result.isProgress) {
            assertEquals(
                ProgressCategory.UNKNOWN,
                result.category,
                "Non-progress result should have UNKNOWN category for seed=$seed, input=$input"
            )
        } else {
            assertTrue(
                result.category != ProgressCategory.UNKNOWN,
                "Progress result should not have UNKNOWN category for seed=$seed, input=$input"
            )
        }
    }

    /**
     * Property 3 (completeness): detect() never throws an exception for any
     * randomly generated stdlib-compatible string input.
     *
     * **Validates: Requirements 7.6**
     */
    @RepeatedTest(100)
    fun `detect never throws for arbitrary string inputs`() {
        val seed = Random.nextLong()
        val rng = Random(seed)

        // Generate truly random strings (not just from predefined fragments)
        val title = generateRandomString(rng, maxLength = 200)
        val description = generateRandomString(rng, maxLength = 500)
        val packageName = generateRandomPackageName(rng)

        val input = DetectionInput(title, description, packageName)

        // Should not throw
        val result = LiveUpdateDetectorCore.detect(input)
        assertNotNull(result, "detect() should never return null for seed=$seed")
    }

    // --- Helper functions ---

    private fun generateRandomInput(rng: Random): DetectionInput {
        val title = TITLE_FRAGMENTS[rng.nextInt(TITLE_FRAGMENTS.size)]
        val description = DESCRIPTION_FRAGMENTS[rng.nextInt(DESCRIPTION_FRAGMENTS.size)]
        val packageName = PACKAGE_NAMES[rng.nextInt(PACKAGE_NAMES.size)]
        return DetectionInput(title, description, packageName)
    }

    private fun generateRandomString(rng: Random, maxLength: Int): String {
        val length = rng.nextInt(0, maxLength + 1)
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789 %/骑手配送正在预计分钟"
        return (1..length).map { chars[rng.nextInt(chars.length)] }.joinToString("")
    }

    private fun generateRandomPackageName(rng: Random): String {
        val segments = rng.nextInt(2, 5)
        return (1..segments).joinToString(".") {
            val len = rng.nextInt(2, 8)
            (1..len).map { ('a' + rng.nextInt(26)) }.joinToString("")
        }
    }
}
