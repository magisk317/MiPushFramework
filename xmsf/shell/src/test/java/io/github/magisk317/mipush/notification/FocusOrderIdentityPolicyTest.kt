package io.github.magisk317.mipush.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
    fun `orderKey falls back to the JD package-prefixed notifyId order number`() {
        // Observed on pudding 2026-10-17: 京东 keys the live session inside the focus param's
        // notifyId as `<targetPackage><orderNo>` and sends no root orderId/scene.
        val key = FocusOrderIdentityPolicy.orderKey(
            """{"title":"预计18:56送达","sequence":1794109999,"updatable":true,""" +
                """"notifyId":"com.jingdong.app.mall1007816202"}""",
            "com.jingdong.app.mall",
        )
        assertEquals("1007816202", key)
    }

    @Test
    fun `orderKey notifyId fallback requires the package prefix and a numeric suffix`() {
        // Wrong package: hash-shaped notifyIds (Alipay's title.hashCode()) stay on the stock path.
        assertNull(
            FocusOrderIdentityPolicy.orderKey(
                """{"notifyId":"com.jingdong.app.mall1007816202"}""",
                "com.eg.android.AlipayGphone",
            ),
        )
        assertNull(
            FocusOrderIdentityPolicy.orderKey(
                """{"notifyId":"-1325363137"}""",
                "com.eg.android.AlipayGphone",
            ),
        )
        // Non-numeric suffix is not an order number.
        assertNull(
            FocusOrderIdentityPolicy.orderKey(
                """{"notifyId":"com.jingdong.app.mall1007816202abc"}""",
                "com.jingdong.app.mall",
            ),
        )
        // No package context: the fallback stays off and stock identity is kept.
        assertNull(
            FocusOrderIdentityPolicy.orderKey("""{"notifyId":"com.jingdong.app.mall1007816202"}"""),
        )
    }

    @Test
    fun `orderKey prefers the root order id over the notifyId fallback`() {
        val key = FocusOrderIdentityPolicy.orderKey(
            """{"orderId":"order-1","scene":"foodDelivery","updatable":true,""" +
                """"notifyId":"com.jingdong.app.mall1007816202"}""",
            "com.jingdong.app.mall",
        )
        assertEquals("foodDelivery|order-1", key)
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
    fun `resolveNotificationId merges JD live-session updates of one order`() {
        fun jdContainer(notifyId: Int, focusParam: String) = com.xiaomi.xmpush.thrift.PushMetaInfo().apply {
            setNotifyId(notifyId)
            putToExtra(
                "miui.focus.param",
                focusParam,
            )
        }

        // Observed on pudding 2026-10-17: one JD order (1007816202) pushed three status updates
        // as separate messages with per-message notifyIds 807997812 / 807964177 / 327792584 and
        // an identical focus-param notifyId com.jingdong.app.mall1007816202; before the
        // notifyId fallback they rendered as three separate 灵动岛 sessions.
        val orderInProgress = FocusOrderIdentityPolicy.resolveNotificationId(
            "com.jingdong.app.mall",
            jdContainer(
                327792584,
                """{"title":"您的订单正在进行中","sequence":1792231779,"updatable":true,""" +
                    """"notifyId":"com.jingdong.app.mall1007816202"}""",
            ),
        )
        val eta1842 = FocusOrderIdentityPolicy.resolveNotificationId(
            "com.jingdong.app.mall",
            jdContainer(
                807964177,
                """{"title":"预计18:42送达","sequence":1793145974,"updatable":true,""" +
                    """"notifyId":"com.jingdong.app.mall1007816202"}""",
            ),
        )
        val eta1856 = FocusOrderIdentityPolicy.resolveNotificationId(
            "com.jingdong.app.mall",
            jdContainer(
                807997812,
                """{"title":"预计18:56送达","sequence":1794109999,"updatable":true,""" +
                    """"notifyId":"com.jingdong.app.mall1007816202"}""",
            ),
        )
        val otherOrder = FocusOrderIdentityPolicy.resolveNotificationId(
            "com.jingdong.app.mall",
            jdContainer(
                327792584,
                """{"sequence":1,"updatable":true,"notifyId":"com.jingdong.app.mall1007816203"}""",
            ),
        )

        assertEquals(orderInProgress, eta1842)
        assertEquals(orderInProgress, eta1856)
        assertNotEquals(orderInProgress, otherOrder)
    }

    @Test
    fun `orderKey reads the JD session notifyId from param_v2`() {
        // Observed on pudding 2026-10-23: 京东 folded the whole session under param_v2 and the
        // root now holds nothing but that container, so a root-only lookup found no notifyId.
        val key = FocusOrderIdentityPolicy.orderKey(
            """{"param_v2":{"baseInfo":{"title":"您的订单正在进行中","content":"来份炒饼","type":2},""" +
                """"param_island":{"islandProperty":1,"islandTimeout":7200,"highlightColor":"#FF0400"},""" +
                """"ticker":"进行中","sequence":1792764366,"protocol":1,"updatable":true,""" +
                """"notifyId":"com.jingdong.app.mall2117"}}""",
            "com.jingdong.app.mall",
        )
        assertEquals("2117", key)
    }

    @Test
    fun `orderKey falls back to param_v1 when param_v2 is absent`() {
        val key = FocusOrderIdentityPolicy.orderKey(
            """{"param_v1":{"sequence":1792764366,"updatable":true,"notifyId":"com.jingdong.app.mall2117"}}""",
            "com.jingdong.app.mall",
        )
        assertEquals("2117", key)
    }

    @Test
    fun `orderKey prefers a param_v2 order id over the notifyId fallback`() {
        val key = FocusOrderIdentityPolicy.orderKey(
            """{"param_v2":{"orderId":"order-1","scene":"foodDelivery","updatable":true,""" +
                """"notifyId":"com.jingdong.app.mall2117"}}""",
            "com.jingdong.app.mall",
        )
        assertEquals("foodDelivery|order-1", key)
    }

    @Test
    fun `orderKey rejects hash-shaped notifyIds nested in param_v2`() {
        // Alipay's title.hashCode() nested in a versioned container must stay on the stock path.
        assertNull(
            FocusOrderIdentityPolicy.orderKey(
                """{"param_v2":{"updatable":true,"notifyId":"-1325363137"}}""",
                "com.eg.android.AlipayGphone",
            ),
        )
    }

    @Test
    fun `resolveNotificationId merges JD param_v2 updates of one order`() {
        fun jdContainer(notifyId: Int, focusParam: String) = com.xiaomi.xmpush.thrift.PushMetaInfo().apply {
            setNotifyId(notifyId)
            putToExtra(
                "miui.focus.param",
                focusParam,
            )
        }

        // Observed on pudding 2026-10-23: one JD order (2117) pushed its updates as separate
        // messages with per-message notifyIds 327792584 / 1523639136 and an identical param_v2
        // notifyId com.jingdong.app.mall2117; with a root-only lookup they rendered as two
        // coexisting 灵动岛 sessions (ids 1501889864 and -1597230880).
        val orderInProgress = FocusOrderIdentityPolicy.resolveNotificationId(
            "com.jingdong.app.mall",
            jdContainer(
                327792584,
                """{"param_v2":{"baseInfo":{"title":"您的订单正在进行中","content":"来份炒饼","type":2},""" +
                    """"param_island":{"islandProperty":1,"islandTimeout":7200},""" +
                    """"sequence":1792764366,"updatable":true,"notifyId":"com.jingdong.app.mall2117"}}""",
            ),
        )
        val riderArriving = FocusOrderIdentityPolicy.resolveNotificationId(
            "com.jingdong.app.mall",
            jdContainer(
                1523639136,
                """{"param_v2":{"baseInfo":{"title":"骑手正在赶往目的地","type":2},""" +
                    """"param_island":{"islandProperty":1,"islandTimeout":14400},""" +
                    """"sequence":1793375157,"updatable":true,"notifyId":"com.jingdong.app.mall2117"}}""",
            ),
        )
        val otherOrder = FocusOrderIdentityPolicy.resolveNotificationId(
            "com.jingdong.app.mall",
            jdContainer(
                327792584,
                """{"param_v2":{"sequence":1,"updatable":true,"notifyId":"com.jingdong.app.mall2118"}}""",
            ),
        )

        assertEquals(orderInProgress, riderArriving)
        assertNotEquals(orderInProgress, otherOrder)
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

    @Test
    fun `hasFocusParam separates a live session from a plain notification`() {
        assertFalse(FocusOrderIdentityPolicy.hasFocusParam(null))
        assertFalse(
            FocusOrderIdentityPolicy.hasFocusParam(
                com.xiaomi.xmpush.thrift.PushMetaInfo().apply { setNotifyId(42) },
            ),
        )
        assertFalse(
            FocusOrderIdentityPolicy.hasFocusParam(
                com.xiaomi.xmpush.thrift.PushMetaInfo().apply {
                    putToExtra("miui.focus.param", "")
                },
            ),
        )
        // The case the fallback log exists for: a focus payload the policy cannot key on.
        // resolveNotificationId says null here, exactly as it does for a plain notification,
        // so only hasFocusParam tells the two apart.
        val unkeyable = com.xiaomi.xmpush.thrift.PushMetaInfo().apply {
            setNotifyId(42)
            putToExtra("miui.focus.param", """{"param_v3":{"orderId":"order-1"}}""")
        }
        assertTrue(FocusOrderIdentityPolicy.hasFocusParam(unkeyable))
        assertNull(FocusOrderIdentityPolicy.resolveNotificationId("com.example.app", unkeyable))
    }
}
