package io.github.magisk317.mipush.hook.system

import android.app.Application
import android.app.Notification
import android.graphics.drawable.Icon
import android.os.Bundle
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
class AmapNavigationFocusCompatTest {
    @Test
    fun `normal AMap navigation gets a native focus payload in place`() {
        val notification = navigationNotification()

        assertTrue(
            AmapNavigationFocusCompat.attachIfEligible(
                AMAP_PACKAGE,
                notification,
                focusAuthorizationBypassEnabled = true,
            )
        )
        assertFalse(
            AmapNavigationFocusCompat.attachIfEligible(
                AMAP_PACKAGE,
                notification,
                focusAuthorizationBypassEnabled = true,
            )
        )

        val payload = focusPayload(notification)
        assertEquals(1, payload.getInt("protocol"))
        assertTrue(payload.getBoolean("updatable"))
        assertFalse(payload.getBoolean("enableFloat"))
        assertTrue(payload.getBoolean("islandFirstFloat"))
        assertEquals("Navigation in progress", payload.getString("ticker"))

        val paramV2 = payload.getJSONObject("param_v2")
        assertCommonFieldsMatch(payload, paramV2)
        val baseInfo = paramV2.getJSONObject("baseInfo")
        assertEquals("Turn right", baseInfo.getString("title"))
        assertEquals("Navigation in progress", baseInfo.getString("content"))
        val compact = paramV2.getJSONObject("param_island").getJSONObject("bigIslandArea")
        assertEquals(
            "Navigation in progress",
            compact.getJSONObject("imageTextInfoLeft").getJSONObject("textInfo").getString("title"),
        )
        assertEquals("Turn right", compact.getJSONObject("textInfo").getString("title"))

        val pictures = notification.extras.getBundle(FOCUS_PICTURES)
        assertNotNull(pictures)
        assertNotNull(pictures!!.icon(FOCUS_NAVIGATION_PICTURE))
        assertNotNull(pictures.icon(FOCUS_LARGE_PICTURE))
    }

    @Test
    fun `rich drive data uses native param v2 compact navigation layout`() {
        val notification = navigationNotification(
            title = "300\u7c73 \u53f3\u8f6c",
            content = "\u8fdb\u5165\u957f\u5b89\u8857",
        )

        assertTrue(
            AmapNavigationFocusCompat.attachIfEligible(
                AMAP_PACKAGE,
                notification,
                focusAuthorizationBypassEnabled = true,
            ),
        )

        val payload = focusPayload(notification)
        assertEquals(1, payload.getInt("protocol"))
        assertTrue(payload.getBoolean("updatable"))
        assertEquals("com.autonavi.minimap99910001", payload.getString("notifyId"))
        assertEquals("\u53f3\u8f6c", payload.getString("ticker"))
        val paramV2 = payload.getJSONObject("param_v2")
        assertCommonFieldsMatch(payload, paramV2)

        val baseInfo = paramV2.getJSONObject("baseInfo")
        assertEquals("300\u7c73 \u53f3\u8f6c", baseInfo.getString("title"))
        assertEquals("\u8fdb\u5165\u957f\u5b89\u8857", baseInfo.getString("content"))

        val paramIsland = paramV2.getJSONObject("param_island")
        val bigIslandArea = paramIsland.getJSONObject("bigIslandArea")
        val compactLeft = bigIslandArea.getJSONObject("imageTextInfoLeft")
        assertEquals(
            FOCUS_NAVIGATION_PICTURE,
            compactLeft.getJSONObject("picInfo").getString("pic"),
        )
        assertEquals("\u53f3\u8f6c", compactLeft.getJSONObject("textInfo").getString("title"))

        val compactRight = bigIslandArea.getJSONObject("textInfo")
        assertEquals("300\u7c73", compactRight.getString("title"))
        assertEquals("\u540e", compactRight.getString("content"))
        assertTrue(compactRight.getBoolean("showHighlightColor"))
        assertFalse(bigIslandArea.toString().contains("\u8fdb\u5165\u957f\u5b89\u8857"))
    }

    @Test
    fun `drive layout accepts decimal metric distance without misclassifying static titles`() {
        val rich = navigationNotification(title = "1.2\u516c\u91cc \u76f4\u884c", content = "\u6cbf\u4e3b\u8def\u884c\u9a76")
        val static = navigationNotification(title = "Route 66", content = "Navigation in progress")

        assertTrue(AmapNavigationFocusCompat.attachIfEligible(AMAP_PACKAGE, rich, true))
        assertTrue(AmapNavigationFocusCompat.attachIfEligible(AMAP_PACKAGE, static, true))

        assertEquals(
            "1.2\u516c\u91cc",
            focusPayload(rich)
                .getJSONObject("param_v2")
                .getJSONObject("param_island")
                .getJSONObject("bigIslandArea")
                .getJSONObject("textInfo")
                .getString("title"),
        )
        val staticPayload = focusPayload(static)
        assertTrue(staticPayload.has("param_v2"))
        assertEquals(
            "Navigation in progress",
            staticPayload
                .getJSONObject("param_v2")
                .getJSONObject("param_island")
                .getJSONObject("bigIslandArea")
                .getJSONObject("imageTextInfoLeft")
                .getJSONObject("textInfo")
                .getString("title"),
        )
        assertEquals(
            "Route 66",
            staticPayload
                .getJSONObject("param_v2")
                .getJSONObject("param_island")
                .getJSONObject("bigIslandArea")
                .getJSONObject("textInfo")
                .getString("title"),
        )
    }

    @Test
    fun `distance-only drive data gets a compact navigation label and expanded fallback`() {
        val notification = navigationNotification(title = "80\u7c73", content = "80\u7c73")

        assertTrue(AmapNavigationFocusCompat.attachIfEligible(AMAP_PACKAGE, notification, true))

        val paramV2 = focusPayload(notification).getJSONObject("param_v2")
        val bigIslandArea = paramV2.getJSONObject("param_island").getJSONObject("bigIslandArea")
        assertEquals(
            "\u5bfc\u822a\u4e2d",
            bigIslandArea.getJSONObject("imageTextInfoLeft").getJSONObject("textInfo").getString("title"),
        )
        assertEquals("80\u7c73", bigIslandArea.getJSONObject("textInfo").getString("title"))
        assertEquals("\u540e", bigIslandArea.getJSONObject("textInfo").getString("content"))
        assertEquals("\u6b63\u5728\u5bfc\u822a", paramV2.getJSONObject("baseInfo").getString("content"))
    }

    @Test
    fun `focus updates use a stable identity and monotonic sequence`() {
        val first = navigationNotification()
        val second = navigationNotification(title = "300\u7c73 \u53f3\u8f6c", content = "\u8fdb\u5165\u957f\u5b89\u8857")

        assertTrue(AmapNavigationFocusCompat.attachIfEligible(AMAP_PACKAGE, first, true))
        assertTrue(AmapNavigationFocusCompat.attachIfEligible(AMAP_PACKAGE, second, true))

        val firstPayload = focusPayload(first)
        val secondPayload = focusPayload(second)
        assertEquals(firstPayload.getString("notifyId"), secondPayload.getString("notifyId"))
        assertTrue(secondPayload.getLong("sequence") > firstPayload.getLong("sequence"))
        assertCommonFieldsMatch(firstPayload, firstPayload.getJSONObject("param_v2"))
        assertCommonFieldsMatch(secondPayload, secondPayload.getJSONObject("param_v2"))
    }

    @Test
    fun `NMS internal foreground-service arguments use the same compatibility path`() {
        listOf(
            arrayOf<Any?>(
                AMAP_PACKAGE, AMAP_PACKAGE, 10295, 12345, null, 99910001,
                navigationNotification(), 0, true, true,
            ),
            arrayOf<Any?>(
                AMAP_PACKAGE, AMAP_PACKAGE, 10295, 12345, null, 99910001,
                navigationNotification(), 0, false, true, true,
            ),
        ).forEach { arguments ->
            assertTrue(
                AmapNavigationFocusCompat.attachIfEligibleFromForegroundServiceNmsArguments(
                    arguments,
                    focusAuthorizationBypassEnabled = true,
                )
            )
            val notification = arguments.first { it is Notification } as Notification
            assertEquals(
                "Turn right",
                focusPayload(notification).getJSONObject("param_v2").getJSONObject("baseInfo").getString("title"),
            )
        }
    }

    @Test
    fun `NMS internal non foreground-service arguments remain untouched`() {
        val notification = navigationNotification()
        val arguments = arrayOf<Any?>(
            AMAP_PACKAGE,
            AMAP_PACKAGE,
            10295,
            12345,
            null,
            99910001,
            notification,
            0,
            false,
            true,
        )

        assertFalse(
            AmapNavigationFocusCompat.attachIfEligibleFromForegroundServiceNmsArguments(
                arguments,
                focusAuthorizationBypassEnabled = true,
            )
        )
        assertFalse(notification.extras.containsKey(FOCUS_PARAM))
    }

    @Test
    fun `normal AMap navigation keeps the shade notification when bypass is disabled`() {
        val notification = navigationNotification()

        assertFalse(
            AmapNavigationFocusCompat.attachIfEligible(
                AMAP_PACKAGE,
                notification,
                focusAuthorizationBypassEnabled = false,
            )
        )
        assertFalse(notification.extras.containsKey(FOCUS_PARAM))
        assertFalse(notification.extras.containsKey(FOCUS_PICTURES))
    }

    @Test
    fun `native AMap taxi walk ride and UA focus payloads remain untouched`() {
        listOf("taxi", "walk-ride", "ua").forEach { source ->
            val notification = navigationNotification().apply {
                extras.putString(FOCUS_PARAM, "native-$source")
                extras.putBundle(
                    FOCUS_PICTURES,
                    android.os.Bundle().apply { putString("source", source) },
                )
            }

            assertFalse(
                AmapNavigationFocusCompat.attachIfEligible(
                    AMAP_PACKAGE,
                    notification,
                    focusAuthorizationBypassEnabled = true,
                )
            )
            assertEquals("native-$source", notification.extras.getString(FOCUS_PARAM))
            assertEquals(source, notification.extras.getBundle(FOCUS_PICTURES)?.getString("source"))
        }
    }

    @Test
    fun `only active normal AMap navigation is eligible`() {
        assertFalse(AmapNavigationFocusCompat.shouldAttach("com.example.other", navigationNotification()))
        assertFalse(
            AmapNavigationFocusCompat.shouldAttach(
                AMAP_PACKAGE,
                navigationNotification(category = Notification.CATEGORY_EVENT),
            ),
        )
        assertFalse(AmapNavigationFocusCompat.shouldAttach(AMAP_PACKAGE, navigationNotification(navigating = false)))
        assertFalse(
            AmapNavigationFocusCompat.shouldAttach(
                AMAP_PACKAGE,
                navigationNotification().apply { extras.putString(FOCUS_REMOTE_VIEW, "native-remote-view") },
            ),
        )
    }

    private fun navigationNotification(
        category: String = Notification.CATEGORY_NAVIGATION,
        navigating: Boolean = true,
        title: String = "Turn right",
        content: String = "Navigation in progress",
    ): Notification {
        val context = RuntimeEnvironment.getApplication()
        return Notification.Builder(context, "test")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setLargeIcon(Icon.createWithResource(context, android.R.drawable.ic_dialog_map))
            .setCategory(category)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .build()
            .apply {
                extras.putBoolean(EXTRA_NAVIGATING, navigating)
            }
    }

    private fun focusPayload(notification: Notification): JSONObject {
        return JSONObject(requireNotNull(notification.extras.getString(FOCUS_PARAM)))
    }

    private fun assertCommonFieldsMatch(root: JSONObject, paramV2: JSONObject) {
        listOf(
            "ticker",
            "aodPic",
            "picInfo",
            "enableFloat",
            "islandFirstFloat",
            "timeout",
            "sequence",
            "protocol",
            "aodTitle",
            "updatable",
            "notifyId",
        ).forEach { key ->
            assertEquals(root.get(key).toString(), paramV2.get(key).toString(), key)
        }
    }

    @Suppress("DEPRECATION")
    private fun Bundle.icon(key: String): Icon? = getParcelable(key)

    private companion object {
        private const val AMAP_PACKAGE = "com.autonavi.minimap"
        private const val EXTRA_NAVIGATING = "com.autonavi.minimap.navigating"
        private const val FOCUS_PARAM = "miui.focus.param"
        private const val FOCUS_REMOTE_VIEW = "miui.focus.rv"
        private const val FOCUS_PICTURES = "miui.focus.pics"
        private const val FOCUS_LARGE_PICTURE = "miui.focus.pic_large"
        private const val FOCUS_NAVIGATION_PICTURE = "miui.focus.pic_amap_navigation"
    }
}
