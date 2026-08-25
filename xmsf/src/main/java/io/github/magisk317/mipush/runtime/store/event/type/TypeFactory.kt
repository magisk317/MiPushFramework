package io.github.magisk317.mipush.runtime.store.event.type

import android.annotation.SuppressLint
import io.github.magisk317.mipush.platform.support.XMPushUtils
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.magisk317.mipush.utils.ConvertUtils
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeEventRow
import io.github.magisk317.mipush.runtime.store.kmp.EventRowType
import io.github.magisk317.mipush.runtime.store.event.EventType

/**
 * Created by Trumeet on 2018/2/7.
 */
object TypeFactory {
    @JvmStatic
    fun createForStore(buildContainer: XmPushActionContainer): EventType {
        val pkg = buildContainer.packageName
        val info = ConvertUtils.toJson(buildContainer).toString()
        val payload = XMPushUtils.packToBytes(buildContainer)

        return when (buildContainer.getAction()) {
            ActionType.SendMessage -> {
                val eventType = NotificationType(info, pkg, payload)
                eventType.type = EventRowType.SendMessage
                eventType
            }
            ActionType.Notification -> NotificationType(info, pkg, payload)
            ActionType.Registration -> RegistrationResultType(info, pkg, payload)
            else -> UnknownType(getTypeId(buildContainer.getAction()), info, pkg, payload)
        }
    }

    @JvmStatic
    fun createForDisplay(eventFromDB: RuntimeEventRow): EventType {
        val pkg = eventFromDB.pkg
        val info = eventFromDB.info
        val payload = eventFromDB.payload

        return when (eventFromDB.type) {
            EventRowType.Command -> CommandType(info, pkg, payload)
            EventRowType.Notification -> NotificationType(info, pkg, payload)
            EventRowType.SendMessage -> {
                val type = NotificationType(info, pkg, payload)
                type.type = EventRowType.SendMessage
                type
            }
            EventRowType.Registration -> RegistrationType(info, pkg, payload)
            EventRowType.RegistrationResult -> RegistrationResultType(info, pkg, payload)
            else -> UnknownType(eventFromDB.type, info, pkg, payload)
        }
    }

    @SuppressLint("WrongConstant")
    @JvmStatic
    private fun getTypeId(type: ActionType): Int {
        return when (type) {
            ActionType.Command -> EventRowType.Command
            ActionType.SendMessage -> EventRowType.SendMessage
            ActionType.Notification -> EventRowType.Notification
            ActionType.SetConfig -> EventRowType.SetConfig
            ActionType.AckMessage -> EventRowType.AckMessage
            ActionType.Registration -> EventRowType.Registration
            ActionType.Subscription -> EventRowType.Subscription
            ActionType.ReportFeedback -> EventRowType.ReportFeedback
            ActionType.UnRegistration -> EventRowType.UnRegistration
            ActionType.UnSubscription -> EventRowType.UnSubscription
            ActionType.MultiConnectionResult -> EventRowType.MultiConnectionResult
            ActionType.MultiConnectionBroadcast -> EventRowType.MultiConnectionBroadcast
            else -> -1
        }
    }
}
