package com.xiaomi.xmsf.push.service.receivers

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import com.xiaomi.mipush.sdk.MiPushCommandMessage
import com.xiaomi.mipush.sdk.MiPushMessage
import com.xiaomi.push.service.receivers.PingReceiver
import com.xiaomi.push.service.timers.Alarm
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class MiuiPushMessageReceiverTest {
    private lateinit var context: Application

    @BeforeEach
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        shadowOf(context).clearBroadcastIntents()
        context.getSharedPreferences("pref_registered_pkg_names", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @AfterEach
    fun tearDown() {
        Alarm.setIAlarm(null)
    }

    @Test
    fun `all-message callback plans stock app broadcast and click service routes`() {
        val message = MiPushMessage().apply {
            extra = mapOf("miui_package_name" to "com.example.target")
        }

        assertEquals(
            MiuiPushMessageReceiver.AppRoute(
                packageName = "com.example.target",
                action = "com.xiaomi.mipush.miui.RECEIVE_MESSAGE",
                startService = false,
            ),
            MiuiPushMessageReceiver.appRouteFor(message),
        )

        message.isNotified = true
        assertEquals(
            "com.xiaomi.mipush.miui.CLICK_MESSAGE",
            MiuiPushMessageReceiver.appRouteFor(message)?.action,
        )
        assertTrue(MiuiPushMessageReceiver.appRouteFor(message)?.startService == true)
    }

    @Test
    fun `route type two forwards stock inner push action`() {
        val message = MiPushMessage().apply {
            messageId = "message-id"
            passThrough = 1
            extra = mapOf("route_type" to "2")
        }

        MiuiPushMessageReceiver().onReceivePassThroughMessage(context, message)

        val broadcast = shadowOf(context).broadcastIntents.single()
        assertEquals("com.xiaomi.xmsf.inner.PUSH_MESSAGE", broadcast.action)
        assertEquals(context.packageName, broadcast.`package`)
        assertEquals("message-id", broadcast.getStringExtra("messageId"))
    }

    @Test
    fun `task parser recognizes stock online config and log fetch commands`() {
        assertEquals(
            MiuiPushMessageReceiver.TaskCommand.ONLINE_CONFIG_REFRESH,
            MiuiPushMessageReceiver.taskCommandFor("""{"CMD":"cloud_control_update"}"""),
        )
        assertEquals(
            MiuiPushMessageReceiver.TaskCommand.LOG_FETCH,
            MiuiPushMessageReceiver.taskCommandFor("""{"CMD":"log_fetch"}"""),
        )
        assertEquals(
            MiuiPushMessageReceiver.TaskCommand.UNKNOWN,
            MiuiPushMessageReceiver.taskCommandFor("not-json"),
        )
        listOf(
            "[]",
            "{\"CMD\":null}",
            "{\"CMD\":{}}",
            "{\"CMD\":[]}",
            "{\"CMD\":123}",
        ).forEach { content ->
            assertEquals(
                MiuiPushMessageReceiver.TaskCommand.UNKNOWN,
                MiuiPushMessageReceiver.taskCommandFor(content),
                content,
            )
        }
    }

    @Test
    fun `successful register result applies stock auto mark when badge setting allows`() {
        ShadowContentResolver.registerProviderInternal(
            "statusbar.notification",
            BadgeSettingsProvider(canShowBadge = true),
        )
        val message = MiPushCommandMessage().apply {
            resultCode = 0L
            autoMarkPkgs = listOf("com.example.target")
        }

        MiuiPushMessageReceiver().onReceiveRegisterResult(context, message)

        assertEquals(
            1,
            Settings.Global.getInt(
                context.contentResolver,
                "com.example.target.superscript_count",
                0,
            ),
        )
    }

    @Test
    fun `registered package and denied badge setting do not apply auto mark`() {
        ShadowContentResolver.registerProviderInternal(
            "statusbar.notification",
            BadgeSettingsProvider(canShowBadge = false),
        )
        context.getSharedPreferences("pref_registered_pkg_names", Context.MODE_PRIVATE)
            .edit()
            .putString("com.example.registered", "app-id")
            .commit()
        val message = MiPushCommandMessage().apply {
            resultCode = 0L
            autoMarkPkgs = listOf("com.example.registered", "com.example.denied")
        }

        MiuiPushMessageReceiver().onReceiveRegisterResult(context, message)

        assertEquals(
            0,
            Settings.Global.getInt(
                context.contentResolver,
                "com.example.registered.superscript_count",
                0,
            ),
        )
        assertEquals(
            0,
            Settings.Global.getInt(
                context.contentResolver,
                "com.example.denied.superscript_count",
                0,
            ),
        )
    }

    @Test
    fun `stock ping receiver stops legacy alarm on action mismatch`() {
        val alarm = RecordingAlarm()
        Alarm.setIAlarm(alarm)

        PingReceiver().onReceive(context, Intent("com.example.OLD_TIMER"))

        assertTrue(alarm.stopped)
        assertFalse(alarm.registered)
    }

    private class RecordingAlarm : Alarm.IAlarm {
        var stopped = false
        var registered = false

        override fun isAlive(): Boolean = true

        override fun registerPing(force: Boolean) {
            registered = true
        }

        override fun stop() {
            stopped = true
        }
    }

    private class BadgeSettingsProvider(
        private val canShowBadge: Boolean,
    ) : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
            return if (method == "canShowBadge") {
                Bundle().apply { putBoolean("canShowBadge", canShowBadge) }
            } else {
                null
            }
        }

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? = null

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0
    }
}
