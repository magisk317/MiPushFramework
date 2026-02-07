@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
package top.trumeet.mipush.provider.event

import android.content.Context
import com.nihility.Global
import top.trumeet.mipush.provider.entities.Event

/**
 * 喂给 [Event] 的详细信息。
 *
 * Created by Trumeet on 2018/2/7.
 */
abstract class EventType(@Event.Type var type: Int, val info: String?, val pkg: String?, val payload: ByteArray?) {

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
