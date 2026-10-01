package io.github.magisk317.mipush.utils

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Guards the copy-on-write contract of [ConfigurationsLoader].
 *
 * The manager upload writer used to roll a failed write back with `clear()` + `putAll()` on the live
 * table, which exposed an empty or half-applied table to the push pipeline for the duration of the
 * swap. Every mutation now replaces the table by reference instead, and these tests pin that down.
 */
class ConfigurationsLoaderCowTest {
    private fun sample(): MutableMap<String, MutableList<Any>> =
        mutableMapOf("com.example" to mutableListOf<Any>("entry"))

    @Test
    fun `replaceConfigs swaps the live table by reference`() {
        val loader = ConfigurationsLoader()
        val before = loader.getConfigs()

        loader.replaceConfigs(sample())

        assertNotSame(before, loader.getConfigs())
        assertEquals(listOf<Any>("entry"), loader.getConfigs()["com.example"])
    }

    @Test
    fun `a reference read before a replace keeps the old content`() {
        val loader = ConfigurationsLoader()
        loader.replaceConfigs(mutableMapOf("com.example" to mutableListOf<Any>("old")))
        val held = loader.getConfigs()

        loader.replaceConfigs(mutableMapOf("com.example" to mutableListOf<Any>("new")))

        assertEquals(listOf<Any>("old"), held["com.example"])
        assertEquals(listOf<Any>("new"), loader.getConfigs()["com.example"])
    }

    @Test
    fun `snapshotConfigs returns an independent copy`() {
        val loader = ConfigurationsLoader()
        loader.replaceConfigs(sample())

        val snapshot = loader.snapshotConfigs()
        snapshot["com.example"]!!.add("extra")
        snapshot["com.other"] = mutableListOf<Any>("added")

        assertEquals(listOf<Any>("entry"), loader.getConfigs()["com.example"])
        assertNull(loader.getConfigs()["com.other"])
    }

    @Test
    fun `a concurrent reader never observes an empty table during a replace storm`() {
        val loader = ConfigurationsLoader()
        loader.replaceConfigs(sample())
        val stop = AtomicBoolean(false)
        val emptyReads = AtomicInteger(0)
        val reader = Thread {
            while (!stop.get()) {
                if (loader.getConfigs().isEmpty()) {
                    emptyReads.incrementAndGet()
                }
            }
        }

        reader.start()
        repeat(2_000) { loader.replaceConfigs(sample()) }
        stop.set(true)
        reader.join()

        assertEquals(0, emptyReads.get())
    }
}
