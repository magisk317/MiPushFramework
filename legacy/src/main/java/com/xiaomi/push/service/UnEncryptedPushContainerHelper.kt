package com.xiaomi.push.service

import android.content.Context
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.XmPushActionAckMessage
import com.xiaomi.xmpush.thrift.XmPushActionAckNotification
import com.xiaomi.xmpush.thrift.XmPushActionCommand
import com.xiaomi.xmpush.thrift.XmPushActionCommandResult
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushActionRegistration
import com.xiaomi.xmpush.thrift.XmPushActionRegistrationResult
import com.xiaomi.xmpush.thrift.XmPushActionSendFeedbackResult
import com.xiaomi.xmpush.thrift.XmPushActionSendMessage
import com.xiaomi.xmpush.thrift.XmPushActionSubscription
import com.xiaomi.xmpush.thrift.XmPushActionSubscriptionResult
import com.xiaomi.xmpush.thrift.XmPushActionUnRegistration
import com.xiaomi.xmpush.thrift.XmPushActionUnRegistrationResult
import com.xiaomi.xmpush.thrift.XmPushActionUnSubscription
import com.xiaomi.xmpush.thrift.XmPushActionUnSubscriptionResult
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import org.apache.thrift.TBase
import org.apache.thrift.TException

object UnEncryptedPushContainerHelper {
    private fun createRespMessageFromAction(actionType: ActionType, isRequest: Boolean): TBase<*, *>? {
        return when (actionType) {
            ActionType.Registration -> if (isRequest) XmPushActionRegistration() else XmPushActionRegistrationResult()
            ActionType.UnRegistration -> if (isRequest) XmPushActionUnRegistration() else XmPushActionUnRegistrationResult()
            ActionType.Subscription -> if (isRequest) XmPushActionSubscription() else XmPushActionSubscriptionResult()
            ActionType.UnSubscription -> if (isRequest) XmPushActionUnSubscription() else XmPushActionUnSubscriptionResult()
            ActionType.SendMessage -> XmPushActionSendMessage()
            ActionType.AckMessage -> XmPushActionAckMessage()
            ActionType.SetConfig -> XmPushActionCommandResult()
            ActionType.ReportFeedback -> XmPushActionSendFeedbackResult()
            ActionType.Notification -> {
                if (isRequest) {
                    XmPushActionNotification()
                } else {
                    XmPushActionAckNotification().apply { setErrorCodeIsSet(true) }
                }
            }
            ActionType.Command -> if (isRequest) XmPushActionCommand() else XmPushActionCommandResult()
            else -> null
        }
    }

    @JvmStatic
    @Throws(TException::class)
    fun getResponseMessageBodyFromContainer(
        context: Context,
        container: XmPushActionContainer,
    ): TBase<*, *>? {
        if (container.isEncryptAction) {
            return null
        }
        val pushAction = container.getPushAction()
        val response = createRespMessageFromAction(container.action, container.isRequest) ?: return null
        XmPushThriftSerializeUtils.convertByteArrayToThriftObject(response, pushAction)
        return response
    }
}
