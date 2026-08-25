package io.github.magisk317.mipush.main.viewmodel

import io.github.magisk317.mipush.manager.client.ManagerRuntimeAvailability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/** Runs availability-triggered reloads in the collector coroutine so collectLatest can cancel them. */
internal suspend fun collectAvailableRuntimeReloads(
    availability: Flow<ManagerRuntimeAvailability>,
    shouldReloadWhenAvailable: () -> Boolean,
    reload: suspend () -> Unit,
) {
    var sawUnavailable = false
    availability.collectLatest { state ->
        if (state is ManagerRuntimeAvailability.Available) {
            if (sawUnavailable || shouldReloadWhenAvailable()) {
                sawUnavailable = false
                reload()
            }
        } else {
            sawUnavailable = true
        }
    }
}
