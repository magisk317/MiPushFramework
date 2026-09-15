package io.github.magisk317.mipush.subscribe

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * SharedPreferences/JSON-backed cache of the app channel configuration learned from the
 * subscribe-channel-sync stack (stock keeps this in the Room AppSubChannels table; we avoid the
 * Room schema debt deliberately). The cached per-package `appConfigVersion` is what the next
 * `subscribe_channel_sync` request batch reports, letting the server skip unchanged apps.
 *
 * Persistence is injected through [Persistence] so the cache semantics stay unit-testable without
 * the Android framework; [SubscribeChannelSyncPolicy] holds the neutral merge rules.
 */
class SubscribeChannelSyncStore(
    private val persistence: Persistence,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    @Serializable
    data class ChannelSnapshot(
        val channelId: String,
        val channelName: String,
        val description: String,
        val importance: Int,
        val defaultOpen: Int,
        val channelPermission: Int,
        val soundUri: String? = null,
        val channelNotifyType: Int,
        val isDeprecated: Int = 0,
        val lockscreenVisibility: Int = 0,
    )

    @Serializable
    data class ChannelGroupSnapshot(
        val channelGroupId: String,
        val channelGroupName: String,
        val channelGroupDescription: String,
        val defaultOpen: Int,
        val isDefaultGroup: Int,
        val channels: List<ChannelSnapshot> = emptyList(),
        val isDeprecated: Int = 0,
    )

    @Serializable
    data class AppChannelRecord(
        val appConfigVersion: Long,
        val updatedAtMs: Long,
        val channelGroups: List<ChannelGroupSnapshot> = emptyList(),
    )

    interface Persistence {
        fun load(): Map<String, String>

        fun save(values: Map<String, String>)

        companion object {
            /** In-memory persistence for tests and dry runs. */
            @JvmStatic
            fun inMemory(initial: Map<String, String> = emptyMap()): Persistence {
                val storage = LinkedHashMap(initial)
                return object : Persistence {
                    override fun load(): Map<String, String> = LinkedHashMap(storage)

                    override fun save(values: Map<String, String>) {
                        storage.clear()
                        storage.putAll(values)
                    }
                }
            }
        }
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val cache: MutableMap<String, AppChannelRecord> = loadCache()

    /** Package name -> locally known server config version (stock id.b.k(...)); unknown apps are absent. */
    @Synchronized
    fun appConfigVersions(): Map<String, Long> = cache.mapValues { it.value.appConfigVersion }

    @Synchronized
    fun cachedConfig(packageName: String): AppChannelRecord? = cache[packageName]

    /**
     * Merges the apps carried by one decoded result batch. Server copies win (stock performs a
     * Room upsert); blank packages and versions below the -1 sentinel are dropped by the neutral
     * policy rules. Returns the packages actually persisted.
     */
    @Synchronized
    fun applyReceived(received: Map<String, AppChannelRecord>): Set<String> {
        if (received.isEmpty()) {
            return emptySet()
        }
        val accepted = LinkedHashMap<String, AppChannelRecord>()
        received.forEach { (packageName, record) ->
            if (packageName.isNotBlank() && record.appConfigVersion >= SubscribeChannelSyncPolicy.DEFAULT_APP_CONFIG_VERSION) {
                accepted[packageName] = record.copy(updatedAtMs = nowMs())
            }
        }
        if (accepted.isEmpty()) {
            return emptySet()
        }
        cache.putAll(accepted)
        persistence.save(encodeAll(cache))
        return accepted.keys
    }

    private fun loadCache(): MutableMap<String, AppChannelRecord> {
        val result = LinkedHashMap<String, AppChannelRecord>()
        runCatching { persistence.load() }.getOrDefault(emptyMap()).forEach { (packageName, raw) ->
            runCatching { json.decodeFromString(AppChannelRecord.serializer(), raw) }
                .getOrNull()
                ?.let { result[packageName] = it }
        }
        return result
    }

    private fun encodeAll(values: Map<String, AppChannelRecord>): Map<String, String> =
        values.mapValues { json.encodeToString(AppChannelRecord.serializer(), it.value) }

    /** Production persistence: one SharedPreferences file, package name -> JSON record. */
    class SharedPreferencesPersistence(
        context: Context,
        private val fileName: String = SP_NAME,
    ) : Persistence {
        private val preferences = context.applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)

        override fun load(): Map<String, String> =
            preferences.all
                .filterValues { it is String }
                .mapValues { it.value as String }

            override fun save(values: Map<String, String>) {
                preferences.edit()
                    .clear()
                    .apply { values.forEach { (key, value) -> putString(key, value) } }
                    .apply()
            }
    }

    companion object {
        const val SP_NAME = "subscribe_channel_sync"

        @JvmStatic
        fun forContext(context: Context): SubscribeChannelSyncStore =
            SubscribeChannelSyncStore(SharedPreferencesPersistence(context))
    }
}
