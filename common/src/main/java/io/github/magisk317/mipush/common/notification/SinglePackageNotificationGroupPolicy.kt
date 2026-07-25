package io.github.magisk317.mipush.common.notification

import android.app.Notification
import android.os.Bundle

/**
 * Force every non-island notification of a package into one shade group.
 *
 * Native posts, MiPush delegated posts, and system auto-group leftovers otherwise use different
 * group keys (app group vs Aggregate_*), which splits one app into multiple shade stacks.
 * The NMS enqueue path is the choke point for all apps; this policy is the pure rewrite core.
 */
object SinglePackageNotificationGroupPolicy {
    const val EXTRA_TARGET_PACKAGE = "target_package"
    const val EXTRA_MIUI_TARGET_PACKAGE = "miui.targetPkg"
    const val EXTRA_XMSF_TARGET_PACKAGE = "xmsf_target_package"
    const val EXTRA_MOCK_REPLAY_SOURCE_PACKAGE = "mipush_mock_replay_source_package"
    const val EXTRA_HYPERISLAND_SOURCE_PACKAGE = "hyperisland_source_pkg"
    const val EXTRA_ISLAND_OWNER = "hyperisland.owner"
    const val ISLAND_OWNER_MARKER = "io.github.magisk317.mipush"

    private const val EXTRA_SUPPORT_GROUP_KEY = "android.support.groupKey"
    private const val EXTRA_GROUP_KEY = "android.groupKey"

    /**
     * Resolve the package identity that should own the shade group.
     * Prefer MiPush/island target markers so delegated posts group under the app, not XMSF.
     */
    @JvmStatic
    fun resolveGroupOwnerPackage(postingPackage: String, extras: Bundle?): String {
        if (postingPackage.isBlank()) return postingPackage
        val target = sequenceOf(
            extras?.getString(EXTRA_TARGET_PACKAGE),
            extras?.getString(EXTRA_MIUI_TARGET_PACKAGE),
            extras?.getString(EXTRA_XMSF_TARGET_PACKAGE),
            extras?.getString(EXTRA_MOCK_REPLAY_SOURCE_PACKAGE),
        ).firstOrNull { !it.isNullOrBlank() }
        return target?.takeIf { it.isNotBlank() } ?: postingPackage
    }

    @JvmStatic
    fun canonicalGroupKey(groupOwnerPackage: String): String = groupOwnerPackage

    @JvmStatic
    fun isIslandProxy(extras: Bundle?): Boolean {
        if (extras == null) return false
        return extras.getString(EXTRA_ISLAND_OWNER) == ISLAND_OWNER_MARKER ||
            !extras.getString(EXTRA_HYPERISLAND_SOURCE_PACKAGE).isNullOrBlank()
    }

    /**
     * MiPush-delegated posts carry target package markers. Their synthetic GROUP_SUMMARY headers
     * collide with native app summaries once both sides share the package group key, which makes
     * SystemUI thrash on "Duplicate summary for group".
     */
    @JvmStatic
    fun isMiPushDelegated(extras: Bundle?): Boolean {
        if (extras == null) return false
        return sequenceOf(
            extras.getString(EXTRA_TARGET_PACKAGE),
            extras.getString(EXTRA_MIUI_TARGET_PACKAGE),
            extras.getString(EXTRA_XMSF_TARGET_PACKAGE),
            extras.getString(EXTRA_MOCK_REPLAY_SOURCE_PACKAGE),
        ).any { !it.isNullOrBlank() }
    }

    /**
     * Drop MiPush synthetic group summaries. Children keep the package group key; native summaries
     * (if any) remain the sole header. Pure-MiPush stacks still group by key without a second header.
     */
    @JvmStatic
    fun demoteDelegatedGroupSummary(notification: Notification): Boolean {
        return demoteDelegatedGroupSummary(postingPackage = "", notification = notification)
    }

    /**
     * @param postingPackage Binder/enqueue package. XMSF-hosted posts are always treated as
     * delegated even if target extras were stripped by an OEM path.
     */
    @JvmStatic
    fun demoteDelegatedGroupSummary(postingPackage: String, notification: Notification): Boolean {
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY == 0) return false
        if (isIslandProxy(notification.extras)) return false
        val fromXmsf = postingPackage == "com.xiaomi.xmsf"
        if (!fromXmsf && !isMiPushDelegated(notification.extras)) return false
        notification.flags = notification.flags and Notification.FLAG_GROUP_SUMMARY.inv()
        return true
    }

    @JvmStatic
    fun isAggregateGroupKey(groupKey: String?): Boolean {
        if (groupKey.isNullOrBlank()) return false
        return groupKey.contains("Aggregate_", ignoreCase = false) ||
            groupKey.contains("ranker_group", ignoreCase = true)
    }

    @JvmStatic
    fun needsRewrite(
        postingPackage: String,
        currentGroup: String?,
        extras: Bundle?,
    ): Boolean {
        if (postingPackage.isBlank()) return false
        if (isIslandProxy(extras)) return false
        val owner = resolveGroupOwnerPackage(postingPackage, extras)
        if (owner.isBlank()) return false
        if (isAggregateGroupKey(currentGroup)) return true
        return currentGroup != canonicalGroupKey(owner)
    }

    /**
     * Rewrite [notification] group in-place to the single-package canonical key.
     * @return true when the group key was changed or forced successfully
     */
    @JvmStatic
    fun apply(postingPackage: String, notification: Notification): Boolean {
        val extras = notification.extras
        var changed = false
        if (needsRewrite(postingPackage, notification.group, extras)) {
            val owner = resolveGroupOwnerPackage(postingPackage, extras)
            val group = canonicalGroupKey(owner)
            if (setGroupKey(notification, group)) {
                changed = true
            }
        }
        if (demoteDelegatedGroupSummary(postingPackage, notification)) {
            changed = true
        }
        return changed
    }

    /**
     * Ensure package group even when [needsRewrite] is false (used before clearing Aggregate override).
     */
    @JvmStatic
    fun ensurePackageGroup(postingPackage: String, notification: Notification): String? {
        if (postingPackage.isBlank()) return null
        if (isIslandProxy(notification.extras)) return null
        val owner = resolveGroupOwnerPackage(postingPackage, notification.extras)
        if (owner.isBlank()) return null
        val group = canonicalGroupKey(owner)
        if (notification.group != group) {
            setGroupKey(notification, group)
        }
        demoteDelegatedGroupSummary(postingPackage, notification)
        return group
    }

    /**
     * Locate posting package + Notification inside NMS enqueue argument lists and rewrite.
     * @return true when a notification argument was rewritten
     */
    @JvmStatic
    fun applyToNmsEnqueueArgs(args: Array<Any?>): Boolean {
        val index = args.indexOfFirst { it is Notification }
        if (index < 0) return false
        val notification = args[index] as Notification
        val stringArgs = args.mapNotNull { it as? String }.filter { it.isNotBlank() }
        if (stringArgs.isEmpty()) return false
        // AOSP: enqueue*(pkg, opPkg, ...). Prefer pkg, but honor target extras either way.
        val postingPackage = stringArgs.first()
        return apply(postingPackage, notification)
    }

    private fun setGroupKey(notification: Notification, group: String): Boolean {
        var wrote = false
        runCatching {
            val field = Notification::class.java.getDeclaredField("mGroupKey")
            field.isAccessible = true
            field.set(notification, group)
            wrote = true
        }
        if (!wrote) {
            runCatching {
                val method = Notification::class.java.methods.firstOrNull {
                    it.name == "setGroup" && it.parameterTypes.contentEquals(arrayOf(String::class.java))
                } ?: return@runCatching
                method.invoke(notification, group)
                wrote = true
            }
        }
        // HyperOS Aggregate_* stacks often live on overrideGroupKey. Clear it so package group wins.
        clearOverrideGroupKey(notification)
        // Compat extras used by some builders / OEM paths.
        runCatching {
            val extras = notification.extras ?: return@runCatching
            extras.putString(EXTRA_SUPPORT_GROUP_KEY, group)
            extras.putString(EXTRA_GROUP_KEY, group)
        }
        return notification.group == group || wrote
    }

    private fun clearOverrideGroupKey(notification: Notification) {
        runCatching {
            val method = Notification::class.java.methods.firstOrNull {
                it.name == "setOverrideGroupKey" &&
                    it.parameterTypes.contentEquals(arrayOf(String::class.java))
            }
            method?.invoke(notification, null)
        }
        runCatching {
            val field = Notification::class.java.getDeclaredField("mOverrideGroupKey")
            field.isAccessible = true
            field.set(notification, null)
        }
    }
}
