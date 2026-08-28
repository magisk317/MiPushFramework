package io.github.magisk317.mipush.runtime.data

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.manager.application.MockReplayOutcome

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import io.github.magisk317.mipush.platform.support.XMPushUtils
import io.github.magisk317.mipush.utils.MockMIPushMessage
import com.xiaomi.push.service.MIPushEventProcessor
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.XmPushActionCommandResult
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmsf.R
import io.github.magisk317.mipush.common.notification.NotificationAvailabilityReader
import io.github.magisk317.mipush.common.notification.NotificationAvailabilityRequest
import io.github.magisk317.mipush.bridge.MiPushRuntimeObserverBridge
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.utils.Configurations
import io.github.magisk317.mipush.utils.RegSecUtils
import com.xiaomi.push.service.XMPushServiceCore as SdkXMPushService
import io.github.magisk317.mipush.app.ConfigCenter
import io.github.magisk317.mipush.utils.ConvertUtils
import co.touchlab.kermit.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.apache.thrift.TBase
import io.github.magisk317.mipush.notification.policy.CustomConfiguration
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.runtime.store.db.EventDb
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeEventRow
import io.github.magisk317.mipush.runtime.store.kmp.DayCount
import io.github.magisk317.mipush.runtime.store.kmp.EventRowType
import io.github.magisk317.mipush.runtime.store.kmp.EventRowResultType
import io.github.magisk317.mipush.runtime.store.adapter.container
import io.github.magisk317.mipush.config.ConfigNavigationHelper
import io.github.magisk317.mipush.platform.support.LegacyUiEntryPoints
import io.github.magisk317.mipush.service.PushServiceStarter
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import io.github.magisk317.xposed.logging.MagiskOtel

class EventRepository constructor(
    private val context: Context,
    private val configCenter: ConfigCenter,
    private val configurations: Configurations,
    private val configNavigationHelper: ConfigNavigationHelper,
    private val notificationAvailabilityReader: NotificationAvailabilityReader,
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
        return notificationAvailabilityReader.isNotificationDisabled(
            NotificationAvailabilityRequest(
                packageName = container.packageName,
                metaInfoExtra = container.metaInfo?.extra.orEmpty(),
            ),
        )
    }

    fun getStatusDescription(item: RuntimeEventRow): String =
        getStatusDescription(item, RegSecUtils.getContainerWithRegSec(item))

    /**
     * Reuses a container that the caller already decoded for the same event. Event-list
     * projection needs both the decorated summary and the channel/status label; decoding the
     * payload again for the latter made each row repeat the most expensive part of the read.
     */
    fun getStatusDescription(item: RuntimeEventRow, container: XmPushActionContainer?): String {
        return when (item.result) {
            EventRowResultType.OK -> getStatusDescriptionByEvent(container)
            EventRowResultType.DENY_DISABLED -> context.getString(R.string.status_deny_disable)
            EventRowResultType.DENY_USER -> context.getString(R.string.status_deny_user)
            else -> ""
        }
    }

    private fun getStatusDescriptionByEvent(container: XmPushActionContainer?): String {
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

    fun getEventsById(lastId: Long?, size: Int, packetName: String, query: String): List<RuntimeEventRow> {
        var types: Set<Int>? = null
        if (!runBlocking { configCenter.isShowAllEventsAsync() }) {
            types = setOf(
                EventRowType.SendMessage,
                EventRowType.Registration,
                EventRowType.RegistrationResult,
                EventRowType.UnRegistration
            )
        }
        return runBlocking { EventDb.queryByIdAsync(lastId, size, types, packetName, query) }
    }

    fun getEvents(pageIndex: Int, pageSize: Int, packetName: String, query: String): List<RuntimeEventRow> {
        var types: Set<Int>? = null
        if (!runBlocking { configCenter.isShowAllEventsAsync() }) {
            types = setOf(
                EventRowType.SendMessage,
                EventRowType.Registration,
                EventRowType.RegistrationResult,
                EventRowType.UnRegistration
            )
        }
        return EventDb.queryByPage(pageIndex, pageSize, types, packetName, query)
    }

    suspend fun deleteEvent(event: RuntimeEventRow): Boolean {
        val id = event.id ?: return false
        if (event.pkg.isBlank()) return false
        return EventDb.deleteByIdWithUndoSnapshotAsync(id, event.pkg, event.userId)
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

    suspend fun restoreEvent(event: RuntimeEventRow): Long {
        val preferredId = event.id
        if (preferredId != null && preferredId > 0L && event.pkg.isNotBlank()) {
            return EventDb.restoreDeletedEventAsync(preferredId, event.pkg, event.userId) ?: 0L
        }
        return 0L
    }

    fun copyToClipboard(info: CharSequence) {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.setPrimaryClip(android.content.ClipData.newPlainText(null, info))
    }

    suspend fun mockMessage(containerWithRegSec: XmPushActionContainer): MockReplayOutcome {
        var pushService: SdkXMPushService? = MiPushRuntimeObserverBridge.currentService()
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
        if (containerWithRegSec.isEncryptAction && RegSecUtils.getRegSec(containerWithRegSec).isNullOrBlank()) {
            PushRuntime.observeNotificationEvent(
                containerWithRegSec.packageName,
                "mock_replay_missing_regsec_continue",
                "EventRepository.mockMessage",
            )
            logW(
                "mock replay continuing with raw encrypted payload because regSec is unavailable " +
                    "pkg=${containerWithRegSec.packageName}"
            )
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
                // Stock 7.4.67-C exposes two public XMPushService facades. The previous correction
                // started one with an empty internal Intent, but that exported facade correctly
                // rejected it at the external-ingress gate. Start the product-owned private core
                // directly so replay bootstrap needs no extra facade component or gate bypass.
                PushServiceStarter.start(context, runtimeServiceIntent(context))
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

    fun getContent(event: RuntimeEventRow, containerWithRegSec: XmPushActionContainer): String {
        return try {
            val newContainer = containerWithRegSec.deepCopy()
            configurations.handle(event.pkg, newContainer)
            containerToJson(newContainer, event.regSec).toString()
        } catch (e: Throwable) {
            Logger.withTag("EventRepository").e(e) { "getContent failed for ${event.pkg}" }
            e.toString()
        }
    }

    fun getJson(event: RuntimeEventRow): CharSequence? {
        val container = event.container() ?: return null
        return containerToJson(container, event.regSec)
    }

    private fun configureContainer(container: XmPushActionContainer): Set<String> {
        return try {
            configurations.handle(container.packageName, container)
        } catch (t: Throwable) {
            Logger.withTag("EventRepository").w { "configureContainer failed for ${container.packageName}: ${t.message}" }
            HashSet()
        }
    }

    private suspend fun waitForPushService(): Pair<SdkXMPushService, Long>? {
        var waited = 0L
        while (waited < MOCK_REPLAY_MAX_WAIT_MS) {
            delay(MOCK_REPLAY_POLL_MS)
            waited += MOCK_REPLAY_POLL_MS
            val service = MiPushRuntimeObserverBridge.currentService()
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
        val statusOk = outcome == MockReplayOutcome.Dispatched || outcome == MockReplayOutcome.Posted
        emitMockReplay(
            packageName = packageName,
            result = if (statusOk) "ok" else if (outcome == MockReplayOutcome.BlockedByPermission) "skip" else "error",
            reason = action,
            statusOk = statusOk,
        )
    }

    private fun emitMockReplay(
        packageName: String,
        result: String,
        reason: String,
        statusOk: Boolean = true,
    ) {
        MagiskOtel.event(
            name = "push.event",
            attributes = mapOf(
                "result" to result,
                "duration_ms" to "0",
                "process" to "app",
                "stage" to "mock_replay",
                "reason" to reason,
                "target_package" to packageName,
            ),
            statusOk = statusOk,
        )
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

        internal fun runtimeServiceIntent(context: Context): Intent {
            return Intent(context, SdkXMPushService::class.java)
        }
    }
}
