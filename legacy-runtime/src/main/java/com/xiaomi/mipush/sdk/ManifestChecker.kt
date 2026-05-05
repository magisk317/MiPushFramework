package com.xiaomi.mipush.sdk

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.text.TextUtils
import android.util.Log
import com.xiaomi.push.service.PushConstants
import java.util.Arrays
import java.util.HashMap
import java.util.HashSet

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/mipush/sdk/ManifestChecker.java
 * No stock 7.4.67-C same-path source was found in the split source tree.
 */
object ManifestChecker {
    class IllegalManifestException(str: String) : RuntimeException(str) {
        companion object {
            private const val serialVersionUID = 1L
        }
    }

    class ServiceCheckInfo(
        @JvmField val serviceName: String,
        @JvmField val enabled: Boolean,
        @JvmField val exported: Boolean,
        @JvmField val permission: String,
    )

    @JvmStatic
    fun asynCheckManifest(context: Context) {
        Thread {
            try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 4612)
                checkReceivers(context)
                checkServices(context, packageInfo)
                checkPermissions(context, packageInfo)
            } catch (throwable: Throwable) {
                Log.e("ManifestChecker", "", throwable)
            }
        }.start()
    }

    private fun checkAssembleReceiver(context: Context, str: String, str2: String) {
        val packageManager = context.packageManager
        val packageName = context.packageName
        val intent = Intent(str).apply {
            setPackage(packageName)
        }
        var found = false
        for (resolveInfo in packageManager.queryBroadcastReceivers(intent, 16384)) {
            val activityInfo = resolveInfo.activityInfo
            found = activityInfo != null && !TextUtils.isEmpty(activityInfo.name) && activityInfo.name == str2
            if (found) {
                break
            }
        }
        if (!found) {
            throw IllegalManifestException(
                String.format("<receiver android:name=\"%1\$s\" .../> is missing or disabled in AndroidManifest.", str2)
            )
        }
    }

    @JvmStatic
    fun checkPermissions(context: Context, packageInfo: PackageInfo) {
        val permissions = HashSet<String>()
        val receivePermission = context.packageName + ".permission.MIPUSH_RECEIVE"
        permissions.addAll(
            Arrays.asList(
                "android.permission.INTERNET",
                "android.permission.ACCESS_NETWORK_STATE",
                receivePermission,
                "android.permission.ACCESS_WIFI_STATE",
                "android.permission.VIBRATE",
            )
        )
        var defined = false
        val declaredPermissions: Array<PermissionInfo>? = packageInfo.permissions
        if (declaredPermissions != null) {
            for (permissionInfo in declaredPermissions) {
                if (receivePermission == permissionInfo.name) {
                    defined = true
                    break
                }
            }
        }
        if (!defined) {
            throw IllegalManifestException(
                String.format("<permission android:name=\"%1\$s\" .../> is undefined in AndroidManifest.", receivePermission)
            )
        }
        val requestedPermissions = packageInfo.requestedPermissions
        if (requestedPermissions != null) {
            for (permission in requestedPermissions) {
                if (!TextUtils.isEmpty(permission) && permissions.contains(permission)) {
                    permissions.remove(permission)
                    if (permissions.isEmpty()) {
                        break
                    }
                }
            }
        }
        if (permissions.isNotEmpty()) {
            throw IllegalManifestException(
                String.format("<uses-permission android:name=\"%1\$s\"/> is missing in AndroidManifest.", permissions.iterator().next())
            )
        }
    }

    private fun checkReceiverInfo(activityInfo: ActivityInfo, boolArr: Array<Boolean>) {
        if (boolArr[0] != activityInfo.enabled) {
            throw IllegalManifestException(
                String.format(
                    "<receiver android:name=\"%1\$s\" .../> in AndroidManifest had the wrong enabled attribute, which should be android:enabled=%2\$b.",
                    activityInfo.name,
                    boolArr[0],
                )
            )
        }
        if (boolArr[1] != activityInfo.exported) {
            throw IllegalManifestException(
                String.format(
                    "<receiver android:name=\"%1\$s\" .../> in AndroidManifest had the wrong exported attribute, which should be android:exported=%2\$b.",
                    activityInfo.name,
                    boolArr[1],
                )
            )
        }
    }

    @JvmStatic
    fun checkReceivers(context: Context) {
        val packageManager = context.packageManager
        val packageName = context.packageName
        val pushIntent = Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE).apply {
            setPackage(packageName)
        }
        val receiverInfo = findReceiverInfo(packageManager, pushIntent, PushServiceReceiver::class.java)
        if (receiverInfo == null) {
            throw IllegalManifestException(
                String.format(
                    "<receiver android:name=\"%1\$s\" .../> is missing or disabled in AndroidManifest.",
                    PushServiceReceiver::class.java.canonicalName
                )
            )
        }
        checkReceiverInfo(receiverInfo, arrayOf(true, true))
        if (!MiPushClient.shouldUseMIUIPush(context)) {
            val pingIntent = Intent(PushConstants.ACTION_PING_TIMER).apply {
                setPackage(packageName)
            }
            val pingReceiverInfo = findReceiverInfo(
                packageManager,
                pingIntent,
                com.xiaomi.push.service.receivers.PingReceiver::class.java,
            )
            if (pingReceiverInfo == null) {
                throw IllegalManifestException(
                    String.format(
                        "<receiver android:name=\"%1\$s\" .../> is missing or disabled in AndroidManifest.",
                        com.xiaomi.push.service.receivers.PingReceiver::class.java.canonicalName
                    )
                )
            }
            checkReceiverInfo(pingReceiverInfo, arrayOf(true, false))
        }
        if (MiPushClient.getOpenHmsPush(context)) {
            checkOptionalAssembleReceiver(
                context,
                PushConstants.HMS_PUSH_ACTION_NEW_MESSAGE,
                PushConstants.HMS_PUSH_RECEIVER_CLASS_NAME,
                PushConstants.HMS_OLD_PUSH_ACTION_NEW_MESSAGE,
                PushConstants.HMS_PUSH_OLD_RECEIVER_CLASS_NAME,
            )
        }
        if (MiPushClient.getOpenVIVOPush(context)) {
            checkOptionalAssembleReceiver(
                context,
                PushConstants.VIVO_PUSH_MESSAGE_RECEIVER_ACTION_NAME,
                PushConstants.VIVO_PUSH_MESSAGE_RECEIVER_CLASS_NAME,
            )
        }
    }

    @JvmStatic
    fun checkServices(context: Context, packageInfo: PackageInfo) {
        val processMap = HashMap<String, String?>()
        val serviceRequirements = HashMap<String, ServiceCheckInfo>()
        serviceRequirements[PushMessageHandler::class.java.canonicalName] =
            ServiceCheckInfo(PushMessageHandler::class.java.canonicalName, true, true, "")
        serviceRequirements[MessageHandleService::class.java.canonicalName] =
            ServiceCheckInfo(MessageHandleService::class.java.canonicalName, true, false, "")
        if (
            !MiPushClient.shouldUseMIUIPush(context) ||
            containAnyService(packageInfo, arrayOf(PushConstants.XM_SERVICE_CLASS_NAME_JAR, PushConstants.PUSH_SERVICE_CLASS_NAME_JAR))
        ) {
            serviceRequirements[PushConstants.XM_SERVICE_CLASS_NAME_JAR] =
                ServiceCheckInfo(PushConstants.XM_SERVICE_CLASS_NAME_JAR, true, false, "android.permission.BIND_JOB_SERVICE")
            serviceRequirements[PushConstants.PUSH_SERVICE_CLASS_NAME_JAR] =
                ServiceCheckInfo(PushConstants.PUSH_SERVICE_CLASS_NAME_JAR, true, false, "")
        }
        if (MiPushClient.getOpenFCMPush(context)) {
            serviceRequirements[PushConstants.FCM_PUSH_INSTANCE_ID_SERVICE_NAME] =
                ServiceCheckInfo(PushConstants.FCM_PUSH_INSTANCE_ID_SERVICE_NAME, true, false, "")
            serviceRequirements[PushConstants.FCM_PUSH_MESSAGE_SERVICE_NAME] =
                ServiceCheckInfo(PushConstants.FCM_PUSH_MESSAGE_SERVICE_NAME, true, false, "")
        }
        if (MiPushClient.getOpenOPPOPush(context)) {
            serviceRequirements[PushConstants.OPPO_PUSH_MESSAGE_SERVICE_NAME] =
                ServiceCheckInfo(
                    PushConstants.OPPO_PUSH_MESSAGE_SERVICE_NAME,
                    true,
                    true,
                    "com.coloros.mcs.permission.SEND_MCS_MESSAGE",
                )
        }
        for (serviceInfo in packageInfo.services.orEmpty()) {
            if (!TextUtils.isEmpty(serviceInfo.name) && serviceRequirements.containsKey(serviceInfo.name)) {
                val requirement = serviceRequirements.remove(serviceInfo.name) ?: continue
                if (requirement.enabled != serviceInfo.enabled) {
                    throw IllegalManifestException(
                        String.format(
                            "<service android:name=\"%1\$s\" .../> in AndroidManifest had the wrong enabled attribute, which should be android:enabled=%2\$b.",
                            serviceInfo.name,
                            requirement.enabled,
                        )
                    )
                }
                if (requirement.exported != serviceInfo.exported) {
                    throw IllegalManifestException(
                        String.format(
                            "<service android:name=\"%1\$s\" .../> in AndroidManifest had the wrong exported attribute, which should be android:exported=%2\$b.",
                            serviceInfo.name,
                            requirement.exported,
                        )
                    )
                }
                if (!TextUtils.isEmpty(requirement.permission) && !TextUtils.equals(requirement.permission, serviceInfo.permission)) {
                    throw IllegalManifestException(
                        String.format(
                            "<service android:name=\"%1\$s\" .../> in AndroidManifest had the wrong permission attribute, which should be android:permission=\"%2\$s\".",
                            serviceInfo.name,
                            requirement.permission,
                        )
                    )
                }
                processMap[serviceInfo.name] = serviceInfo.processName
                if (serviceRequirements.isEmpty()) {
                    break
                }
            }
        }
        if (serviceRequirements.isNotEmpty()) {
            throw IllegalManifestException(
                String.format(
                    "<service android:name=\"%1\$s\" .../> is missing or disabled in AndroidManifest.",
                    serviceRequirements.keys.iterator().next()
                )
            )
        }
        if (
            !TextUtils.equals(
                processMap[PushMessageHandler::class.java.canonicalName],
                processMap[MessageHandleService::class.java.canonicalName],
            )
        ) {
            throw IllegalManifestException(
                String.format(
                    "\"%1\$s\" and \"%2\$s\" must be running in the same process.",
                    PushMessageHandler::class.java.canonicalName,
                    MessageHandleService::class.java.canonicalName,
                )
            )
        }
        if (
            processMap.containsKey(PushConstants.XM_SERVICE_CLASS_NAME_JAR) &&
            processMap.containsKey(PushConstants.PUSH_SERVICE_CLASS_NAME_JAR) &&
            !TextUtils.equals(
                processMap[PushConstants.XM_SERVICE_CLASS_NAME_JAR],
                processMap[PushConstants.PUSH_SERVICE_CLASS_NAME_JAR],
            )
        ) {
            throw IllegalManifestException(
                String.format(
                    "\"%1\$s\" and \"%2\$s\" must be running in the same process.",
                    PushConstants.XM_SERVICE_CLASS_NAME_JAR,
                    PushConstants.PUSH_SERVICE_CLASS_NAME_JAR,
                )
            )
        }
    }

    private fun containAnyService(packageInfo: PackageInfo, strArr: Array<String>): Boolean {
        for (serviceInfo in packageInfo.services.orEmpty()) {
            if (containString(strArr, serviceInfo.name)) {
                return true
            }
        }
        return false
    }

    private fun containString(strArr: Array<String>?, str: String?): Boolean {
        if (strArr == null || str == null) {
            return false
        }
        for (value in strArr) {
            if (TextUtils.equals(value, str)) {
                return true
            }
        }
        return false
    }

    private fun findReceiverInfo(packageManager: PackageManager, intent: Intent, cls: Class<*>): ActivityInfo? {
        for (resolveInfo: ResolveInfo in packageManager.queryBroadcastReceivers(intent, 16384)) {
            val activityInfo = resolveInfo.activityInfo
            if (activityInfo != null && cls.canonicalName == activityInfo.name) {
                return activityInfo
            }
        }
        return null
    }

    private fun findReceiverInfo(packageManager: PackageManager, intent: Intent, str: String): ActivityInfo? {
        for (resolveInfo: ResolveInfo in packageManager.queryBroadcastReceivers(intent, 16384)) {
            val activityInfo = resolveInfo.activityInfo
            if (activityInfo != null && TextUtils.equals(str, activityInfo.name)) {
                return activityInfo
            }
        }
        return null
    }

    private fun checkOptionalAssembleReceiver(context: Context, vararg strArr: String) {
        val packageManager = context.packageManager
        val packageName = context.packageName
        var i = 0
        while (i + 1 < strArr.size) {
            val action = strArr[i]
            val receiverClass = strArr[i + 1]
            if (!TextUtils.isEmpty(action) && !TextUtils.isEmpty(receiverClass)) {
                val intent = Intent(action).apply {
                    setPackage(packageName)
                }
                val receiverInfo = findReceiverInfo(packageManager, intent, receiverClass)
                if (receiverInfo != null) {
                    checkReceiverInfo(receiverInfo, arrayOf(true, true))
                    return
                }
            }
            i += 2
        }
        throw IllegalManifestException(
            String.format("<receiver android:name=\"%1\$s\" .../> is missing or disabled in AndroidManifest.", strArr[1])
        )
    }
}
