package com.xiaomi.xmsf.stock

import android.app.Application
import android.content.ContentProvider
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.os.Bundle
import com.xiaomi.push.provider.PushSupportProvider
import com.xiaomi.push.service.MIPushHelper
import com.xiaomi.xmsf.provider.ChannelProvider
import com.xiaomi.xmsf.provider.PushProfileIdProvider
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionSendMessage
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.push.pipeline.MiPushRuntimeBridge
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.runtime.store.db.EventDb
import io.github.magisk317.mipush.runtime.store.entities.Event
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBinder
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
class StockProviderIngressTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @BeforeEach
    fun resetPreferences() {
        listOf("mipush_profile_id", "pref_registered_pkg_names", "stock_surface").forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        Utils.setApplicationContext(context)
        PushRuntime.clearStateForTests()
    }

    @AfterEach
    fun resetBinderIdentity() {
        ShadowBinder.reset()
    }

    @Test
    fun `channel provider transport fails closed without a framework verified caller`() {
        val provider = Robolectric.setupContentProvider(ChannelProvider::class.java)

        val result = transportCall(provider, "com.miui.systemAdSolution", "unknown", Bundle())

        assertEquals(4, result.getInt(StockSurfaceSupport.KEY_CODE))
    }

    @Test
    fun `profile provider transport binds state to Binder uid package`() {
        val callerUid = 42_424
        val callerPackage = "com.example.profile.owner"
        shadowOf(context.packageManager).setNameForUid(callerUid, callerPackage)
        context.getSharedPreferences("pref_registered_pkg_names", Context.MODE_PRIVATE)
            .edit()
            .putString(callerPackage, "registered")
            .commit()
        ShadowBinder.setCallingUid(callerUid)
        val provider = Robolectric.setupContentProvider(PushProfileIdProvider::class.java)

        val added = transportCall(
            provider,
            "com.example.ignored.transport.name",
            "addProfileId",
            Bundle().apply {
                putString("profileId", "owned-profile")
                putString("packageName", "com.example.victim")
            },
        )
        val queried = transportCall(provider, "com.example.ignored.transport.name", "queryProfileIds", Bundle())

        assertEquals("0", added.getString(StockSurfaceSupport.KEY_CODE))
        assertEquals(listOf("owned-profile"), queried.getStringArrayList("allProfileIds"))
        assertEquals(listOf("owned-profile"), StockProfileIdStore.read(context, callerPackage))
        assertTrue(StockProfileIdStore.read(context, "com.example.victim").isEmpty())
    }

    @Test
    fun `push support provider transport retains stock nested data shape`() {
        val provider = Robolectric.setupContentProvider(PushSupportProvider::class.java)

        val set = transportCall(
            provider,
            "com.example.caller",
            "setPrivacyStatus",
            Bundle().apply { putBoolean("agreedPrivacyPolicy", true) },
        )
        val get = transportCall(provider, "com.example.caller", "getPrivacyStatus", null)

        assertEquals(0, set.getInt(StockSurfaceSupport.KEY_CODE))
        assertTrue(requireNotNull(get.getBundle(StockSurfaceSupport.KEY_DATA)).getBoolean("privacyStatus"))
        assertNull(get.getBundle("result"))
    }

    @Test
    fun `payload ingress persists event exposed by push support provider transport`() {
        val targetPackage = "com.example.box.owner"
        val messageId = "box-ingress-${System.nanoTime()}"
        installBridgeActivity(targetPackage)
        val provider = Robolectric.setupContentProvider(PushSupportProvider::class.java)
        val privacyResult = transportCall(
            provider,
            "com.example.box.client",
            "setPrivacyStatus",
            Bundle().apply { putBoolean("agreedPrivacyPolicy", true) },
        )
        assertEquals(0, privacyResult.getInt(StockSurfaceSupport.KEY_CODE))

        val container = MIPushHelper.generateRequestContainer(
            targetPackage,
            "app-id",
            XmPushActionSendMessage().apply {
                setId("body-id")
                setAppId("app-id")
                setPackageName(targetPackage)
            },
            ActionType.SendMessage,
        ).apply {
            metaInfo = PushMetaInfo().apply {
                id = messageId
                title = "Ingress title"
                description = "Ingress content"
                passThrough = 0
                putToExtra("allow_box", "true")
                putToExtra("channel_type", "news")
                putToExtra("channel_id", "news")
            }
        }
        val payload = XmPushThriftSerializeUtils.convertThriftObjectToBytes(container)

        assertTrue(
            MiPushRuntimeBridge.onPayloadFromServer(
                context,
                payload,
                payload.size.toLong(),
                "StockProviderIngressTest",
            ),
        )
        val storedEvent = runBlocking {
            EventDb.queryByIdAsync(
                lastId = null,
                size = Int.MAX_VALUE,
                types = setOf(Event.Type.SendMessage),
                pkg = targetPackage,
                text = null,
            ).firstOrNull { it.container?.metaInfo?.id == messageId }
        }
        assertNotNull(storedEvent)

        val result = transportCall(
            provider,
            "com.example.box.client",
            "getPushMsgs",
            Bundle().apply { putInt("msgCount", 100) },
        )
        assertEquals(0, result.getInt(StockSurfaceSupport.KEY_CODE))
        @Suppress("DEPRECATION")
        val messages = requireNotNull(result.getBundle(StockSurfaceSupport.KEY_DATA))
            .getParcelableArrayList<Bundle>("data")
            .orEmpty()
        val projected = messages.firstOrNull { it.getString("msgId") == messageId }
        assertNotNull(projected)
        requireNotNull(projected)
        assertEquals(targetPackage, projected.getString("packageName"))
        assertEquals("news", projected.getString("channelTypeId"))
        assertEquals("Ingress title", projected.getString("title"))
        assertEquals("Ingress content", projected.getString("content"))
        assertTrue(requireNotNull(projected.getByteArray("intentData")).isNotEmpty())
    }

    private fun installBridgeActivity(packageName: String) {
        shadowOf(context.packageManager).installPackage(
            PackageInfo().apply {
                this.packageName = packageName
                activities = arrayOf(ActivityInfo().apply {
                    this.packageName = packageName
                    name = "com.xiaomi.mipush.sdk.BridgeActivity"
                    enabled = true
                    exported = true
                })
            },
        )
    }

    private fun transportCall(
        provider: ContentProvider,
        callingPackage: String,
        method: String,
        extras: Bundle?,
    ): Bundle {
        val transport = ContentProvider::class.java
            .getDeclaredMethod("getIContentProvider")
            .apply { isAccessible = true }
            .invoke(provider)
        val call = transport.javaClass.methods.first { candidate ->
            candidate.name == "call" && candidate.parameterTypes.size == 4
        }
        return requireNotNull(call.invoke(transport, callingPackage, method, null, extras) as? Bundle)
    }
}
