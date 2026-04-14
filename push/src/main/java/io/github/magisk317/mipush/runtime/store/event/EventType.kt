package io.github.magisk317.mipush.runtime.store.event

import android.content.Context
import io.github.magisk317.mipush.Global
import io.github.magisk317.mipush.runtime.store.entities.Event

/**
 * 喂给 [Event] 的详细信息。
 *
 * Created by Trumeet on 2018/2/7.
 */
abstract class EventType(@param:Event.Type var type: Int, val info: String?, val pkg: String?, val payload: ByteArray?) {

    open fun getTitle(context: Context): CharSequence {
        return Global.ApplicationNameCache().getAppName(context, pkg ?: "") ?: (pkg ?: "")
    }

    abstract fun getSummary(context: Context): CharSequence?

    override fun toString(): String {
        return "EventType{" +
                "mType=" + type +
                ", mInfo='" + info + '\'' +
                ", pkg='" + pkg + '\'' +
                '}'
    }
}
