package io.github.magisk317.mipush.manager.api

import android.os.Parcel
import android.os.Parcelable

/** Fields are append-only so older peers can skip additions inside the size-delimited frame. */
data class ManagerHandshake(
    val protocolMajor: Int,
    val protocolMinor: Int,
    val runtimeVersionName: String,
    val runtimeVersionCode: Long,
    val supportedCapabilities: List<String>,
    val maxPageSize: Int,
    val maxPayloadBytes: Int,
    val compatibilityReason: String? = null,
    /**
     * Short git commit of the runtime (xmsf) build, used by the manager to detect a
     * module/runtime version mismatch. Null on older runtimes that predate the field.
     */
    val runtimeCommit: String? = null,
) : Parcelable {
    override fun writeToParcel(destination: Parcel, flags: Int) {
        destination.writeWireFrame {
            writeInt(protocolMajor)
            writeInt(protocolMinor)
            writeString(runtimeVersionName)
            writeLong(runtimeVersionCode)
            writeStringList(supportedCapabilities)
            writeInt(maxPageSize)
            writeInt(maxPayloadBytes)
            writeString(compatibilityReason)
            writeString(runtimeCommit)
        }
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ManagerHandshake> = object : Parcelable.Creator<ManagerHandshake> {
            override fun createFromParcel(source: Parcel): ManagerHandshake = source.readWireFrame {
                ManagerHandshake(
                    protocolMajor = readInt(),
                    protocolMinor = readInt(),
                    runtimeVersionName = readString(
                        maxLength = ManagerProtocol.MAX_RUNTIME_VERSION_NAME_LENGTH,
                    ).orEmpty(),
                    runtimeVersionCode = readLong(),
                    supportedCapabilities = readStringList(),
                    maxPageSize = readInt(),
                    maxPayloadBytes = readInt(),
                    compatibilityReason = readString(
                        maxLength = ManagerProtocol.MAX_COMPATIBILITY_REASON_LENGTH,
                    ),
                    runtimeCommit = readString(
                        maxLength = ManagerProtocol.MAX_RUNTIME_COMMIT_LENGTH,
                    ),
                )
            }

            override fun newArray(size: Int): Array<ManagerHandshake?> = arrayOfNulls(size)
        }
    }
}
