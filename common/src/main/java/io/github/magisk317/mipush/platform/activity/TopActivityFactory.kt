package io.github.magisk317.mipush.platform.activity

import io.github.magisk317.mipush.platform.activity.impl.ActivityAccessibilityImpl
import io.github.magisk317.mipush.platform.activity.impl.ActivityUsageStatsImpl
import io.github.magisk317.mipush.platform.activity.impl.FakeImpl

/**
 * Created by zts1993 on 2018/2/18.
 */
object TopActivityFactory {
    @JvmStatic
    fun newInstance(accessMode: Int): ITopActivity {
        if (accessMode == AccessMode.ACCESSIBILITY) {
            return ActivityAccessibilityImpl()
        }

        if (accessMode == AccessMode.USAGE_STATS) {
            return ActivityUsageStatsImpl()
        }

        return FakeImpl() // should never be here
    }
}
