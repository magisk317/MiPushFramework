package com.xiaomi.channel.commonutils.stats

import java.util.LinkedList

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/channel/commonutils/stats/Stats.java
 */
class Stats {
    private var statsQueue: LinkedList<Item> = LinkedList()

    class Item {
        @JvmField
        var annotation: String? = null
        @JvmField
        var key: Int = 0
        @JvmField
        var obj: Any? = null

        constructor(key: Int, obj: Any?) {
            this.key = key
            this.obj = obj
        }

        constructor(key: Int, annotation: String?) {
            this.key = key
            this.annotation = annotation
        }

        companion object {
            private val sStats = Stats()

            @JvmStatic
            fun stats(): Stats = sStats
        }
    }

    private fun checkSize() {
        if (statsQueue.size > MAX_STATS_ITEMS) {
            statsQueue.removeFirst()
        }
    }

    @Synchronized
    fun count(key: Int) {
        statsQueue.add(Item(key, null as String?))
        checkSize()
    }

    @Synchronized
    fun count(key: Int, annotation: String?) {
        statsQueue.add(Item(key, annotation))
        checkSize()
    }

    @Synchronized
    fun getCount(): Int = statsQueue.size

    @get:Synchronized
    val stats: LinkedList<Item>
        get() {
            val linkedList = statsQueue
            statsQueue = LinkedList()
            return linkedList
        }

    @Synchronized
    fun stat(obj: Any?) {
        statsQueue.add(Item(0, obj))
        checkSize()
    }

    companion object {
        private const val MAX_STATS_ITEMS = 100

        @JvmStatic
        fun instance(): Stats = Item.stats()
    }
}
