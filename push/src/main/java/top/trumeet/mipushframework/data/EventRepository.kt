package top.trumeet.mipushframework.data

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import com.magisk317.XMPushUtils
import com.magisk317.service.XMPushServiceLifecycleBridge
import com.magisk317.utils.MockMIPushMessage
import com.xiaomi.channel.commonutils.android.DataCryptUtils
import com.xiaomi.channel.commonutils.string.Base64Coder
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.MIPushEventProcessor
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.XmPushActionCommandResult
import com.xiaomi.xmpush.thrift.XmPushActionSendMessage
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmsf.R
import com.xiaomi.xmsf.push.notification.NotificationChannelManager
import com.xiaomi.xmsf.push.notification.NotificationController
import com.xiaomi.xmsf.push.utils.Configurations
import com.xiaomi.xmsf.push.utils.RegSecUtils
import com.xiaomi.push.service.XMPushService as SdkXMPushService
import com.xiaomi.xmsf.push.service.XMPushService as AppXMPushService
import com.xiaomi.xmsf.utils.ConfigCenter
import com.xiaomi.xmsf.utils.ConvertUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.apache.thrift.TBase
import top.trumeet.common.utils.CustomConfiguration
import top.trumeet.common.utils.Utils
import top.trumeet.mipush.provider.db.EventDb
import top.trumeet.mipush.provider.entities.Event
import top.trumeet.mipushframework.config.ConfigNavigationHelper
import top.trumeet.mipushframework.main.ApplicationInfoPage
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
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
        top.trumeet.common.utils.Utils.getApplication()!!,
        com.magisk317.utils.Singleton.instance<ConfigCenter>(),
        Configurations.getInstance()
        ,
        ConfigNavigationHelper(),
    )

    init {
        try {
            com.magisk317.utils.Singleton.reset(this)
        } catch (_: Throwable) {}
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

    fun copyToClipboard(info: CharSequence) {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.setPrimaryClip(android.content.ClipData.newPlainText(null, info))
    }

    fun mockMessage(containerWithRegSec: XmPushActionContainer) {
        val pushService: SdkXMPushService? = XMPushServiceLifecycleBridge.peekService()
        logger.d("EventRepository", "mockMessage called. pushService exists: ${pushService != null}")
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
            logger.d("EventRepository", "pushService is null, starting AppXMPushService (Bridge)")
            context.startService(Intent(context, AppXMPushService::class.java))
            Utils.makeText(context, "Service starting, please try again", 0)
            return
        }
        val replayContainer = containerWithRegSec.deepCopy().also {
            rewriteReplayMessageIdentityIfNeeded(it, regSec)
        }
        val handled = MockMIPushMessage.mockProcessMIPushMessage(
            pushService,
            replayContainer
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
            e.printStackTrace()
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
        } catch (_: Throwable) {
            HashSet()
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
        val intent = Intent(context, ApplicationInfoPage::class.java)
            .putExtra(ApplicationInfoPage.EXTRA_PACKAGE_NAME, packageName)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (IGNORE_NOT_REGISTERED) {
            intent.putExtra(ApplicationInfoPage.EXTRA_IGNORE_NOT_REGISTERED, true)
        }
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

    private fun rewriteReplayMessageIdentityIfNeeded(container: XmPushActionContainer, regSec: String?) {
        if (container.action != ActionType.SendMessage) return

        runCatching {
            val body = ConvertUtils.getResponseMessageBodyFromContainer(container, regSec)
            val sendMessage = body as? XmPushActionSendMessage ?: return

            val newId = "smm${System.currentTimeMillis()}${Random.nextInt(1000, 9999)}"
            val oldId = sendMessage.id
            sendMessage.id = newId

            container.metaInfo?.let { meta ->
                meta.id = newId
                meta.extra?.put(PushConstants.EXTRA_JOB_KEY, newId)
            }

            val updatedPayload = XMPushUtils.packToBytes(sendMessage)
            val finalPayload = if (container.isEncryptAction && !regSec.isNullOrBlank()) {
                val keyBytes = Base64Coder.decode(regSec)
                DataCryptUtils.mipushEncrypt(keyBytes, updatedPayload) as ByteArray
            } else {
                updatedPayload
            }
            container.setPushAction(finalPayload)

            logger.i(
                "EventRepository",
                "mock replay id rewritten old=$oldId new=$newId encrypt=${container.isEncryptAction}"
            )
        }.onFailure {
            logger.w("EventRepository", "mock replay id rewrite skipped", it)
        }
    }
    companion object {
        private val logger = object {
            fun d(tag: String, msg: String) = io.github.aakira.napier.Napier.d(msg, tag = tag)
            fun i(tag: String, msg: String) = io.github.aakira.napier.Napier.i(msg, tag = tag)
            fun w(tag: String, msg: String, t: Throwable? = null) = io.github.aakira.napier.Napier.w(msg, t, tag = tag)
            fun e(tag: String, msg: String, t: Throwable? = null) = io.github.aakira.napier.Napier.e(msg, t, tag = tag)
        }
    }
}
