package com.xiaomi.push.service.clientReport
import io.github.magisk317.mipush.protocol.model.*

import android.content.Context
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.clientreport.data.ClientReportConstants
import com.xiaomi.clientreport.data.Config
import com.xiaomi.clientreport.data.EventClientReport
import com.xiaomi.clientreport.data.PerfClientReport
import com.xiaomi.clientreport.manager.ClientReportClient
import com.xiaomi.push.service.OnlineConfig
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.TinyDataHelper
import com.xiaomi.push.service.TinyDataStorage
import com.xiaomi.push.service.xmpush.Command
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.ClientUploadDataItem
import com.xiaomi.xmpush.thrift.ConfigKey
import com.xiaomi.xmpush.thrift.NotificationType
import java.util.HashMap

object PushClientReportHelper {
    private var mUploader: Uploader? = null
    private var notificationTypeMap: MutableMap<String, NotificationType>? = null

    fun interface Uploader {
        fun uploader(context: Context, clientUploadDataItem: ClientUploadDataItem)
    }

    @JvmStatic
    fun changeOrdinalToCode(enum: Enum<*>): Int {
        return when (enum) {
            is ActionType -> enum.ordinal + 1001
            is NotificationType -> enum.ordinal + 2001
            is Command -> enum.ordinal + 3001
            else -> -1
        }
    }

    @JvmStatic
    fun changeValueToCode(i: Int): Int {
        return if (i > 0) i + 1000 else -1
    }

    @JvmStatic
    fun changeValueToNotificationType(str: String): NotificationType {
        val map = notificationTypeMap ?: synchronized(NotificationType::class.java) {
            notificationTypeMap ?: run {
                val newMap = HashMap<String, NotificationType>()
                for (notificationType in NotificationType.values()) {
                    newMap[notificationType.value.lowercase()] = notificationType
                }
                notificationTypeMap = newMap
                newMap
            }
        }
        return map[str.lowercase()] ?: NotificationType.Invalid
    }

    @JvmStatic
    fun checkConfigChange(context: Context) {
        ClientReportClient.updateConfig(context, getConfig(context))
    }

    @JvmStatic
    fun getConfig(context: Context): Config {
        val onlineConfig = OnlineConfig.getInstance(context)
        val eventSwitch = onlineConfig.getBooleanValue(ConfigKey.EventUploadNewSwitch.value, false)
        val perfSwitch = onlineConfig.getBooleanValue(ConfigKey.PerfUploadSwitch.value, false)
        val eventFreq = onlineConfig.getIntValue(ConfigKey.EventUploadFrequency.value, 86400)
        val perfFreq = onlineConfig.getIntValue(ConfigKey.PerfUploadFrequency.value, 86400)
        return Config.getBuilder()
            .setEventUploadSwitchOpen(eventSwitch)
            .setEventUploadFrequency(eventFreq.toLong())
            .setPerfUploadSwitchOpen(perfSwitch)
            .setPerfUploadFrequency(perfFreq.toLong())
            .build(context)
    }

    @JvmStatic
    fun getInterfaceIdByType(i: Int): String {
        return when (i) {
            1000 -> ReportConstants.NOTIFICATION_EVENT_CHAIN_INTERFACE_ID
            3000 -> ReportConstants.AWAKE_EVENT_CHAIN_INTERFACE_ID
            2000 -> ReportConstants.THROUGH_EVENT_CHAIN_INTERFACE_ID
            6000 -> ReportConstants.REGISTER_EVENT_CHAIN_INTERFACE_ID
            else -> ""
        }
    }

    @JvmStatic
    fun initEventPerfLogic(context: Context, config: Config) {
        ClientReportClient.init(context, config, MIPushEventDataProcessor(context), MIPushPerfDataProcessor(context))
    }

    @JvmStatic
    fun isInXmsf(context: Context?): Boolean {
        return context != null &&
            context.packageName.isNotEmpty() &&
            PushConstants.PUSH_SERVICE_PACKAGE_NAME == context.packageName
    }

    @JvmStatic
    fun newEvent(str: String): EventClientReport {
        return EventClientReport().apply {
            production = 1000
            reportType = 1001
            clientInterfaceId = str
        }
    }

    @JvmStatic
    fun newPerf(): PerfClientReport {
        return PerfClientReport().apply {
            production = 1000
            reportType = 1000
            clientInterfaceId = ReportConstants.DATA_FLOW_INTERFACE_ID
        }
    }

    @JvmStatic
    fun reportEvent2Object(context: Context?, str: String, str2: String, i: Int, j: Long, str3: String?): EventClientReport {
        return newEvent(str).apply {
            eventId = str2
            eventType = i
            eventTime = j
            eventContent = str3
        }
    }

    @JvmStatic
    fun reportPerf2Object(context: Context?, i: Int, j: Long, j2: Long): PerfClientReport {
        return newPerf().apply {
            code = i
            perfCounts = j
            perfLatencies = j2
        }
    }

    private fun sendByTinyData(context: Context, clientUploadDataItem: ClientUploadDataItem) {
        if (isInXmsf(context.applicationContext)) {
            TinyDataStorage.cacheTinyData(context.applicationContext, clientUploadDataItem)
            return
        }
        mUploader?.uploader(context, clientUploadDataItem)
    }

    @JvmStatic
    fun sendData(context: Context, list: List<String>?) {
        if (list == null) return
        try {
            for (str in list) {
                val item = wrapperData(context, str) ?: continue
                if (!TinyDataHelper.verify(item, false)) {
                    sendByTinyData(context, item)
                }
            }
        } catch (th: Throwable) {
            MyLog.e(th.message ?: "unknown error")
        }
    }

    @JvmStatic
    fun setUploader(uploader: Uploader?) {
        mUploader = uploader
    }

    @JvmStatic
    fun wrapperData(context: Context, str: String?): ClientUploadDataItem? {
        if (str.isNullOrEmpty()) return null
        return ClientUploadDataItem().apply {
            category = "category_client_report_data"
            channel = "push_sdk_channel"
            counter = 1L
            this.setData(str)
            setFromSdk(true)
            timestamp = System.currentTimeMillis()
            pkgName = context.packageName
            sourcePackage = PushConstants.PUSH_SERVICE_PACKAGE_NAME
            id = TinyDataHelper.nextTinyDataItemId()
            name = ClientReportConstants.NAME
        }
    }
}
