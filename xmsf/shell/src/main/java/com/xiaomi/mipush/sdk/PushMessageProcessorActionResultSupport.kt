package com.xiaomi.mipush.sdk

import android.content.Context
import android.text.TextUtils
import com.xiaomi.channel.commonutils.android.DeviceInfo
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.mipush.sdk.PushMessageHandler.PushMessageInterface
import com.xiaomi.push.clientreport.PerfMessageHelper
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.clientReport.PushClientReportHelper
import com.xiaomi.push.service.clientReport.PushClientReportManager
import com.xiaomi.push.service.clientReport.ReportConstants
import com.xiaomi.push.service.xmpush.Command
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.XmPushActionCommandResult
import com.xiaomi.xmpush.thrift.XmPushActionRegistrationResult
import com.xiaomi.xmpush.thrift.XmPushActionSubscriptionResult
import com.xiaomi.xmpush.thrift.XmPushActionUnSubscriptionResult
import io.github.magisk317.mipush.runtime.PushRuntime
import java.util.TimeZone

internal typealias PushMessageProcessorEventEmitter = (
    name: String,
    result: String,
    stage: String,
    reason: String,
    statusOk: Boolean,
    extra: Map<String, String>,
) -> Unit

/** Context-bound synchronous handlers for registration, subscription, and command results. */
internal class PushMessageProcessorActionResultSupport(
    context: Context,
    private val eventEmitter: PushMessageProcessorEventEmitter,
) {
    private val sAppContext: Context = context.applicationContext ?: context

    private fun emitProcessEvent(
        name: String,
        result: String,
        stage: String,
        reason: String,
        statusOk: Boolean = true,
        extra: Map<String, String> = emptyMap(),
    ) {
        eventEmitter(name, result, stage, reason, statusOk, extra)
    }

    fun processRegistrationResult(
        result: XmPushActionRegistrationResult,
        messageId: String?,
        eventMessageType: Int
    ): PushMessageInterface? {
        val appInfoHolder = AppInfoHolder.getInstance(sAppContext)
        val requestId = appInfoHolder.appRegRequestId
        MyLog.w(
            "registration result received errorCode=${result.errorCode} reason=${result.reason} " +
                "requestIdPresent=${!TextUtils.isEmpty(requestId)} resultIdMatch=${TextUtils.equals(requestId, result.id)} " +
                "resultAppIdPresent=${!TextUtils.isEmpty(result.appId)} resultAppIdMatch=${TextUtils.equals(appInfoHolder.appID, result.appId)} " +
                "regIdPresent=${!TextUtils.isEmpty(result.regId)} regSecretPresent=${!TextUtils.isEmpty(result.regSecret)} " +
                "regionPresent=${!TextUtils.isEmpty(result.region)} messageIdPresent=${!TextUtils.isEmpty(messageId)} " +
                appInfoHolder.registrationStateSummary(result.appId, appInfoHolder.appToken)
        )
        if (TextUtils.isEmpty(requestId) || !TextUtils.equals(requestId, result.id)) {
            MyLog.w("bad Registration result: " + appInfoHolder.registrationStateSummary(result.appId, appInfoHolder.appToken))
            PushClientReportManager.getInstance(sAppContext).reportEvent4ERROR(
                sAppContext.packageName,
                PushClientReportHelper.getInterfaceIdByType(eventMessageType),
                messageId ?: "",
                ReportConstants.ERROR_BAD_REGISTRATION_RESULT
            )
            emitProcessEvent(
                name = "push.register",
                result = "error",
                stage = "registration_result",
                reason = "bad_request_id",
                statusOk = false,
                extra = mapOf("target_package" to sAppContext.packageName),
            )
            return null
        }
        appInfoHolder.appRegRequestId = null
        if (result.errorCode == 0L) {
            appInfoHolder.putRegIDAndSecret(result.regId, result.regSecret, result.region)
            PushRuntime.observeRegistrationResult(
                packageName = sAppContext.packageName,
                success = true,
                source = "PushMessageProcessor.processRegistrationResult",
                reason = "server_result",
            )
            MyLog.w("registration result stored " + appInfoHolder.registrationStateSummary(result.appId, appInfoHolder.appToken))
            PushClientReportManager.getInstance(sAppContext).reportEvent(
                sAppContext.packageName,
                PushClientReportHelper.getInterfaceIdByType(eventMessageType),
                messageId ?: "",
                ReportConstants.REGISTER_TYPE_APP_SUCCESS,
                ReportConstants.REGISTER_SUCCESS
            )
            emitProcessEvent(
                name = "push.register",
                result = "ok",
                stage = "registration_result",
                reason = "success",
                extra = mapOf(
                    "target_package" to sAppContext.packageName,
                    "region_present" to (!TextUtils.isEmpty(result.region)).toString(),
                ),
            )
        } else {
            PushRuntime.observeRegistrationResult(
                packageName = sAppContext.packageName,
                success = false,
                source = "PushMessageProcessor.processRegistrationResult",
                reason = "error_code:${result.errorCode}",
            )
            MyLog.w(
                "registration result failed errorCode=${result.errorCode} reason=${result.reason} " +
                    appInfoHolder.registrationStateSummary(result.appId, appInfoHolder.appToken)
            )
            PushClientReportManager.getInstance(sAppContext).reportEvent(
                sAppContext.packageName,
                PushClientReportHelper.getInterfaceIdByType(eventMessageType),
                messageId ?: "",
                ReportConstants.REGISTER_TYPE,
                ReportConstants.REGISTER_FAIL
            )
            emitProcessEvent(
                name = "push.register",
                result = "error",
                stage = "registration_result",
                reason = "error_code",
                statusOk = false,
                extra = mapOf(
                    "target_package" to sAppContext.packageName,
                    "error_code" to result.errorCode.toString(),
                ),
            )
        }
        val args = if (!TextUtils.isEmpty(result.regId)) arrayListOf(result.regId) else null
        val commandMessage = PushMessageHelper.generateCommandMessage(
            Command.COMMAND_REGISTER.value,
            args,
            result.errorCode,
            result.reason,
            null
        )
        PushServiceClient.getInstance(sAppContext).processPendRequest()
        return commandMessage
    }

    fun processSubscriptionResult(result: XmPushActionSubscriptionResult): PushMessageInterface {
        if (result.errorCode == 0L) {
            MiPushClient.addTopic(sAppContext, result.topic)
        }
        val args = if (!TextUtils.isEmpty(result.topic)) arrayListOf(result.topic) else null
        MyLog.persist("resp-cmd:" + Command.COMMAND_SUBSCRIBE_TOPIC + ", " + result.id)
        return PushMessageHelper.generateCommandMessage(
            Command.COMMAND_SUBSCRIBE_TOPIC.value,
            args,
            result.errorCode,
            result.reason,
            result.category
        )
    }

    fun processUnSubscriptionResult(result: XmPushActionUnSubscriptionResult): PushMessageInterface {
        if (result.errorCode == 0L) {
            MiPushClient.removeTopic(sAppContext, result.topic)
        }
        val args = if (!TextUtils.isEmpty(result.topic)) arrayListOf(result.topic) else null
        MyLog.persist("resp-cmd:" + Command.COMMAND_UNSUBSCRIBE_TOPIC + ", " + result.id)
        return PushMessageHelper.generateCommandMessage(
            Command.COMMAND_UNSUBSCRIBE_TOPIC.value,
            args,
            result.errorCode,
            result.reason,
            result.category
        )
    }

    fun processCommandResult(result: XmPushActionCommandResult, payload: ByteArray): PushMessageInterface? {
        PerfMessageHelper.collectPerfData(sAppContext.packageName, sAppContext, result, ActionType.Command, payload.size)
        val cmdName = result.cmdName
        var cmdArgs = result.cmdArgs
        if (result.errorCode == 0L) {
            if (TextUtils.equals(cmdName, Command.COMMAND_SET_ACCEPT_TIME.value) && cmdArgs != null && cmdArgs.size > 1) {
                MiPushClient.addAcceptTime(sAppContext, cmdArgs[0], cmdArgs[1])
                AppInfoHolder.getInstance(sAppContext).setPaused("00:00" == cmdArgs[0] && "00:00" == cmdArgs[1])
                cmdArgs = PushMessageProcessorTimeZoneConverter.convert(
                    TimeZone.getTimeZone("GMT+08"),
                    TimeZone.getDefault(),
                    cmdArgs
                )
            } else if (TextUtils.equals(cmdName, Command.COMMAND_SET_ALIAS.value) && !cmdArgs.isNullOrEmpty()) {
                MiPushClient.addAlias(sAppContext, cmdArgs[0])
            } else if (TextUtils.equals(cmdName, Command.COMMAND_UNSET_ALIAS.value) && !cmdArgs.isNullOrEmpty()) {
                MiPushClient.removeAlias(sAppContext, cmdArgs[0])
            } else if (TextUtils.equals(cmdName, Command.COMMAND_SET_ACCOUNT.value) && !cmdArgs.isNullOrEmpty()) {
                MiPushClient.addAccount(sAppContext, cmdArgs[0])
            } else if (TextUtils.equals(cmdName, Command.COMMAND_UNSET_ACCOUNT.value) && !cmdArgs.isNullOrEmpty()) {
                MiPushClient.removeAccount(sAppContext, cmdArgs[0])
            } else if (TextUtils.equals(cmdName, Command.COMMAND_CHK_VDEVID.value)) {
                if (cmdArgs.isNullOrEmpty()) {
                    return null
                }
                DeviceInfo.updateVirtDevId(sAppContext, cmdArgs[0])
                return null
            }
        }
        MyLog.persist("resp-cmd:$cmdName, " + result.id)
        val cmdToken = cmdName?.takeIf { it.isNotBlank() } ?: "unknown"
        val ok = result.errorCode == 0L
        emitProcessEvent(
            name = "push.control",
            result = if (ok) "ok" else "error",
            stage = "command_result",
            reason = if (ok) "success" else "error_code",
            statusOk = ok,
            extra = mapOf(
                "target_package" to sAppContext.packageName,
                "operation" to cmdToken,
                "error_code" to result.errorCode.toString(),
            ),
        )
        return PushMessageHelper.generateCommandMessage(
            cmdName,
            cmdArgs,
            result.errorCode,
            result.reason,
            result.category
        )
    }
}
