package com.xiaomi.xmsf.stock

import android.content.Context
import android.os.Bundle
import com.xiaomi.xmpush.thrift.XmPushActionContainer

/**
 * Stock-compatible implementation of the profile-id provider state.
 *
 * The wire contract is intentionally kept separate from the other stock surfaces: profile calls
 * use String result codes and bind all state to the Binder caller's package, whereas the generic
 * push-support provider uses integer result codes and a nested data Bundle.
 */
internal object StockProfileIdStore {
    private const val PROFILE_PREFS = "mipush_profile_id"
    private const val REGISTERED_PACKAGE_PREFS = "pref_registered_pkg_names"
    private const val LEGACY_PREFS = "stock_surface"
    private const val LEGACY_PROFILE_PREFIX = "profile:"
    private const val SEPARATOR = ";#;"
    private const val MAX_PROFILE_IDS = 10
    private const val MAX_PROFILE_ID_LENGTH = 64

    private const val CODE_OK = "0"
    private const val CODE_CALLER_MISSING = "2"
    private const val CODE_NOT_REGISTERED = "3"
    private const val CODE_METHOD_MISSING = "4"
    private const val CODE_PROFILE_ID_MISSING = "5"
    private const val CODE_EXCEPTION = "6"
    private const val CODE_RESERVED_SEPARATOR = "7"
    private const val CODE_PROFILE_ID_TOO_LONG = "8"
    private const val CODE_UNKNOWN_METHOD = "9"

    private val supportedMethods = setOf(
        "addProfileId",
        "deleteProfileId",
        "queryProfileIds",
        "deleteAllProfileId",
    )

    fun handle(
        context: Context,
        method: String?,
        extras: Bundle?,
        callingPackage: String?,
    ): Bundle {
        return try {
            val packageName = callingPackage?.takeIf { it.isNotBlank() }
                ?: return result(CODE_CALLER_MISSING)
            if (!registeredPrefs(context).contains(packageName)) {
                return result(CODE_NOT_REGISTERED)
            }
            if (method.isNullOrBlank()) {
                return result(CODE_METHOD_MISSING)
            }

            // Stock reads profileId before dispatching the method. Keeping that ordering also
            // makes a missing extras Bundle an explicit execution error instead of inventing a
            // method-specific default.
            val profileId = requireNotNull(extras) { "extras_required" }.getString("profileId")
            if (method == "addProfileId" || method == "deleteProfileId") {
                if (profileId.isNullOrEmpty()) {
                    return result(CODE_PROFILE_ID_MISSING)
                }
                if (profileId.length > MAX_PROFILE_ID_LENGTH) {
                    return result(CODE_PROFILE_ID_TOO_LONG)
                }
                if (profileId.contains(SEPARATOR)) {
                    return result(CODE_RESERVED_SEPARATOR)
                }
            }

            when (method) {
                "addProfileId" -> {
                    val ids = read(context, packageName).toMutableList()
                    // This order deliberately mirrors stock: capacity is made available before
                    // checking for a duplicate profile id.
                    while (ids.size >= MAX_PROFILE_IDS) {
                        ids.removeAt(0)
                    }
                    if (!ids.contains(profileId)) {
                        ids += requireNotNull(profileId)
                    }
                    write(context, packageName, ids)
                    result(CODE_OK)
                }

                "deleteProfileId" -> {
                    val ids = read(context, packageName).toMutableList()
                    ids.remove(profileId)
                    write(context, packageName, ids)
                    result(CODE_OK)
                }

                "queryProfileIds" -> result(CODE_OK).apply {
                    putStringArrayList("allProfileIds", ArrayList(read(context, packageName)))
                }

                "deleteAllProfileId" -> {
                    profilePrefs(context).edit().remove(packageName).apply()
                    result(CODE_OK)
                }

                else -> result(CODE_UNKNOWN_METHOD)
            }
        } catch (_: Throwable) {
            result(CODE_EXCEPTION)
        }
    }

    fun isAllowed(context: Context, container: XmPushActionContainer): Boolean {
        val profileId = container.metaInfo?.extra
            ?.get("profileId")
            ?.takeIf { it.isNotEmpty() }
            ?: return true
        val packageName = container.packageName?.takeIf { it.isNotBlank() } ?: return false
        return read(context, packageName).any { it == profileId }
    }

    internal fun isSupportedMethod(method: String): Boolean = method in supportedMethods

    internal fun read(context: Context, packageName: String): List<String> {
        val prefs = profilePrefs(context)
        migrateLegacyValueIfNeeded(context, prefs, packageName)
        return prefs.getString(packageName, "")
            .orEmpty()
            .split(SEPARATOR)
            .filter(String::isNotEmpty)
    }

    internal fun clear(context: Context, packageName: String) {
        if (packageName.isBlank()) return
        profilePrefs(context).edit().remove(packageName).apply()
    }

    private fun write(context: Context, packageName: String, ids: List<String>) {
        profilePrefs(context).edit().putString(packageName, ids.joinToString(SEPARATOR)).apply()
    }

    private fun migrateLegacyValueIfNeeded(
        context: Context,
        profilePrefs: android.content.SharedPreferences,
        packageName: String,
    ) {
        if (profilePrefs.contains(packageName)) return
        val legacy = context.applicationContext
            .getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            .getString(LEGACY_PROFILE_PREFIX + packageName, null)
            ?: return
        val bounded = legacy.split(SEPARATOR)
            .filter { it.isNotEmpty() && it.length <= MAX_PROFILE_ID_LENGTH && !it.contains(SEPARATOR) }
            .takeLast(MAX_PROFILE_IDS)
        profilePrefs.edit().putString(packageName, bounded.joinToString(SEPARATOR)).apply()
    }

    private fun profilePrefs(context: Context) = context.applicationContext
        .getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)

    private fun registeredPrefs(context: Context) = context.applicationContext
        .getSharedPreferences(REGISTERED_PACKAGE_PREFS, Context.MODE_PRIVATE)

    private fun result(code: String) = Bundle().apply { putString(StockSurfaceSupport.KEY_CODE, code) }
}
