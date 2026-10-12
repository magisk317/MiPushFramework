package io.github.magisk317.mipush.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FocusOrderIdentityPolicyTest {

    @Test
    fun `orderKey extracts scene-scoped order id from an alipay-style focus param`() {
        val key = FocusOrderIdentityPolicy.orderKey(
            """{"progressCount":2,"orderId":"208870222575168520261011122933190002876515",""" +
                """"enableFloat":false,"title":"骑手配送中","scene":"foodDelivery","sequence":9,""" +
                """"protocol":1,"progress":25,"updatable":true,"colorDesc":"#1677ff"}""",
        )
        assertEquals("foodDelivery|208870222575168520261011122933190002876515", key)
    }

    @Test
    fun `orderKey without scene collapses to the bare order id`() {
        assertEquals(
            "order-1",
            FocusOrderIdentityPolicy.orderKey("""{"orderId":"order-1","updatable":true}"""),
        )
    }

    @Test
    fun `orderKey rejects params without a usable order id`() {
        assertNull(FocusOrderIdentityPolicy.orderKey("""{"updatable":true,"reopen":"close"}"""))
        assertNull(FocusOrderIdentityPolicy.orderKey("""{"orderId":""}"""))
        assertNull(FocusOrderIdentityPolicy.orderKey("""{"orderId":null}"""))
        assertNull(FocusOrderIdentityPolicy.orderKey("not json at all"))
        assertNull(FocusOrderIdentityPolicy.orderKey("""["not","an","object"]"""))
        assertNull(FocusOrderIdentityPolicy.orderKey(""))
        assertNull(FocusOrderIdentityPolicy.orderKey(null))
    }

    @Test
    fun `orderKey coerces numeric order ids`() {
        assertEquals(
            "208870222575168520261011122933190002876515",
            FocusOrderIdentityPolicy.orderKey("""{"orderId":208870222575168520261011122933190002876515}"""),
        )
    }

    @Test
    fun `notificationIdFor stays in the package namespace and is stable`() {
        val first = FocusOrderIdentityPolicy.notificationIdFor("com.eg.android.AlipayGphone", "foodDelivery|order-1")
        val second = FocusOrderIdentityPolicy.notificationIdFor("com.eg.android.AlipayGphone", "foodDelivery|order-1")
        assertEquals(first, second)
        assertEquals(
            ("com.eg.android.AlipayGphone".hashCode() / 10) * 10 + "foodDelivery|order-1".hashCode(),
            first,
        )
        assertNotEquals(first, FocusOrderIdentityPolicy.notificationIdFor("com.ruanmei.ithome", "foodDelivery|order-1"))
    }

    @Test
    fun `distinct orders and scenes resolve to distinct ids`() {
        val base = FocusOrderIdentityPolicy.notificationIdFor("com.example.app", "")
        val orderA = FocusOrderIdentityPolicy.notificationIdFor("com.example.app", "foodDelivery|a")
        val orderB = FocusOrderIdentityPolicy.notificationIdFor("com.example.app", "foodDelivery|b")
        val taxiA = FocusOrderIdentityPolicy.notificationIdFor("com.example.app", "taxi|a")
        assertNotEquals(orderA, orderB)
        assertNotEquals(orderA, taxiA)
        assertNotEquals(base, orderA)
    }

    @Test
    fun `resolveNotificationId keys same-order updates on the order regardless of notifyId`() {
        fun alipayContainer(notifyId: Int, orderId: String) = com.xiaomi.xmpush.thrift.PushMetaInfo().apply {
            setNotifyId(notifyId)
            putToExtra(
                "miui.focus.param",
                """{"orderId":"$orderId","scene":"foodDelivery","updatable":true,"sequence":1}""",
            )
        }

        // Server-side assignment observed on pudding 2026-10-11: notifyId = title.hashCode(), so
        // every status of one order arrives with a different notifyId ("rider delivering" =
        // -1325363137, "rider picking up in store" = 630311518) while the order key stays constant.
        val riderDelivering = FocusOrderIdentityPolicy.resolveNotificationId(
            "com.eg.android.AlipayGphone",
            alipayContainer(notifyId = -1325363137, orderId = "208870222575168520261011122933190002876515"),
        )
        val riderPickingUp = FocusOrderIdentityPolicy.resolveNotificationId(
            "com.eg.android.AlipayGphone",
            alipayContainer(notifyId = 630311518, orderId = "208870222575168520261011122933190002876515"),
        )
        val otherOrder = FocusOrderIdentityPolicy.resolveNotificationId(
            "com.eg.android.AlipayGphone",
            alipayContainer(notifyId = -1325363137, orderId = "208870222575168520261011122933190002876516"),
        )

        assertEquals(riderDelivering, riderPickingUp)
        assertNotEquals(riderDelivering, otherOrder)
    }

    @Test
    fun `resolveNotificationId keeps stock identity for payloads without an order key`() {
        assertNull(
            FocusOrderIdentityPolicy.resolveNotificationId(
                "com.example.app",
                com.xiaomi.xmpush.thrift.PushMetaInfo().apply {
                    setNotifyId(42)
                    putToExtra("miui.focus.param", """{"updatable":true,"reopen":"close"}""")
                },
            ),
        )
        assertNull(
            FocusOrderIdentityPolicy.resolveNotificationId(
                "com.example.app",
                com.xiaomi.xmpush.thrift.PushMetaInfo().apply { setNotifyId(42) },
            ),
        )
        assertNull(FocusOrderIdentityPolicy.resolveNotificationId("com.example.app", null))
    }
}
