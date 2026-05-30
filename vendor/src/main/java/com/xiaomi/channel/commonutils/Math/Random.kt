package com.xiaomi.channel.commonutils.Math

object Random {
    private val random = java.util.Random()

    @JvmStatic
    fun randomBoolean(): Boolean = random.nextBoolean()

    @JvmStatic
    fun randomInt(i: Int): Int = random.nextInt(kotlin.math.abs(i))
}
