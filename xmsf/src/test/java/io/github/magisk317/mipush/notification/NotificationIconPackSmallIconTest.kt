package io.github.magisk317.mipush.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import androidx.core.app.NotificationCompat
import io.github.magisk317.mipush.common.R as CommonR
import io.github.magisk317.mipush.common.notification.iconpack.ICON_PACK_SOURCE_IDENTITY_EXTRA
import io.github.magisk317.mipush.common.notification.iconpack.IconPackData
import io.github.magisk317.mipush.common.notification.iconpack.IconPackProtocolAdapter
import io.github.magisk317.mipush.common.notification.iconpack.IconPackResolver
import io.github.magisk317.mipush.common.notification.iconpack.IconPackQuery
import io.github.magisk317.mipush.common.notification.iconpack.ProtocolCompatibility
import io.github.magisk317.mipush.common.notification.iconpack.ProtocolFailureReason
import io.github.magisk317.mipush.common.notification.iconpack.ProtocolPermissionAudit
import io.github.magisk317.mipush.common.notification.iconpack.ProtocolResult
import io.github.magisk317.mipush.common.notification.iconpack.ProtocolState
import io.github.magisk317.mipush.common.notification.iconpack.ProtocolVersion
import io.github.magisk317.mipush.common.notification.iconpack.ResolveFailure
import io.github.magisk317.mipush.common.notification.iconpack.ResolveResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Focused coverage for the final MiPush builder seam. The test adapter is in-memory only and does
 * not imply a provider, Binder/AIDL endpoint, or private-storage read.
 *
 * **Validates: Requirements 2.1, 2.4, 2.5, 2.6, 2.8, 3.2, 3.3, 3.7**
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class NotificationIconPackSmallIconTest {
    @Test
    fun `available pack bitmap becomes framework small icon and preserves builder fields`() {
        val context = RuntimeEnvironment.getApplication()
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val contentIntent = PendingIntent.getBroadcast(
            context,
            10,
            Intent("icon-pack-content"),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val actionIntent = PendingIntent.getBroadcast(
            context,
            11,
            Intent("icon-pack-action"),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, "icon-pack-test")
            .setSmallIcon(CommonR.drawable.ic_notifications_black_24dp)
            .setContentTitle("title")
            .setContentText("body")
            .setContentIntent(contentIntent)
            .addAction(CommonR.drawable.ic_notifications_black_24dp, "reply", actionIntent)
            .setGroup("existing-group")
            .setWhen(1234L)
            .setOngoing(true)
            .setAutoCancel(false)
            .setColor(Color.BLUE)
        val result = NotificationController.applyIconPackSmallIcon(
            context = context,
            targetPackage = "com.example.target",
            notificationBuilder = builder,
            colorStatusBarIcon = true,
            resolver = resolverFor(
                targetPackage = "com.example.target",
                bitmap = bitmap,
                iconColor = null,
            ),
        )

        assertTrue(result is ResolveResult.Available)
        val notification = builder.build()
        assertEquals(Icon.TYPE_BITMAP, notification.smallIcon.type)
        assertEquals(bitmap, (notification.smallIcon.loadDrawable(context) as BitmapDrawable).bitmap)
        assertEquals(
            "THIRD_PARTY_PACK(com.example.target)",
            notification.extras.getString(ICON_PACK_SOURCE_IDENTITY_EXTRA),
        )
        assertEquals("title", notification.extras.getCharSequence(Notification.EXTRA_TITLE))
        assertEquals("body", notification.extras.getCharSequence(Notification.EXTRA_TEXT))
        assertEquals("existing-group", notification.group)
        assertEquals("icon-pack-test", notification.channelId)
        assertEquals(contentIntent, notification.contentIntent)
        assertEquals(1, notification.actions.size)
        assertEquals("reply", notification.actions[0].title)
        assertEquals(actionIntent, notification.actions[0].actionIntent)
        assertEquals(1234L, notification.`when`)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(0, notification.flags and Notification.FLAG_AUTO_CANCEL)
        // Missing iconColor preserves the existing color metadata and never invalidates the bitmap.
        assertEquals(Color.BLUE, notification.color)
    }

    @Test
    fun `available icon color maps only to existing color metadata in color mode`() {
        val context = RuntimeEnvironment.getApplication()
        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        val builder = NotificationCompat.Builder(context, "icon-pack-color-test")
            .setSmallIcon(CommonR.drawable.ic_notifications_black_24dp)
            .setColor(Color.BLUE)

        NotificationController.applyIconPackSmallIcon(
            context = context,
            targetPackage = "com.example.target",
            notificationBuilder = builder,
            colorStatusBarIcon = true,
            resolver = resolverFor("com.example.target", bitmap, Color.RED),
        )

        val notification = builder.build()
        assertEquals(Icon.TYPE_BITMAP, notification.smallIcon.type)
        assertEquals(bitmap, (notification.smallIcon.loadDrawable(context) as BitmapDrawable).bitmap)
        assertEquals(Color.RED, notification.color)
    }

    @Test
    fun `blocked and resolver errors retain fallback icon without throwing`() {
        val context = RuntimeEnvironment.getApplication()
        val blockedBuilder = NotificationCompat.Builder(context, "blocked-test")
            .setSmallIcon(CommonR.drawable.ic_notifications_black_24dp)
        val blocked = NotificationController.applyIconPackSmallIcon(
            context = context,
            targetPackage = "com.example.target",
            notificationBuilder = blockedBuilder,
            colorStatusBarIcon = true,
            resolver = IconPackResolver(),
        )
        assertEquals(ResolveResult.Unavailable(ResolveFailure.BLOCKED, ProtocolFailureReason.ADAPTER_NOT_REGISTERED), blocked)
        assertEquals(CommonR.drawable.ic_notifications_black_24dp, blockedBuilder.build().smallIcon.resId)

        val errorBuilder = NotificationCompat.Builder(context, "error-test")
            .setSmallIcon(CommonR.drawable.ic_notifications_black_24dp)
        val errorResult = NotificationController.applyIconPackSmallIcon(
            context = context,
            targetPackage = "com.example.target",
            notificationBuilder = errorBuilder,
            colorStatusBarIcon = true,
            resolver = IconPackResolver(
                adapter = object : IconPackProtocolAdapter {
                    override fun query(query: IconPackQuery): ProtocolResult {
                        error("transport failure")
                    }
                },
            ),
        )
        assertNotNull(errorResult)
        assertInstanceOf(ResolveResult.Unavailable::class.java, errorResult)
        assertEquals(CommonR.drawable.ic_notifications_black_24dp, errorBuilder.build().smallIcon.resId)
    }

    @Test
    fun `available bitmap is still selected when color metadata is absent in monochrome mode`() {
        val context = RuntimeEnvironment.getApplication()
        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        val builder = NotificationCompat.Builder(context, "monochrome-test")
            .setSmallIcon(CommonR.drawable.ic_notifications_black_24dp)
            .setColor(Color.BLUE)

        NotificationController.applyIconPackSmallIcon(
            context = context,
            targetPackage = "com.example.target",
            notificationBuilder = builder,
            colorStatusBarIcon = false,
            resolver = resolverFor("com.example.target", bitmap, null),
        )

        val notification = builder.build()
        assertEquals(Icon.TYPE_BITMAP, notification.smallIcon.type)
        assertEquals(bitmap, (notification.smallIcon.loadDrawable(context) as BitmapDrawable).bitmap)
        assertEquals(Color.BLUE, notification.color)
    }

    private fun resolverFor(
        targetPackage: String,
        bitmap: Bitmap,
        iconColor: Int?,
    ): IconPackResolver = IconPackResolver(
        adapter = object : IconPackProtocolAdapter {
            override fun query(query: IconPackQuery): ProtocolResult {
                val version = ProtocolVersion("icon-pack/1")
                return ProtocolResult(
                    state = ProtocolState.AVAILABLE,
                    targetPackage = targetPackage,
                    userId = query.userId,
                    iconData = IconPackData(targetPackage, bitmap, iconColor),
                    protocolVersion = version,
                    sourceIdentity = "test-protocol",
                    caller = query.caller,
                    permission = query.permission.copy(granted = true),
                    timeout = query.timeout,
                    compatibility = ProtocolCompatibility(
                        requested = query.compatibility.requested,
                        compatible = true,
                        negotiated = version,
                    ),
                    failureReason = null,
                )
            }
        },
        bitmapScaler = io.github.magisk317.mipush.common.notification.iconpack.NotificationBitmapScaler { it },
    )
}
