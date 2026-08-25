package io.github.magisk317.mipush.notification

import android.content.Context
import android.os.Build
import android.os.Bundle
import com.xiaomi.push.service.OnlineConfig
import com.xiaomi.xmpush.thrift.ConfigKey

/**
 * Android 16 (API 36) notification grouping strategy.
 *
 * On Android 16+, the platform may force ungrouped notifications into a system-managed group.
 * Stock XMSF 7.4.67-C uses remote config key 208 ([ConfigKey.AndroidWGroupStrategy]) to control
 * opt-out behavior via the `miui_skipForceGroup` notification extra:
 *
 * - Strategy 1 (default): opt out only when the notification already carries an explicit group.
 * - Strategy 2: always opt out (skip force grouping for all notifications).
 * - Strategy 3: never opt out (allow platform force grouping for all notifications).
 *
 * On Android < 16 (API < 36), this strategy is a no-op — it never applies the skip flag.
 *
 * @see <a href="https://developer.android.com/about/versions/16">Android 16 behavior changes</a>
 */
object AndroidWGroupStrategy {

    private const val MIUI_SKIP_FORCE_GROUP = "miui_skipForceGroup"

    /**
     * Whether the current device runs Android 16+ and this strategy is applicable.
     */
    @JvmStatic
    val isApplicable: Boolean
        get() = Build.VERSION.SDK_INT >= 36

    /**
     * Evaluates whether the given notification should skip Android 16's forced grouping,
     * and applies the `miui_skipForceGroup` extra to [notificationExtras] if so.
     *
     * This method is a no-op on Android < 16.
     *
     * @param context Application context used to read the remote config value.
     * @param sourceGroup The notification's explicit group key (from payload), or null if none.
     * @param notificationExtras The notification's extras Bundle to write the flag into.
     * @return `true` if the skip flag was applied, `false` otherwise.
     */
    @JvmStatic
    fun applyIfEligible(
        context: Context,
        sourceGroup: String?,
        notificationExtras: Bundle,
    ): Boolean {
        if (!isApplicable) return false

        val strategy = OnlineConfig.getInstance(context).getIntValue(
            ConfigKey.AndroidWGroupStrategy.value,
            1,
        )
        if (shouldSkipForceGroup(sourceGroup, strategy)) {
            notificationExtras.putBoolean(MIUI_SKIP_FORCE_GROUP, true)
            return true
        }
        return false
    }

    /**
     * Pure decision function: given the notification's source group and the remote strategy value,
     * determines whether Android 16 force-grouping should be skipped.
     *
     * Stock 7.4.67-C t0.l control flow:
     * - Strategy 1 (default): skip only if the notification already has an explicit group.
     * - Strategy 2: always skip.
     * - Strategy 3: never skip.
     * - Any other value: treated as strategy 1 (skip if group present).
     *
     * @param sourceGroup The notification's original group key, or null/empty if none.
     * @param strategy The integer strategy value from config key 208.
     * @return `true` if force grouping should be skipped.
     */
    @JvmStatic
    fun shouldSkipForceGroup(sourceGroup: String?, strategy: Int): Boolean {
        return when (strategy) {
            2 -> true
            3 -> false
            else -> !sourceGroup.isNullOrEmpty()
        }
    }
}
