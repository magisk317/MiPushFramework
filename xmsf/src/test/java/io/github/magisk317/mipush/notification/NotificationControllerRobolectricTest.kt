package io.github.magisk317.mipush.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.core.app.NotificationCompat
import com.xiaomi.xmpush.thrift.PushMetaInfo
import io.github.magisk317.mipush.common.NotificationStyle
import io.github.magisk317.mipush.common.utils.CustomConfiguration
import io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind
import io.github.magisk317.mipush.platform.support.LegacyUiEntryPoints
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class NotificationControllerRobolectricTest {

    companion object {
        /** Mirrors [io.github.magisk317.mipush.feature.navigation.AppDestinations.EventsList.ROUTE] */
        private const val EVENTS_ROUTE = "events"
        /** Mirrors MainActivity.EXTRA_START_ROUTE */
        private const val EXTRA_START_ROUTE = "extra_start_route"
        private const val ACTION_SHOW_ISLAND = "io.github.magisk317.mipush.action.SHOW_ISLAND"
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    }

    @AfterEach
    fun tearDown() {
        val notificationManager = RuntimeEnvironment.getApplication()
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()
        notificationManager.deleteNotificationChannel("target-default-implicit")
        notificationManager.deleteNotificationChannel("target-explicit")
        shadowOf(RuntimeEnvironment.getApplication()).clearBroadcastIntents()
    }

    @Test
    fun `channel selection uses managed channel when borrow channel is absent`() {
        val context = RuntimeEnvironment.getApplication()
        val packageName = context.packageName
        val metaInfo = PushMetaInfo().apply {
            extra = mutableMapOf("channel_id" to "push")
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(
                "target-default-implicit",
                "Target Default",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )

        NotificationManagerEx.init(context)

        val channelId = NotificationController.getExistsChannelId(context, metaInfo, packageName)

        assertEquals(NotificationChannelManager.getChannelId(metaInfo, packageName), channelId)
    }

    @Test
    fun `channel selection borrows only the explicitly requested channel`() {
        val context = RuntimeEnvironment.getApplication()
        val packageName = context.packageName
        val metaInfo = PushMetaInfo().apply {
            extra = mutableMapOf("__mi_push_borrow_channel_id" to "target-explicit")
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(
                "target-explicit",
                "Target Explicit",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )

        NotificationManagerEx.init(context)

        val channelId = NotificationController.getExistsChannelId(context, metaInfo, packageName)

        assertEquals("target-explicit", channelId)
    }

    @Test
    fun `focus bundle uses each focus pic uri and skips missing bitmaps`() {
        val loadedUris = mutableListOf<String>()
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val configuration = CustomConfiguration(
            linkedMapOf(
                "miui.focus.param" to """{"updatable":true,"reopen":"close"}""",
                "miui.focus.pic_profile" to "content://profile",
                "miui.focus.pic_aod" to "content://aod",
                "miui.focus.pic_empty" to ""
            )
        )

        val focusBundle = NotificationController.buildFocusBundle(configuration) { uri ->
            loadedUris += uri
            if (uri == "content://profile") bitmap else null
        }

        assertNotNull(focusBundle)
        assertEquals(setOf("content://profile", "content://aod"), loadedUris.toSet())
        assertEquals("content://profile", focusBundle!!.getString("miui.focus.pic_profile"))
        assertEquals("content://aod", focusBundle.getString("miui.focus.pic_aod"))

        val pics = focusBundle.getBundle("miui.focus.pics")
        assertNotNull(pics)
        assertNotNull(pics!!.parcelable<Icon>("miui.focus.pic_profile"))
        assertNull(pics.parcelable<Icon>("miui.focus.pic_aod"))
    }

    @Test
    fun `default island payload uses hyperisland param v2 and icon bundle`() {
        val context = RuntimeEnvironment.getApplication()
        val metaInfo = PushMetaInfo().apply {
            title = "Island title"
            description = "Island body"
        }
        val icon = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        val focusBundle = MiPushIslandPayloadBuilder.build(
            context = context,
            metaInfo = metaInfo,
            packageName = context.packageName,
            largeIcon = icon
        )

        assertNotNull(focusBundle)
        val focusParam = focusBundle!!.getString("miui.focus.param")
        assertNotNull(focusParam)
        assertTrue(focusParam!!.contains(""""param_v2""""))
        assertTrue(focusParam.contains(""""mipush_framework_push""""))
        assertFocusSequenceEnabled(focusParam)
        assertEquals(true to "close", NotificationSortFilter.parseFocusParamForTest(focusParam))
        assertEquals("miui.focus.pic_mipush_icon", focusBundle.getString("miui.focus.pic_mipush_icon"))
        val pics = focusBundle.getBundle("miui.focus.pics")
        assertNotNull(pics)
        assertNotNull(pics!!.parcelable<Icon>("miui.focus.pic_mipush_icon"))
    }

    @Test
    fun `default island payload supports description only notifications`() {
        val context = RuntimeEnvironment.getApplication()
        val metaInfo = PushMetaInfo().apply {
            description = "Body only"
        }

        val focusBundle = MiPushIslandPayloadBuilder.build(
            context = context,
            metaInfo = metaInfo,
            packageName = context.packageName,
            largeIcon = null,
        )

        assertNotNull(focusBundle)
        val focusParam = focusBundle!!.getString("miui.focus.param")
        assertNotNull(focusParam)
        assertFocusSequenceEnabled(focusParam!!)
    }

    @Test
    fun `general island payload uses icon text template to keep icon leading`() {
        val context = RuntimeEnvironment.getApplication()
        val metaInfo = PushMetaInfo().apply {
            title = "芝麻分成长锦囊可领！"
            description = "点滴信用 重在积累"
        }

        val focusBundle = MiPushIslandPayloadBuilder.build(
            context = context,
            metaInfo = metaInfo,
            packageName = "com.eg.android.AlipayGphone",
            largeIcon = null,
        )

        assertNotNull(focusBundle)
        val focusParam = focusBundle!!.getString("miui.focus.param")
        assertNotNull(focusParam)
        val paramV2 = JSONObject(focusParam!!).getJSONObject("param_v2")
        assertFalse(paramV2.has("baseInfo"))
        assertTrue(paramV2.has("iconTextInfo"))
        assertEquals(
            "miui.focus.pic_mipush_icon",
            paramV2
                .getJSONObject("iconTextInfo")
                .getJSONObject("animIconInfo")
                .getString("src"),
            )
    }

    @Test
    fun `reminder island payload uses two-line icon text layout instead of highlight hint`() {
        val context = RuntimeEnvironment.getApplication()
        val metaInfo = PushMetaInfo().apply {
            title = "芝麻粒消失提醒"
            description = "可攒30粒，产生后7天消失，请及时处理"
        }

        val focusBundle = MiPushIslandPayloadBuilder.build(
            context = context,
            metaInfo = metaInfo,
            packageName = "com.eg.android.AlipayGphone",
            largeIcon = null,
        )

        assertNotNull(focusBundle)
        val paramV2 = JSONObject(focusBundle!!.getString("miui.focus.param")!!)
            .getJSONObject("param_v2")
        assertFalse(paramV2.has("highlightInfo"))
        assertFalse(paramV2.has("hintInfo"))
        assertTrue(paramV2.has("iconTextInfo"))
        assertEquals(
            metaInfo.title,
            paramV2.getJSONObject("iconTextInfo").getString("title"),
        )
        assertEquals(
            metaInfo.description,
            paramV2.getJSONObject("iconTextInfo").getString("content"),
        )

        val bigIslandArea = paramV2
            .getJSONObject("param_island")
            .getJSONObject("bigIslandArea")
        assertFalse(bigIslandArea.has("imageTextInfoRight"))
        val left = bigIslandArea.getJSONObject("imageTextInfoLeft")
        assertEquals(
            "miui.focus.pic_mipush_icon",
            left.getJSONObject("picInfo").getString("pic"),
        )
        val textInfo = left.getJSONObject("textInfo")
        assertEquals(metaInfo.title, textInfo.getString("title"))
        assertEquals(metaInfo.description, textInfo.getString("content"))
    }

    @Test
    fun `island payload wires click action as activity action`() {
        val context = RuntimeEnvironment.getApplication()
        val clickIntent = LegacyUiEntryPoints.mainActivityIntent(context, startRoute = EVENTS_ROUTE)
        val clickPendingIntent = PendingIntent.getActivity(
            context,
            0,
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val metaInfo = PushMetaInfo().apply {
            title = "Focus title"
            description = "Focus body"
        }

        val focusBundle = MiPushIslandPayloadBuilder.build(
            context = context,
            metaInfo = metaInfo,
            packageName = context.packageName,
            largeIcon = null,
            contentIntent = clickPendingIntent,
            actionTitle = "Open events",
        )

        assertNotNull(focusBundle)
        val focusParam = focusBundle!!.getString("miui.focus.param")
        assertNotNull(focusParam)
        val actionInfo = JSONObject(focusParam!!)
            .getJSONObject("param_v2")
            .getJSONObject("hintInfo")
            .getJSONObject("actionInfo")
        assertEquals(1, actionInfo.getInt("actionIntentType"))
        val action = focusBundle
            .getBundle("miui.focus.actions")
            ?.parcelable<Notification.Action>("miui.focus.action_mipush_open")
        assertNotNull(action)
        assertTrue(shadowOf(action!!.actionIntent).isActivity)
        assertEquals(
            EVENTS_ROUTE,
            shadowOf(action.actionIntent).savedIntent.getStringExtra(EXTRA_START_ROUTE),
        )
    }

    @Test
    fun `plain mock notification does not opt into island proxy`() {
        val context = RuntimeEnvironment.getApplication()
        val packageName = context.packageName

        NotificationController.testMock(context, MockNotificationKind.PLAIN, packageName)

        val posted = findMockNotification(context, MockNotificationKind.PLAIN)

        assertNull(posted.extras.getString("miui.focus.param"))
        assertNull(posted.extras.getBundle("miui.focus.pics"))
        assertFalse(posted.extras.getBoolean("mipush_island_allow_proxy", false))
        assertEquals(packageName, posted.extras.getString("target_package"))
        assertTrue(shadowOf(posted.contentIntent).isActivity)
        assertEquals(
            EVENTS_ROUTE,
            shadowOf(posted.contentIntent).savedIntent.getStringExtra(EXTRA_START_ROUTE),
        )
    }

    @Test
    fun `dynamic island mock broadcasts promo SystemUI island request`() {
        val context = RuntimeEnvironment.getApplication()
        val packageName = context.packageName

        NotificationController.testMock(context, MockNotificationKind.DYNAMIC_ISLAND, packageName)

        val intent = singleIslandBroadcast()

        assertEquals(SYSTEM_UI_PACKAGE, intent.`package`)
        assertEquals(packageName, intent.getStringExtra("sourcePackage"))
        assertEquals("mipush_mock_island", intent.getStringExtra("sourceChannelId"))
        assertEquals(NotificationStyle.PROMO.name, intent.getStringExtra("style"))
        assertFalse(intent.getBooleanExtra("smallOnly", true))
        assertFalse(intent.getBooleanExtra("showNotification", true))
        assertTrue(intent.getBooleanExtra("islandOuterGlow", false))
        assertTrue(intent.getBooleanExtra("clearBeforePost", false))
    }

    @Test
    fun `focus mock notification broadcasts SystemUI island request with activity action`() {
        val context = RuntimeEnvironment.getApplication()
        val packageName = context.packageName

        NotificationController.testMock(context, MockNotificationKind.FOCUS_NOTIFICATION, packageName)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertFalse(notificationManager.activeNotifications.any { it.tag == "xmsf_mock_${MockNotificationKind.FOCUS_NOTIFICATION.name}" })

        val intent = singleIslandBroadcast()
        val contentIntent = intent.parcelableExtra<PendingIntent>("contentIntent")

        assertEquals(SYSTEM_UI_PACKAGE, intent.`package`)
        assertEquals(packageName, intent.getStringExtra("sourcePackage"))
        assertEquals(NotificationStyle.GENERAL.name, intent.getStringExtra("style"))
        assertFalse(intent.getBooleanExtra("smallOnly", true))
        assertFalse(intent.getBooleanExtra("showNotification", true))
        assertTrue(intent.getBooleanExtra("islandOuterGlow", false))
        assertNotNull(contentIntent)
        assertTrue(shadowOf(contentIntent!!).isActivity)
        assertEquals(
            EVENTS_ROUTE,
            shadowOf(contentIntent).savedIntent.getStringExtra(EXTRA_START_ROUTE),
        )
    }

    @Test
    fun `focus mock notifications broadcast every hyperisland template style`() {
        val context = RuntimeEnvironment.getApplication()
        val packageName = context.packageName
        val expectedStyles = mapOf(
            MockNotificationKind.FOCUS_NOTIFICATION to NotificationStyle.GENERAL,
            MockNotificationKind.FOCUS_MESSAGE to NotificationStyle.MESSAGE,
            MockNotificationKind.FOCUS_BANNER to NotificationStyle.BANNER,
            MockNotificationKind.FOCUS_ALERT to NotificationStyle.ALERT,
            MockNotificationKind.FOCUS_PROMO to NotificationStyle.PROMO,
            MockNotificationKind.FOCUS_MEDIA to NotificationStyle.MEDIA,
            MockNotificationKind.FOCUS_PROGRESS to NotificationStyle.PROGRESS,
        )

        expectedStyles.forEach { (kind, expectedStyle) ->
            shadowOf(RuntimeEnvironment.getApplication()).clearBroadcastIntents()
            NotificationController.testMock(context, kind, packageName)

            val intent = singleIslandBroadcast()
            assertEquals(expectedStyle.name, intent.getStringExtra("style"))
            assertEquals(expectedStyle, kind.focusTemplateStyle)
            assertFalse(intent.getBooleanExtra("smallOnly", true))
            assertEquals(expectedStyle == NotificationStyle.MEDIA || expectedStyle == NotificationStyle.PROGRESS, intent.getBooleanExtra("isOngoing", false))
        }
    }

    @Test
    fun `publish keeps generated island payload off regular notification and enables proxy`() {
        val context = RuntimeEnvironment.getApplication()
        val packageName = context.packageName
        val notificationId = 32017
        val metaInfo = PushMetaInfo().apply {
            title = "Island title"
            description = "Island body"
        }
        val builder = NotificationCompat.Builder(context, "placeholder")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(metaInfo.title)
            .setContentText(metaInfo.description)

        NotificationManagerEx.init(context)
        NotificationController.publish(context, metaInfo, notificationId, packageName, builder)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = notificationManager.activeNotifications
            .first { it.id == notificationId }
            .notification

        assertNull(posted.extras.getString("miui.focus.param"))
        assertNull(posted.extras.getString("miui.focus.pic_mipush_icon"))
        assertNull(posted.extras.getBundle("miui.focus.pics"))
        assertNull(posted.extras.getString("hyperisland_source_pkg"))
        assertTrue(posted.extras.getBoolean("mipush_island_allow_proxy", false))
        assertEquals(packageName, posted.extras.getString("target_package"))
    }

    @Test
    fun `publish supplements configured focus payload with app icon bundle`() {
        val context = RuntimeEnvironment.getApplication()
        val packageName = context.packageName
        val notificationId = 32018
        val focusParam = """{"updatable":true,"reopen":"close"}"""
        val metaInfo = PushMetaInfo().apply {
            title = "Configured focus"
            description = "Configured body"
            extra = mutableMapOf("miui.focus.param" to focusParam)
        }
        val builder = NotificationCompat.Builder(context, "placeholder")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(metaInfo.title)
            .setContentText(metaInfo.description)

        NotificationManagerEx.init(context)
        NotificationController.publish(context, metaInfo, notificationId, packageName, builder)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = notificationManager.activeNotifications
            .first { it.id == notificationId }
            .notification

        assertEquals(focusParam, posted.extras.getString("miui.focus.param"))
        assertEquals("miui.focus.pic_mipush_icon", posted.extras.getString("miui.focus.pic_mipush_icon"))
        assertFalse(posted.extras.getBoolean("mipush_island_allow_proxy", false))
        assertNotNull(
            posted.extras
                .getBundle("miui.focus.pics")
                ?.parcelable<Icon>("miui.focus.pic_mipush_icon")
        )
    }

    @Test
    fun `grouped notifications use island proxy while summary stays plain`() {
        val context = RuntimeEnvironment.getApplication()
        val packageName = context.packageName
        val groupId = "focus-group"

        NotificationManagerEx.init(context)

        repeat(2) { index ->
            val metaInfo = PushMetaInfo().apply {
                title = "Grouped $index"
                description = "Grouped body $index"
            }
            val builder = NotificationCompat.Builder(context, "placeholder")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(metaInfo.title)
                .setContentText(metaInfo.description)
                .setGroup(groupId)

            NotificationController.publish(context, metaInfo, 33000 + index, packageName, builder)
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val active = notificationManager.activeNotifications.associateBy { it.id }
        val summary = active.getValue(groupId.hashCode()).notification

        assertTrue(summary.extras.getCharSequence(Notification.EXTRA_TITLE).toString().isNotBlank())
        assertNull(summary.extras.getString("miui.focus.param"))
        assertNull(summary.extras.getBundle("miui.focus.pics"))
        assertFalse(summary.extras.getBoolean("mipush_island_allow_proxy", false))
        assertNull(active.getValue(33000).notification.extras.getString("miui.focus.param"))
        assertNull(active.getValue(33001).notification.extras.getString("miui.focus.param"))
        assertTrue(active.getValue(33000).notification.extras.getBoolean("mipush_island_allow_proxy", false))
        assertTrue(active.getValue(33001).notification.extras.getBoolean("mipush_island_allow_proxy", false))
    }

    @Test
    fun `live update notifications keep ongoing auto cancel semantics`() {
        val context = RuntimeEnvironment.getApplication()
        val builder = NotificationCompat.Builder(context, "progress")
            .setSmallIcon(android.R.drawable.stat_sys_download)
        val metaInfo = PushMetaInfo().apply {
            title = "外卖配送"
            description = "骑手已取餐，预计15分钟送达"
        }
        val result = LiveUpdateDetector.DetectionResult(
            isProgress = true,
            category = LiveUpdateDetector.ProgressCategory.DELIVERY,
            progressPercent = 65,
            progressText = metaInfo.description,
            startLabel = "商家",
            endLabel = "目的地",
            trackerLabel = "配送中"
        )

        ProgressStyleBuilder.applyProgressStyle(context, builder, metaInfo, result)
        val notification = builder.build()

        assertTrue(ProgressStyleBuilder.isLiveUpdate(builder))
        assertFalse(NotificationController.shouldAutoCancelNotification(metaInfo, builder))
        assertEquals(0, notification.flags and Notification.FLAG_AUTO_CANCEL)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test
    fun `regular notifications remain auto cancellable`() {
        val context = RuntimeEnvironment.getApplication()
        val builder = NotificationCompat.Builder(context, "plain")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
        val metaInfo = PushMetaInfo().apply {
            title = "普通消息"
            description = "内容"
        }

        assertFalse(ProgressStyleBuilder.isLiveUpdate(builder))
        assertTrue(NotificationController.shouldAutoCancelNotification(metaInfo, builder))
    }

    private inline fun <reified T : Parcelable> Bundle.parcelable(key: String): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelable(key, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelable(key)
        }
    }

    private inline fun <reified T : Parcelable> Intent.parcelableExtra(key: String): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(key)
        }
    }

    private fun assertFocusSequenceEnabled(focusParam: String) {
        val paramV2 = JSONObject(focusParam).getJSONObject("param_v2")
        assertTrue(paramV2.getBoolean("enableFloat"))
        assertTrue(paramV2.getBoolean("islandFirstFloat"))
        assertTrue(
            paramV2.optBoolean(
                "isShowNotification",
                paramV2.optBoolean("showNotification", false),
            )
        )
    }

    private fun singleIslandBroadcast(): Intent {
        return shadowOf(RuntimeEnvironment.getApplication())
            .broadcastIntents
            .single { it.action == ACTION_SHOW_ISLAND }
    }

    private fun findMockNotification(
        context: Context,
        kind: MockNotificationKind,
    ): Notification {
        return (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .activeNotifications
            .single { it.tag == "xmsf_mock_${kind.name}" }
            .notification
    }
}
