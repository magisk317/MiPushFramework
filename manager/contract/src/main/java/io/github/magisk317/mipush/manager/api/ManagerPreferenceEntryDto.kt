package io.github.magisk317.mipush.manager.api

import android.os.Parcel
import android.os.Parcelable

data class ManagerPreferenceEntryDto(
    val schemaVersion: Int = ManagerProtocol.RUNTIME_PREFERENCE_ENTRY_SCHEMA_VERSION,
    val key: String = "",
    val type: String = "string",
    val value: String = "",
    val owner: String = "runtime",
) : Parcelable {
    override fun writeToParcel(destination: Parcel, flags: Int) {
        destination.writeWireFrame {
            writeInt(schemaVersion)
            writeString(key)
            writeString(type)
            writeString(value)
            writeString(owner)
        }
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ManagerPreferenceEntryDto> =
            object : Parcelable.Creator<ManagerPreferenceEntryDto> {
                override fun createFromParcel(source: Parcel): ManagerPreferenceEntryDto =
                    source.readWireFrame {
                        ManagerPreferenceEntryDto(
                            schemaVersion = readInt(ManagerProtocol.RUNTIME_PREFERENCE_ENTRY_SCHEMA_VERSION),
                            key = readString(maxLength = ManagerProtocol.MAX_PREFERENCE_KEY_LENGTH).orEmpty(),
                            type = readString(maxLength = 32).orEmpty(),
                            value = readString(maxLength = ManagerProtocol.MAX_PREFERENCE_VALUE_LENGTH).orEmpty(),
                            owner = readString(maxLength = 32).orEmpty(),
                        )
                    }

                override fun newArray(size: Int): Array<ManagerPreferenceEntryDto?> = arrayOfNulls(size)
            }
    }
}
