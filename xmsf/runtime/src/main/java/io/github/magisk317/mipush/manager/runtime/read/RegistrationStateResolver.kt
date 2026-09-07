package io.github.magisk317.mipush.manager.runtime.read

import io.github.magisk317.mipush.manager.application.ManagerApplication
import java.util.concurrent.ConcurrentHashMap

/** Result of probing the application-local registration artifacts. */
enum class LocalRegistrationProbeState {
    REGISTERED,
    NOT_REGISTERED,
    UNKNOWN,
}

/** Short-lived in-memory cache; it never persists registration artifacts or regSec. */
class LocalRegistrationSnapshotCache(
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private data class Key(val userId: Int, val packageName: String)

    private data class Entry(
        val state: LocalRegistrationProbeState,
        val expiresAtMs: Long,
    )

    private val entries = ConcurrentHashMap<Key, Entry>()

    init {
        require(ttlMs > 0L) { "Registration cache TTL must be positive" }
    }

    fun getFresh(userId: Int, packageNames: Collection<String>): Map<String, LocalRegistrationProbeState> {
        val now = nowMs()
        return packageNames.distinct().mapNotNull { packageName ->
            val entry = entries[Key(userId, packageName)]
            if (entry == null || entry.expiresAtMs <= now) {
                if (entry != null) entries.remove(Key(userId, packageName), entry)
                null
            } else {
                packageName to entry.state
            }
        }.toMap()
    }

    fun put(
        userId: Int,
        states: Map<String, LocalRegistrationProbeState>,
    ) {
        val expiresAtMs = nowMs() + ttlMs
        states.forEach { (packageName, state) ->
            if (state != LocalRegistrationProbeState.UNKNOWN) {
                entries[Key(userId, packageName)] = Entry(state, expiresAtMs)
            }
        }
    }

    fun invalidate(userId: Int, packageName: String? = null) {
        if (packageName != null) {
            entries.remove(Key(userId, packageName))
        } else {
            entries.keys.toList().forEach { key ->
                if (key.userId == userId) entries.remove(key)
            }
        }
    }

    companion object {
        const val DEFAULT_TTL_MS = 15_000L
    }
}

/** Process-local only; it is intentionally lost when the runtime process stops. */
object ProcessLocalRegistrationSnapshotCache {
    val instance = LocalRegistrationSnapshotCache()
}

/** Single policy for merging persisted Manager state with local registration evidence. */
object RegistrationStateResolver {
    fun shouldProbe(storedType: Int?): Boolean =
        storedType == null || storedType == ManagerApplication.RegisteredType.NOT_REGISTERED

    fun resolveRegisteredType(
        storedType: Int?,
        localState: LocalRegistrationProbeState,
    ): Int {
        val baseType = storedType ?: ManagerApplication.RegisteredType.NOT_REGISTERED
        return if (
            localState == LocalRegistrationProbeState.REGISTERED &&
            baseType == ManagerApplication.RegisteredType.NOT_REGISTERED
        ) {
            ManagerApplication.RegisteredType.REGISTERED
        } else {
            baseType
        }
    }
}
