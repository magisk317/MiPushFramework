package com.xiaomi.push.clientreport

import android.content.Context
import android.text.TextUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.clientReport.PushClientReportHelper
import com.xiaomi.push.service.clientReport.PushClientReportManager
import com.xiaomi.push.service.xmpush.Command
import com.xiaomi.smack.util.TrafficUtils
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.NotificationType
import com.xiaomi.xmpush.thrift.XmPushActionAckNotification
import com.xiaomi.xmpush.thrift.XmPushActionCommand
import com.xiaomi.xmpush.thrift.XmPushActionCommandResult
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import org.apache.thrift.TBase
import org.apache.thrift.TException

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/push/clientreport/PerfMessageHelper.java
 * Stock 7.4.67-C keeps related report construction in ea.d and l5.d; no stock same-path source was found.
 */
abstract class PerfMessageHelper {
    companion object {
        @JvmStatic
        fun collectPerfData(str: String?, context: Context?, type: Int, payloadSize: Int) {
            if (type <= 0 || payloadSize <= 0) {
                return
            }
            val actualContext = context ?: throw NullPointerException("context")
            val traffic = getTraffic(actualContext, payloadSize)
            if (type != PushClientReportHelper.changeOrdinalToCode(NotificationType.UploadTinyData)) {
                PushClientReportManager.getInstance(actualContext.applicationContext)
                    .reportPerf(str ?: throw NullPointerException("packageName"), type, 1L, traffic.toLong())
            }
        }

        @JvmStatic
        fun collectPerfData(str: String?, context: Context?, tBase: TBase<*, *>?, actionType: ActionType, payloadSize: Int) {
            collectPerfData(str, context, getMessageType(tBase, actionType), payloadSize)
        }

        @JvmStatic
        fun collectUpStream(str: String?, context: Context?, container: XmPushActionContainer?, payloadSize: Int) {
            val action = container?.action ?: return
            var size = payloadSize
            val type = typeToCode(action)
            if (size <= 0) {
                val bytes = XmPushThriftSerializeUtils.convertThriftObjectToBytes(container)
                size = bytes?.size ?: 0
            }
            collectPerfData(str, context, type, size)
        }

        @JvmStatic
        fun collectUpStream(str: String?, context: Context?, payload: ByteArray?) {
            if (context == null || payload == null || payload.isEmpty()) {
                return
            }
            val container = XmPushActionContainer()
            try {
                XmPushThriftSerializeUtils.convertByteArrayToThriftObject(container, payload)
                collectUpStream(str, context, container, payload.size)
            } catch (e: TException) {
                MyLog.w("fail to convert bytes to container")
            }
        }

        @JvmStatic
        fun getMessageType(tBase: TBase<*, *>?, actionType: ActionType): Int {
            return when (actionType) {
                ActionType.Registration,
                ActionType.UnRegistration,
                ActionType.Subscription,
                ActionType.UnSubscription,
                ActionType.SendMessage,
                ActionType.AckMessage,
                ActionType.SetConfig,
                ActionType.ReportFeedback,
                ActionType.MultiConnectionBroadcast,
                ActionType.MultiConnectionResult -> PushClientReportHelper.changeValueToCode(actionType.value)

                ActionType.Notification -> getNotificationMessageType(tBase, PushClientReportHelper.changeValueToCode(actionType.value))
                ActionType.Command -> getCommandMessageType(tBase, PushClientReportHelper.changeValueToCode(actionType.value))
                else -> -1
            }
        }

        private fun getNotificationMessageType(tBase: TBase<*, *>?, fallback: Int): Int {
            if (tBase == null) {
                return fallback
            }
            return try {
                when (tBase) {
                    is XmPushActionAckNotification -> {
                        val type = tBase.type
                        if (!TextUtils.isEmpty(type)) {
                            val notificationType = PushClientReportHelper.changeValueToNotificationType(type)
                            val code = PushClientReportHelper.changeOrdinalToCode(notificationType)
                            if (code != -1) code else fallback
                        } else {
                            fallback
                        }
                    }

                    is XmPushActionNotification -> {
                        val type = tBase.type
                        if (!TextUtils.isEmpty(type)) {
                            val notificationType = PushClientReportHelper.changeValueToNotificationType(type)
                            val code = PushClientReportHelper.changeOrdinalToCode(notificationType)
                            when {
                                NotificationType.UploadTinyData == notificationType -> -1
                                code != -1 -> code
                                else -> fallback
                            }
                        } else {
                            fallback
                        }
                    }

                    else -> fallback
                }
            } catch (e: Exception) {
                MyLog.e("PERF_ERROR : parse Notification type error")
                fallback
            }
        }

        private fun getCommandMessageType(tBase: TBase<*, *>?, fallback: Int): Int {
            if (tBase == null) {
                return fallback
            }
            return try {
                val cmdName = when (tBase) {
                    is XmPushActionCommandResult -> tBase.getCmdName()
                    is XmPushActionCommand -> tBase.getCmdName()
                    else -> null
                }
                if (!TextUtils.isEmpty(cmdName)) {
                    val code = Command.getCode(cmdName)
                    if (code != -1) code else fallback
                } else {
                    fallback
                }
            } catch (e: Exception) {
                MyLog.e("PERF_ERROR : parse Command type error")
                fallback
            }
        }

        @JvmStatic
        fun getTraffic(context: Context, payloadSize: Int): Int {
            val networkType = TrafficUtils.getNetworkType(context)
            if (-1 == networkType) {
                return -1
            }
            return ((if (networkType == 0) 13 else 11) * payloadSize) / 10
        }

        @JvmStatic
        fun typeToCode(actionType: ActionType): Int {
            return PushClientReportHelper.changeValueToCode(actionType.value)
        }
    }
}
