package io.github.magisk317.mipush.hook.system

import android.app.Application
import android.app.Notification
import android.graphics.drawable.Icon
import android.os.Bundle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        assertEquals(1, payload["protocol"]?.jsonPrimitive?.int)
        assertTrue(payload["updatable"]?.jsonPrimitive?.boolean == true)
        assertFalse(payload["enableFloat"]?.jsonPrimitive?.boolean == true)
        assertTrue(payload["islandFirstFloat"]?.jsonPrimitive?.boolean == true)
        assertEquals("Navigation in progress", payload["ticker"]?.jsonPrimitive?.content)

        val paramV2 = payload["param_v2"]?.jsonObject ?: error("missing param_v2")
        assertCommonFieldsMatch(payload, paramV2)
        val baseInfo = paramV2["baseInfo"]?.jsonObject ?: error("missing baseInfo")
        assertEquals("Turn right", baseInfo["title"]?.jsonPrimitive?.content)
        assertEquals("Navigation in progress", baseInfo["content"]?.jsonPrimitive?.content)
        val compact = paramV2["param_island"]?.jsonObject?.get("bigIslandArea")?.jsonObject ?: error("missing bigIslandArea")
        assertEquals(
            "Navigation in progress",
            compact["imageTextInfoLeft"]?.jsonObject?.get("textInfo")?.jsonObject?.get("title")?.jsonPrimitive?.content,
        )
        assertEquals("Turn right", compact["textInfo"]?.jsonObject?.get("title")?.jsonPrimitive?.content)

        val pictures = notification.extras.getBundle(FOCUS_PICTURES)
        assertNotNull(pictures)
        assertNotNull(pictures!!.icon(FOCUS_NAVIGATION_PICTURE))
        assertNotNull(pictures.icon(FOCUS_LARGE_PICTURE))
    }

    @Test
    fun `rich drive data uses native param v2 compact navigation layout`() {
        val notification = navigationNotification(
            title = "300米 右转",
            content = "进入长安街",
        )

        assertTrue(
            AmapNavigationFocusCompat.attachIfEligible(
                AMAP_PACKAGE,
                notification,
                focusAuthorizationBypassEnabled = true,
            ),
        )

        val payload = focusPayload(notification)
        assertEquals(1, payload["protocol"]?.jsonPrimitive?.int)
        assertTrue(payload["updatable"]?.jsonPrimitive?.boolean == true)
        assertEquals("com.autonavi.minimap99910001", payload["notifyId"]?.jsonPrimitive?.content)
        assertEquals("右转", payload["ticker"]?.jsonPrimitive?.content)
        val paramV2 = payload["param_v2"]?.jsonObject ?: error("missing param_v2")
        assertCommonFieldsMatch(payload, paramV2)

        val baseInfo = paramV2["baseInfo"]?.jsonObject ?: error("missing baseInfo")
        assertEquals("300米 右转", baseInfo["title"]?.jsonPrimitive?.content)
        assertEquals("进入长安街", baseInfo["content"]?.jsonPrimitive?.content)

        val paramIsland = paramV2["param_island"]?.jsonObject ?: error("missing param_island")
        val bigIslandArea = paramIsland["bigIslandArea"]?.jsonObject ?: error("missing bigIslandArea")
        val compactLeft = bigIslandArea["imageTextInfoLeft"]?.jsonObject ?: error("missing imageTextInfoLeft")
        assertEquals(
            FOCUS_NAVIGATION_PICTURE,
            compactLeft["picInfo"]?.jsonObject?.get("pic")?.jsonPrimitive?.content,
        )
        assertEquals("右转", compactLeft["textInfo"]?.jsonObject?.get("title")?.jsonPrimitive?.content)

        val compactRight = bigIslandArea["textInfo"]?.jsonObject ?: error("missing textInfo")
        assertEquals("300米", compactRight["title"]?.jsonPrimitive?.content)
        assertEquals("后", compactRight["content"]?.jsonPrimitive?.content)
        assertTrue(compactRight["showHighlightColor"]?.jsonPrimitive?.boolean == true)
        assertFalse(bigIslandArea.toString().contains("进入长安街"))
    }

    @Test
    fun `drive layout accepts decimal metric distance without misclassifying static titles`() {
        val rich = navigationNotification(title = "1.2公里 直行", content = "沿主路行驶")
        val static = navigationNotification(title = "Route 66", content = "Navigation in progress")

        assertTrue(AmapNavigationFocusCompat.attachIfEligible(AMAP_PACKAGE, rich, true))
        assertTrue(AmapNavigationFocusCompat.attachIfEligible(AMAP_PACKAGE, static, true))

        val richBigIsland = focusPayload(rich)["param_v2"]?.jsonObject
            ?.get("param_island")?.jsonObject
            ?.get("bigIslandArea")?.jsonObject
        assertEquals(
            "1.2公里",
            richBigIsland?.get("textInfo")?.jsonObject?.get("title")?.jsonPrimitive?.content,
        )
        val staticPayload = focusPayload(static)
        assertTrue(staticPayload.containsKey("param_v2"))
        val staticBigIsland = staticPayload["param_v2"]?.jsonObject
            ?.get("param_island")?.jsonObject
            ?.get("bigIslandArea")?.jsonObject
        assertEquals(
            "Navigation in progress",
            staticBigIsland?.get("imageTextInfoLeft")?.jsonObject
                ?.get("textInfo")?.jsonObject
                ?.get("title")?.jsonPrimitive?.content,
        )
        assertEquals(
            "Route 66",
            staticBigIsland?.get("textInfo")?.jsonObject?.get("title")?.jsonPrimitive?.content,
        )
    }

    @Test
    fun `distance-only drive data gets a compact navigation label and expanded fallback`() {
        val notification = navigationNotification(title = "80米", content = "80米")

        assertTrue(AmapNavigationFocusCompat.attachIfEligible(AMAP_PACKAGE, notification, true))

        val paramV2 = focusPayload(notification)["param_v2"]?.jsonObject ?: error("missing param_v2")
        val bigIslandArea = paramV2["param_island"]?.jsonObject?.get("bigIslandArea")?.jsonObject ?: error("missing bigIslandArea")
        assertEquals(
            "导航中",
            bigIslandArea["imageTextInfoLeft"]?.jsonObject?.get("textInfo")?.jsonObject?.get("title")?.jsonPrimitive?.content,
        )
        assertEquals("80米", bigIslandArea["textInfo"]?.jsonObject?.get("title")?.jsonPrimitive?.content)
        assertEquals("后", bigIslandArea["textInfo"]?.jsonObject?.get("content")?.jsonPrimitive?.content)
        assertEquals("正在导航", paramV2["baseInfo"]?.jsonObject?.get("content")?.jsonPrimitive?.content)
    }

    @Test
    fun `focus updates use a stable identity and monotonic sequence`() {
        val first = navigationNotification()
        val second = navigationNotification(title = "300米 右转", content = "进入长安街")

        assertTrue(AmapNavigationFocusCompat.attachIfEligible(AMAP_PACKAGE, first, true))
        assertTrue(AmapNavigationFocusCompat.attachIfEligible(AMAP_PACKAGE, second, true))

        val firstPayload = focusPayload(first)
        val secondPayload = focusPayload(second)
        assertEquals(firstPayload["notifyId"]?.jsonPrimitive?.content, secondPayload["notifyId"]?.jsonPrimitive?.content)
        val firstSeq = firstPayload["sequence"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
        val secondSeq = secondPayload["sequence"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
        assertTrue(secondSeq > firstSeq)
        assertCommonFieldsMatch(firstPayload, firstPayload["param_v2"]?.jsonObject ?: error("missing param_v2"))
        assertCommonFieldsMatch(secondPayload, secondPayload["param_v2"]?.jsonObject ?: error("missing param_v2"))
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
            val payload = focusPayload(notification)
            assertEquals(
                "Turn right",
                payload["param_v2"]?.jsonObject?.get("baseInfo")?.jsonObject?.get("title")?.jsonPrimitive?.content,
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

    private fun focusPayload(notification: Notification): JsonObject {
        return Json.parseToJsonElement(requireNotNull(notification.extras.getString(FOCUS_PARAM))).jsonObject
    }

    private fun assertCommonFieldsMatch(root: JsonObject, paramV2: JsonObject) {
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
            assertEquals(root[key]?.toString(), paramV2[key]?.toString(), key)
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
