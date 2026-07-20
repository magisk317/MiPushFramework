package io.github.magisk317.mipush.runtime.data

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.common.notification.MockReplayOutcome

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
import io.github.magisk317.mipush.bridge.MiPushRuntimeObserverBridge
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.utils.Configurations
import io.github.magisk317.mipush.utils.RegSecUtils
import com.xiaomi.push.service.XMPushServiceCore as SdkXMPushService
import com.xiaomi.xmsf.push.service.MiPushFacadeService as AppXMPushService
import io.github.magisk317.mipush.app.ConfigCenter
import io.github.magisk317.mipush.utils.ConvertUtils
import io.github.aakira.napier.Napier
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.apache.thrift.TBase
import io.github.magisk317.mipush.common.utils.CustomConfiguration
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.runtime.store.db.DayCount
import io.github.magisk317.mipush.runtime.store.db.EventDb
import io.github.magisk317.mipush.runtime.store.entities.Event
import io.github.magisk317.mipush.config.ConfigNavigationHelper
import io.github.magisk317.mipush.platform.support.LegacyUiEntryPoints
import io.github.magisk317.mipush.service.PushServiceStarter
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay

class EventRepository constructor(
    private val context: Context,
    private val configCenter: ConfigCenter,
    private val configurations: Configurations,
    private val configNavigationHelper: ConfigNavigationHelper,
) {
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

    /** 按本地日历日聚合可清理事件的条数(排除注册态),供日历清理界面高亮与计数。 */
    suspend fun countEventsByDay(): List<DayCount> {
        return EventDb.countEventsByDayAsync()
    }

    /** 删除某个时间区间 [start, end) 内的可清理事件(排除注册态),供"仅清理当天"使用。 */
    suspend fun deleteHistoryInRange(start: Long, end: Long): Int {
        return EventDb.deleteHistoryInRangeAsync(start, end)
    }

    /** 清理某个时间点之前的可清理事件(排除注册态),供"清理此日期及之前"使用。 */
    suspend fun deleteHistoryBefore(cutoff: Long): Int {
        return EventDb.deleteHistoryBeforeAsync(cutoff)
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

    suspend fun mockMessage(containerWithRegSec: XmPushActionContainer): MockReplayOutcome {
        var pushService: SdkXMPushService? = XMPushServiceLifecycleBridge.peekService()
        PushRuntime.observeNotificationEvent(
            containerWithRegSec.packageName,
            "mock_replay_request",
            "EventRepository.mockMessage",
        )
        logI(
            "mock replay request pkg=${containerWithRegSec.packageName} action=${containerWithRegSec.action} " +
                "pushServiceReady=${pushService != null}"
        )
        logD("mockMessage called. pushService exists: ${pushService != null}")
        logD(
            "mockMessage request pkg=${containerWithRegSec.packageName} action=${containerWithRegSec.action} " +
                "messageId=${io.github.magisk317.mipush.push.pipeline.MessageIdentity.fromContainer(containerWithRegSec)} " +
                "isEncrypt=${containerWithRegSec.isEncryptAction} isRequest=${containerWithRegSec.isRequest}"
        )
        val regSec = RegSecUtils.getRegSec(containerWithRegSec)
        if (containerWithRegSec.isEncryptAction && regSec.isNullOrBlank()) {
            PushRuntime.observeNotificationEvent(
                containerWithRegSec.packageName,
                "mock_replay_missing_regsec",
                "EventRepository.mockMessage",
            )
            logW("mock replay rejected: encrypted payload has no regSec pkg=${containerWithRegSec.packageName}")
            return MockReplayOutcome.Failed
        }

        // Preflight diagnostics: if framework cannot even resolve receiver, replay will be dropped silently.
        runCatching {
            val payload = XMPushUtils.packToBytes(containerWithRegSec)
            val candidateIntent = MIPushEventProcessor.buildIntent(payload, System.currentTimeMillis())
            if (candidateIntent == null) {
                PushRuntime.observeNotificationEvent(
                    containerWithRegSec.packageName,
                    "mock_replay_preflight_no_intent",
                    "EventRepository.mockMessage",
                )
                logW("mock preflight: buildIntent returned null")
            } else {
                // Use 0 to match framework dispatch behavior in MIPushEventProcessor.isIntentAvailable.
                val receivers = context.packageManager.queryBroadcastReceivers(candidateIntent, 0)
                val receiverNames = receivers.mapNotNull { it.activityInfo?.name }.take(3)
                logD(
                    "mock preflight: action=${candidateIntent.action} pkg=${candidateIntent.`package`} " +
                        "receivers=${receivers.size} names=$receiverNames"
                )
                if (receivers.isEmpty()) {
                    PushRuntime.observeNotificationEvent(
                        containerWithRegSec.packageName,
                        "mock_replay_preflight_no_receiver",
                        "EventRepository.mockMessage",
                    )
                } else {
                    PushRuntime.observeNotificationEvent(
                        containerWithRegSec.packageName,
                        "mock_replay_preflight_ready",
                        "EventRepository.mockMessage",
                    )
                }
            }
        }.onFailure {
            PushRuntime.observeNotificationEvent(
                containerWithRegSec.packageName,
                "mock_replay_preflight_failed",
                "EventRepository.mockMessage",
            )
            logE("mock preflight check failed", it)
        }

        if (pushService == null) {
            PushRuntime.observeNotificationEvent(
                containerWithRegSec.packageName,
                "mock_replay_wait_service",
                "EventRepository.mockMessage",
            )
            logD("pushService is null, ensuring observer and starting service")
            // Ensure the runtime observer is initialized before starting the push service.
            // Normally BootReceiver does this, but it may not have run.
            if (MiPushRuntimeObserverBridge.ensureInstalled(context)) {
                logD("runtime observer bridge was missing, installed MiPushRuntimeObserverBridge")
            }
            runCatching {
                PushServiceStarter.start(context, Intent(context, AppXMPushService::class.java))
            }.onFailure {
                PushRuntime.observeNotificationEvent(
                    containerWithRegSec.packageName,
                    "mock_replay_service_start_failed",
                    "EventRepository.mockMessage",
                )
                logE("mock replay could not start push service", it)
            }.getOrElse {
                return MockReplayOutcome.Failed
            }
            val ready = waitForPushService()
            if (ready == null) {
                PushRuntime.observeNotificationEvent(
                    containerWithRegSec.packageName,
                    "mock_replay_service_timeout",
                    "EventRepository.mockMessage",
                )
                logW("pushService did not become ready within ${MOCK_REPLAY_MAX_WAIT_MS}ms")
                return MockReplayOutcome.Failed
            }
            pushService = ready.first
            logD("pushService became ready after ${ready.second}ms, replaying mock")
            PushRuntime.observeNotificationEvent(
                containerWithRegSec.packageName,
                "mock_replay_dispatch_deferred",
                "EventRepository.mockMessage",
            )
        } else {
            PushRuntime.observeNotificationEvent(
                containerWithRegSec.packageName,
                "mock_replay_dispatch_immediate",
                "EventRepository.mockMessage",
            )
        }
        val replayContainer = containerWithRegSec.deepCopy()
        val outcome = runCatching {
            MockMIPushMessage.mockProcessMIPushMessage(
                requireNotNull(pushService),
                replayContainer,
            )
        }.onFailure {
            logE("mock replay dispatch failed pkg=${replayContainer.packageName}", it)
        }.getOrDefault(MockReplayOutcome.Failed)
        observeMockReplayOutcome(replayContainer.packageName, outcome)
        logD(
            "mockMessage finished pkg=${replayContainer.packageName} action=${replayContainer.action} " +
                "messageId=${io.github.magisk317.mipush.push.pipeline.MessageIdentity.fromContainer(replayContainer)} " +
                "outcome=$outcome"
        )
        return outcome
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

    private suspend fun waitForPushService(): Pair<SdkXMPushService, Long>? {
        var waited = 0L
        while (waited < MOCK_REPLAY_MAX_WAIT_MS) {
            delay(MOCK_REPLAY_POLL_MS)
            waited += MOCK_REPLAY_POLL_MS
            val service = XMPushServiceLifecycleBridge.peekService()
            if (service != null) {
                return service to waited
            }
        }
        return null
    }

    private fun observeMockReplayOutcome(packageName: String, outcome: MockReplayOutcome) {
        val action = when (outcome) {
            MockReplayOutcome.BlockedByPermission -> "mock_replay_blocked_by_permission"
            MockReplayOutcome.Dispatched -> "mock_replay_dispatched"
            MockReplayOutcome.Posted -> "mock_replay_posted"
            MockReplayOutcome.Failed -> "mock_replay_failed"
        }
        PushRuntime.observeNotificationEvent(packageName, action, "EventRepository.mockMessage")
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
    }
}
