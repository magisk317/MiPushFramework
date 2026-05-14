package io.github.magisk317.mipush.service.runtime

import android.content.Context
import android.text.TextUtils
import com.xiaomi.channel.commonutils.android.AppInfoUtils
import com.xiaomi.channel.commonutils.android.DeviceInfo
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.string.XMStringUtils
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
        if (packageName.isBlank()) return null
        val credential = credentialForPackage(context, packageName) ?: return null
        val payload = buildRegistrationPayload(
            context = context,
            packageName = packageName,
            appId = credential.appId,
            appToken = credential.appKey,
        ) ?: return null
        return PushRegistrationPayloadRepairResult(
            packageName = packageName,
            appId = credential.appId,
            appToken = credential.appKey,
            payload = payload,
        )
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

    private fun loadCredentialCache(context: Context): Map<String, Credential> {
        val jsonText = runCatching {
            context.assets.open(COMPAT_PROFILES_ASSET).bufferedReader().use { it.readText() }
        }.getOrElse { error ->
            MyLog.w("registration payload repair profile unavailable: ${error.message}")
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
                if (!TextUtils.isEmpty(imei)) {
                    setImeiMd5(XMStringUtils.getMd5Digest(imei!!) + "," + DeviceInfo.quicklyGetSubIMEISMd5(context))
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
