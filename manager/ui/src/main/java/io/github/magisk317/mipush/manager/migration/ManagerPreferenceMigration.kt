package io.github.magisk317.mipush.manager.migration

import co.touchlab.kermit.Logger
import io.github.magisk317.mipush.data.OwnedPreferenceValue
import io.github.magisk317.mipush.data.PreferenceOwner
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.manager.api.ManagerPreferenceEntryDto
import io.github.magisk317.mipush.manager.client.ManagerRuntimeAvailability
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.client.ManagerRuntimeResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One-shot import of manager-owned preferences from the runtime package via Binder snapshot.
 */
object ManagerPreferenceMigration {
    private const val TAG = "ManagerPrefMigration"
    private const val WAIT_AVAILABLE_MS = 8_000L

    fun schedule(
        scope: CoroutineScope,
        client: ManagerRuntimeClient,
        preferenceRepository: PreferenceRepository,
    ) {
        scope.launch(Dispatchers.IO) {
            runCatching { maybeMigrate(client, preferenceRepository) }
                .onFailure { Logger.withTag(TAG).w(it) { "migration failed" } }
        }
    }

    suspend fun maybeMigrate(
        client: ManagerRuntimeClient,
        preferenceRepository: PreferenceRepository,
    ): Int {
        if (preferenceRepository.isManagerMigrationApplied()) {
            return 0
        }
        val available = withTimeoutOrNull(WAIT_AVAILABLE_MS) {
            client.availability.first { it is ManagerRuntimeAvailability.Available }
        }
        if (available == null) {
            Logger.withTag(TAG).i { "runtime not available yet; migration deferred" }
            return 0
        }
        return when (val result = client.getManagerMigrationSnapshot()) {
            is ManagerRuntimeResult.Success -> {
                val entries = result.value.entries.map { it.toOwned() }
                val written = preferenceRepository.importOwnedPreferences(
                    entries = entries,
                    owner = PreferenceOwner.MANAGER,
                    onlyMissing = true,
                )
                preferenceRepository.setManagerMigrationApplied(true)
                Logger.withTag(TAG).i { "imported $written manager preference keys" }
                written
            }
            is ManagerRuntimeResult.Unsupported -> {
                preferenceRepository.setManagerMigrationApplied(true)
                Logger.withTag(TAG).i { "migration snapshot unsupported; marked applied" }
                0
            }
            else -> {
                Logger.withTag(TAG).i { "migration snapshot unavailable: $result" }
                0
            }
        }
    }

    private fun ManagerPreferenceEntryDto.toOwned(): OwnedPreferenceValue =
        OwnedPreferenceValue(
            key = key,
            type = type,
            value = value,
            owner = PreferenceOwner.MANAGER,
        )
}
