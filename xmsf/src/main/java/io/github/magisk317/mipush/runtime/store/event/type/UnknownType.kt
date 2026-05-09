package io.github.magisk317.mipush.runtime.store.event.type

import android.content.Context
import io.github.magisk317.mipush.runtime.store.entities.Event
import io.github.magisk317.mipush.runtime.store.event.EventType

/**
 * Created by Trumeet on 2018/2/7.
 */
class UnknownType(mType: Int, mInfo: String?, pkg: String?, payload: ByteArray?) :
    EventType(mType, mInfo, pkg, payload) {

    override fun getSummary(context: Context): CharSequence? {
        return when (type) {
            Event.Type.Registration -> "Registration"
            Event.Type.Notification -> "Notification"
            Event.Type.SendMessage -> "SendMessage"
            Event.Type.Command -> "Command"
            Event.Type.AckMessage -> "AckMessage"
            Event.Type.MultiConnectionBroadcast -> "MultiConnectionBroadcast"
            Event.Type.MultiConnectionResult -> "MultiConnectionResult"
            Event.Type.ReportFeedback -> "ReportFeedback"
            Event.Type.UnRegistration -> "UnRegistration"
            Event.Type.UnSubscription -> "UnSubscription"
            Event.Type.SetConfig -> "SetConfig"
            Event.Type.Subscription -> "Subscription"
            Event.Type.RegistrationResult -> "RegistrationResult"
            else -> null
        }
    }
}
