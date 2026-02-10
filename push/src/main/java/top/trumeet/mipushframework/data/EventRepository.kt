package top.trumeet.mipushframework.data

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import com.magisk317.XMPushUtils
import com.magisk317.service.XMPushServiceAbility
import com.magisk317.utils.MockMIPushMessage
import com.xiaomi.xmpush.thrift.XmPushActionCommandResult
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
import top.trumeet.mipushframework.main.ApplicationInfoPage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val configCenter: ConfigCenter,
    private val configurations: Configurations
) {
    // No-arg fallback for legacy Singleton access.
    constructor() : this(
        top.trumeet.common.utils.Utils.getApplication()!!,
        com.magisk317.utils.Singleton.instance<ConfigCenter>(),
        Configurations.getInstance()
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
        if (!configCenter.isShowAllEvents) {
            types = setOf(
                Event.Type.SendMessage,
                Event.Type.Registration,
                Event.Type.RegistrationResult,
                Event.Type.UnRegistration
            )
        }
        return EventDb.queryById(lastId, size, types, packetName, query)
    }

    fun getEvents(pageIndex: Int, pageSize: Int, packetName: String, query: String): List<Event> {
        var types: Set<Int>? = null
        if (!configCenter.isShowAllEvents) {
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
        val pushService: SdkXMPushService? = XMPushServiceAbility.xmPushService
        if (pushService == null) {
            context.startService(Intent(context, AppXMPushService::class.java))
            Utils.makeText(context, "Service starting, please try again", 0)
            return
        }
        MockMIPushMessage.mockProcessMIPushMessage(
            pushService,
            containerWithRegSec.deepCopy()
        )
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
}
