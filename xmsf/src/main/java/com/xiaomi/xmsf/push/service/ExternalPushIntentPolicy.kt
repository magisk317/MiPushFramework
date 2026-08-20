package com.xiaomi.xmsf.push.service

import android.content.Context
import android.content.Intent
import com.xiaomi.channel.commonutils.string.MD5
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.NotificationType
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import io.github.magisk317.mipush.common.utils.Utils

/** Security boundary for the MiPush service components exposed to third-party SDK clients. */
internal object ExternalPushIntentPolicy {
    private const val MAX_PACKAGE_NAME_LENGTH = 255
    private const val MAX_PAYLOAD_BYTES = 512 * 1024
    private const val EXTRA_MESSAGE_CACHE_COLLECTION = "mipush_message_cache_collection"
    private const val CACHE_COLLECTION_DEFAULT = 0
    private const val CACHE_COLLECTION_NOTIFICATION_EXPOSURE = 1
    private val packageNamePattern = Regex("^[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+$")

    private val externallyAllowedActions = setOf(
        PushConstants.MIPUSH_ACTION_REGISTER_APP,
        PushConstants.MIPUSH_ACTION_SEND_MESSAGE,
        PushConstants.MIPUSH_ACTION_UNREGISTER_APP,
        PushConstants.MIPUSH_ACTION_CLEAR_NOTIFICATION,
        PushConstants.MIPUSH_ACTION_SET_NOTIFICATION_TYPE,
        PushConstants.MIPUSH_ACTION_DISABLE_PUSH,
        PushConstants.MIPUSH_ACTION_DISABLE_PUSH_MESSAGE,
        PushConstants.MIPUSH_ACTION_ENABLE_PUSH_MESSAGE,
        // Internal control actions from the stock timer — accepted as a fallback when
        // MiPushFacadeService.isInternalControlAction() already routes them directly.
        PushConstants.ACTION_OPEN_CHANNEL,
    )

    internal data class ValidationResult(
        val intent: Intent? = null,
        val rejectionReason: String? = null,
    )

    fun isAllowed(action: String?): Boolean = action in externallyAllowedActions

    fun validate(
        context: Context,
        intent: Intent,
        callingUid: Int? = null,
        callingPackages: Array<String>? = null,
    ): ValidationResult {
        val normalizedIntent = normalizeExternalIntent(intent) ?: return rejected("action_not_public")
        val action = normalizedIntent.action
        if (action == PushConstants.MIPUSH_ACTION_SEND_TINYDATA || isNotificationExposure(normalizedIntent)) {
            return rejected("telemetry_disabled")
        }
        if (action != null && !isAllowed(action)) return rejected("action_not_public")
        val packageName = runCatching {
            normalizedIntent.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE)
        }.getOrNull()
        if (!isValidPackageName(packageName)) return rejected("invalid_package")
        if (!Utils.isAppInstalled(context, packageName!!)) return rejected("package_not_installed")
        if (callingUid != null) {
            val callerMatchesPackage = callingPackages?.let {
                isCallerPackageAllowed(it, packageName)
            } ?: isCallerPackageAllowed(context, callingUid, packageName)
            if (!callerMatchesPackage) {
                return rejected("caller_package_mismatch")
            }
        }

        val rejectionReason = when (action) {
            null -> wakeRequestRejectionReason(normalizedIntent)
            PushConstants.MIPUSH_ACTION_REGISTER_APP,
            PushConstants.MIPUSH_ACTION_SEND_MESSAGE,
            PushConstants.MIPUSH_ACTION_UNREGISTER_APP,
            PushConstants.MIPUSH_ACTION_DISABLE_PUSH_MESSAGE,
            PushConstants.MIPUSH_ACTION_ENABLE_PUSH_MESSAGE -> payloadRejectionReason(
                normalizedIntent,
                action,
                packageName,
            )
            PushConstants.MIPUSH_ACTION_CLEAR_NOTIFICATION -> clearNotificationRejectionReason(
                normalizedIntent,
                packageName,
            )
            PushConstants.MIPUSH_ACTION_SET_NOTIFICATION_TYPE -> notificationTypeRejectionReason(
                normalizedIntent,
                packageName,
            )
            PushConstants.MIPUSH_ACTION_DISABLE_PUSH -> null
            // Internal control actions — no payload to validate, just pass through.
            PushConstants.ACTION_OPEN_CHANNEL -> null
            else -> "action_not_public"
        }
        return if (rejectionReason == null) {
            ValidationResult(intent = sanitizedCopy(normalizedIntent))
        } else {
            rejected(rejectionReason)
        }
    }

    fun rejectionReason(
        context: Context,
        intent: Intent,
        callingUid: Int? = null,
        callingPackages: Array<String>? = null,
    ): String? {
        return validate(context, intent, callingUid, callingPackages).rejectionReason
    }

    internal fun isValidPackageName(packageName: String?): Boolean {
        return packageName != null &&
            packageName.length <= MAX_PACKAGE_NAME_LENGTH &&
            packageNamePattern.matches(packageName)
    }

    internal fun isPayloadSizeAllowed(size: Int): Boolean = size in 1..MAX_PAYLOAD_BYTES

    internal fun isTelemetryDisabled(intent: Intent): Boolean {
        return intent.action == PushConstants.MIPUSH_ACTION_SEND_TINYDATA || isNotificationExposure(intent)
    }

    internal fun isCallerPackageAllowed(context: Context, callingUid: Int, packageName: String): Boolean {
        if (callingUid < 0) return false
        return runCatching {
            isCallerPackageAllowed(context.packageManager.getPackagesForUid(callingUid), packageName)
        }.getOrDefault(false)
    }

    internal fun isCallerPackageAllowed(packagesForUid: Array<String>?, packageName: String): Boolean {
        return packagesForUid?.contains(packageName) == true
    }

    /**
     * SDK 3.7.9 and stock 7.4.67-C e0.i() use an action-less, payload-less XMSF wake. The old
     * product policy accepted only action-less registration payloads, so a normal SDK wake could
     * not bootstrap the connection. Keep the older registration normalization as a narrow fallback.
     */
    private fun normalizeExternalIntent(intent: Intent): Intent? {
        if (intent.action != null) return intent
        val packageName = runCatching {
            intent.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE)
        }.getOrNull() ?: return null
        if (!intent.hasExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD)) {
            return Intent().apply {
                putExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE, packageName)
            }
        }
        val payload = runCatching {
            intent.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD)
        }.getOrNull() ?: return null
        if (!isPayloadSizeAllowed(payload.size)) return null
        val container = decodeContainer(payload) ?: return null
        if (container.action != ActionType.Registration ||
            container.packageName?.takeIf { it.isNotBlank() } != packageName
        ) {
            return null
        }
        return Intent(PushConstants.MIPUSH_ACTION_REGISTER_APP).apply {
            putExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE, packageName)
            putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, payload.copyOf())
            copyStringExtra(intent, this, PushConstants.MIPUSH_EXTRA_APP_ID)
            copyStringExtra(intent, this, PushConstants.MIPUSH_EXTRA_SESSION)
            copyBooleanExtra(intent, this, PushConstants.MIPUSH_EXTRA_ENV_CHANAGE)
            copyIntExtra(intent, this, PushConstants.MIPUSH_EXTRA_ENV_TYPE)
        }
    }

    private fun payloadRejectionReason(intent: Intent, action: String, packageName: String): String? {
        val payload = runCatching {
            intent.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD)
        }.getOrElse { return "invalid_payload" }
        if (payload == null || !isPayloadSizeAllowed(payload.size)) return "invalid_payload_size"
        return containerRejectionReason(action, packageName, payload)
    }

    private fun containerRejectionReason(action: String, packageName: String, payload: ByteArray): String? {
        val container = decodeContainer(payload) ?: return "invalid_container"
        if (container.packageName?.takeIf { it.isNotBlank() } != packageName) return "package_mismatch"
        return when (action) {
            PushConstants.MIPUSH_ACTION_REGISTER_APP -> {
                if (container.action == ActionType.Registration) null else "container_action_mismatch"
            }
            PushConstants.MIPUSH_ACTION_UNREGISTER_APP -> {
                if (container.action == ActionType.UnRegistration) null else "container_action_mismatch"
            }
            PushConstants.MIPUSH_ACTION_SEND_MESSAGE -> {
                if (container.action == null ||
                    container.action == ActionType.Registration ||
                    container.action == ActionType.UnRegistration ||
                    container.action == ActionType.BadAction
                ) {
                    "container_action_mismatch"
                } else {
                    null
                }
            }
            PushConstants.MIPUSH_ACTION_DISABLE_PUSH_MESSAGE -> notificationTypeRejectionReason(
                container,
                NotificationType.DisablePushMessage.value,
            )
            PushConstants.MIPUSH_ACTION_ENABLE_PUSH_MESSAGE -> notificationTypeRejectionReason(
                container,
                NotificationType.EnablePushMessage.value,
            )
            else -> "action_not_public"
        }
    }

    private fun notificationTypeRejectionReason(container: XmPushActionContainer, expectedType: String): String? {
        if (container.action != ActionType.Notification || container.isEncryptAction) {
            return "container_action_mismatch"
        }
        val notification = runCatching {
            XmPushActionNotification().also {
                XmPushThriftSerializeUtils.convertByteArrayToThriftObject(it, container.getPushAction())
            }
        }.getOrNull() ?: return "invalid_notification"
        return if (notification.type == expectedType) null else "notification_type_mismatch"
    }

    private fun wakeRequestRejectionReason(intent: Intent): String? {
        return if (intent.hasExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD)) "invalid_wake_request" else null
    }

    private fun localPackageRejectionReason(intent: Intent, packageName: String): String? {
        val localPackage = runCatching { intent.getStringExtra(PushConstants.EXTRA_PACKAGE_NAME) }.getOrNull()
        return if (localPackage == packageName) null else "package_mismatch"
    }

    private fun clearNotificationRejectionReason(intent: Intent, packageName: String): String? {
        localPackageRejectionReason(intent, packageName)?.let { return it }
        if (intent.hasExtra(PushConstants.EXTRA_NOTIFY_ID)) {
            typedExtra<Int>(intent, PushConstants.EXTRA_NOTIFY_ID)
                ?: return "invalid_notification_id"
        }
        return null
    }

    private fun notificationTypeRejectionReason(intent: Intent, packageName: String): String? {
        localPackageRejectionReason(intent, packageName)?.let { return it }
        val signature = runCatching { intent.getStringExtra(PushConstants.EXTRA_SIG) }.getOrNull()
        val expected = if (intent.hasExtra(PushConstants.EXTRA_NOTIFY_TYPE)) {
            val notificationType = typedExtra<Int>(intent, PushConstants.EXTRA_NOTIFY_TYPE)
                ?: return "invalid_notification_type"
            MD5.MD5_16(packageName + notificationType)
        } else {
            MD5.MD5_16(packageName)
        }
        return if (signature == expected) null else "invalid_notification_signature"
    }

    private fun isNotificationExposure(intent: Intent): Boolean {
        if (intent.action != PushConstants.MIPUSH_ACTION_SEND_MESSAGE) return false
        if (!intent.hasExtra(EXTRA_MESSAGE_CACHE_COLLECTION)) {
            return CACHE_COLLECTION_DEFAULT == CACHE_COLLECTION_NOTIFICATION_EXPOSURE
        }
        val collection = typedExtra<Int>(intent, EXTRA_MESSAGE_CACHE_COLLECTION)
            ?: CACHE_COLLECTION_NOTIFICATION_EXPOSURE
        // Stock 7.4.67-C currently defines 0=normal and 1=notification exposure. Treat unknown
        // future collection values as disabled too, so they cannot fall through as ordinary uplink.
        return collection != CACHE_COLLECTION_DEFAULT
    }

    private fun decodeContainer(payload: ByteArray): XmPushActionContainer? {
        return runCatching {
            XmPushActionContainer().also { container ->
                XmPushThriftSerializeUtils.convertByteArrayToThriftObject(container, payload)
            }
        }.getOrNull()
    }

    /** Rebuild an exported request before it reaches the private XMPushServiceCore component. */
    internal fun copyAllowedExtras(source: Intent, target: Intent) {
        copyStringExtra(source, target, PushConstants.MIPUSH_EXTRA_APP_PACKAGE)
        val action = source.action ?: return
        target.action = action
        when (action) {
            PushConstants.MIPUSH_ACTION_REGISTER_APP -> {
                copyByteArrayExtra(source, target, PushConstants.MIPUSH_EXTRA_PAYLOAD)
                copyStringExtra(source, target, PushConstants.MIPUSH_EXTRA_APP_ID)
                copyStringExtra(source, target, PushConstants.MIPUSH_EXTRA_SESSION)
                copyBooleanExtra(source, target, PushConstants.MIPUSH_EXTRA_ENV_CHANAGE)
                copyIntExtra(source, target, PushConstants.MIPUSH_EXTRA_ENV_TYPE)
            }
            PushConstants.MIPUSH_ACTION_SEND_MESSAGE -> {
                copyByteArrayExtra(source, target, PushConstants.MIPUSH_EXTRA_PAYLOAD)
                copyBooleanExtra(source, target, PushConstants.MIPUSH_EXTRA_MESSAGE_CACHE)
            }
            PushConstants.MIPUSH_ACTION_UNREGISTER_APP -> {
                copyByteArrayExtra(source, target, PushConstants.MIPUSH_EXTRA_PAYLOAD)
                copyStringExtra(source, target, PushConstants.MIPUSH_EXTRA_APP_ID)
            }
            PushConstants.MIPUSH_ACTION_DISABLE_PUSH_MESSAGE,
            PushConstants.MIPUSH_ACTION_ENABLE_PUSH_MESSAGE -> {
                copyByteArrayExtra(source, target, PushConstants.MIPUSH_EXTRA_PAYLOAD)
                copyStringExtra(source, target, PushConstants.MIPUSH_EXTRA_APP_ID)
                copyStringExtra(source, target, PushConstants.MIPUSH_EXTRA_APP_TOKEN)
                copyBooleanExtra(source, target, PushConstants.MIPUSH_EXTRA_MESSAGE_CACHE)
            }
            PushConstants.MIPUSH_ACTION_CLEAR_NOTIFICATION -> {
                copyStringExtra(source, target, PushConstants.EXTRA_PACKAGE_NAME)
                copyIntExtra(source, target, PushConstants.EXTRA_NOTIFY_ID)
                copyStringExtra(source, target, PushConstants.EXTRA_NOTIFY_TITLE)
                copyStringExtra(source, target, PushConstants.EXTRA_NOTIFY_DESCRIPTION)
            }
            PushConstants.MIPUSH_ACTION_SET_NOTIFICATION_TYPE -> {
                copyStringExtra(source, target, PushConstants.EXTRA_PACKAGE_NAME)
                copyIntExtra(source, target, PushConstants.EXTRA_NOTIFY_TYPE)
                copyStringExtra(source, target, PushConstants.EXTRA_SIG)
            }
        }
    }

    private fun sanitizedCopy(source: Intent): Intent {
        return Intent(source.action).also { target -> copyAllowedExtras(source, target) }
    }

    private fun copyStringExtra(source: Intent, target: Intent, key: String) {
        runCatching { source.getStringExtra(key) }
            .getOrNull()
            ?.let { target.putExtra(key, it) }
    }

    private fun copyByteArrayExtra(source: Intent, target: Intent, key: String) {
        typedExtra<ByteArray>(source, key)
            ?.copyOf()
            ?.let { target.putExtra(key, it) }
    }

    private fun copyBooleanExtra(source: Intent, target: Intent, key: String) {
        if (source.hasExtra(key)) {
            typedExtra<Boolean>(source, key)
                ?.let { target.putExtra(key, it) }
        }
    }

    private fun copyIntExtra(source: Intent, target: Intent, key: String) {
        if (source.hasExtra(key)) {
            typedExtra<Int>(source, key)
                ?.let { target.putExtra(key, it) }
        }
    }

    @Suppress("DEPRECATION")
    private inline fun <reified T> typedExtra(source: Intent, key: String): T? {
        return runCatching { source.extras?.get(key) as? T }.getOrNull()
    }

    private fun rejected(reason: String): ValidationResult = ValidationResult(rejectionReason = reason)
}
