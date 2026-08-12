package io.github.magisk317.mipush.hook.island

internal object IslandDispatchState {
    @Volatile
    var registered: Boolean = false

    private val postedKeys = LinkedHashSet<DispatchKey>()

    fun markPosted(notificationId: Int, userId: Int) {
        synchronized(postedKeys) {
            postedKeys.add(DispatchKey(userId, notificationId))
        }
    }

    fun markCancelled(notificationId: Int, userId: Int) {
        synchronized(postedKeys) {
            postedKeys.remove(DispatchKey(userId, notificationId))
        }
    }

    fun postedKeys(): Set<DispatchKey> = synchronized(postedKeys) { postedKeys.toSet() }

    /** Compatibility view for existing diagnostics that only display notification IDs. */
    fun postedIds(): Set<Int> = synchronized(postedKeys) { postedKeys.mapTo(linkedSetOf()) { it.notificationId } }

    fun resetForTest() {
        registered = false
        synchronized(postedKeys) {
            postedKeys.clear()
        }
    }
}

internal data class DispatchKey(
    val userId: Int,
    val notificationId: Int,
) {
    init {
        require(userId >= 0) { "userId must be non-negative" }
    }
}
