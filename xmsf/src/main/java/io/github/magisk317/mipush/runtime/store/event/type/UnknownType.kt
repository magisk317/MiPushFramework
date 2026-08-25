package io.github.magisk317.mipush.runtime.store.event.type

import android.content.Context
import io.github.magisk317.mipush.runtime.store.adapter.EventRowType
import io.github.magisk317.mipush.runtime.store.event.EventType

/**
 * Created by Trumeet on 2018/2/7.
 */
class UnknownType(mType: Int, mInfo: String?, pkg: String?, payload: ByteArray?) :
    EventType(mType, mInfo, pkg, payload) {

    override fun getSummary(context: Context): CharSequence? {
        return when (type) {
            EventRowType.Registration -> "Registration"
            EventRowType.Notification -> "Notification"
            EventRowType.SendMessage -> "SendMessage"
            EventRowType.Command -> "Command"
            EventRowType.AckMessage -> "AckMessage"
            EventRowType.MultiConnectionBroadcast -> "MultiConnectionBroadcast"
            EventRowType.MultiConnectionResult -> "MultiConnectionResult"
            EventRowType.ReportFeedback -> "ReportFeedback"
            EventRowType.UnRegistration -> "UnRegistration"
            EventRowType.UnSubscription -> "UnSubscription"
            EventRowType.SetConfig -> "SetConfig"
            EventRowType.Subscription -> "Subscription"
            EventRowType.RegistrationResult -> "RegistrationResult"
            else -> null
        }
    }
}
