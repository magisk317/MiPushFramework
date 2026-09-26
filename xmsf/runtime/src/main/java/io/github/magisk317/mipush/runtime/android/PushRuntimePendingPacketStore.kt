package io.github.magisk317.mipush.runtime.android

import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.mipush.common.utils.Utils

data class PendingPacketEntry(
    val packageName: String,
    val payload: ByteArray,
    val userId: Int,
)

data class DiscardedPendingPackets(
    val registrationRequests: Int,
    val messages: Int,
)

fun interface PendingPacketSender {
    fun send(packageName: String, payload: ByteArray)
}

fun interface PendingPacketErrorNotifier {
    fun notify(packageName: String, payload: ByteArray, errorCode: Int, errorMessage: String)
}

object PushRuntimePendingPacketStore {
    private const val MAX_PENDING_MESSAGES = 50
    private val lock = Any()
    private val pendingRegistrationRequests = LinkedHashMap<String, ByteArray>()
    private var pendingMessages = ArrayList<PendingPacketEntry>()

    @JvmStatic
    fun addPendingMessage(
        packageName: String,
        payload: ByteArray,
        androidUserId: Int = currentUserId(),
    ) {
        val userId = Utils.requireValidUserId(androidUserId)
        val pendingCount = synchronized(lock) {
            pendingMessages.add(PendingPacketEntry(packageName, payload.copyOf(), userId))
            val userMessages = pendingMessages.count { it.userId == userId }
            if (userMessages > MAX_PENDING_MESSAGES) {
                val oldestForUser = pendingMessages.indexOfFirst { it.userId == userId }
                if (oldestForUser >= 0) pendingMessages.removeAt(oldestForUser)
            }
            pendingMessages.count { it.userId == userId }
        }
        MagiskOtel.event(
            name = "push.receive",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "main",
                "stage" to "pending_cache",
                "reason" to "message_queued",
                "target_package" to packageName,
                "payload_size" to payload.size.toString(),
                "pending_count" to pendingCount.toString(),
            ),
            statusOk = true,
        )
    }

    @JvmStatic
    fun cacheRegistrationRequest(
        packageName: String,
        payload: ByteArray,
        androidUserId: Int = currentUserId(),
    ) {
        val userId = Utils.requireValidUserId(androidUserId)
        val pendingCount = synchronized(lock) {
            pendingRegistrationRequests[registrationKey(userId, packageName)] = payload.copyOf()
            pendingRegistrationRequests.keys.count { it.startsWith("$userId:") }
        }
        AndroidPushRuntime.observeRegistrationRequest(
            packageName = packageName,
            source = "PushRuntimePendingPacketStore.cacheRegistrationRequest",
            reason = "awaiting_connection",
            androidUserId = userId,
        )
        MagiskOtel.event(
            name = "push.register",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "main",
                "stage" to "pending_cache",
                "reason" to "awaiting_connection",
                "target_package" to packageName,
                "payload_size" to payload.size.toString(),
                "pending_count" to pendingCount.toString(),
            ),
            statusOk = true,
        )
    }

    @JvmStatic
    fun processPendingMessages(
        source: String,
        sender: PendingPacketSender,
        androidUserId: Int = currentUserId(),
    ): Int {
        val userId = Utils.requireValidUserId(androidUserId)
        val queued = synchronized(lock) {
            pendingMessages.filter { it.userId == userId }.also {
                pendingMessages = ArrayList(pendingMessages.filterNot { it.userId == userId })
            }
        }
        if (queued.isEmpty()) {
            return 0
        }
        var sent = 0
        try {
            queued.forEach { entry ->
                sender.send(entry.packageName, entry.payload.copyOf())
                sent += 1
            }
        } catch (t: Throwable) {
            requeueMessages(queued.drop(sent))
            throw t
        }
        if (sent > 0) {
            AndroidPushRuntime.observeChannelEvent(
                packageName = null,
                action = "pending_messages_flushed",
                source = source
            )
        }
        MagiskOtel.event(
            name = "push.receive",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "main",
                "stage" to "pending_flush",
                "reason" to source,
                "pending_count" to sent.toString(),
            ),
            statusOk = true,
        )
        return sent
    }

    @JvmStatic
    fun processPendingRegistrationRequests(
        source: String,
        sender: PendingPacketSender,
        androidUserId: Int = currentUserId(),
    ): Int {
        val userId = Utils.requireValidUserId(androidUserId)
        val queued = synchronized(lock) {
            pendingRegistrationRequests
                .filterKeys { it.startsWith("$userId:") }
                .map { PendingPacketEntry(it.key.removePrefix("$userId:"), it.value.copyOf(), userId) }
                .also { pendingRegistrationRequests.keys.removeIf { it.startsWith("$userId:") } }
        }
        if (queued.isEmpty()) {
            return 0
        }
        var sent = 0
        try {
            queued.forEach { entry ->
                sender.send(entry.packageName, entry.payload.copyOf())
                sent += 1
            }
        } catch (t: Throwable) {
            requeueRegistrations(queued.drop(sent))
            throw t
        }
        if (sent > 0) {
            AndroidPushRuntime.observeChannelEvent(
                packageName = null,
                action = "pending_registrations_flushed",
                source = source
            )
        }
        MagiskOtel.event(
            name = "push.register",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "main",
                "stage" to "pending_flush",
                "reason" to source,
                "pending_count" to sent.toString(),
            ),
            statusOk = true,
        )
        return sent
    }

    @JvmStatic
    fun notifyRegisterError(
        errorCode: Int,
        errorMessage: String,
        notifier: PendingPacketErrorNotifier,
        androidUserId: Int = currentUserId(),
    ): Int {
        val userId = Utils.requireValidUserId(androidUserId)
        val queued = synchronized(lock) {
            pendingRegistrationRequests
                .filterKeys { it.startsWith("$userId:") }
                .map { PendingPacketEntry(it.key.removePrefix("$userId:"), it.value.copyOf(), userId) }
                .also { pendingRegistrationRequests.keys.removeIf { it.startsWith("$userId:") } }
        }
        var notified = 0
        try {
            queued.forEach { entry ->
                notifier.notify(entry.packageName, entry.payload.copyOf(), errorCode, errorMessage)
                notified += 1
                AndroidPushRuntime.observeRegistrationResult(
                    packageName = entry.packageName,
                    success = false,
                    source = "PushRuntimePendingPacketStore.notifyRegisterError",
                    reason = errorMessage,
                    androidUserId = userId,
                )
            }
        } catch (t: Throwable) {
            requeueRegistrations(queued.drop(notified))
            throw t
        }
        // An error callback with an empty queue is the routine "nothing pending" case, not a
        // registration failure: report it as skip so real register errors stay visible, and only
        // attach a target package when the error maps unambiguously to a single pending app.
        val hasPendingWork = notified > 0
        val attrs = mutableMapOf(
            "result" to if (hasPendingWork) "error" else "skip",
            "duration_ms" to "0",
            "process" to "main",
            "stage" to "notify_error",
            "reason" to if (hasPendingWork) "register_error" else "register_error_no_pending",
            "pending_count" to notified.toString(),
        )
        if (hasPendingWork && queued.size == 1) {
            attrs["target_package"] = queued.first().packageName
        }
        MagiskOtel.event(name = "push.register", attributes = attrs, statusOk = hasPendingWork)
        return notified
    }

    @JvmStatic
    fun pendingMessageCount(androidUserId: Int = currentUserId()): Int {
        val userId = Utils.requireValidUserId(androidUserId)
        return synchronized(lock) { pendingMessages.count { it.userId == userId } }
    }

    @JvmStatic
    fun pendingRegistrationCount(androidUserId: Int = currentUserId()): Int {
        val userId = Utils.requireValidUserId(androidUserId)
        return synchronized(lock) { pendingRegistrationRequests.keys.count { it.startsWith("$userId:") } }
    }

    @JvmStatic
    fun discardPackage(packageName: String): DiscardedPendingPackets {
        return discardPackage(packageName, currentUserId())
    }

    @JvmStatic
    fun discardPackage(packageName: String, userId: Int): DiscardedPendingPackets {
        val scopedUserId = requireValidUserId(userId)
        return synchronized(lock) {
            val registrationRequests = if (pendingRegistrationRequests.remove(registrationKey(scopedUserId, packageName)) != null) 1 else 0
            val previousMessageCount = pendingMessages.count { it.userId == scopedUserId && it.packageName == packageName }
            pendingMessages = ArrayList(pendingMessages.filterNot { it.userId == scopedUserId && it.packageName == packageName })
            DiscardedPendingPackets(
                registrationRequests = registrationRequests,
                messages = previousMessageCount,
            )
        }
    }

    @JvmStatic
    fun clearForTests() {
        synchronized(lock) {
            pendingRegistrationRequests.clear()
            pendingMessages.clear()
        }
    }

    private fun requeueMessages(entries: List<PendingPacketEntry>) {
        if (entries.isEmpty()) return
        synchronized(lock) {
            val merged = ArrayList<PendingPacketEntry>(entries.size + pendingMessages.size)
            entries.forEach { merged += it.copy(payload = it.payload.copyOf()) }
            merged += pendingMessages
            val userId = entries.first().userId
            while (merged.count { it.userId == userId } > MAX_PENDING_MESSAGES) {
                val oldestForUser = merged.indexOfFirst { it.userId == userId }
                if (oldestForUser < 0) break
                merged.removeAt(oldestForUser)
            }
            pendingMessages = ArrayList(merged)
        }
    }

    private fun requeueRegistrations(entries: List<PendingPacketEntry>) {
        if (entries.isEmpty()) return
        synchronized(lock) {
            entries.forEach { entry ->
                // Stock XMSF 7.4.67-C h0.f holds the registration-map lock while flushing. A
                // newer same-package put therefore runs after the failed flush and wins. Our
                // snapshot-based flush must preserve that ordering when it restores the old tail.
                pendingRegistrationRequests.putIfAbsent(
                    registrationKey(entry.userId, entry.packageName),
                    entry.payload.copyOf(),
                )
            }
        }
    }

    private fun currentUserId(): Int = runCatching { Utils.myUserId() }
        .getOrNull()
        ?.takeIf { it >= 0 }
        ?: error("Unable to resolve current Android user id")

    private fun requireValidUserId(userId: Int): Int {
        require(userId >= 0) { "Invalid Android user id: $userId" }
        return userId
    }

    private fun registrationKey(userId: Int, packageName: String): String = "$userId:$packageName"
}
