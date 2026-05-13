package io.github.magisk317.mipush.hook.fakedevice.compat

import android.content.Context
import android.content.pm.ComponentInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle

object ModuleCredentialResolver {
    private val appIdKeys = arrayOf(
        "com.xiaomi.push.api_id",
        "com.xiaomi.push.app_id",
        "com.xiaomi.mipush.APP_ID",
        "org.android.agoo.xiaomi.app_id",
        "mipush_app_id",
        "MIPUSH_APPID",
        "MI_PUSH_APP_ID",
        "MIAPP_ID",
        "XM_APP_ID",
        "XIAOMI_APP_ID",
        "XIAOMI_PUSH_APP_ID",
    )

    private val appKeyKeys = arrayOf(
        "com.xiaomi.push.api_key",
        "com.xiaomi.push.app_key",
        "com.xiaomi.mipush.APP_KEY",
        "org.android.agoo.xiaomi.app_key",
        "mipush_app_key",
        "MIPUSH_APPKEY",
        "MI_PUSH_APP_KEY",
        "MIAPP_KEY",
        "XM_APP_KEY",
        "XIAOMI_APP_KEY",
        "XIAOMI_PUSH_APP_KEY",
    )

    fun resolve(context: Context, packageName: String): ModuleCredential? {
        ModuleCompatRegistry.credentialOverride(packageName)?.let { return it }
        val packageInfo = try {
            context.packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_META_DATA or
                    PackageManager.GET_RECEIVERS or
                    PackageManager.GET_SERVICES or
                    PackageManager.GET_PROVIDERS or
                    PackageManager.GET_ACTIVITIES,
            )
        } catch (_: Throwable) {
            return null
        }
        val entries = collectMetadataEntries(context, packageInfo)
        return resolveFromMetadataEntries(entries)
    }

    internal fun resolveFromMetadataEntries(entries: List<Map<String, String>>): ModuleCredential? {
        entries.forEach { entry ->
            val appId = firstMetaValue(entry, appIdKeys)
            val appKey = firstMetaValue(entry, appKeyKeys)
            if (!appId.isNullOrBlank() && !appKey.isNullOrBlank()) {
                return ModuleCredential(appId = appId, appKey = appKey)
            }
        }

        val appIdFallback = findFallbackValue(entries, isAppId = true) ?: return null
        val appKeyFallback = findFallbackValue(entries, isAppId = false) ?: return null
        return ModuleCredential(appId = appIdFallback, appKey = appKeyFallback)
    }

    private fun collectMetadataEntries(context: Context, packageInfo: PackageInfo): List<Map<String, String>> {
        val entries = ArrayList<Map<String, String>>()
        packageInfo.applicationInfo?.metaData?.let { entries += bundleToStringMap(context, it) }
        appendComponentMetadata(entries, context, packageInfo.receivers)
        appendComponentMetadata(entries, context, packageInfo.services)
        appendComponentMetadata(entries, context, packageInfo.providers)
        appendComponentMetadata(entries, context, packageInfo.activities)
        return entries
    }

    private fun appendComponentMetadata(
        target: MutableList<Map<String, String>>,
        context: Context,
        components: Array<out ComponentInfo>?,
    ) {
        components?.forEach { component ->
            component.metaData?.let { target += bundleToStringMap(context, it) }
        }
    }

    private fun bundleToStringMap(context: Context, meta: Bundle): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for (key in meta.keySet()) {
            val value = resolveMetaValue(context, meta, key) ?: continue
            result[key] = value
        }
        return result
    }

    private fun firstMetaValue(meta: Map<String, String>, keys: Array<String>): String? {
        keys.forEach { key ->
            val value = meta[key]?.let(::normalizeCredentialValue)
            if (!value.isNullOrBlank()) {
                return value
            }
        }
        return null
    }

    private fun resolveMetaValue(context: Context, meta: Bundle, key: String): String? {
        if (!meta.containsKey(key)) {
            return null
        }
        @Suppress("DEPRECATION")
        val valueAny = meta.get(key) ?: return null
        return when (valueAny) {
            is String -> valueAny
            is Int -> runCatching { context.getString(valueAny) }.getOrNull()
            else -> null
        }
    }

    private fun findFallbackValue(entries: List<Map<String, String>>, isAppId: Boolean): String? {
        entries.forEach { entry ->
            entry.forEach { (key, rawValue) ->
                val value = normalizeCredentialValue(rawValue)
                if (value.isEmpty()) {
                    return@forEach
                }
                val keyNormalized = key.lowercase()
                if (isAppId) {
                    if ((keyNormalized.contains("xiaomi") || keyNormalized.contains("mipush") || keyNormalized.contains("app_id")) &&
                        value.matches(Regex("^\\d{10,}$"))
                    ) {
                        return value
                    }
                } else {
                    if ((keyNormalized.contains("app_key") || keyNormalized.contains("appkey") || keyNormalized.contains("api_key")) &&
                        value.matches(Regex("^[0-9A-Za-z]{12,}$"))
                    ) {
                        return value
                    }
                }
            }
        }
        return null
    }

    private fun normalizeCredentialValue(rawValue: String): String {
        val value = rawValue.trim()
        val strippedPrefix = value.substringAfter("=", value).trim()
        return if (strippedPrefix.matches(Regex("^\\d+L$"))) {
            strippedPrefix.dropLast(1)
        } else {
            strippedPrefix
        }
    }
}
