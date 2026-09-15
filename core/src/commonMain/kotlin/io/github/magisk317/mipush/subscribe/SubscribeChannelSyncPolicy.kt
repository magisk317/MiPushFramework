package io.github.magisk317.mipush.subscribe

/**
 * Platform-neutral decision logic for the stock subscribe-channel-sync stack (7.5.29).
 *
 * The stock implementation lives inside the XMSF service (com.xiaomi.push.subscribenotification.*,
 * obfuscated ae.g0/ae.d0 flows). This object keeps the pure parts — request batch chunking,
 * result-to-cache merging and ack extra composition — free of Android and thrift types so they
 * stay testable in :core, while the wire encoding lives in :pinned and the Android adapters in
 * :xmsf:shell.
 */
object SubscribeChannelSyncPolicy {
    /** Stock AppSubManager chunks the app list into batches of 50 entries (ae.b run(): size / 50). */
    const val STOCK_BATCH_SIZE = 50

    /** Stock stores -1 for apps without a server-acknowledged config version yet. */
    const val DEFAULT_APP_CONFIG_VERSION = -1L

    /** Ack extra keys written by stock m0.m(sendSubscribeChannelsSyncResultACK). */
    const val EXTRA_SESSION_ID = "sessionId"
    const val EXTRA_BATCH_INDEX = "batchIndex"

    /** One line of the uplink request batch: package plus the locally known config version. */
    data class AppConfigEntry(val packageName: String, val appConfigVersion: Long)

    /** One uplink request batch; mirrors XmPushSubscribeChannelSync field semantics. */
    data class RequestBatch(
        val sessionId: String,
        val batchIndex: Int,
        val totalBatch: Int,
        val totalNum: Int,
        val apps: List<AppConfigEntry>,
    )

    /**
     * Drops blank packages and maps unknown packages to the stock default version (-1),
     * preserving caller order (stock iterates the whitelist set in list order).
     */
    fun requestEntries(knownVersions: Map<String, Long>, packages: List<String>): List<AppConfigEntry> =
        packages
            .filter { it.isNotBlank() }
            .map { AppConfigEntry(it, knownVersions[it] ?: DEFAULT_APP_CONFIG_VERSION) }

    /**
     * Stock batching: `totalBatch = size / 50 + (size % 50 == 0 ? 0 : 1)`, 0-based batchIndex,
     * totalNum counts every app of the session, all batches share one sessionId. An empty app
     * list or a blank session produces no batches (stock aborts before sending).
     */
    fun buildRequestBatches(
        sessionId: String,
        apps: List<AppConfigEntry>,
        batchSize: Int = STOCK_BATCH_SIZE,
    ): List<RequestBatch> {
        require(batchSize > 0) { "batchSize must be positive" }
        if (sessionId.isBlank() || apps.isEmpty()) {
            return emptyList()
        }
        val totalBatch = (apps.size + batchSize - 1) / batchSize
        return (0 until totalBatch).map { index ->
            RequestBatch(
                sessionId = sessionId,
                batchIndex = index,
                totalBatch = totalBatch,
                totalNum = apps.size,
                apps = apps.subList(index * batchSize, minOf((index + 1) * batchSize, apps.size)).toList(),
            )
        }
    }

    /**
     * Applies one received result batch to the local version cache. Stock treats the server copy
     * as authoritative (room upsert of the AppSubChannels row), so every valid entry overwrites
     * the stored version; blank packages and sub-default sentinel versions are ignored.
     */
    fun applyResult(current: Map<String, Long>, received: Map<String, Long>): Map<String, Long> {
        val merged = LinkedHashMap(current)
        received.forEach { (packageName, version) ->
            if (packageName.isNotBlank() && version >= DEFAULT_APP_CONFIG_VERSION) {
                merged[packageName] = version
            }
        }
        return merged
    }

    /**
     * Ack extras for one result batch, mirroring stock m0.m: copy the inbound notification extra,
     * then put sessionId/batchIndex as strings (String.valueOf semantics: a null session becomes
     * the literal "null").
     */
    fun ackExtras(existingExtra: Map<String, String>?, sessionId: String?, batchIndex: Int): Map<String, String> {
        val extras = LinkedHashMap(existingExtra.orEmpty())
        extras[EXTRA_SESSION_ID] = sessionId.toString()
        extras[EXTRA_BATCH_INDEX] = batchIndex.toString()
        return extras
    }
}
