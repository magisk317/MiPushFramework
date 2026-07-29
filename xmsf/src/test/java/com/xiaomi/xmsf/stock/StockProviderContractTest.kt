package com.xiaomi.xmsf.stock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.os.Bundle
import android.os.Parcel
import com.xiaomi.push.service.MIPushHelper
import com.xiaomi.push.service.MIPushEventProcessor
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionSendMessage
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import io.github.magisk317.mipush.runtime.store.db.EventDb
import io.github.magisk317.mipush.runtime.store.entities.Event
import io.github.magisk317.mipush.push.pipeline.MiPushRuntimeBridge
import io.github.magisk317.mipush.push.pipeline.StalePackagePushGuard
import io.github.magisk317.mipush.notification.NotificationController
import io.github.magisk317.mipush.bridge.MiPushRuntimeObserverBridge
import kotlinx.coroutines.runBlocking
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
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class StockProviderContractTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @BeforeEach
    fun resetPreferences() {
        listOf("mipush_profile_id", "pref_registered_pkg_names", "stock_surface").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test
    fun `push control uses stock ConfigKey ids`() {
        assertEquals(listOf(61, 57, 58, 56), StockSurfaceSupport.pushControlConfigKeys())
    }

    @Test
    fun `notification metadata maps stock HyperOS controls and collection fields`() {
        val metaInfo = PushMetaInfo().apply {
            id = "s123456789012345678901"
            putToExtra("hyper_nms_skip_assistants", "true")
            putToExtra("hyper_skip_group_opt", "false")
            putToExtra("use_clicked_activity", "1")
            putToExtra("high_priority_event", "high")
            putToExtra("msg_busi_type", "business")
            putToExtra("simplify_pull_type", "simple")
            putToExtra("message_count", "7")
            putToExtra("miui.showAtTail", "true")
            putToExtra("miui.fold.timeout", "45")
            putToExtra("enable_keyguard", "true")
            putToExtra("enable_float", "false")
            putToExtra("section_is_prr", "1")
            putToExtra("section_prr_cl", "3")
            putToExtra("disable_notification_flags", "5")
        }
        val extras = Bundle()

        StockNotificationMetadataBridge.apply(metaInfo, extras, isMiui = true)

        assertEquals(metaInfo.id, extras.getString("message_id"))
        assertTrue(extras.getBoolean("skip_assistants"))
        assertTrue(extras.containsKey("miui_skip_group_opt"))
        assertFalse(extras.getBoolean("miui_skip_group_opt"))
        assertEquals("1", extras.getString("xmsf.stat.useNCA"))
        assertEquals("high", extras.getString("xmsf.stat.highPriorityEvent"))
        assertEquals("business", extras.getString("xmsf.stat.msgBusiType"))
        assertEquals("simple", extras.getString("xmsf.stat.sPullType"))
        assertEquals(7, extras.getInt("miui.messageCount"))
        assertTrue(extras.getBoolean("miui.showAtTail"))
        assertEquals(45_000L, extras.getLong("miui.fold.timeout"))
        assertTrue(extras.getBoolean("miui.enableKeyguard"))
        assertTrue(extras.containsKey("miui.enableFloat"))
        assertFalse(extras.getBoolean("miui.enableFloat"))
        assertEquals(1, extras.getInt("is_priority"))
        assertEquals(3, extras.getInt("mipush_class"))
        assertEquals("5", extras.getString("disable_notification_flags"))
    }

    @Test
    fun `notification metadata ignores malformed numeric controls without reporting`() {
        val metaInfo = PushMetaInfo().apply {
            putToExtra("message_count", "many")
            putToExtra("miui.fold.timeout", "never")
            putToExtra("section_is_prr", "priority")
            putToExtra("section_prr_cl", "3")
            putToExtra("enable_keyguard", "invalid")
        }
        val extras = Bundle()

        StockNotificationMetadataBridge.apply(metaInfo, extras, isMiui = true)

        assertFalse(extras.containsKey("miui.messageCount"))
        assertFalse(extras.containsKey("miui.fold.timeout"))
        assertFalse(extras.containsKey("is_priority"))
        assertFalse(extras.containsKey("mipush_class"))
        assertTrue(extras.containsKey("miui.enableKeyguard"))
        assertFalse(extras.getBoolean("miui.enableKeyguard"))
    }

    @Test
    fun `notification metadata omits MIUI-only policy fields off MIUI`() {
        val metaInfo = PushMetaInfo().apply {
            putToExtra("message_count", "7")
            putToExtra("miui.showAtTail", "true")
            putToExtra("disable_notification_flags", "5")
            putToExtra("miui.fold.timeout", "45")
            putToExtra("enable_float", "true")
        }
        val extras = Bundle()

        StockNotificationMetadataBridge.apply(metaInfo, extras, isMiui = false)

        assertFalse(extras.containsKey("miui.messageCount"))
        assertFalse(extras.containsKey("miui.showAtTail"))
        assertFalse(extras.containsKey("disable_notification_flags"))
        assertEquals(45_000L, extras.getLong("miui.fold.timeout"))
        assertTrue(extras.getBoolean("miui.enableFloat"))
    }

    @Test
    fun `profile calls use stock methods string codes and oldest eviction`() {
        register("com.example.owner")

        repeat(11) { index ->
            val result = profileCall("addProfileId", "profile-$index")
            assertEquals("0", result.getString("code"))
        }

        val queried = profileCall("queryProfileIds", null)
        assertEquals("0", queried.getString("code"))
        assertEquals((1..10).map { "profile-$it" }, queried.getStringArrayList("allProfileIds"))
        assertNull(queried.getStringArrayList("profileIds"))

        assertEquals("7", profileCall("addProfileId", "bad;#;id").getString("code"))
        assertEquals("8", profileCall("addProfileId", "x".repeat(65)).getString("code"))
        assertEquals("9", profileCall("inventedMethod", null).getString("code"))

        assertEquals("0", profileCall("deleteProfileId", "profile-5").getString("code"))
        assertFalse(profileCall("queryProfileIds", null).getStringArrayList("allProfileIds")!!.contains("profile-5"))
        assertEquals("0", profileCall("deleteAllProfileId", null).getString("code"))
        assertTrue(profileCall("queryProfileIds", null).getStringArrayList("allProfileIds")!!.isEmpty())
    }

    @Test
    fun `profile caller must be registered and packageName extra cannot redirect ownership`() {
        val extras = Bundle().apply {
            putString("profileId", "owned")
            putString("packageName", "com.example.victim")
        }
        assertEquals(
            "3",
            StockProfileIdStore.handle(context, "addProfileId", extras, "com.example.owner").getString("code"),
        )

        register("com.example.owner")
        assertEquals(
            "0",
            StockProfileIdStore.handle(context, "addProfileId", extras, "com.example.owner").getString("code"),
        )
        assertEquals(listOf("owned"), StockProfileIdStore.read(context, "com.example.owner"))
        assertTrue(StockProfileIdStore.read(context, "com.example.victim").isEmpty())
    }

    @Test
    fun `profile ids clear on package absence and package data clear only`() {
        StockSurfaceSupport.ensureInitialized(context)
        val removedPackage = "com.example.profile.removed"
        val dataClearedPackage = "com.example.profile.cleared"
        context.getSharedPreferences("mipush_profile_id", Context.MODE_PRIVATE).edit()
            .putString(removedPackage, "removed-profile")
            .putString(dataClearedPackage, "cleared-profile")
            .commit()

        StalePackagePushGuard.onPackageRemoved(context, removedPackage, "StockProviderContractTest")
        assertTrue(StockProfileIdStore.read(context, removedPackage).isEmpty())
        assertEquals(listOf("cleared-profile"), StockProfileIdStore.read(context, dataClearedPackage))

        MiPushRuntimeObserverBridge(context).onPackageDataCleared(dataClearedPackage)
        assertTrue(StockProfileIdStore.read(context, dataClearedPackage).isEmpty())
    }

    @Test
    fun `profile filter applies only to stock display messages`() {
        register("com.example.owner")
        profileCall("addProfileId", "accepted")
        val container = displayContainer("rejected")

        assertTrue(MIPushEventProcessor.shouldCheckProfile(container))
        assertFalse(StockSurfaceSupport.isProfileAllowed(context, container))

        container.metaInfo.putToExtra("profileId", "accepted")
        assertTrue(StockSurfaceSupport.isProfileAllowed(context, container))

        container.metaInfo.passThrough = 1
        container.metaInfo.putToExtra("profileId", "rejected")
        assertFalse(MIPushEventProcessor.shouldCheckProfile(container))
        assertFalse(StockSurfaceSupport.isProfileAllowed(context, container))
    }

    @Test
    fun `box pagination follows receivedTime then string msgId cursor`() {
        val records = listOf(
            record("b", 200L),
            record("a", 200L),
            record("z", 100L),
        )

        val first = StockPushSupport.paginate(records, 2, null, -1L)
        assertEquals(listOf("b", "a"), first.records.map { it.messageId })
        assertTrue(first.hasMore)

        val second = StockPushSupport.paginate(records, 2, "a", 200L)
        assertEquals(listOf("z"), second.records.map { it.messageId })
        assertFalse(second.hasMore)
    }

    @Test
    fun `box status and privacy use stock nested data field names`() {
        StockSurfaceSupport.ensureInitialized(context)
        val setStatus = StockPushSupport.handle(
            context,
            null,
            "setAppPushStatus",
            Bundle().apply {
                putString("packageName", "com.example.owner")
                putParcelableArrayList(
                    "channelTypeIds",
                    arrayListOf(Bundle().apply {
                        putString("channelTypeId", "news")
                        putBoolean("enabled", false)
                    }),
                )
            },
        )
        assertEquals(0, setStatus.getInt("code"))

        val status = StockPushSupport.handle(
            context,
            null,
            "getAppPushStatusByPkg",
            Bundle().apply { putString("packageName", "com.example.owner") },
        )
        @Suppress("DEPRECATION")
        val statuses = status.getBundle("data")!!.getParcelableArrayList<Bundle>("data")!!
        assertEquals("news", statuses.single().getString("channelTypeId"))
        assertFalse(statuses.single().getBoolean("enabled"))
        assertNull(status.getBundle("result"))

        StockPushSupport.handle(
            context,
            null,
            "setPrivacyStatus",
            Bundle().apply { putBoolean("agreedPrivacyPolicy", true) },
        )
        val privacy = StockPushSupport.handle(context, null, "getPrivacyStatus", null)
        assertTrue(privacy.getBundle("data")!!.getBoolean("privacyStatus"))
        assertFalse(privacy.getBundle("data")!!.containsKey("agreedPrivacyPolicy"))
    }

    @Test
    fun `deleteMsgs tombstones Box projection without deleting Event history`() {
        StockSurfaceSupport.ensureInitialized(context)
        agreeToBoxPrivacy()
        installBridgeActivity("com.example.owner")
        val messageId = "delete-${System.nanoTime()}"
        val eventId = runBlocking {
            EventDb.insertEventAsync(
                Event(
                    id = null,
                    pkg = "com.example.owner",
                    type = Event.Type.SendMessage,
                    date = System.currentTimeMillis(),
                    result = Event.ResultType.OK,
                    info = null,
                    payload = serialize(displayContainer(messageId, profileExtra = false)),
                    regSec = null,
                ),
            )
        }

        val result = StockPushSupport.handle(
            context,
            null,
            "deleteMsgs",
            Bundle().apply { putString("msgIds", messageId) },
        )

        assertEquals(0, result.getInt("code"))
        val remaining = runBlocking {
            EventDb.queryByIdAsync(null, Int.MAX_VALUE, setOf(Event.Type.SendMessage), null, null)
        }
        assertTrue(remaining.any { it.id == eventId })

        val deletedPage = getBoxMessages()
        assertFalse(deletedPage.any { it.getString("msgId") == messageId })

        // The provider's local tombstone is tied to the projected Event row, not the remote msgId.
        // A later message that reuses the same msgId must remain visible.
        insertBoxEvent(displayContainer(messageId, profileExtra = false))
        val reusedIdPage = getBoxMessages()
        assertTrue(reusedIdPage.any { it.getString("msgId") == messageId })
    }

    @Test
    fun `box messages require opt in enabled channel and Activity target`() {
        StockSurfaceSupport.ensureInitialized(context)
        installBridgeActivity("com.example.owner")
        shadowOf(context.packageManager).installPackage(
            PackageInfo().apply { packageName = "com.example.noactivity" },
        )
        val suffix = System.nanoTime().toString()
        val eligibleId = "eligible-$suffix"
        val disabledId = "disabled-$suffix"
        val missingOptInId = "missing-opt-in-$suffix"
        val noActivityId = "no-activity-$suffix"

        insertBoxEvent(displayContainer(eligibleId, profileExtra = false, channelType = "news"))
        insertBoxEvent(displayContainer(disabledId, profileExtra = false, channelType = "muted"))
        insertBoxEvent(
            displayContainer(missingOptInId, profileExtra = false, channelType = "news").apply {
                metaInfo.extra.remove("allow_box")
            },
        )
        insertBoxEvent(
            displayContainer(
                noActivityId,
                profileExtra = false,
                channelType = "news",
                packageName = "com.example.noactivity",
            ),
        )
        StockPushSupport.handle(
            context,
            null,
            "setAppPushStatus",
            Bundle().apply {
                putString("packageName", "com.example.owner")
                putParcelableArrayList(
                    "channelTypeIds",
                    arrayListOf(Bundle().apply {
                        putString("channelTypeId", "muted")
                        putBoolean("enabled", false)
                    }),
                )
            },
        )

        val beforeConsent = StockPushSupport.handle(
            context,
            null,
            "getPushMsgs",
            Bundle().apply { putInt("msgCount", 100) },
        )
        @Suppress("DEPRECATION")
        val hiddenMessages = beforeConsent.getBundle("data")!!.getParcelableArrayList<Bundle>("data")!!
        assertFalse(hiddenMessages.any { it.getString("msgId") == eligibleId })

        agreeToBoxPrivacy()
        val afterConsent = StockPushSupport.handle(
            context,
            null,
            "getPushMsgs",
            Bundle().apply { putInt("msgCount", 100) },
        )
        @Suppress("DEPRECATION")
        val messages = afterConsent.getBundle("data")!!.getParcelableArrayList<Bundle>("data")!!
        val byId = messages.associateBy { it.getString("msgId") }
        assertTrue(eligibleId in byId)
        assertFalse(disabledId in byId)
        assertFalse(missingOptInId in byId)
        assertFalse(noActivityId in byId)

        val parcel = Parcel.obtain()
        try {
            val intentData = byId.getValue(eligibleId).getByteArray("intentData")!!
            parcel.unmarshall(intentData, 0, intentData.size)
            parcel.setDataPosition(0)
            val restored = Intent.CREATOR.createFromParcel(parcel)
            assertEquals("com.xiaomi.mipush.sdk.BridgeActivity", restored.component?.className)
        } finally {
            parcel.recycle()
        }
    }

    @Test
    fun `profile mismatch storage fence creates neither Event nor notification allowance`() {
        StockSurfaceSupport.ensureInitialized(context)
        shadowOf(context.packageManager).installPackage(
            PackageInfo().apply { packageName = "com.example.owner" },
        )
        val messageId = "fenced-${System.nanoTime()}"
        val container = displayContainer(messageId)
        val payload = serialize(container)

        assertFalse(
            MiPushRuntimeBridge.onPayloadFromServer(
                context,
                payload,
                payload.size.toLong(),
                "StockProviderContractTest",
            ),
        )

        val events = runBlocking {
            EventDb.queryByIdAsync(null, Int.MAX_VALUE, setOf(Event.Type.SendMessage), null, null)
        }
        assertFalse(events.any { it.container?.metaInfo?.id == messageId })
        assertFalse(MiPushRuntimeBridge.onNotificationDispatch(context, container, payload))
    }

    @Test
    fun `channel permissions and broker allowlist are fail closed`() {
        val channel = NotificationChannel("mapped", "Mapped", NotificationManager.IMPORTANCE_HIGH)
        StockChannelSupport.applyPermissions(channel, 2 or 4 or 16)

        assertEquals(2 or 4 or 16, StockChannelSupport.computePermissions(channel))
        assertTrue(StockPushSupport.isNotificationBrokerAllowed("com.miui.systemAdSolution"))
        assertFalse(StockPushSupport.isNotificationBrokerAllowed("com.example.attacker"))
        assertFalse(StockPushSupport.isNotificationBrokerAllowed(null))
        assertEquals(
            "mipush|com.example.owner|news",
            NotificationController.selectManagedChannelId(
                stockChannelId = "mipush|com.example.owner|news",
                stockChannelExists = true,
                legacyChannelId = "ch_com.example.owner_news",
                legacyChannelExists = true,
            ),
        )
        assertEquals(
            "ch_com.example.owner_news",
            NotificationController.selectManagedChannelId(
                stockChannelId = "mipush|com.example.owner|news",
                stockChannelExists = false,
                legacyChannelId = "ch_com.example.owner_news",
                legacyChannelExists = true,
            ),
        )
        assertEquals(
            "mipush|com.example.owner|news",
            NotificationController.selectManagedChannelId(
                stockChannelId = "mipush|com.example.owner|news",
                stockChannelExists = false,
                legacyChannelId = "ch_com.example.owner_news",
                legacyChannelExists = false,
            ),
        )
    }

    private fun register(packageName: String) {
        context.getSharedPreferences("pref_registered_pkg_names", Context.MODE_PRIVATE)
            .edit()
            .putString(packageName, "app-id")
            .commit()
    }

    private fun profileCall(method: String, profileId: String?): Bundle = StockProfileIdStore.handle(
        context,
        method,
        Bundle().apply { putString("profileId", profileId) },
        "com.example.owner",
    )

    private fun displayContainer(
        profileId: String,
        profileExtra: Boolean = true,
        channelType: String = "news",
        packageName: String = "com.example.owner",
    ) = MIPushHelper.generateRequestContainer(
        packageName,
        "app-id",
        XmPushActionSendMessage().apply {
            setId("body-id")
            setAppId("app-id")
            setPackageName(packageName)
        },
        ActionType.SendMessage,
    ).apply {
        metaInfo = PushMetaInfo().apply {
            id = profileId
            title = "title"
            description = "content"
            passThrough = 0
            if (profileExtra) putToExtra("profileId", profileId)
            putToExtra("allow_box", "true")
            putToExtra("channel_type", channelType)
            putToExtra("channel_id", channelType)
        }
    }

    private fun serialize(container: com.xiaomi.xmpush.thrift.XmPushActionContainer): ByteArray =
        XmPushThriftSerializeUtils.convertThriftObjectToBytes(container)

    private fun record(messageId: String, receivedTime: Long) = StockPushSupport.BoxMessageRecord(
        localEventId = null,
        messageId = messageId,
        channelTypeId = "channel",
        packageName = "com.example.owner",
        title = "title",
        content = "content",
        receivedTime = receivedTime,
        exposed = false,
        intentData = null,
    )

    private fun insertBoxEvent(container: com.xiaomi.xmpush.thrift.XmPushActionContainer): Long = runBlocking {
        EventDb.insertEventAsync(
            Event(
                id = null,
                pkg = container.packageName,
                type = Event.Type.SendMessage,
                date = System.currentTimeMillis(),
                result = Event.ResultType.OK,
                info = null,
                payload = serialize(container),
                regSec = null,
            ),
        )
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

    private fun agreeToBoxPrivacy() {
        val result = StockPushSupport.handle(
            context,
            null,
            "setPrivacyStatus",
            Bundle().apply { putBoolean("agreedPrivacyPolicy", true) },
        )
        assertEquals(0, result.getInt("code"))
    }

    @Suppress("DEPRECATION")
    private fun getBoxMessages(): List<Bundle> = StockPushSupport.handle(
        context,
        null,
        "getPushMsgs",
        Bundle().apply { putInt("msgCount", 100) },
    ).getBundle("data")!!.getParcelableArrayList<Bundle>("data")!!
}
