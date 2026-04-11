package io.github.magisk317.mipush.runtime.store.event.type

import android.content.Context
import io.github.magisk317.mipush.common.R
import io.github.magisk317.mipush.runtime.store.entities.Event
import io.github.magisk317.mipush.runtime.store.event.EventType

/**
 * Created by Trumeet on 2018/2/7.
 */
class RegistrationType(mInfo: String?, pkg: String?, payload: ByteArray?) :
    EventType(Event.Type.Registration, mInfo, pkg, payload) {

    override fun getSummary(context: Context): CharSequence {
        return context.getString(R.string.event_register)
    }
}
