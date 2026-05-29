package io.github.magisk317.mipush.common.manager

import java.io.File

data class ManagerApplication(
    val id: Long? = null,
    val packageName: String = "",
    val type: Int = Type.ASK,
    val notificationOnRegister: Boolean = false,
    val blocked: Boolean = false,
    val islandEnabled: Boolean = true,
    val islandFocusNotification: Boolean = true,
    val registeredType: Int = RegisteredType.NOT_REGISTERED,
    val existServices: Boolean = false,
    val appName: String = "",
    val appNamePinYin: String = "",
    val lastReceiveTimeMs: Long = 0L,
) {
    object Type {
        const val ASK = 0
        const val ALLOW = 2
        const val DENY = 3
        const val ALLOW_ONCE = -1
    }

    object RegisteredType {
        const val NOT_REGISTERED = 0
        const val REGISTERED = 1
        const val UNREGISTERED = 2
    }
}

data class ManagerApplications(
    val registeredPkgs: Map<String, ManagerApplication> = emptyMap(),
    val items: List<ManagerApplication> = emptyList(),
    val totalPkg: Int = 0,
)

data class ManagerApplicationDiagnostics(
    val hasLocalRegistration: Boolean,
    val regSecCount: Int,
    val latestRegistrationEventResult: Int?,
    val registeredType: Int,
    val inferenceReason: String,
)

object ManagerEventType {
    const val SEND_MESSAGE = 0
    const val REGISTRATION = 2
    const val UN_REGISTRATION = 20
    const val REGISTRATION_RESULT = 21
}

object ManagerEventResult {
    const val OK = 0
    const val DENY_DISABLED = 1
    const val DENY_USER = 2
}

data class ManagerEvent(
    val id: Long,
    val packageName: String,
    val configOptions: Set<String>,
    val channel: String,
    val receiveDateMs: Long,
    val title: String,
    val content: String,
    val appName: String? = null,
    val type: Int = 0,
    val result: Int = ManagerEventResult.OK,
    val info: String? = null,
    val payload: ByteArray? = null,
    val regSec: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ManagerEvent) return false
        return id == other.id &&
            packageName == other.packageName &&
            configOptions == other.configOptions &&
            channel == other.channel &&
            receiveDateMs == other.receiveDateMs &&
            title == other.title &&
            content == other.content &&
            appName == other.appName &&
            type == other.type &&
            result == other.result &&
            info == other.info &&
            payload.contentEquals(other.payload) &&
            regSec == other.regSec
    }

    override fun hashCode(): Int {
        var resultHash = id.hashCode()
        resultHash = 31 * resultHash + packageName.hashCode()
        resultHash = 31 * resultHash + configOptions.hashCode()
        resultHash = 31 * resultHash + channel.hashCode()
        resultHash = 31 * resultHash + receiveDateMs.hashCode()
        resultHash = 31 * resultHash + title.hashCode()
        resultHash = 31 * resultHash + content.hashCode()
        resultHash = 31 * resultHash + (appName?.hashCode() ?: 0)
        resultHash = 31 * resultHash + type
        resultHash = 31 * resultHash + result
        resultHash = 31 * resultHash + (info?.hashCode() ?: 0)
        resultHash = 31 * resultHash + (payload?.contentHashCode() ?: 0)
        resultHash = 31 * resultHash + (regSec?.hashCode() ?: 0)
        return resultHash
    }
}

data class ManagerRuntimeLogFileInfo(
    val name: String,
    val sizeBytes: Long,
    val lineCount: Int,
    val lastTimestamp: Long?,
)

data class ManagerRuntimeLogFileSummary(
    val fileCount: Int,
    val totalBytes: Long,
    val entryCount: Int,
    val firstTimestamp: Long?,
    val lastTimestamp: Long?,
    val files: List<ManagerRuntimeLogFileInfo>,
)

data class ManagerRuntimeLogFileContent(
    val name: String,
    val text: String,
)

data class ManagerLogExportResult(
    val file: File?,
    val details: String,
)

data class ManagerLogClearResult(
    val success: Boolean,
    val details: String,
)
