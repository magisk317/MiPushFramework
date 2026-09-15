package io.github.magisk317.mipush.service.runtime

import android.content.Context
import com.xiaomi.channel.commonutils.android.AppInfoUtils
import com.xiaomi.channel.commonutils.android.DeviceInfo
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.channel.commonutils.string.XMStringUtils
import io.github.magisk317.mipush.common.utils.logW
import com.xiaomi.push.service.MIPushHelper
import com.xiaomi.push.service.PacketHelper
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.PushRegistrationPayloadRepairResult
import com.xiaomi.push.service.PushVersionInfo
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.RegistrationReason
import com.xiaomi.xmpush.thrift.XmPushActionRegistration
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.github.magisk317.xposed.logging.MagiskOtel

object RegistrationPayloadRepair {
    private const val COMPAT_PROFILES_ASSET = "compat-profiles.json"
    private val json = Json { ignoreUnknownKeys = true }

    private data class Credential(
        val packageName: String,
        val appId: String,
        val appKey: String,
    )

    @Volatile
    private var credentialCache: Map<String, Credential>? = null

    @JvmStatic
    fun repair(context: Context, packageName: String): PushRegistrationPayloadRepairResult? {
        if (packageName.isBlank()) {
            emitRepair(result = "skip", reason = "blank_package", statusOk = false)
            return null
        }
        val credential = credentialForPackage(context, packageName) ?: manifestCredential(context, packageName)
        if (credential == null) {
            emitRepair(
                result = "skip",
                reason = "no_credential",
                statusOk = false,
                extra = mapOf("target_package" to packageName),
            )
            return null
        }
        val payload = buildRegistrationPayload(
            context = context,
            packageName = packageName,
            appId = credential.appId,
            appToken = credential.appKey,
        )
        if (payload == null) {
            emitRepair(
                result = "error",
                reason = "payload_build_failed",
                statusOk = false,
                extra = mapOf("target_package" to packageName),
            )
            return null
        }
        emitRepair(
            result = "ok",
            reason = "repaired",
            extra = mapOf(
                "target_package" to packageName,
                "payload_size" to payload.size.toString(),
            ),
        )
        return PushRegistrationPayloadRepairResult(
            packageName = packageName,
            appId = credential.appId,
            appToken = credential.appKey,
            payload = payload,
        )
    }

    private fun emitRepair(
        result: String,
        reason: String,
        statusOk: Boolean = true,
        extra: Map<String, String> = emptyMap(),
    ) {
        val attrs = linkedMapOf(
            "result" to result,
            "duration_ms" to "0",
            "process" to "app",
            "stage" to "registration_repair",
            "reason" to reason,
        )
        attrs.putAll(extra)
        MagiskOtel.event(name = "push.register", attributes = attrs, statusOk = statusOk)
    }

    internal fun parseCredentialOverrides(jsonText: String): Map<String, Pair<String, String>> {
        val root = json.parseToJsonElement(jsonText).jsonObject
        val profiles = root["profiles"]?.jsonArray ?: return emptyMap()
        val result = LinkedHashMap<String, Pair<String, String>>()
        for (profileElement in profiles) {
            val profile = profileElement.jsonObject
            val packageName = profile["packageName"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: continue
            val credential = profile["credentialOverride"]?.jsonObject ?: continue
            val appId = credential["appId"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: continue
            val appKey = credential["appKey"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: continue
            result[packageName] = appId to appKey
        }
        return result
    }

    private fun credentialForPackage(context: Context, packageName: String): Credential? {
        val cache = credentialCache ?: loadCredentialCache(context).also { credentialCache = it }
        return cache[packageName]
    }

    private val manifestAppIdKeys = listOf(
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
        "xiaomi_appid",
    )

    private val manifestAppKeyKeys = listOf(
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
        "xiaomi_appkey",
    )

    /** Pure resolver: the first meta entry holding a complete appId+appKey pair. */
    internal fun credentialFromMetadataEntries(entries: List<Map<String, String>>): Pair<String, String>? {
        entries.forEach { entry ->
            val appId = manifestAppIdKeys.firstNotNullOfOrNull { entry[it]?.trim() }?.takeIf { it.isNotEmpty() }
                ?: return@forEach
            val appKey = manifestAppKeyKeys.firstNotNullOfOrNull { entry[it]?.trim() }?.takeIf { it.isNotEmpty() }
                ?: return@forEach
            return appId to appKey
        }
        return null
    }

    /**
     * Manifest fallback for applications missing from compat-profiles.json. Apps such as
     * cn.gov.pbc.dcep or com.sgcc.wsgw.cn ship their MiPush appId/appKey as manifest
     * meta-data, so the service can synthesize registrations for them without a per-app
     * hardcoded override; the JSON credentialOverride stays authoritative when present.
     */
    private fun manifestCredential(context: Context, packageName: String): Credential? {
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(
                packageName,
                android.content.pm.PackageManager.GET_META_DATA or
                    android.content.pm.PackageManager.GET_RECEIVERS or
                    android.content.pm.PackageManager.GET_SERVICES or
                    android.content.pm.PackageManager.GET_PROVIDERS or
                    android.content.pm.PackageManager.GET_ACTIVITIES,
            )
        }.getOrNull() ?: return null
        val entries = ArrayList<Map<String, String>>(8)
        packageInfo.applicationInfo?.metaData?.let { entries += bundleToMap(it) }
        listOfNotNull(
            packageInfo.services?.toList(),
            packageInfo.receivers?.toList(),
            packageInfo.providers?.toList(),
            packageInfo.activities?.toList(),
        ).flatten().forEach { component ->
            component.metaData?.let { entries += bundleToMap(it) }
        }
        val credential = credentialFromMetadataEntries(entries) ?: return null
        return Credential(packageName, credential.first, credential.second)
    }

    // BaseBundle.get(String) is the only type-agnostic reader, and its typed
    // accessors would silently drop non-string meta-data; the value is stringified below.
    @Suppress("DEPRECATION")
    private fun bundleToMap(bundle: android.os.Bundle): Map<String, String> {
        val map = LinkedHashMap<String, String>(bundle.size())
        bundle.keySet().forEach { key ->
            runCatching {
                val value: String? = bundle.get(key)?.toString()?.trim()
                if (!value.isNullOrBlank()) {
                    map[key] = value
                }
            }
        }
        return map
    }

    private fun loadCredentialCache(context: Context): Map<String, Credential> {
        val jsonText = runCatching {
            context.assets.open(COMPAT_PROFILES_ASSET).bufferedReader().use { it.readText() }
        }.getOrElse { error ->
            logW("registration payload repair profile unavailable: ${error.message}", error)
            return emptyMap()
        }
        return parseCredentialOverrides(jsonText).mapValues { (packageName, credential) ->
            Credential(packageName, credential.first, credential.second)
        }
    }

    private fun buildRegistrationPayload(
        context: Context,
        packageName: String,
        appId: String,
        appToken: String,
    ): ByteArray? {
        val registration = XmPushActionRegistration().apply {
            id = PacketHelper.generatePacketID()
            setAppId(appId)
            setToken(appToken)
            setPackageName(packageName)
            setDeviceId(XMStringUtils.generateRandomString(6))
            val versionName = AppInfoUtils.getVersionName(context, packageName)
            val versionCode = AppInfoUtils.getVersionCode(context, packageName)
            setAppVersion(PushVersionInfo.reportedAppVersionName(packageName, versionName))
            setAppVersionCode(PushVersionInfo.reportedAppVersionCode(packageName, versionCode))
            setPushSdkVersionName(PushConstants.PUSH_VERSION_NAME)
            setPushSdkVersionCode(PushConstants.PUSH_VERSION_CODE)
            setReason(RegistrationReason.Init)
            if (!MIUIUtils.isGlobalRegion()) {
                val imei = DeviceInfo.quicklyGetIMEI(context)
                if (!imei.isNullOrEmpty()) {
                    setImeiMd5(XMStringUtils.getMd5Digest(imei) + "," + DeviceInfo.quicklyGetSubIMEISMd5(context))
                }
            }
            val spaceId = DeviceInfo.getSpaceId()
            if (spaceId >= 0) {
                setSpaceId(spaceId)
            }
        }
        val container = MIPushHelper.generateRequestContainer(
            packageName,
            appId,
            registration,
            ActionType.Registration,
        )
        return XmPushThriftSerializeUtils.convertThriftObjectToBytes(container)
    }
}
