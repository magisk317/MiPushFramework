package io.github.magisk317.mipush.manager.api

import android.os.Parcel
import android.os.Parcelable

/** Remote configuration catalog metadata only; local SAF content stays manager-owned. */
data class ManagerConfigurationCatalogDto(
    val schemaVersion: Int = ManagerProtocol.CONFIGURATION_CATALOG_SCHEMA_VERSION,
    val sourceRepo: String = "",
    val branch: String = "",
    val generatedAt: String = "",
    val files: List<ManagerConfigurationCatalogEntryDto> = emptyList(),
) : Parcelable {
    override fun writeToParcel(destination: Parcel, flags: Int) {
        destination.writeWireFrame {
            writeInt(schemaVersion)
            writeString(sourceRepo)
            writeString(branch)
            writeString(generatedAt)
            writeInt(files.size)
            files.forEach { it.writeToParcel(this, flags) }
        }
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ManagerConfigurationCatalogDto> =
            object : Parcelable.Creator<ManagerConfigurationCatalogDto> {
                override fun createFromParcel(source: Parcel): ManagerConfigurationCatalogDto =
                    source.readWireFrame {
                        ManagerConfigurationCatalogDto(
                            schemaVersion = readInt(ManagerProtocol.CONFIGURATION_CATALOG_SCHEMA_VERSION),
                            sourceRepo = readString().orEmpty(),
                            branch = readString().orEmpty(),
                            generatedAt = readString().orEmpty(),
                            files = readParcelableList(
                                creator = ManagerConfigurationCatalogEntryDto.CREATOR,
                                maxItems = ManagerProtocol.MAX_CONFIGURATION_CATALOG_ITEM_COUNT,
                            ),
                        )
                    }

                override fun newArray(size: Int): Array<ManagerConfigurationCatalogDto?> = arrayOfNulls(size)
            }
    }
}
