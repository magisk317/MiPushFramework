package top.trumeet.common.ita

import android.content.Context

/**
 * Created by zts1993 on 2018/2/18.
 */
interface ITopActivity {
    fun isEnabled(context: Context): Boolean
    fun guideToEnable(context: Context)
    fun isAppForeground(context: Context, packageName: String): Boolean
}
