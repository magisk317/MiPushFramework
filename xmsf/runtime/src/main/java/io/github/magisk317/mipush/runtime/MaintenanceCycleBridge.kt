package io.github.magisk317.mipush.runtime

import com.xiaomi.push.service.MaintenanceCycle

/** Public xmsf-facing bridge for consumers that cannot depend on vendor directly. */
object MaintenanceCycleBridge {
    fun setListener(listener: ((sequence: Long, action: String) -> Unit)?) {
        MaintenanceCycle.listener = listener?.let { callback ->
            { tick -> callback(tick.sequence, tick.action) }
        }
    }
}
