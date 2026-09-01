package io.github.magisk317.mipush.common.notification.iconpack

import android.content.Context
import android.graphics.Bitmap
import co.touchlab.kermit.Logger
import java.util.LinkedHashMap
import kotlin.math.ceil
import kotlin.math.max

/** A result that can be safely consumed by notification icon callers. */
sealed interface ResolveResult {
    data class Available(val value: ValidatedIconPack) : ResolveResult

    data class Unavailable(
        val reason: ResolveFailure,
        val protocolReason: ProtocolFailureReason? = null,
    ) : ResolveResult
}

enum class ResolveFailure {
    BLOCKED,
    EMPTY,
    INACCESSIBLE,
    PROTOCOL_ERROR,
    PARSE_ERROR,
    PACKAGE_MISMATCH,
    NULL_BITMAP,
    BITMAP_DECODE_FAILED,
    INVALID_SIZE,
    LOAD_FAILED,
}

/**
 * Immutable value object crossing the resolver boundary. The bitmap is the only icon resource;
 * iconColor is optional metadata and never participates in bitmap validation.
 */
data class ValidatedIconPack(
    val targetPackage: String,
    val userId: Int,
    val bitmap: Bitmap,
    val iconColor: Int?,
    val protocolVersion: String,
    val revisionToken: String? = null,
    val sourceIdentity: String = thirdPartyPackSourceIdentity(targetPackage),
) {
    init {
        require(targetPackage.isNotBlank()) { "validated target package must not be blank" }
        require(userId >= 0) { "validated user id must not be negative" }
        require(!bitmap.isRecycled) { "validated bitmap must not be recycled" }
        require(bitmap.width in 1..MAX_BITMAP_DIMENSION) { "validated bitmap width is invalid" }
        require(bitmap.height in 1..MAX_BITMAP_DIMENSION) { "validated bitmap height is invalid" }
        require(protocolVersion.isNotBlank()) { "validated protocol version must not be blank" }
        require(revisionToken == null || revisionToken.isNotBlank()) {
            "validated revision token must not be blank"
        }
        require(sourceIdentity == thirdPartyPackSourceIdentity(targetPackage)) {
            "validated source identity must identify the exact target package"
        }
    }
}

data class IconPackCacheKey(
    val userId: Int,
    val targetPackage: String,
    val protocolVersion: String,
    val revisionToken: String,
) {
    init {
        require(userId >= 0) { "cache user id must not be negative" }
        require(targetPackage.isNotBlank()) { "cache target package must not be blank" }
        require(protocolVersion.isNotBlank()) { "cache protocol version must not be blank" }
        require(revisionToken.isNotBlank()) { "cache revision token must not be blank" }
    }
}

/** Successful values only; failures are deliberately never inserted into this cache. */
interface IconPackResultCache {
    fun get(key: IconPackCacheKey): ValidatedIconPack?
    fun put(key: IconPackCacheKey, value: ValidatedIconPack)
    fun clear()
}

/** Small bounded cache with active invalidation when a scope's version or revision changes. */
class InMemoryIconPackResultCache(private val maxEntries: Int = 32) : IconPackResultCache {
    init {
        require(maxEntries > 0) { "cache capacity must be positive" }
    }

    private val entries = object : LinkedHashMap<IconPackCacheKey, ValidatedIconPack>(maxEntries, 0.75f, true) {
        protected override fun removeEldestEntry(eldest: MutableMap.MutableEntry<IconPackCacheKey, ValidatedIconPack>?): Boolean =
            size > maxEntries
    }

    @Synchronized
    override fun get(key: IconPackCacheKey): ValidatedIconPack? = entries[key]

    @Synchronized
    override fun put(key: IconPackCacheKey, value: ValidatedIconPack) {
        entries.keys.removeAll { existing ->
            existing.userId == key.userId &&
                existing.targetPackage == key.targetPackage &&
                existing != key
        }
        entries[key] = value
    }

    @Synchronized
    fun size(): Int = entries.size

    @Synchronized
    override fun clear() {
        entries.clear()
    }
}

data class IconPackResolutionObservation(
    val state: ProtocolState,
    val errorType: String,
    val fallbackReason: String,
    val targetPackageDigest: String,
    val userDigest: String,
    val sourceDigest: String,
)

fun interface IconPackObservationLogger {
    fun record(observation: IconPackResolutionObservation)
}

private val DefaultIconPackObservationLogger = IconPackObservationLogger { observation ->
    Logger.withTag("IconPackResolver").w {
        "icon-pack fallback state=${observation.state} error=${observation.errorType} " +
            "reason=${observation.fallbackReason} " +
            "pkgDigest=${observation.targetPackageDigest} userDigest=${observation.userDigest} " +
            "sourceDigest=${observation.sourceDigest}"
    }
}


/** Scope seam supplied by the existing MiPush/notification caller. */
fun interface IconPackCallerScope {
    fun isApplicable(context: Context, targetPackage: String, userId: Int): Boolean
}

/** User normalization seam; the protocol contract requires a concrete non-negative user id. */
fun interface IconPackUserNormalizer {
    fun normalize(context: Context, requestedUserId: Int?): Int
}

/** Query construction seam so protocol metadata remains auditable and testable. */
fun interface IconPackQueryFactory {
    fun create(context: Context, targetPackage: String, userId: Int): IconPackQuery
}

/** Existing notification-size strategy seam. */
fun interface NotificationBitmapScaler {
    fun scale(bitmap: Bitmap): Bitmap?
}

const val MAX_BITMAP_DIMENSION: Int = 4096
const val DEFAULT_NOTIFICATION_RECOMMENDED_SIZE: Int = 24

private const val DEFAULT_PROTOCOL_VERSION = "icon-pack/1"
private const val DEFAULT_PROTOCOL_TIMEOUT_MILLIS = 250L
private const val DEFAULT_PERMISSION_AUDITOR = "resolver"
private const val INVALID_USER_ID = -1

/**
 * Resolves protocol data only after the caller scope and normalized identity have passed preflight.
 * The default adapter is fail-closed; no provider, Binder/AIDL endpoint, or private storage is
 * discovered or inferred here.
 */
class IconPackResolver(
    private val adapter: IconPackProtocolAdapter = BlockedIconPackProtocolAdapter,
    private val callerScope: IconPackCallerScope = IconPackCallerScope { _, _, _ -> true },
    private val userNormalizer: IconPackUserNormalizer = IconPackUserNormalizer { _, userId ->
        userId ?: INVALID_USER_ID
    },
    private val queryFactory: IconPackQueryFactory = DefaultIconPackQueryFactory,
    private val bitmapScaler: NotificationBitmapScaler = ExistingNotificationBitmapScaler,
    private val cache: IconPackResultCache = InMemoryIconPackResultCache(),
    private val observationLogger: IconPackObservationLogger = DefaultIconPackObservationLogger,
) {
    fun resolve(targetPackage: String, userId: Int?, context: Context): ResolveResult {
        val normalizedPackage = targetPackage.trim()
        if (normalizedPackage.isEmpty()) {
            return unavailable(normalizedPackage, userId ?: INVALID_USER_ID, ResolveFailure.PARSE_ERROR)
        }

        val normalizedUser = try {
            userNormalizer.normalize(context, userId)
        } catch (_: Exception) {
            return unavailable(normalizedPackage, userId ?: INVALID_USER_ID, ResolveFailure.PARSE_ERROR)
        }
        if (normalizedUser < 0) {
            return unavailable(normalizedPackage, normalizedUser, ResolveFailure.PARSE_ERROR)
        }

        val applicable = try {
            callerScope.isApplicable(context, normalizedPackage, normalizedUser)
        } catch (_: Exception) {
            return unavailable(normalizedPackage, normalizedUser, ResolveFailure.PROTOCOL_ERROR)
        }
        if (!applicable) {
            return unavailable(normalizedPackage, normalizedUser, ResolveFailure.BLOCKED)
        }

        val query = try {
            queryFactory.create(context, normalizedPackage, normalizedUser)
        } catch (_: Exception) {
            return unavailable(normalizedPackage, normalizedUser, ResolveFailure.PROTOCOL_ERROR)
        }
        val protocolResult = try {
            adapter.querySafely(query)
        } catch (_: Exception) {
            return unavailable(
                normalizedPackage,
                normalizedUser,
                ResolveFailure.BLOCKED,
                ProtocolFailureReason.EXCEPTION,
            )
        }
        return validateProtocolResult(protocolResult, normalizedPackage, normalizedUser)
    }

    private fun validateProtocolResult(
        result: ProtocolResult,
        targetPackage: String,
        userId: Int,
    ): ResolveResult {
        if (result.targetPackage != targetPackage || result.userId != userId) {
            return unavailable(targetPackage, userId, ResolveFailure.PACKAGE_MISMATCH)
        }
        val elapsed = result.timeout.elapsedMillis
        if (elapsed != null && elapsed > result.timeout.timeoutMillis) {
            return unavailable(targetPackage, userId, ResolveFailure.PROTOCOL_ERROR, ProtocolFailureReason.TIMEOUT)
        }
        if (result.state != ProtocolState.AVAILABLE) {
            return unavailable(
                targetPackage,
                userId,
                result.state.toResolveFailure(),
                result.failureReason,
                result.sourceIdentity,
                result.state,
            )
        }

        return validateAvailableResult(result, targetPackage, userId)
    }

    private fun validateAvailableResult(
        result: ProtocolResult,
        targetPackage: String,
        userId: Int,
    ): ResolveResult {
        val data = result.iconData
            ?: return unavailable(targetPackage, userId, ResolveFailure.EMPTY, sourceIdentity = result.sourceIdentity)
        if (data.packageName != targetPackage) {
            return unavailable(targetPackage, userId, ResolveFailure.PACKAGE_MISMATCH, sourceIdentity = result.sourceIdentity)
        }
        val version = result.protocolVersion?.value
            ?: return unavailable(targetPackage, userId, ResolveFailure.PROTOCOL_ERROR, sourceIdentity = result.sourceIdentity)
        val revisionToken = data.revisionToken
        val cacheKey = revisionToken?.let {
            IconPackCacheKey(userId, targetPackage, version, it)
        }
        if (cacheKey != null) {
            val cached = cache.get(cacheKey)
            if (cached != null && isCacheHitInScope(cached, cacheKey, targetPackage, userId)) {
                return ResolveResult.Available(cached)
            }
        }

        val bitmap = data.iconBitmap
            ?: return unavailable(targetPackage, userId, ResolveFailure.NULL_BITMAP, sourceIdentity = result.sourceIdentity)
        val initialValidation = validateBitmap(bitmap)
        if (initialValidation != null) {
            return unavailable(targetPackage, userId, initialValidation, sourceIdentity = result.sourceIdentity)
        }

        val candidate = if (
            bitmap.width < DEFAULT_NOTIFICATION_RECOMMENDED_SIZE ||
            bitmap.height < DEFAULT_NOTIFICATION_RECOMMENDED_SIZE
        ) {
            try {
                bitmapScaler.scale(bitmap)
            } catch (_: Exception) {
                return unavailable(targetPackage, userId, ResolveFailure.LOAD_FAILED, sourceIdentity = result.sourceIdentity)
            } ?: return unavailable(targetPackage, userId, ResolveFailure.LOAD_FAILED, sourceIdentity = result.sourceIdentity)
        } else {
            bitmap
        }
        val scaledValidation = validateBitmap(candidate)
        if (scaledValidation != null) {
            return unavailable(targetPackage, userId, scaledValidation, sourceIdentity = result.sourceIdentity)
        }

        val validated = try {
            ValidatedIconPack(
                targetPackage = targetPackage,
                userId = userId,
                bitmap = candidate,
                iconColor = data.iconColor,
                protocolVersion = version,
                revisionToken = revisionToken,
            )
        } catch (_: Exception) {
            return unavailable(targetPackage, userId, ResolveFailure.LOAD_FAILED, sourceIdentity = result.sourceIdentity)
        }
        // A missing revision cannot safely identify a cache entry. It is therefore never cached.
        if (cacheKey != null) {
            cache.put(cacheKey, validated)
        }
        return ResolveResult.Available(validated)
    }

    private fun isCacheHitInScope(
        cached: ValidatedIconPack,
        key: IconPackCacheKey,
        targetPackage: String,
        userId: Int,
    ): Boolean =
        cached.targetPackage == targetPackage &&
            cached.userId == userId &&
            cached.protocolVersion == key.protocolVersion &&
            cached.revisionToken == key.revisionToken &&
            cached.sourceIdentity == thirdPartyPackSourceIdentity(targetPackage) &&
            validateBitmap(cached.bitmap) == null

    private fun validateBitmap(bitmap: Bitmap): ResolveFailure? {
        return try {
            when {
                bitmap.isRecycled -> ResolveFailure.BITMAP_DECODE_FAILED
                bitmap.width !in 1..MAX_BITMAP_DIMENSION ||
                    bitmap.height !in 1..MAX_BITMAP_DIMENSION -> ResolveFailure.INVALID_SIZE
                else -> null
            }
        } catch (_: Exception) {
            ResolveFailure.BITMAP_DECODE_FAILED
        }
    }

    private fun unavailable(
        targetPackage: String,
        userId: Int,
        reason: ResolveFailure,
        protocolReason: ProtocolFailureReason? = null,
        sourceIdentity: String = ProtocolResult.BLOCKED_SOURCE,
        state: ProtocolState = reason.toProtocolState(),
    ): ResolveResult.Unavailable {
        observationLogger.record(
            IconPackResolutionObservation(
                state = state,
                errorType = protocolReason?.name ?: reason.name,
                fallbackReason = "APP",
                targetPackageDigest = digestIdentity(targetPackage),
                userDigest = digestIdentity("user:$userId"),
                sourceDigest = digestIdentity(sourceIdentity),
            ),
        )
        return ResolveResult.Unavailable(reason, protocolReason)
    }
}

private object DefaultIconPackQueryFactory : IconPackQueryFactory {
    override fun create(context: Context, targetPackage: String, userId: Int): IconPackQuery {
        val requestedVersion = ProtocolVersion(DEFAULT_PROTOCOL_VERSION)
        return IconPackQuery(
            targetPackage = targetPackage,
            userId = userId,
            caller = ProtocolCallerIdentity(
                packageName = context.packageName,
                uid = null,
                processName = null,
            ),
            permission = ProtocolPermissionAudit(
                requiredPermission = null,
                granted = false,
                checkedBy = DEFAULT_PERMISSION_AUDITOR,
            ),
            timeout = ProtocolTimeout(DEFAULT_PROTOCOL_TIMEOUT_MILLIS),
            compatibility = ProtocolCompatibility(
                requested = requestedVersion,
                compatible = false,
                negotiated = null,
            ),
        )
    }
}

/**
 * Equivalent to the existing notification icon strategy: scale a small bitmap uniformly until
 * both dimensions meet the recommendation, preserving aspect ratio. The resolver validates the
 * result again, including the 4096-pixel upper bound.
 */
private object ExistingNotificationBitmapScaler : NotificationBitmapScaler {
    override fun scale(bitmap: Bitmap): Bitmap? {
        val scale = max(
            DEFAULT_NOTIFICATION_RECOMMENDED_SIZE.toDouble() / bitmap.width.toDouble(),
            DEFAULT_NOTIFICATION_RECOMMENDED_SIZE.toDouble() / bitmap.height.toDouble(),
        )
        val width = ceil(bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = ceil(bitmap.height * scale).toInt().coerceAtLeast(1)
        if (width == bitmap.width && height == bitmap.height) return bitmap
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
}

private fun ProtocolState.toResolveFailure(): ResolveFailure = when (this) {
    ProtocolState.EMPTY -> ResolveFailure.EMPTY
    ProtocolState.INACCESSIBLE -> ResolveFailure.INACCESSIBLE
    ProtocolState.ERROR -> ResolveFailure.PROTOCOL_ERROR
    ProtocolState.BLOCKED -> ResolveFailure.BLOCKED
    ProtocolState.AVAILABLE -> ResolveFailure.PROTOCOL_ERROR
}

private fun ResolveFailure.toProtocolState(): ProtocolState = when (this) {
    ResolveFailure.BLOCKED -> ProtocolState.BLOCKED
    ResolveFailure.EMPTY -> ProtocolState.EMPTY
    ResolveFailure.INACCESSIBLE -> ProtocolState.INACCESSIBLE
    ResolveFailure.PROTOCOL_ERROR,
    ResolveFailure.PARSE_ERROR,
    ResolveFailure.PACKAGE_MISMATCH,
    ResolveFailure.NULL_BITMAP,
    ResolveFailure.BITMAP_DECODE_FAILED,
    ResolveFailure.INVALID_SIZE,
    ResolveFailure.LOAD_FAILED -> ProtocolState.ERROR
}
