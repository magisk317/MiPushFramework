package io.github.magisk317.mipush.manager.api

import io.github.magisk317.mipush.manager.ConfigurationCatalogEntryValidationInput
import io.github.magisk317.mipush.manager.ConfigurationCatalogValidationInput
import io.github.magisk317.mipush.manager.ConfigurationUploadRequestValidationInput
import io.github.magisk317.mipush.manager.ConfigurationUploadResultValidationInput
import io.github.magisk317.mipush.manager.ManagerContractValidationCore
import io.github.magisk317.mipush.manager.PreferenceEntryValidationInput
import io.github.magisk317.mipush.manager.RuntimePreferencesValidationInput

/** Parcelable facade over the platform-neutral configuration contract validation. */
internal object ManagerConfigurationContractValidation {
    fun validateRuntimePreferences(snapshot: ManagerRuntimePreferencesDto): String? =
        ManagerContractValidationCore.validateRuntimePreferences(
            RuntimePreferencesValidationInput(
                schemaVersion = snapshot.schemaVersion,
                entries = snapshot.entries.map(::preferenceInput),
            ),
        )

    fun validateManagerMigrationSnapshot(snapshot: ManagerMigrationSnapshotDto): String? =
        ManagerContractValidationCore.validateManagerMigrationSnapshot(
            schemaVersion = snapshot.schemaVersion,
            entries = snapshot.entries.map(::preferenceInput),
        )

    fun validateConfigurationUploadRequest(request: ManagerConfigurationUploadRequestDto): String? =
        ManagerContractValidationCore.validateConfigurationUploadRequest(
            ConfigurationUploadRequestValidationInput(
                schemaVersion = request.schemaVersion,
                path = request.path,
                contentLength = request.contentLength,
                descriptorPresent = request.parcelFileDescriptor != null,
            ),
        )

    fun validateConfigurationUploadResult(result: ManagerConfigurationUploadResultDto): String? =
        ManagerContractValidationCore.validateConfigurationUploadResult(
            ConfigurationUploadResultValidationInput(
                schemaVersion = result.schemaVersion,
                details = result.details,
            ),
        )

    private fun preferenceInput(entry: ManagerPreferenceEntryDto) = PreferenceEntryValidationInput(
        schemaVersion = entry.schemaVersion,
        key = entry.key,
        type = entry.type,
        value = entry.value,
        owner = entry.owner,
    )
}
