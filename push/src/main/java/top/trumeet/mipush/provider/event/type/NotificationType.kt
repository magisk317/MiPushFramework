package top.trumeet.mipush.provider.event.type

import android.content.Context
import com.nihility.XMPushUtils
import top.trumeet.common.R
import top.trumeet.mipush.provider.entities.Event
import top.trumeet.mipush.provider.event.EventType

/**
 * 对应 [top.trumeet.mipush.provider.entities.Event.Type.SendMessage]
 *
 * Created by Trumeet on 2018/2/7.
 */
class NotificationType(mInfo: String?, pkg: String?, payload: ByteArray?) :
    EventType(Event.Type.Notification, mInfo, pkg, payload) {

    private val mNotificationTitle: String?
    private val mNotificationDetail: String?

    init {
        val container = XMPushUtils.packToContainer(payload)
        if (container?.metaInfo != null) {
            mNotificationTitle = container.metaInfo.title
            mNotificationDetail = container.metaInfo.description
        } else {
            mNotificationTitle = null
            mNotificationDetail = null
        }
    }

    override fun getTitle(context: Context): CharSequence {
        return mNotificationTitle ?: super.getTitle(context)
    }

    override fun getSummary(context: Context): CharSequence {
        return mNotificationDetail ?: context.getString(R.string.event_push)
    }
}
