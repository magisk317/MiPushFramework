package com.xiaomi.push.service

import android.content.Context
import android.content.SharedPreferences
import com.xiaomi.channel.commonutils.android.SharedPrefsCompat
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.misc.DebugUtils
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager
import com.xiaomi.channel.commonutils.network.Network
import java.util.concurrent.ConcurrentHashMap

class Sync private constructor(context: Context) : NetworkListener {
    abstract class SyncJob(
        val group: String,
        val period: Long,
    ) : Runnable {
        override fun run() {
            val sync = instance ?: return
            val context = sync.appContext
            if (!Network.isConnected(context)) {
                return
            }
            val lastTimestamp = sync.preferences.getLong(PREF_TIMESTAMP + group, 0L)
            if (System.currentTimeMillis() - lastTimestamp > period || DebugUtils.isTesting(context)) {
                SharedPrefsCompat.apply(
                    sync.preferences.edit().putLong(PREF_TIMESTAMP + group, System.currentTimeMillis()),
                )
                sync(sync)
            }
        }

        abstract fun sync(sync: Sync)
    }

    companion object {
        private const val MINIMUM_SYNC_DURATION = 3600000L
        private const val PREF_NAME = "sync"
        private const val PREF_TIMESTAMP = ":ts-"

        @Volatile
        private var instance: Sync? = null

        @JvmStatic
        fun getInstance(context: Context): Sync {
            return instance ?: synchronized(this) {
                instance ?: Sync(context).also { instance = it }
            }
        }
    }

    private val appContext: Context = context.applicationContext
    private val preferences: SharedPreferences = appContext.getSharedPreferences(PREF_NAME, 0)
    private var lastSyncTime = 0L
    @Volatile private var isSyncing = false
    private val currentJobs = ConcurrentHashMap<String, SyncJob>()

    fun getString(group: String, key: String): String {
        return preferences.getString("$group:$key", "") ?: ""
    }

    override fun onNetwrokAvaible() {
        if (isSyncing) {
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastSyncTime < MINIMUM_SYNC_DURATION) {
            return
        }
        lastSyncTime = now
        isSyncing = true
        ScheduledJobManager.getInstance(appContext).addOneShootJob(
            {
                try {
                    currentJobs.values.forEach { it.run() }
                } catch (e: Exception) {
                    MyLog.w("Sync job exception :${e.message}")
                }
                isSyncing = false
            },
            (Math.random() * 10.0).toInt(),
        )
    }

    fun put(group: String, key: String, value: String) {
        SharedPrefsCompat.apply(preferences.edit().putString("$group:$key", value))
    }

    fun schedSync(syncJob: SyncJob) {
        if (currentJobs.putIfAbsent(syncJob.group, syncJob) == null) {
            ScheduledJobManager.getInstance(appContext)
                .addOneShootJob(syncJob, (Math.random() * 30.0).toInt() + 10)
        }
    }
}
