package io.github.magisk317.mipush.service.runtime

import com.xiaomi.push.service.*
import io.github.magisk317.xposed.logging.MagiskOtel

object PushConnectionStatusRuntime {

    @JvmStatic
    fun planStatusChange(
        currentStatus: Int,
        newStatus: Int
    ): PushConnectionStatusPlan {
        val corePlan = io.github.magisk317.mipush.runtime.core.PushConnectionStatusPlanFactory.planStatusChange(
            currentStatus = currentStatus,
            newStatus = newStatus,
        )
        val plan = PushConnectionStatusPlan(
            eventAction = corePlan.eventAction,
            connectionState = corePlan.connectionState,
            shouldRemoveConnectingTimeout = corePlan.shouldRemoveConnectingTimeout,
            listenerEvent = PushConnectionListenerEvent.valueOf(corePlan.listenerEvent.name),
            warningMessage = corePlan.warningMessage,
        )
        MagiskOtel.event(
            name = "push.lifecycle",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "xmsf",
                "stage" to "connection_status",
                "reason" to plan.eventAction,
                "source" to currentStatus.toString(),
            ),
            statusOk = true,
        )
        return plan
    }
}
