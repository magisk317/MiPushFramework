package com.xiaomi.xmsf.push.service

import android.content.Context
import android.content.Intent
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.ClientUploadDataItem
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import io.github.magisk317.mipush.common.utils.Utils

/** Security boundary for the MiPush service components exposed to third-party SDK clients. */
internal object ExternalPushIntentPolicy {
    private const val MAX_PACKAGE_NAME_LENGTH = 255
    private const val MAX_PAYLOAD_BYTES = 512 * 1024
    private val packageNamePattern = Regex("^[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+$")

    private val externallyAllowedActions = setOf(
        PushConstants.MIPUSH_ACTION_REGISTER_APP,
        PushConstants.MIPUSH_ACTION_SEND_MESSAGE,
        PushConstants.MIPUSH_ACTION_UNREGISTER_APP,
        PushConstants.MIPUSH_ACTION_SEND_TINYDATA,
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
        val action = normalizedIntent.action ?: return rejected("action_not_public")
        if (!isAllowed(action)) return rejected("action_not_public")
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
        val payload = runCatching {
            normalizedIntent.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD)
        }.getOrElse { return rejected("invalid_payload") }
        if (payload == null || !isPayloadSizeAllowed(payload.size)) return rejected("invalid_payload_size")

        val rejectionReason = when (action) {
            PushConstants.MIPUSH_ACTION_SEND_TINYDATA -> tinyDataRejectionReason(packageName, payload)
            else -> containerRejectionReason(action, packageName, payload)
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
     * Some legacy SDKs perform an action-less bootstrap. Only convert it when the wire payload
     * independently proves that it is a registration request for the claimed package.
     */
    private fun normalizeExternalIntent(intent: Intent): Intent? {
        if (intent.action != null) return intent
        val packageName = runCatching {
            intent.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE)
        }.getOrNull() ?: return null
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
            else -> "action_not_public"
        }
    }

    private fun tinyDataRejectionReason(packageName: String, payload: ByteArray): String? {
        val item = runCatching {
            ClientUploadDataItem().also { tinyData ->
                XmPushThriftSerializeUtils.convertByteArrayToThriftObject(tinyData, payload)
            }
        }.getOrNull() ?: return "invalid_tinydata"
        val embeddedPackage = item.pkgName?.takeIf { it.isNotBlank() }
        if (embeddedPackage != null && embeddedPackage != packageName) {
            return "tinydata_package_mismatch"
        }
        val sourcePackage = item.sourcePackage?.takeIf { it.isNotBlank() }
        return if (sourcePackage != null && sourcePackage != packageName) {
            "tinydata_source_package_mismatch"
        } else null
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
        val action = source.action ?: return
        target.action = action
        copyStringExtra(source, target, PushConstants.MIPUSH_EXTRA_APP_PACKAGE)
        source.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD)
            ?.copyOf()
            ?.let { target.putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, it) }
        when (action) {
            PushConstants.MIPUSH_ACTION_REGISTER_APP -> {
                copyStringExtra(source, target, PushConstants.MIPUSH_EXTRA_APP_ID)
                copyStringExtra(source, target, PushConstants.MIPUSH_EXTRA_SESSION)
                copyBooleanExtra(source, target, PushConstants.MIPUSH_EXTRA_ENV_CHANAGE)
                copyIntExtra(source, target, PushConstants.MIPUSH_EXTRA_ENV_TYPE)
            }
            PushConstants.MIPUSH_ACTION_SEND_MESSAGE,
            PushConstants.MIPUSH_ACTION_UNREGISTER_APP -> {
                copyStringExtra(source, target, PushConstants.MIPUSH_EXTRA_APP_ID)
                copyBooleanExtra(source, target, PushConstants.MIPUSH_EXTRA_MESSAGE_CACHE)
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

    private fun copyBooleanExtra(source: Intent, target: Intent, key: String) {
        if (source.hasExtra(key)) {
            target.putExtra(key, source.getBooleanExtra(key, false))
        }
    }

    private fun copyIntExtra(source: Intent, target: Intent, key: String) {
        if (source.hasExtra(key)) {
            target.putExtra(key, source.getIntExtra(key, 1))
        }
    }

    private fun rejected(reason: String): ValidationResult = ValidationResult(rejectionReason = reason)
}
