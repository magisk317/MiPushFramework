package io.github.magisk317.mipush.notification

import com.xiaomi.xmpush.thrift.PushMetaInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Product-owned live-session identity for focus notifications (focus cards / island live sessions).
 *
 * [PRODUCT-OWNED DEVIATION — recheck against stock XMSF on every upgrade before retiring]
 * Stock 7.4.67-C (and the stock-parity formula in
 * [MIPushNotificationPublishHelper.getNotificationId]) derives the publish identity purely from
 * the per-message `notifyId`. Live-session vendors push every status update as an independent
 * message whose `notifyId` differs per message (Alipay's server even assigns
 * `notifyId = title.hashCode()`), so one delivery/taxi/logistics order renders as N separate
 * cards and N separate island sessions instead of updating one card in place. The payload
 * itself carries the session key in `miui.focus.param.orderId` (+ `scene`), which stock never
 * consults for identity; this policy re-targets the notification id onto that order key so
 * SystemUI replaces the existing card and island session instead of stacking a new one.
 *
 * 京东 (com.jingdong.app.mall) keys the same session differently: no root `orderId`/`scene`,
 * the order number rides inside the focus param's `notifyId` as `<targetPackage><orderNo>`
 * (e.g. `com.jingdong.app.mall1007816202`), stable across the messages of one order while the
 * per-message notifyId differs. Observed on pudding 2026-10-17: one JD order's
 * 进行中 → 预计18:42送达 → 预计18:56送达 updates arrived as three messages with per-message
 * notifyIds 807997812 / 807964177 / 327792584 and an identical focus-param notifyId, and
 * rendered as three separate island sessions because the root order id was absent. When the
 * root order id is missing, the policy falls back to that package-prefixed numeric suffix; the
 * prefix check keeps hash-shaped notifyIds (Alipay's `title.hashCode()`) on the stock path.
 *
 * What to compare when stock XMSF grows its own focus-order handling, before dropping this:
 *  - identity source (order key vs per-message notifyId) and id space (same `base + offset`
 *    package namespace here);
 *  - whether stock also handles the explicit-cancel path (this repo re-cancels the order slot
 *    from the cancel container in [NotificationController.cancel]);
 *  - that mock replay keeps precedence over the order identity (it does in getNotificationId).
 * Hook points: getNotificationId (publish identity) and NotificationController.cancel (cancel).
 * Any payload without a parsable order key keeps the stock per-message identity unchanged.
 */
object FocusOrderIdentityPolicy {
    private const val FOCUS_PARAM = "miui.focus.param"
    private const val KEY_ORDER_ID = "orderId"
    private const val KEY_SCENE = "scene"
    private const val KEY_NOTIFY_ID = "notifyId"
    private val json = Json

    /**
     * Stable session token of a focus payload: root `orderId`, scene-scoped when the param
     * declares one. When the root order id is absent, falls back to a package-prefixed numeric
     * `notifyId` suffix (京东 form, see class doc) validated against [packageName]. Null for
     * anything that is not an order-keyed live session (including malformed params), which
     * deliberately falls back to the stock identity.
     */
    internal fun orderKey(focusParam: String?, packageName: String? = null): String? {
        if (focusParam.isNullOrBlank()) return null
        val root = runCatching { json.parseToJsonElement(focusParam).jsonObject }.getOrNull() ?: return null
        val orderId = root[KEY_ORDER_ID]
            ?.let { value -> runCatching { value.jsonPrimitive.contentOrNull }.getOrNull() }
            ?.takeIf(String::isNotBlank)
        if (orderId != null) {
            val scene = root[KEY_SCENE]
                ?.let { value -> runCatching { value.jsonPrimitive.contentOrNull }.getOrNull() }
                .orEmpty()
            return if (scene.isEmpty()) orderId else "$scene|$orderId"
        }
        return notifyIdOrderKey(root, packageName)
    }

    /**
     * 京东-form session key: the focus param's `notifyId` carries `<targetPackage><orderNo>`
     * with an all-digit suffix. Returns null when the notifyId is missing, does not start with
     * [packageName], or carries a non-numeric suffix (e.g. Alipay's `title.hashCode()`), so
     * those payloads keep the stock per-message identity.
     */
    private fun notifyIdOrderKey(root: JsonObject, packageName: String?): String? {
        if (packageName.isNullOrBlank()) return null
        val notifyId = root[KEY_NOTIFY_ID]
            ?.let { value -> runCatching { value.jsonPrimitive.contentOrNull }.getOrNull() }
            ?.takeIf(String::isNotBlank)
            ?: return null
        if (!notifyId.startsWith(packageName)) return null
        val orderNo = notifyId.substring(packageName.length)
        if (orderNo.isEmpty() || !orderNo.all { it.isDigit() }) return null
        return orderNo
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
        val key = orderKey(metaInfo.extra?.get(FOCUS_PARAM), packageName) ?: return null
        return notificationIdFor(packageName, key)
    }
}
