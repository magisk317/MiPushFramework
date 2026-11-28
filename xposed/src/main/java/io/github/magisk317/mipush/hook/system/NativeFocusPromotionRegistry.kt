package io.github.magisk317.mipush.hook.system

import android.os.SystemClock
import java.security.SecureRandom
import java.util.LinkedHashMap

private object NativeFocusPromotionTokens {
    private const val TOKEN_BYTES = 16
    private val secureRandom = SecureRandom()

    fun create(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}

/**
 * One-shot system_server authorization for notifications rewritten by the native-focus adapter.
 *
 * The notification extra is only a lookup token, not proof on its own. The registry binds the
 * token to the resolved posting package and UID, and consuming a token always removes it so an
 * unrelated caller or replay cannot reuse the authorization.
 */
internal class NativeFocusPromotionRegistry(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val tokenLifetimeMs: Long = DEFAULT_TOKEN_LIFETIME_MS,
    private val nowMillis: () -> Long = SystemClock::elapsedRealtime,
    private val tokenFactory: () -> String = NativeFocusPromotionTokens::create,
) {
    private data class Grant(
        val packageName: String,
        val uid: Int,
        val expiresAtMillis: Long,
    )

    private val grants = LinkedHashMap<String, Grant>()

    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
        require(tokenLifetimeMs > 0L) { "tokenLifetimeMs must be positive" }
    }

    @Synchronized
    fun issue(packageName: String, uid: Int): String? {
        if (packageName.isBlank() || uid < 0) return null
        val now = nowMillis()
        removeExpired(now)
        if (grants.size >= maxEntries) return null
        val expiry = if (Long.MAX_VALUE - now < tokenLifetimeMs) Long.MAX_VALUE else now + tokenLifetimeMs
        repeat(MAX_TOKEN_ATTEMPTS) {
            val token = tokenFactory().takeIf(String::isNotBlank) ?: return@repeat
            if (!grants.containsKey(token)) {
                grants[token] = Grant(packageName, uid, expiry)
                return token
            }
        }
        return null
    }

    @Synchronized
    fun consume(token: String?, packageName: String, uid: Int): Boolean {
        if (token.isNullOrBlank() || packageName.isBlank() || uid < 0) return false
        val grant = grants.remove(token) ?: return false
        return grant.expiresAtMillis > nowMillis() &&
            grant.packageName == packageName &&
            grant.uid == uid
    }

    @Synchronized
    internal fun sizeForTest(): Int {
        removeExpired(nowMillis())
        return grants.size
    }

    private fun removeExpired(now: Long) {
        val iterator = grants.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value.expiresAtMillis <= now) iterator.remove()
        }
    }

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 512
        const val DEFAULT_TOKEN_LIFETIME_MS = 10_000L
        const val MAX_TOKEN_ATTEMPTS = 4
    }
}
