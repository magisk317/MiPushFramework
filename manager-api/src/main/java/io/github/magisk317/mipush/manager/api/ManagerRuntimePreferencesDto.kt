package io.github.magisk317.mipush.manager.api

import android.os.Parcel
import android.os.Parcelable

data class ManagerRuntimePreferencesDto(
    val schemaVersion: Int = ManagerProtocol.RUNTIME_PREFERENCES_SCHEMA_VERSION,
    val entries: List<ManagerPreferenceEntryDto> = emptyList(),
) : Parcelable {
    override fun writeToParcel(destination: Parcel, flags: Int) {
        destination.writeWireFrame {
            writeInt(schemaVersion)
            writeInt(entries.size)
            entries.forEach { it.writeToParcel(this, flags) }
        }
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ManagerRuntimePreferencesDto> =
            object : Parcelable.Creator<ManagerRuntimePreferencesDto> {
                override fun createFromParcel(source: Parcel): ManagerRuntimePreferencesDto =
                    source.readWireFrame {
                        ManagerRuntimePreferencesDto(
                            schemaVersion = readInt(ManagerProtocol.RUNTIME_PREFERENCES_SCHEMA_VERSION),
                            entries = readParcelableList(
                                creator = ManagerPreferenceEntryDto.CREATOR,
                                maxItems = ManagerProtocol.MAX_PREFERENCE_ENTRY_COUNT,
                            ),
                        )
                    }

                override fun newArray(size: Int): Array<ManagerRuntimePreferencesDto?> = arrayOfNulls(size)
            }
    }
}
