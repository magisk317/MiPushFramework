package io.github.magisk317.mipush.feature.main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppConfigurationUtilsTest {
    @Test
    fun `notification channel group comparator keeps mipush first and null last`() {
        val ids = mutableListOf("z", null, "mipush", "a")

        ids.sortWith { lhs, rhs ->
            AppConfigurationUtils.compareNotificationChannelGroupIds(lhs, rhs, "mipush")
        }

        assertEquals(listOf("mipush", "a", "z", null), ids)
    }

    @Test
    fun `notification channel group comparator is antisymmetric for known ids`() {
        val ids = listOf(null, "mipush", "a", "z")

        for (lhs in ids) {
            for (rhs in ids) {
                val left = AppConfigurationUtils.compareNotificationChannelGroupIds(lhs, rhs, "mipush")
                val right = AppConfigurationUtils.compareNotificationChannelGroupIds(rhs, lhs, "mipush")
                assertTrue(
                    left.sign == -right.sign,
                    "compare($lhs, $rhs)=$left but compare($rhs, $lhs)=$right"
                )
            }
        }
    }

    private val Int.sign: Int
        get() = compareTo(0)
}
