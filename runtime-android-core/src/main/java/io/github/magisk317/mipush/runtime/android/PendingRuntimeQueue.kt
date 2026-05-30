package io.github.magisk317.mipush.runtime.android

internal class PendingRuntimeQueue<T>(private val capacity: Int) {
    private val items = ArrayDeque<T>()

    fun offer(item: T) {
        if (capacity <= 0) return
        if (items.size >= capacity) {
            items.removeFirst()
        }
        items.addLast(item)
    }

    fun drain(): List<T> {
        if (items.isEmpty()) return emptyList()
        return ArrayList<T>(items).also { items.clear() }
    }

    fun clear() {
        items.clear()
    }

    fun size(): Int = items.size
}
