package io.github.magisk317.mipush.runtime.data

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import io.github.magisk317.mipush.platform.support.XMPushUtils
import io.github.magisk317.mipush.service.XMPushServiceLifecycleBridge
import io.github.magisk317.mipush.utils.MockMIPushMessage
import com.xiaomi.push.service.MIPushEventProcessor
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.XmPushActionCommandResult
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmsf.R
import io.github.magisk317.mipush.notification.NotificationChannelManager
import io.github.magisk317.mipush.notification.NotificationController
import io.github.magisk317.mipush.utils.Configurations
import io.github.magisk317.mipush.utils.RegSecUtils
import com.xiaomi.push.service.XMPushService as SdkXMPushService
import com.xiaomi.xmsf.push.service.MiPushFacadeService as AppXMPushService
import io.github.magisk317.mipush.app.ConfigCenter
import io.github.magisk317.mipush.utils.ConvertUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aakira.napier.Napier
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.apache.thrift.TBase
import io.github.magisk317.mipush.common.utils.CustomConfiguration
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.runtime.store.db.EventDb
import io.github.magisk317.mipush.runtime.store.entities.Event
import io.github.magisk317.mipush.config.ConfigNavigationHelper
import io.github.magisk317.mipush.platform.support.LegacyUiEntryPoints
import io.github.magisk317.mipush.service.PushServiceStarter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

@Singleton
class EventRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val configCenter: ConfigCenter,
    private val configurations: Configurations,
    private val configNavigationHelper: ConfigNavigationHelper,
) {
    // No-arg fallback for legacy Singleton access.
    constructor() : this(
        io.github.magisk317.mipush.common.utils.Utils.getApplication()!!,
        io.github.magisk317.mipush.common.utils.Singleton.instance<ConfigCenter>(),
        Configurations.getInstance()
        ,
        ConfigNavigationHelper(),
    )

    init {
        try {
            io.github.magisk317.mipush.common.utils.Singleton.reset(this)
        } catch (t: Throwable) {
            io.github.aakira.napier.Napier.w("Singleton.reset failed for EventRepository", t, tag = "EventRepository")
        }
    }

    fun getStatus(container: XmPushActionContainer?): MutableSet<String> {
        if (container == null) {
            return HashSet()
        }
        val ops = configureContainer(container.deepCopy()).toMutableSet()
        if (isNotificationDisabled(container)) {
            ops.add("disable")
        }
        return ops
    }

    protected fun isNotificationDisabled(container: XmPushActionContainer): Boolean {
        return !Utils.isAppInstalled(container.packageName) || !NotificationChannelManager.isNotificationChannelEnabled(
            container.packageName,
            NotificationController.getExistsChannelId(context, container.metaInfo, container.packageName)
        )
    }

    fun getStatusDescription(item: Event): String {
        return when (item.result) {
            Event.ResultType.OK -> getStatusDescriptionByEvent(item)
            Event.ResultType.DENY_DISABLED -> context.getString(R.string.status_deny_disable)
            Event.ResultType.DENY_USER -> context.getString(R.string.status_deny_user)
            else -> ""
        }
    }

    private fun getStatusDescriptionByEvent(item: Event): String {
        val container = RegSecUtils.getContainerWithRegSec(item)
        if (container != null) {
            if (container.metaInfo.passThrough == 1) {
                return context.getString(R.string.message_type_pass_through)
            }
            if (container.metaInfo.passThrough == 0) {
                configureContainer(container)
                val configuration: CustomConfiguration = XMPushUtils.getConfiguration(container)
                return configuration.channelName(context.getString(R.string.message_type_notification)) ?: ""
            }
        }
        return ""
    }

    fun getEventsById(lastId: Long?, size: Int, packetName: String, query: String): List<Event> {
        var types: Set<Int>? = null
        if (!runBlocking { configCenter.isShowAllEventsAsync() }) {
            types = setOf(
                Event.Type.SendMessage,
                Event.Type.Registration,
                Event.Type.RegistrationResult,
                Event.Type.UnRegistration
            )
        }
        return runBlocking { EventDb.queryByIdAsync(lastId, size, types, packetName, query) }
    }

    fun getEvents(pageIndex: Int, pageSize: Int, packetName: String, query: String): List<Event> {
        var types: Set<Int>? = null
        if (!runBlocking { configCenter.isShowAllEventsAsync() }) {
            types = setOf(
                Event.Type.SendMessage,
                Event.Type.Registration,
                Event.Type.RegistrationResult,
                Event.Type.UnRegistration
            )
        }
        return EventDb.queryByPage(pageIndex, pageSize, types, packetName, query)
    }

    suspend fun deleteEvent(event: Event): Boolean {
        val id = event.id ?: return false
        return EventDb.deleteByIdAsync(id)
    }

    suspend fun restoreEvent(event: Event): Long {
        val restored = Event(
            id = null,
            pkg = event.pkg,
            type = event.type,
            date = event.date,
            result = event.result,
            info = event.info,
            payload = event.payload,
            regSec = event.regSec,
        )
        return EventDb.insertEventAsync(restored)
    }

    fun copyToClipboard(info: CharSequence) {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.setPrimaryClip(android.content.ClipData.newPlainText(null, info))
    }

    fun mockMessage(containerWithRegSec: XmPushActionContainer) {
        val pushService: SdkXMPushService? = XMPushServiceLifecycleBridge.peekService()
        logger.d("EventRepository", "mockMessage called. pushService exists: ${pushService != null}")
        logger.d(
            "EventRepository",
            "mockMessage request pkg=${containerWithRegSec.packageName} action=${containerWithRegSec.action} " +
                "messageId=${io.github.magisk317.mipush.push.pipeline.MessageIdentity.fromContainer(containerWithRegSec)} " +
                "isEncrypt=${containerWithRegSec.isEncryptAction} isRequest=${containerWithRegSec.isRequest}"
        )
        val regSec = RegSecUtils.getRegSec(containerWithRegSec)
        if (containerWithRegSec.isEncryptAction && regSec.isNullOrBlank()) {
            Utils.makeText(
                context,
                context.getString(R.string.mock_notification_missing_regsec),
                0
            )
            return
        }

        // Preflight diagnostics: if framework cannot even resolve receiver, replay will be dropped silently.
        runCatching {
            val payload = XMPushUtils.packToBytes(containerWithRegSec)
            val candidateIntent = MIPushEventProcessor.buildIntent(payload, System.currentTimeMillis())
                if (candidateIntent == null) {
                    logger.w("EventRepository", "mock preflight: buildIntent returned null")
                } else {
                    // Use 0 to match framework dispatch behavior in MIPushEventProcessor.isIntentAvailable.
                    val receivers = context.packageManager.queryBroadcastReceivers(candidateIntent, 0)
                    val receiverNames = receivers.mapNotNull { it.activityInfo?.name }.take(3)
                    logger.d(
                        "EventRepository",
                        "mock preflight: action=${candidateIntent.action} pkg=${candidateIntent.`package`} " +
                            "receivers=${receivers.size} names=$receiverNames"
                    )
                    if (receivers.isEmpty()) {
                        Utils.makeText(
                            context,
                            context.getString(R.string.mock_notification_no_receiver),
                        0
                    )
                }
            }
        }.onFailure {
            logger.e("EventRepository", "mock preflight check failed", it)
        }

        if (pushService == null) {
            logger.d("EventRepository", "pushService is null, ensuring observer and starting service")
            // Ensure the runtime observer is initialized before starting the push service.
            // Normally BootReceiver does this, but it may not have run.
            if (com.xiaomi.push.service.XMPushService.observer == null) {
                logger.d("EventRepository", "XMPushService.observer is null, initializing MiPushRuntimeObserverBridge")
                io.github.magisk317.mipush.bridge.MiPushRuntimeObserverBridge(context)
            }
            PushServiceStarter.start(context, Intent(context, AppXMPushService::class.java))
            waitForPushServiceAndReplay(containerWithRegSec)
            return
        }
        val replayContainer = containerWithRegSec.deepCopy()
        val handled = MockMIPushMessage.mockProcessMIPushMessage(
            pushService,
            replayContainer
        )
        logger.d(
            "EventRepository",
            "mockMessage finished pkg=${replayContainer.packageName} action=${replayContainer.action} " +
                "messageId=${io.github.magisk317.mipush.push.pipeline.MessageIdentity.fromContainer(replayContainer)} handled=$handled"
        )
        if (!handled) {
            Utils.makeText(
                context,
                context.getString(R.string.mock_notification_failed),
                0
            )
        }
    }

    fun getContent(event: Event, containerWithRegSec: XmPushActionContainer): String {
        return try {
            val newContainer = containerWithRegSec.deepCopy()
            configurations.handle(event.pkg, newContainer)
            containerToJson(newContainer, event.regSec).toString()
        } catch (e: Throwable) {
            Napier.e("getContent failed for ${event.pkg}", e, tag = "EventRepository")
            e.toString()
        }
    }

    fun getJson(event: Event): CharSequence? {
        val container = event.container ?: return null
        return containerToJson(container, event.regSec)
    }

    private fun configureContainer(container: XmPushActionContainer): Set<String> {
        return try {
            configurations.handle(container.packageName, container)
        } catch (t: Throwable) {
            Napier.w("configureContainer failed for ${container.packageName}: ${t.message}", tag = "EventRepository")
            HashSet()
        }
    }

    private fun waitForPushServiceAndReplay(containerWithRegSec: XmPushActionContainer) {
        Thread {
            try {
                val waitedMs = waitForPushService { service, waited ->
                    logger.d("EventRepository", "pushService became ready after ${waited}ms, replaying mock")
                    val replayContainer = containerWithRegSec.deepCopy()
                    val handled = MockMIPushMessage.mockProcessMIPushMessage(service, replayContainer)
                    logger.d("EventRepository", "deferred mockMessage handled=$handled")
                    if (!handled) {
                        showMockFailedToast()
                    }
                }
                if (waitedMs == null) {
                    logger.w("EventRepository", "pushService did not become ready within ${MOCK_REPLAY_MAX_WAIT_MS}ms")
                    showMockFailedToast()
                }
            } catch (t: InterruptedException) {
                Thread.currentThread().interrupt()
                logger.w("EventRepository", "mock replay wait interrupted", t)
            } catch (t: Throwable) {
                logger.e("EventRepository", "deferred mock replay failed", t)
                showMockFailedToast()
            }
        }.apply {
            name = "MiPushMockReplay"
            isDaemon = true
            start()
        }
    }

    private fun waitForPushService(onReady: (SdkXMPushService, Long) -> Unit): Long? {
        var waited = 0L
        while (waited < MOCK_REPLAY_MAX_WAIT_MS) {
            Thread.sleep(MOCK_REPLAY_POLL_MS)
            waited += MOCK_REPLAY_POLL_MS
            val service = XMPushServiceLifecycleBridge.peekService()
            if (service != null) {
                onReady(service, waited)
                return waited
            }
        }
        return null
    }

    private fun showMockFailedToast() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Utils.makeText(context, context.getString(R.string.mock_notification_failed), 0)
        }
    }

    fun containerToJson(container: XmPushActionContainer, regSec: String?): CharSequence {
        val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            encodeDefaults = true
        }
        return json.encodeToString(JsonElement.serializer(), ConvertUtils.toJson(container, regSec))
    }

    fun startManagePermissions(packageName: String, IGNORE_NOT_REGISTERED: Boolean = false) {
        val intent = LegacyUiEntryPoints.applicationInfoIntent(
            context = context,
            packageName = packageName,
            ignoreNotRegistered = IGNORE_NOT_REGISTERED,
        ).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    suspend fun startConfigPreview(packageName: String) {
        configNavigationHelper.openForPackage(packageName)
    }

    fun getDecoratedSummary(summary: String, container: XmPushActionContainer): String {
        if (container.isSetPushAction) {
            val data = getContainer(container)
            if (data is XmPushActionNotification) {
                return "$summary: ${data.type}"
            } else if (data is XmPushActionCommandResult) {
                return "$summary: ${data.cmdName}"
            }
        }
        return summary
    }

    fun getContainer(container: XmPushActionContainer): TBase<*, *>? {
        return try {
            ConvertUtils.getResponseMessageBodyFromContainer(container, RegSecUtils.getRegSec(container))
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val MOCK_REPLAY_MAX_WAIT_MS = 5_000L
        private const val MOCK_REPLAY_POLL_MS = 100L
        private val logger = object {
            fun d(tag: String, msg: String) = io.github.aakira.napier.Napier.d(msg, tag = tag)
            fun i(tag: String, msg: String) = io.github.aakira.napier.Napier.i(msg, tag = tag)
            fun w(tag: String, msg: String, t: Throwable? = null) = io.github.aakira.napier.Napier.w(msg, t, tag = tag)
            fun e(tag: String, msg: String, t: Throwable? = null) = io.github.aakira.napier.Napier.e(msg, t, tag = tag)
        }
    }
}
