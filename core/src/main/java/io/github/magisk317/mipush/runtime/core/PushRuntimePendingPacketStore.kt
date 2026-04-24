package io.github.magisk317.mipush.runtime.core

data class PendingPacketEntry(
    val packageName: String,
    val payload: ByteArray
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
        synchronized(lock) {
            pendingMessages.add(PendingPacketEntry(packageName, payload.copyOf()))
            if (pendingMessages.size > MAX_PENDING_MESSAGES) {
                pendingMessages.removeAt(0)
            }
        }
    }

    @JvmStatic
    fun cacheRegistrationRequest(packageName: String, payload: ByteArray) {
        synchronized(lock) {
            pendingRegistrationRequests[packageName] = payload.copyOf()
        }
        PushRuntime.observeRegistrationRequest(
            packageName = packageName,
            source = "PushRuntimePendingPacketStore.cacheRegistrationRequest",
            reason = "awaiting_connection"
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
            PushRuntime.observeChannelEvent(
                packageName = null,
                action = "pending_messages_flushed",
                source = source
            )
        }
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
            PushRuntime.observeChannelEvent(
                packageName = null,
                action = "pending_registrations_flushed",
                source = source
            )
        }
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
        queued.forEach { entry ->
            notifier.notify(entry.packageName, entry.payload.copyOf(), errorCode, errorMessage)
            PushRuntime.observeRegistrationResult(
                packageName = entry.packageName,
                success = false,
                source = "PushRuntimePendingPacketStore.notifyRegisterError",
                reason = errorMessage
            )
        }
        return queued.size
    }

    @JvmStatic
    fun pendingMessageCount(): Int = synchronized(lock) { pendingMessages.size }

    @JvmStatic
    fun pendingRegistrationCount(): Int = synchronized(lock) { pendingRegistrationRequests.size }

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
                pendingRegistrationRequests[entry.packageName] = entry.payload.copyOf()
            }
        }
    }
}
