package io.github.magisk317.mipush.manager.api

import android.os.Parcel
import android.os.Parcelable

data class ManagerApplicationDiagnosticsDto(
    val schemaVersion: Int = ManagerProtocol.APPLICATION_DIAGNOSTICS_SCHEMA_VERSION,
    val hasLocalRegistration: Boolean = false,
    val regSecCount: Int = 0,
    val latestRegistrationEventResult: Int? = null,
    val registeredType: Int = 0,
    val inferenceReason: String = "",
    val userId: Int = 0,
) : Parcelable {
    override fun writeToParcel(destination: Parcel, flags: Int) {
        destination.writeWireFrame {
            writeInt(schemaVersion)
            writeWireBoolean(hasLocalRegistration)
            writeInt(regSecCount)
            writeWireNullableInt(latestRegistrationEventResult)
            writeInt(registeredType)
            writeString(inferenceReason)
            writeInt(userId)
        }
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ManagerApplicationDiagnosticsDto> =
            object : Parcelable.Creator<ManagerApplicationDiagnosticsDto> {
                override fun createFromParcel(source: Parcel): ManagerApplicationDiagnosticsDto =
                    source.readWireFrame {
                        ManagerApplicationDiagnosticsDto(
                            schemaVersion = readInt(ManagerProtocol.APPLICATION_DIAGNOSTICS_SCHEMA_VERSION),
                            hasLocalRegistration = readBoolean(),
                            regSecCount = readInt(),
                            latestRegistrationEventResult = readNullableInt(),
                            registeredType = readInt(),
                            inferenceReason = readString().orEmpty(),
                            userId = readInt(-1),
                        )
                    }

                override fun newArray(size: Int): Array<ManagerApplicationDiagnosticsDto?> = arrayOfNulls(size)
            }
    }
}
