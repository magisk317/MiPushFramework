package io.github.magisk317.mipush.manager.backup

import kotlinx.serialization.Serializable

/** One typed manager preference entry inside a [MiPushBackupArchive]. */
@Serializable
data class MiPushBackupPreference(
    val key: String,
    val type: String,
    val value: String,
)

/**
 * Payload format written by [MiPushCloudBackupPayloadSource]. Kept deliberately
 * small: only the manager-owned preference plane. [formatVersion] gates restore,
 * so the archive can grow sections (runtime policy, configuration catalog, ...)
 * without breaking older writers.
 */
@Serializable
data class MiPushBackupArchive(
    val formatVersion: Int,
    val appVersion: String,
    val preferences: List<MiPushBackupPreference> = emptyList(),
)
