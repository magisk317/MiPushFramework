package io.github.magisk317.mipush.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UtilTest {
    @Test
    fun `default doOnce runs once per receiver`() {
        val first = Any()
        val second = Any()
        var count = 0

        repeat(2) { first.doOnce { count++ } }
        second.doOnce { count++ }

        assertEquals(2, count)
    }

    @Test
    fun `named doOnce keeps keys independent`() {
        val receiver = Any()
        var count = 0

        repeat(2) { receiver.doOnce("first") { count++ } }
        receiver.doOnce("second") { count++ }

        assertEquals(2, count)
    }
}
