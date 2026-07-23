package io.github.magisk317.mipush.manager.runtime.read

import io.github.magisk317.mipush.app.di.AppDependencies
import io.github.magisk317.mipush.data.OwnedPreferenceValue
import io.github.magisk317.mipush.data.PreferenceOwner
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.manager.api.ManagerMigrationSnapshotDto
import io.github.magisk317.mipush.manager.api.ManagerPreferenceEntryDto
import io.github.magisk317.mipush.manager.api.ManagerRuntimePreferencesDto
import kotlinx.coroutines.runBlocking

class ManagerPreferenceRuntimeReader(
    private val preferenceRepository: PreferenceRepository,
) {
    constructor(context: android.content.Context) : this(
        preferenceRepository = AppDependencies.get(context),
    )

    fun readRuntimePreferences(): ManagerRuntimePreferencesDto =
        ManagerRuntimePreferencesDto(
            entries = runBlocking {
                preferenceRepository.exportOwnedPreferences(PreferenceOwner.RUNTIME)
            }.map { it.toWire("runtime") },
        )

    fun readManagerMigrationSnapshot(): ManagerMigrationSnapshotDto =
        ManagerMigrationSnapshotDto(
            entries = runBlocking {
                preferenceRepository.exportOwnedPreferences(PreferenceOwner.MANAGER)
            }.map { it.toWire("manager") },
        )

    private fun OwnedPreferenceValue.toWire(ownerName: String): ManagerPreferenceEntryDto =
        ManagerPreferenceEntryDto(
            key = key,
            type = type,
            value = value,
            owner = ownerName,
        )
}
