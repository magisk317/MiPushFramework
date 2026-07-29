package io.github.magisk317.mipush.runtime.android

import io.github.magisk317.xposed.logging.MagiskOtel

data class PendingPacketEntry(
    val packageName: String,
    val payload: ByteArray
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
    fun addPendingMessage(packageName: String, payload: ByteArray) {
        val pendingCount = synchronized(lock) {
            pendingMessages.add(PendingPacketEntry(packageName, payload.copyOf()))
            if (pendingMessages.size > MAX_PENDING_MESSAGES) {
                pendingMessages.removeAt(0)
            }
            pendingMessages.size
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
    fun cacheRegistrationRequest(packageName: String, payload: ByteArray) {
        val pendingCount = synchronized(lock) {
            pendingRegistrationRequests[packageName] = payload.copyOf()
            pendingRegistrationRequests.size
        }
        AndroidPushRuntime.observeRegistrationRequest(
            packageName = packageName,
            source = "PushRuntimePendingPacketStore.cacheRegistrationRequest",
            reason = "awaiting_connection"
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
        sender: PendingPacketSender
    ): Int {
        val queued = synchronized(lock) {
            pendingMessages.also { pendingMessages = ArrayList() }
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
        sender: PendingPacketSender
    ): Int {
        val queued = synchronized(lock) {
            pendingRegistrationRequests.map { PendingPacketEntry(it.key, it.value.copyOf()) }
                .also { pendingRegistrationRequests.clear() }
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
        notifier: PendingPacketErrorNotifier
    ): Int {
        val queued = synchronized(lock) {
            pendingRegistrationRequests.map { PendingPacketEntry(it.key, it.value.copyOf()) }
                .also { pendingRegistrationRequests.clear() }
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
                    reason = errorMessage
                )
            }
        } catch (t: Throwable) {
            requeueRegistrations(queued.drop(notified))
            throw t
        }
        MagiskOtel.event(
            name = "push.register",
            attributes = mapOf(
                "result" to "error",
                "duration_ms" to "0",
                "process" to "main",
                "stage" to "notify_error",
                "reason" to "register_error",
                "pending_count" to notified.toString(),
            ),
            statusOk = false,
        )
        return notified
    }

    @JvmStatic
    fun pendingMessageCount(): Int = synchronized(lock) { pendingMessages.size }

    @JvmStatic
    fun pendingRegistrationCount(): Int = synchronized(lock) { pendingRegistrationRequests.size }

    @JvmStatic
    fun discardPackage(packageName: String): DiscardedPendingPackets = synchronized(lock) {
        val registrationRequests = if (pendingRegistrationRequests.remove(packageName) != null) 1 else 0
        val previousMessageCount = pendingMessages.size
        pendingMessages = ArrayList(pendingMessages.filterNot { it.packageName == packageName })
        DiscardedPendingPackets(
            registrationRequests = registrationRequests,
            messages = previousMessageCount - pendingMessages.size,
        )
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
            pendingMessages = ArrayList(merged.takeLast(MAX_PENDING_MESSAGES))
        }
    }

    private fun requeueRegistrations(entries: List<PendingPacketEntry>) {
        if (entries.isEmpty()) return
        synchronized(lock) {
            entries.forEach { entry ->
                // Stock XMSF 7.4.67-C h0.f holds the registration-map lock while flushing. A
                // newer same-package put therefore runs after the failed flush and wins. Our
                // snapshot-based flush must preserve that ordering when it restores the old tail.
                pendingRegistrationRequests.putIfAbsent(entry.packageName, entry.payload.copyOf())
            }
        }
    }
}
