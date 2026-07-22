package io.github.magisk317.mipush.manager.api

import android.os.Parcel
import android.os.Parcelable

/**
 * Idempotent write command. Callers must reuse the same [requestId] when retrying after Binder
 * death so the runtime can return the previous outcome instead of repeating a destructive action.
 */
data class ManagerWriteRequestDto(
    val schemaVersion: Int = ManagerProtocol.WRITE_REQUEST_SCHEMA_VERSION,
    val requestId: String = "",
    val operation: String = "",
    val packageName: String = "",
    val eventId: Long? = null,
    val intArgument: Int = 0,
    val longArgument: Long = 0L,
    val booleanArgument: Boolean = false,
    val argument: String = "",
) : Parcelable {
    override fun writeToParcel(destination: Parcel, flags: Int) {
        destination.writeWireFrame {
            writeInt(schemaVersion)
            writeString(requestId)
            writeString(operation)
            writeString(packageName)
            writeWireNullableLong(eventId)
            writeInt(intArgument)
            writeLong(longArgument)
            writeWireBoolean(booleanArgument)
            writeString(argument)
        }
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ManagerWriteRequestDto> =
            object : Parcelable.Creator<ManagerWriteRequestDto> {
                override fun createFromParcel(source: Parcel): ManagerWriteRequestDto =
                    source.readWireFrame {
                        ManagerWriteRequestDto(
                            schemaVersion = readInt(ManagerProtocol.WRITE_REQUEST_SCHEMA_VERSION),
                            requestId = readString(
                                maxLength = ManagerProtocol.MAX_WRITE_REQUEST_ID_LENGTH,
                            ).orEmpty(),
                            operation = readString(
                                maxLength = ManagerProtocol.MAX_WRITE_OPERATION_LENGTH,
                            ).orEmpty(),
                            packageName = readString(
                                maxLength = ManagerProtocol.MAX_PACKAGE_NAME_LENGTH,
                            ).orEmpty(),
                            eventId = readNullableLong(),
                            intArgument = readInt(),
                            longArgument = readLong(),
                            booleanArgument = readBoolean(),
                            argument = readString(
                                maxLength = ManagerProtocol.MAX_WRITE_ARGUMENT_LENGTH,
                            ).orEmpty(),
                        )
                    }

                override fun newArray(size: Int): Array<ManagerWriteRequestDto?> = arrayOfNulls(size)
            }
    }
}
