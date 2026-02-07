package top.trumeet.common.ita

import android.os.Build
import top.trumeet.common.ita.impl.ActivityAccessibilityImpl
import top.trumeet.common.ita.impl.ActivityUsageStatsImpl
import top.trumeet.common.ita.impl.FakeImpl

/**
 * Created by zts1993 on 2018/2/18.
 */
object TopActivityFactory {
    @JvmStatic
    fun newInstance(accessMode: Int): ITopActivity {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return ActivityAccessibilityImpl()
        }

        if (accessMode == AccessMode.ACCESSIBILITY) {
            return ActivityAccessibilityImpl()
        }

        if (accessMode == AccessMode.USAGE_STATS) {
            return ActivityUsageStatsImpl()
        }

        return FakeImpl() // should never be here
    }
}
