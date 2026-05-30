package com.xiaomi.channel.commonutils.misc

object CollectionUtils {
    @JvmStatic
    fun <T> appendFromPosition(list: List<T>?, list2: List<T>?, position: Int): List<T> {
        if (list == null || list.isEmpty()) {
            val result = ArrayList<T>()
            list2?.let { result.addAll(it) }
            return result
        }
        if (list2 == null || list2.isEmpty()) return list
        val pos = if (position > list.size) list.size else position
        val result = ArrayList(list)
        result.addAll(pos, list2)
        return result
    }

    @JvmStatic
    fun <T> copyFrom(list: List<T>?): ArrayList<T>? {
        if (list == null) return null
        return ArrayList(list)
    }

    @JvmStatic
    fun <T> isEmpty(collection: Collection<T>?): Boolean {
        return collection == null || collection.isEmpty()
    }

    @JvmStatic
    fun <T> notNull(list: List<T>?): List<T> {
        return list ?: emptyList()
    }

    @JvmStatic
    fun <T> replaceFromPosition(list: List<T>?, list2: List<T>?, position: Int): List<T> {
        if (list == null || list.isEmpty()) return list2 ?: emptyList()
        if (list2 == null || list2.isEmpty()) return list
        val pos = if (position > list.size) list.size else position
        val result = ArrayList(list)
        result.addAll(pos, list2)
        while (result.size > list2.size + pos) {
            result.removeAt(result.size - 1)
        }
        return result
    }
}
