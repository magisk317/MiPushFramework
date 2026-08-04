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
import io.github.magisk317.mipush.common.notification.iconpack.IconPackData
import io.github.magisk317.mipush.common.notification.iconpack.ICON_PACK_SOURCE_IDENTITY_EXTRA
import io.github.magisk317.mipush.common.notification.iconpack.IconPackProtocolAdapter
import io.github.magisk317.mipush.common.notification.iconpack.IconPackResolver
import io.github.magisk317.mipush.common.notification.iconpack.IconPackQuery
import io.github.magisk317.mipush.common.notification.iconpack.ProtocolCompatibility
import io.github.magisk317.mipush.common.notification.iconpack.ProtocolFailureReason
import io.github.magisk317.mipush.common.notification.iconpack.ProtocolResult
import io.github.magisk317.mipush.common.notification.iconpack.ProtocolState
import io.github.magisk317.mipush.common.notification.iconpack.ProtocolVersion
import io.github.magisk317.mipush.common.notification.iconpack.ResolveResult
import io.github.magisk317.mipush.common.notification.iconpack.InMemoryIconPackResultCache
import io.github.magisk317.mipush.common.notification.iconpack.NotificationBitmapScaler
import io.github.magisk317.mipush.common.notification.iconpack.thirdPartyPackSourceIdentity
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Task 10 integration seam: protocol -> resolver -> MiPush Notification.smallIcon.
 * The adapters below are in-memory audited seams only; no provider, IPC or private storage is used.
 *
 * **Validates: Requirements 2.1, 2.6, 2.7, 2.8, 3.2, 3.3, 3.7**
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class NotificationIconPackIntegrationTest {
    private val targetPackage = "com.example.target"

    @Test
    fun `color and monochrome small legal bitmap keep one source identity and preserve fields`() {
        val context = RuntimeEnvironment.getApplication()
        val contentIntent = PendingIntent.getBroadcast(
            context,
            901,
            Intent("icon-pack-integration-click"),
            PendingIntent.FLAG_IMMUTABLE,
        )

        listOf(true, false).forEach { colorStatusBarIcon ->
            val original = Bitmap.createBitmap(12, 24, Bitmap.Config.ARGB_8888)
            val scaled = Bitmap.createBitmap(24, 48, Bitmap.Config.ARGB_8888)
            val builder = NotificationCompat.Builder(context, "integration-$colorStatusBarIcon")
                .setSmallIcon(CommonR.drawable.ic_notifications_black_24dp)
                .setContentTitle("title")
                .setContentText("body")
                .setContentIntent(contentIntent)
                .setGroup("integration-group")
                .setWhen(9876L)
                .setOngoing(true)
                .setAutoCancel(false)
                .setColor(Color.BLUE)
            val result = NotificationController.applyIconPackSmallIcon(
                context = context,
                targetPackage = targetPackage,
                notificationBuilder = builder,
                colorStatusBarIcon = colorStatusBarIcon,
                resolver = resolverFor(
                    targetPackage = targetPackage,
                    bitmap = original,
                    iconColor = if (colorStatusBarIcon) Color.RED else null,
                    scaler = NotificationBitmapScaler { scaled },
                ),
            )

            val available = assertInstanceOf(ResolveResult.Available::class.java, result)
            val published = builder.build()
            assertEquals(Icon.TYPE_BITMAP, published.smallIcon.type)
            val publishedBitmap =
                (published.smallIcon.loadDrawable(context) as BitmapDrawable).bitmap
            assertSame(available.value.bitmap, publishedBitmap)
            assertEquals(thirdPartyPackSourceIdentity(targetPackage), available.value.sourceIdentity)
            assertEquals(
                available.value.sourceIdentity,
                published.extras.getString(ICON_PACK_SOURCE_IDENTITY_EXTRA),
            )
            assertEquals("title", published.extras.getCharSequence(Notification.EXTRA_TITLE))
            assertEquals("body", published.extras.getCharSequence(Notification.EXTRA_TEXT))
            assertEquals(contentIntent, published.contentIntent)
            assertEquals("integration-group", published.group)
            assertEquals(9876L, published.`when`)
            assertTrue(published.flags and Notification.FLAG_ONGOING_EVENT != 0)
            assertEquals(0, published.flags and Notification.FLAG_AUTO_CANCEL)
            assertEquals(
                if (colorStatusBarIcon) Color.RED else Color.BLUE,
                published.color,
            )
        }
    }

    @Test
    fun `blocked permission version mismatch empty exception and invalid data keep publish input intact`() {
        val context = RuntimeEnvironment.getApplication()
        val contentIntent = PendingIntent.getBroadcast(
            context,
            902,
            Intent("icon-pack-fallback-click"),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val cases: List<(IconPackQuery) -> ProtocolResult> = listOf(
            { query -> ProtocolResult.blocked(query, ProtocolFailureReason.PERMISSION_DENIED) },
            { query -> ProtocolResult.blocked(query, ProtocolFailureReason.UNSUPPORTED_VERSION) },
            { query -> ProtocolResult(
                state = ProtocolState.EMPTY,
                targetPackage = query.targetPackage,
                userId = query.userId,
                iconData = null,
                protocolVersion = null,
                sourceIdentity = "audited-empty-seam",
                caller = query.caller,
                permission = query.permission,
                timeout = query.timeout,
                compatibility = query.compatibility,
                failureReason = ProtocolFailureReason.EMPTY_RESPONSE,
            ) },
            { error("audited seam exception") },
            { query -> available(query, IconPackData("com.example.other", Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888))) },
            { query -> available(query, IconPackData(query.targetPackage, null)) },
            { query -> available(query, IconPackData(query.targetPackage, invalidBitmap(4097, 24))) },
            { query -> available(query, IconPackData(query.targetPackage, Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)), resultUserId = query.userId + 1) },
        )

        cases.forEachIndexed { index, response ->
            val builder = NotificationCompat.Builder(context, "fallback-$index")
                .setSmallIcon(CommonR.drawable.ic_notifications_black_24dp)
                .setContentTitle("unchanged title")
                .setContentText("unchanged body")
                .setContentIntent(contentIntent)
                .setGroup("unchanged-group")
                .setWhen(12345L)
                .setOngoing(true)
                .setAutoCancel(false)
            val result = NotificationController.applyIconPackSmallIcon(
                context = context,
                targetPackage = targetPackage,
                notificationBuilder = builder,
                colorStatusBarIcon = true,
                resolver = resolverFor(targetPackage, response = response),
            )
            val unavailable = assertInstanceOf(ResolveResult.Unavailable::class.java, result)
            assertNotNull(unavailable)
            val published = builder.build()
            assertEquals(CommonR.drawable.ic_notifications_black_24dp, published.smallIcon.resId)
            assertNull(published.extras.getString(ICON_PACK_SOURCE_IDENTITY_EXTRA))
            assertEquals("unchanged title", published.extras.getCharSequence(Notification.EXTRA_TITLE))
            assertEquals("unchanged body", published.extras.getCharSequence(Notification.EXTRA_TEXT))
            assertEquals(contentIntent, published.contentIntent)
            assertEquals("unchanged-group", published.group)
            assertEquals(12345L, published.`when`)
            assertTrue(published.flags and Notification.FLAG_ONGOING_EVENT != 0)
            assertEquals(0, published.flags and Notification.FLAG_AUTO_CANCEL)
        }
    }

    @Test
    fun `interleaved users and packages never reuse another successful bitmap`() {
        val context = RuntimeEnvironment.getApplication()
        val cache = InMemoryIconPackResultCache()
        val bitmaps = mutableMapOf<String, Bitmap>()
        val resolver = IconPackResolver(
            cache = cache,
            adapter = object : IconPackProtocolAdapter {
                override fun query(query: IconPackQuery): ProtocolResult {
                    val key = "${query.userId}:${query.targetPackage}"
                    val bitmap = bitmaps.getOrPut(key) {
                        Bitmap.createBitmap(24 + query.userId, 24, Bitmap.Config.ARGB_8888)
                    }
                    return available(query, IconPackData(query.targetPackage, bitmap, revisionToken = key))
                }
            },
        )
        val order = listOf(
            0 to "com.example.alpha",
            1 to "com.example.beta",
            1 to "com.example.alpha",
            0 to "com.example.beta",
            0 to "com.example.alpha",
            1 to "com.example.beta",
        )
        order.forEach { (userId, packageName) ->
            val result = resolver.resolve(packageName, userId, context)
            val available = assertInstanceOf(ResolveResult.Available::class.java, result)
            assertSame(bitmaps["$userId:$packageName"], available.value.bitmap)
            assertEquals(packageName, available.value.targetPackage)
            assertEquals(userId, available.value.userId)
        }
        assertEquals(4, cache.size())
    }

    private fun resolverFor(
        targetPackage: String,
        bitmap: Bitmap? = null,
        iconColor: Int? = null,
        scaler: NotificationBitmapScaler = NotificationBitmapScaler { it },
        response: ((IconPackQuery) -> ProtocolResult)? = null,
    ): IconPackResolver = IconPackResolver(
        adapter = object : IconPackProtocolAdapter {
            override fun query(query: IconPackQuery): ProtocolResult = response?.invoke(query)
                ?: available(query, IconPackData(targetPackage, bitmap, iconColor))
        },
        bitmapScaler = scaler,
    )

    private fun available(
        query: IconPackQuery,
        data: IconPackData,
        resultUserId: Int = query.userId,
    ): ProtocolResult {
        val version = ProtocolVersion("icon-pack/1")
        return ProtocolResult(
            state = ProtocolState.AVAILABLE,
            targetPackage = query.targetPackage,
            userId = resultUserId,
            iconData = data,
            protocolVersion = version,
            sourceIdentity = "audited-in-memory-seam",
            caller = query.caller,
            permission = query.permission.copy(granted = true),
            timeout = query.timeout,
            compatibility = ProtocolCompatibility(query.compatibility.requested, true, version),
            failureReason = null,
        )
    }

    private fun invalidBitmap(width: Int, height: Int): Bitmap {
        val bitmap = mockk<Bitmap>()
        every { bitmap.width } returns width
        every { bitmap.height } returns height
        every { bitmap.isRecycled } returns false
        return bitmap
    }
}
