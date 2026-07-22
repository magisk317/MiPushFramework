package io.github.magisk317.mipush.manager.api

import android.os.Parcel
import android.os.Parcelable

/**
 * One-time export of manager-owned preferences from a bundled install so a later split manager
 * package can seed its private store without reading XMSF private files.
 */
data class ManagerMigrationSnapshotDto(
    val schemaVersion: Int = ManagerProtocol.MANAGER_MIGRATION_SNAPSHOT_SCHEMA_VERSION,
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
        val CREATOR: Parcelable.Creator<ManagerMigrationSnapshotDto> =
            object : Parcelable.Creator<ManagerMigrationSnapshotDto> {
                override fun createFromParcel(source: Parcel): ManagerMigrationSnapshotDto =
                    source.readWireFrame {
                        ManagerMigrationSnapshotDto(
                            schemaVersion = readInt(ManagerProtocol.MANAGER_MIGRATION_SNAPSHOT_SCHEMA_VERSION),
                            entries = readParcelableList(
                                creator = ManagerPreferenceEntryDto.CREATOR,
                                maxItems = ManagerProtocol.MAX_PREFERENCE_ENTRY_COUNT,
                            ),
                        )
                    }

                override fun newArray(size: Int): Array<ManagerMigrationSnapshotDto?> = arrayOfNulls(size)
            }
    }
}
