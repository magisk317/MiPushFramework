package io.github.magisk317.mipush.runtime.data

import android.content.Context
import com.xiaomi.channel.commonutils.android.AppInfoUtils
import com.xiaomi.push.service.MIPushEventProcessor
import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.xmsf.stock.StockSurfaceSupport
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.magisk317.mipush.push.pipeline.StalePackagePushGuard
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import io.github.magisk317.mipush.service.runtime.MIPushNotificationIntentSupport
import io.github.magisk317.mipush.service.runtime.MIPushNotificationPresentationSupport
import io.github.magisk317.mipush.service.runtime.MIPushNotificationPublishHelper
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * View-time diagnostics appended to the runtime event debug JSON served to the manager's
 * event-detail panel.
 *
 * Everything here is re-computed on demand from the stored container; nothing is recorded at
 * publish time and no event schema changes. Read-only by contract: the duplicate-dispatch
 * allowance is intentionally reported as not evaluated because querying it would consume
 * allowance state. Individual sections fail safe: a section that throws degrades to an
 * `{"error": ...}` object without dropping the rest of the debug JSON.
 */
internal object EventDebugEnrichment {

    fun enrich(context: Context, container: XmPushActionContainer, base: JsonObject): JsonObject {
        val sections: Map<String, JsonElement> = mapOf(
            section("clickResolution") {
                MIPushNotificationIntentSupport.describeClickResolution(
                    context,
                    container,
                    MIPushNotificationHelper.getTargetPackage(container),
                )
            },
            section("dispatchGates") { describeDispatchGates(context, container) },
            section("presentation") { MIPushNotificationPresentationSupport.describePresentation(context, container) },
        )
        return JsonObject(base + sections)
    }

    private fun section(name: String, block: () -> JsonObject): Pair<String, JsonElement> {
        val value = runCatching(block).getOrElse {
            buildJsonObject { put("error", it.javaClass.simpleName) }
        }
        return name to value
    }

    /**
     * Current status of the publish-path gates that [MIPushNotificationPublishHelper] evaluates
     * when a notification arrives. Values reflect "what would happen now", not what happened at
     * publish time (the historical outcome lives in the event's own result fields).
     */
    private fun describeDispatchGates(context: Context, container: XmPushActionContainer): JsonObject {
        val targetPackage = MIPushNotificationHelper.getTargetPackage(container)
        val profileCheckRequired = MIPushEventProcessor.shouldCheckProfile(container)
        return buildJsonObject {
            put(
                "appInstalled",
                !StalePackagePushGuard.shouldDropNotification(
                    context,
                    container,
                    "EventDebugEnrichment.dispatchGates",
                ),
            )
            put("appBlocked", RegisteredApplicationDb.isBlocked(container.packageName))
            put("profileCheckRequired", profileCheckRequired)
            if (profileCheckRequired) {
                put("profileAllowed", StockSurfaceSupport.isProfileAllowed(context, container))
            }
            put("notificationOp", AppInfoUtils.getAppNotificationOp(context, targetPackage, true).name)
            put("replayDropSuspect", MIPushNotificationPublishHelper.shouldDropReplayNotification(container))
            put("allowance", "not_evaluated")
        }
    }
}
