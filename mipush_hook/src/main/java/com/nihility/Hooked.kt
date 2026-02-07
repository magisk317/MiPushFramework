package com.nihility

object Hooked {
    private val hookedRecord = hashSetOf<String>()

    @JvmStatic
    fun contains(id: String): Boolean = hookedRecord.contains(id)

    @JvmStatic
    fun mark(id: String) {
        hookedRecord.add(id)
    }
}
