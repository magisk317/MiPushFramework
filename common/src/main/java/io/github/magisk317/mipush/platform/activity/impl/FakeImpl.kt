package io.github.magisk317.mipush.platform.activity.impl

import android.content.Context
import io.github.magisk317.mipush.platform.activity.ITopActivity

/**
 * Created by zts1993 on 2018/2/18.
 */
class FakeImpl : ITopActivity {

    override fun isEnabled(context: Context): Boolean {
        return true
    }

    override fun guideToEnable(context: Context) {}

    override fun isAppForeground(context: Context, packageName: String): Boolean {
        return false
    }
}
