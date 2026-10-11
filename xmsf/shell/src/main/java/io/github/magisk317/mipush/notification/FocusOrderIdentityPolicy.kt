package io.github.magisk317.mipush.notification

import com.xiaomi.xmpush.thrift.PushMetaInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Product-owned live-session identity for focus notifications (焦点通知/灵动岛实时卡片).
 *
 * [PRODUCT-OWNED DEVIATION — recheck against stock XMSF on every upgrade before retiring]
 * Stock 7.4.67-C (and the stock-parity formula in
 * [MIPushNotificationPublishHelper.getNotificationId]) derives the publish identity purely from
 * the per-message `notifyId`. Live-session vendors push every status update as an independent
 * message whose `notifyId` differs per message (Alipay's server even assigns
 * `notifyId = title.hashCode()`), so one delivery/taxi/logistics order renders as N separate
 * cards and N separate 灵动岛 sessions instead of updating one card in place. The payload
 * itself carries the session key in `miui.focus.param.orderId` (+ `scene`), which stock never
 * consults for identity; this policy re-targets the notification id onto that order key so
 * SystemUI replaces the existing card and island session instead of stacking a new one.
 *
 * What to compare when stock XMSF grows its own focus-order handling, before dropping this:
 *  - identity source (order key vs per-message notifyId) and id space (same `base + offset`
 *    package namespace here);
 *  - whether stock also handles the explicit-cancel path (this repo re-cancels the order slot
 *    from the cancel container in [NotificationController.cancel]);
 *  - that mock replay keeps precedence over the order identity (it does in getNotificationId).
 * Hook points: getNotificationId (publish identity) and NotificationController.cancel (cancel).
 * Any payload without a parsable `orderId` keeps the stock per-message identity unchanged.
 */
object FocusOrderIdentityPolicy {
    private const val FOCUS_PARAM = "miui.focus.param"
    private const val KEY_ORDER_ID = "orderId"
    private const val KEY_SCENE = "scene"
    private val json = Json

    /**
     * Stable session token of a focus payload: `orderId`, scene-scoped when the param declares
     * one. Null for anything that is not an order-keyed live session (including malformed
     * params), which deliberately falls back to the stock identity.
     */
    internal fun orderKey(focusParam: String?): String? {
        if (focusParam.isNullOrBlank()) return null
        val root = runCatching { json.parseToJsonElement(focusParam).jsonObject }.getOrNull() ?: return null
        val orderId = root[KEY_ORDER_ID]
            ?.let { value -> runCatching { value.jsonPrimitive.contentOrNull }.getOrNull() }
            ?.takeIf(String::isNotBlank)
            ?: return null
        val scene = root[KEY_SCENE]
            ?.let { value -> runCatching { value.jsonPrimitive.contentOrNull }.getOrNull() }
            .orEmpty()
        return if (scene.isEmpty()) orderId else "$scene|$orderId"
    }

    /**
     * Order-derived id inside the same `base + offset` namespace the stock formula uses, so a
     * focus card can only ever collide with the target package's own notification ids. Pure
     * function of the order key: stable across XMSF restarts without any persisted state.
     */
    internal fun notificationIdFor(packageName: String, orderKey: String): Int =
        (packageName.hashCode() / 10) * 10 + orderKey.hashCode()

    /** Order-derived notification id, or null when the payload is not an order-keyed session. */
    fun resolveNotificationId(packageName: String, metaInfo: PushMetaInfo?): Int? {
        if (metaInfo == null) return null
        val key = orderKey(metaInfo.extra?.get(FOCUS_PARAM)) ?: return null
        return notificationIdFor(packageName, key)
    }
}
