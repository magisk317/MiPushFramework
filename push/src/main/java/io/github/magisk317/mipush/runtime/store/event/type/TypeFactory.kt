package io.github.magisk317.mipush.runtime.store.event.type

import android.annotation.SuppressLint
import com.magisk317.XMPushUtils
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmsf.utils.ConvertUtils
import io.github.magisk317.mipush.runtime.store.entities.Event
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
                eventType.type = Event.Type.SendMessage
                eventType
            }
            ActionType.Notification -> NotificationType(info, pkg, payload)
            ActionType.Registration -> RegistrationResultType(info, pkg, payload)
            else -> UnknownType(getTypeId(buildContainer.getAction()), info, pkg, payload)
        }
    }

    @JvmStatic
    fun createForDisplay(eventFromDB: Event): EventType {
        val pkg = eventFromDB.pkg
        val info = eventFromDB.info
        val payload = eventFromDB.payload

        return when (eventFromDB.type) {
            Event.Type.Command -> CommandType(info, pkg, payload)
            Event.Type.Notification -> NotificationType(info, pkg, payload)
            Event.Type.SendMessage -> {
                val type = NotificationType(info, pkg, payload)
                type.type = Event.Type.SendMessage
                type
            }
            Event.Type.Registration -> RegistrationType(info, pkg, payload)
            Event.Type.RegistrationResult -> RegistrationResultType(info, pkg, payload)
            else -> UnknownType(eventFromDB.type, info, pkg, payload)
        }
    }

    @SuppressLint("WrongConstant")
    @JvmStatic
    @Event.Type
    private fun getTypeId(type: ActionType): Int {
        return when (type) {
            ActionType.Command -> Event.Type.Command
            ActionType.SendMessage -> Event.Type.SendMessage
            ActionType.Notification -> Event.Type.Notification
            ActionType.SetConfig -> Event.Type.SetConfig
            ActionType.AckMessage -> Event.Type.AckMessage
            ActionType.Registration -> Event.Type.Registration
            ActionType.Subscription -> Event.Type.Subscription
            ActionType.ReportFeedback -> Event.Type.ReportFeedback
            ActionType.UnRegistration -> Event.Type.UnRegistration
            ActionType.UnSubscription -> Event.Type.UnSubscription
            ActionType.MultiConnectionResult -> Event.Type.MultiConnectionResult
            ActionType.MultiConnectionBroadcast -> Event.Type.MultiConnectionBroadcast
            else -> -1
        }
    }
}
