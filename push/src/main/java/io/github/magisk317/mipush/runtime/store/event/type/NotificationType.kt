package io.github.magisk317.mipush.runtime.store.event.type

import android.content.Context
import com.magisk317.XMPushUtils
import io.github.magisk317.mipush.common.R
import io.github.magisk317.mipush.runtime.store.entities.Event
import io.github.magisk317.mipush.runtime.store.event.EventType

/**
 * 对应 [io.github.magisk317.mipush.runtime.store.entities.Event.Type.SendMessage]
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
