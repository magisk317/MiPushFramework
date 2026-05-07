package io.github.magisk317.mipush.feature.ui.component

import java.util.concurrent.ConcurrentHashMap

object SessionLoadingRegistry {
    private val shownKeys = ConcurrentHashMap.newKeySet<String>()

    fun shouldShowInitial(key: String): Boolean = !shownKeys.contains(key)

    fun markShown(key: String) {
        shownKeys.add(key)
    }
}
