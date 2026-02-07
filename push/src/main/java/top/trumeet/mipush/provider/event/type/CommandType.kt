package top.trumeet.mipush.provider.event.type

import android.content.Context
import top.trumeet.common.R
import top.trumeet.mipush.provider.entities.Event
import top.trumeet.mipush.provider.event.EventType

/**
 * Created by Trumeet on 2018/2/7.
 */
class CommandType(mInfo: String?, pkg: String?, payload: ByteArray?) :
    EventType(Event.Type.Command, mInfo, pkg, payload) {

    override fun getSummary(context: Context): CharSequence {
        return context.getString(R.string.event_command)
    }
}
