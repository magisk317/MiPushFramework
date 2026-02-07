@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
package top.trumeet.mipushframework.main.subpage

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import com.google.gson.GsonBuilder
import com.nihility.Global
import com.nihility.XMPushUtils
import com.nihility.service.XMPushServiceAbility
import com.nihility.utils.MockMIPushMessage
import com.xiaomi.xmpush.thrift.XmPushActionCommandResult
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmsf.R
import com.xiaomi.xmsf.push.notification.NotificationChannelManager
import com.xiaomi.xmsf.push.notification.NotificationController
import com.xiaomi.xmsf.push.utils.Configurations
import com.xiaomi.xmsf.push.utils.RegSecUtils
import com.xiaomi.xmsf.utils.ConvertUtils
import org.apache.thrift.TBase
import top.trumeet.common.utils.CustomConfiguration
import top.trumeet.common.utils.Utils
import top.trumeet.mipush.provider.db.EventDb
import top.trumeet.mipush.provider.entities.Event
import top.trumeet.mipushframework.main.ApplicationInfoPage

class EventListPageUtils(private val context: Context) {
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

    companion object {
        @JvmStatic
        fun getEventsById(lastId: Long?, size: Int, packetName: String, query: String): List<Event> {
            var types: Set<Int>? = null
            if (!Global.ConfigCenter().isShowAllEvents) {
                types = setOf(
                    Event.Type.SendMessage,
                    Event.Type.Registration,
                    Event.Type.RegistrationResult,
                    Event.Type.UnRegistration
                )
            }
            return EventDb.queryById(lastId, size, types, packetName, query)
        }

        @JvmStatic
        fun getEvents(pageIndex: Int, pageSize: Int, packetName: String, query: String): List<Event> {
            var types: Set<Int>? = null
            if (!Global.ConfigCenter().isShowAllEvents) {
                types = setOf(
                    Event.Type.SendMessage,
                    Event.Type.Registration,
                    Event.Type.RegistrationResult,
                    Event.Type.UnRegistration
                )
            }
            return EventDb.queryByPage(pageIndex, pageSize, types, packetName, query)
        }

        @JvmStatic
        fun copyToClipboard(context: Context, info: CharSequence) {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboardManager.text = info
        }

        @JvmStatic
        fun mockMessage(containerWithRegSec: XmPushActionContainer) {
            val pushService = XMPushServiceAbility.xmPushService ?: return
            MockMIPushMessage.mockProcessMIPushMessage(
                pushService,
                containerWithRegSec.deepCopy()
            )
        }

        @JvmStatic
        fun getContent(event: Event, containerWithRegSec: XmPushActionContainer): String {
            return try {
                val newContainer = containerWithRegSec.deepCopy()
                Configurations.getInstance().handle(event.pkg, newContainer)
                containerToJson(newContainer, event.regSec).toString()
            } catch (e: Throwable) {
                e.printStackTrace()
                e.toString()
            }
        }

        @JvmStatic
        fun getJson(event: Event): CharSequence? {
            val container = event.container ?: return null
            return containerToJson(container, event.regSec)
        }

        private fun configureContainer(container: XmPushActionContainer): Set<String> {
            return try {
                Configurations.getInstance().handle(container.packageName, container)
            } catch (_: Throwable) {
                HashSet()
            }
        }

        @JvmStatic
        fun containerToJson(container: XmPushActionContainer, regSec: String?): CharSequence {
            val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()
            return gson.toJson(ConvertUtils.toJson(container, regSec))
        }

        @JvmStatic
        fun startManagePermissions(context: Context, packageName: String) {
            startManagePermissions(context, packageName, false)
        }

        @JvmStatic
        fun startManagePermissions(context: Context, packageName: String, IGNORE_NOT_REGISTERED: Boolean) {
            val intent = Intent(context, ApplicationInfoPage::class.java)
                .putExtra(ApplicationInfoPage.EXTRA_PACKAGE_NAME, packageName)
            if (IGNORE_NOT_REGISTERED) {
                intent.putExtra(ApplicationInfoPage.EXTRA_IGNORE_NOT_REGISTERED, true)
            }
            context.startActivity(intent)
        }

        @JvmStatic
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

        @JvmStatic
        fun getContainer(container: XmPushActionContainer): TBase<*, *>? {
            return try {
                ConvertUtils.getResponseMessageBodyFromContainer(container, RegSecUtils.getRegSec(container))
            } catch (_: Exception) {
                null
            }
        }
    }
}
