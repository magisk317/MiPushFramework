package io.github.magisk317.mipush.manager.api

import android.os.Parcel
import android.os.Parcelable

data class ManagerConfigurationCatalogEntryDto(
    val schemaVersion: Int = ManagerProtocol.CONFIGURATION_CATALOG_ENTRY_SCHEMA_VERSION,
    val path: String = "",
    val name: String = "",
    val sha: String = "",
    val size: Int = 0,
    val updatedAt: String = "",
) : Parcelable {
    override fun writeToParcel(destination: Parcel, flags: Int) {
        destination.writeWireFrame {
            writeInt(schemaVersion)
            writeString(path)
            writeString(name)
            writeString(sha)
            writeInt(size)
            writeString(updatedAt)
        }
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ManagerConfigurationCatalogEntryDto> =
            object : Parcelable.Creator<ManagerConfigurationCatalogEntryDto> {
                override fun createFromParcel(source: Parcel): ManagerConfigurationCatalogEntryDto =
                    source.readWireFrame {
                        ManagerConfigurationCatalogEntryDto(
                            schemaVersion = readInt(
                                ManagerProtocol.CONFIGURATION_CATALOG_ENTRY_SCHEMA_VERSION,
                            ),
                            path = readString(maxLength = ManagerProtocol.MAX_CONFIGURATION_PATH_LENGTH)
                                .orEmpty(),
                            name = readString(maxLength = ManagerProtocol.MAX_CONFIGURATION_NAME_LENGTH)
                                .orEmpty(),
                            sha = readString(maxLength = ManagerProtocol.MAX_CONFIGURATION_SHA_LENGTH).orEmpty(),
                            size = readInt(),
                            updatedAt = readString().orEmpty(),
                        )
                    }

                override fun newArray(size: Int): Array<ManagerConfigurationCatalogEntryDto?> = arrayOfNulls(size)
            }
    }
}
