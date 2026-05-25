package io.github.magisk317.mipush.hook.island

internal object IslandDispatchState {
    @Volatile
    var registered: Boolean = false

    private val postedIds = LinkedHashSet<Int>()

    fun markPosted(notificationId: Int) {
        synchronized(postedIds) {
            postedIds.add(notificationId)
        }
    }

    fun markCancelled(notificationId: Int) {
        synchronized(postedIds) {
            postedIds.remove(notificationId)
        }
    }

    fun postedIds(): Set<Int> = synchronized(postedIds) { postedIds.toSet() }

    fun resetForTest() {
        registered = false
        synchronized(postedIds) {
            postedIds.clear()
        }
    }
}
