package com.xiaomi.smack.util

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteException
import co.touchlab.kermit.Logger
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.channel.commonutils.misc.SerializedAsyncTaskProcessor
import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.push.providers.TrafficDatabaseHelper
import java.util.Collections

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/smack/util/TrafficUtils.java
 * This runtime keeps the local Network helper and updateIMSI hook used by TrafficProvider.
 */
object TrafficUtils {
    private const val IDLE_INTERVAL = 30000L
    private const val TRAFFIC_INTERVAL = 5000L
    private const val WRITE2DB_INTERVAL = 5000L

    private val mAsyncProcessor = SerializedAsyncTaskProcessor(true)
    @Volatile
    private var networkType = -1
    private var lastRxTs = System.currentTimeMillis()
    private val lock = Any()
    private val trafficList: MutableList<TrafficInfo> = Collections.synchronizedList(ArrayList())
    @Volatile
    private var trafficCollectionEnabled = true
    private var imsi = ""
    private var dbHelper: TrafficDatabaseHelper? = null

    class TrafficInfo(
        packageName: String,
        messageTs: Long,
        networkType: Int,
        rcv: Int,
        imsi: String,
        bytes: Long
    ) {
        @JvmField
        var bytes: Long = bytes
        @JvmField
        var imsi: String = imsi
        @JvmField
        var messageTs: Long = messageTs
        @JvmField
        var networkType: Int = networkType
        @JvmField
        var packageName: String = packageName
        @JvmField
        var rcv: Int = rcv

        fun canAccumulate(trafficInfo: TrafficInfo): Boolean {
            return trafficInfo.packageName == packageName &&
                trafficInfo.imsi == imsi &&
                trafficInfo.networkType == networkType &&
                trafficInfo.rcv == rcv &&
                kotlin.math.abs(trafficInfo.messageTs - messageTs) <= TRAFFIC_INTERVAL
        }
    }

    private fun checkTrafficCollection(context: Context) {
        trafficCollectionEnabled = com.xiaomi.channel.commonutils.android.AppInfoUtils.isAppRunning(
            context,
            "com.xiaomi.xmsf"
        )
    }

    private fun clear() {
        trafficList.clear()
    }

    @JvmStatic
    fun distributionTraffic(
        context: Context,
        packageName: String?,
        bytes: Long,
        rcv: Boolean,
        filterRepeat: Boolean,
        timestamp: Long
    ) {
        if (context.packageName != "com.xiaomi.xmsf") return
        if (filterRepeat && rcv && (timestamp - lastRxTs > IDLE_INTERVAL)) {
            lastRxTs = timestamp
            return
        }
        saveTraffic(context, packageName, bytes, rcv, timestamp)
    }

    @JvmStatic
    fun configureTrafficCollection(context: Context?, enabled: Boolean) {
        trafficCollectionEnabled = enabled
        if (enabled) {
            return
        }
        synchronized(lock) {
            trafficList.clear()
        }
        synchronized(TrafficUtils::class.java) {
            imsi = ""
        }
        synchronized(TrafficDatabaseHelper.DataBaseLock) {
            runCatching { dbHelper?.close() }.onFailure { Logger.e(it) { "Failed to close dbHelper" } }
            dbHelper = null
            if (context != null) {
                runCatching { context.deleteDatabase(TrafficDatabaseHelper.DATABASE_NAME) }
                    .onFailure { Logger.e(it) { "Failed to delete database" } }
            }
        }
    }

    @JvmStatic
    fun isTrafficCollectionEnabled(): Boolean = trafficCollectionEnabled

    @JvmStatic
    fun getActiveNetworkType(context: Context?): Int {
        if (context == null) return -1
        return when {
            Network.isWIFIConnected(context) -> 1
            Network.is4GConnected(context) -> 4
            Network.is3GConnected(context) -> 3
            Network.is2GConnected(context) -> 2
            Network.hasNetwork(context) -> 0
            else -> -1
        }
    }

    private fun getIMSI(context: Context): String {
        if (imsi.isNotEmpty()) {
            return imsi
        }
        val subImsi = com.xiaomi.channel.commonutils.android.DeviceInfo.blockingGetSubIMEIS(context)
        if (!subImsi.isNullOrEmpty()) {
            imsi = subImsi
            return subImsi
        }
        return ""
    }

    @JvmStatic
    fun getNetworkType(context: Context): Int {
        if (networkType == -1) {
            networkType = getActiveNetworkType(context)
        }
        return networkType
    }

    private fun getTrafficDatabaseHelper(context: Context): TrafficDatabaseHelper {
        return dbHelper ?: synchronized(TrafficUtils::class.java) {
            dbHelper ?: TrafficDatabaseHelper(context).also { dbHelper = it }
        }
    }

    @JvmStatic
    fun getTrafficFlow(str: String): Int = str.toByteArray(Charsets.UTF_8).size

    @JvmStatic
    fun insertTraffic(context: Context, list: List<TrafficInfo>) {
        if (!trafficCollectionEnabled) {
            return
        }
        try {
            synchronized(TrafficDatabaseHelper.DataBaseLock) {
                if (!trafficCollectionEnabled) {
                    return
                }
                val writableDatabase = getTrafficDatabaseHelper(context).writableDatabase
                writableDatabase.beginTransaction()
                try {
                    for (trafficInfo in list) {
                        val contentValues = ContentValues()
                        contentValues.put("package_name", trafficInfo.packageName)
                        contentValues.put("message_ts", trafficInfo.messageTs)
                        contentValues.put("network_type", trafficInfo.networkType)
                        contentValues.put("bytes", trafficInfo.bytes)
                        contentValues.put("rcv", trafficInfo.rcv)
                        contentValues.put("imsi", trafficInfo.imsi)
                        writableDatabase.insert("traffic", null, contentValues)
                    }
                    writableDatabase.setTransactionSuccessful()
                } finally {
                    writableDatabase.endTransaction()
                }
            }
        } catch (e: SQLiteException) {
            Logger.e(e) { "Failed to insert traffic" }
        }
    }

    private fun insertTrafficInfo2List(trafficInfo: TrafficInfo) {
        for (existing in trafficList) {
            if (existing.canAccumulate(trafficInfo)) {
                existing.bytes += trafficInfo.bytes
                return
            }
        }
        trafficList.add(trafficInfo)
    }

    @JvmStatic
    fun notifyNetworkChanage(context: Context?) {
        networkType = getActiveNetworkType(context)
    }

    private fun saveTraffic(context: Context?, packageName: String?, bytes: Long, rcv: Boolean, timestamp: Long) {
        if (
            !trafficCollectionEnabled ||
            context == null ||
            packageName.isNullOrEmpty() ||
            context.packageName != "com.xiaomi.xmsf" ||
            packageName == "com.xiaomi.xmsf"
        ) {
            return
        }
        val currentNetworkType = getNetworkType(context)
        if (currentNetworkType == -1) {
            return
        }
        val wasEmpty: Boolean
        synchronized(lock) {
            wasEmpty = trafficList.isEmpty()
            insertTrafficInfo2List(
                TrafficInfo(
                    packageName,
                    timestamp,
                    currentNetworkType,
                    if (rcv) 1 else 0,
                    if (currentNetworkType == 0) getIMSI(context) else "",
                    bytes
                )
            )
        }
        if (wasEmpty) {
            mAsyncProcessor.addNewTaskWithDelayed(
                object : SerializedAsyncTaskProcessor.SerializedAsyncTask() {
                    override fun process() {
                        val pending = ArrayList<TrafficInfo>()
                        synchronized(lock) {
                            if (trafficList.isNotEmpty()) {
                                pending.addAll(trafficList)
                                trafficList.clear()
                            }
                        }
                        if (pending.isNotEmpty()) {
                            insertTraffic(context, pending)
                        }
                    }
                },
                WRITE2DB_INTERVAL
            )
        }
    }

    @JvmStatic
    fun updateIMSI(str: String?) {
        if (!trafficCollectionEnabled) {
            return
        }
        synchronized(TrafficUtils::class.java) {
            if (!MIUIUtils.isGlobalRegion() && !str.isNullOrEmpty()) {
                imsi = str
            }
        }
    }
}
