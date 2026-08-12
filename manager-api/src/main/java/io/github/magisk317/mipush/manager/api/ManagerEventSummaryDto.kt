package io.github.magisk317.mipush.manager.api

import android.os.Parcel
import android.os.Parcelable

/** Display-oriented event projection returned by the runtime reader. */
data class ManagerEventSummaryDto(
    val schemaVersion: Int = ManagerProtocol.EVENT_SUMMARY_SCHEMA_VERSION,
    val id: Long = 0L,
    val packageName: String = "",
    val configOptions: List<String> = emptyList(),
    val channel: String = "",
    val receiveDateMs: Long = 0L,
    val title: String = "",
    val content: String = "",
    val appName: String? = null,
    val type: Int = 0,
    val result: Int = 0,
    val info: String? = null,
    val payload: ByteArray? = null,
    val regSec: String? = null,
    val userId: Int = 0,
) : Parcelable {
    override fun writeToParcel(destination: Parcel, flags: Int) {
        destination.writeWireFrame {
            writeInt(schemaVersion)
            writeLong(id)
            writeString(packageName)
            writeInt(configOptions.size)
            configOptions.forEach { writeString(it) }
            writeString(channel)
            writeLong(receiveDateMs)
            writeString(title)
            writeString(content)
            writeString(appName)
            writeInt(type)
            writeInt(result)
            writeString(info)
            writeWireByteArray(payload)
            writeString(regSec)
            writeInt(userId)
        }
    }

    override fun describeContents(): Int = 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ManagerEventSummaryDto) return false
        return schemaVersion == other.schemaVersion &&
            id == other.id &&
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
            && userId == other.userId
    }

    override fun hashCode(): Int {
        var resultHash = schemaVersion
        resultHash = 31 * resultHash + id.hashCode()
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
        resultHash = 31 * resultHash + userId
        return resultHash
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ManagerEventSummaryDto> =
            object : Parcelable.Creator<ManagerEventSummaryDto> {
                override fun createFromParcel(source: Parcel): ManagerEventSummaryDto = source.readWireFrame {
                    ManagerEventSummaryDto(
                        schemaVersion = readInt(ManagerProtocol.EVENT_SUMMARY_SCHEMA_VERSION),
                        id = readLong(),
                        packageName = readString(maxLength = ManagerProtocol.MAX_PACKAGE_NAME_LENGTH).orEmpty(),
                        configOptions = readStringList(
                            maxItems = ManagerProtocol.MAX_EVENT_CONFIG_OPTION_COUNT,
                            maxItemLength = ManagerProtocol.MAX_EVENT_CONFIG_OPTION_LENGTH,
                        ),
                        channel = readString().orEmpty(),
                        receiveDateMs = readLong(),
                        title = readString().orEmpty(),
                        content = readString().orEmpty(),
                        appName = readString(),
                        type = readInt(),
                        result = readInt(),
                        info = readString(),
                        payload = readByteArray(),
                        regSec = readString(),
                        userId = readInt(-1),
                    )
                }

                override fun newArray(size: Int): Array<ManagerEventSummaryDto?> = arrayOfNulls(size)
            }
    }
}
