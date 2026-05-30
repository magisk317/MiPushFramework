package com.xiaomi.smack.util

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteException
import android.text.TextUtils
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.channel.commonutils.logger.MyLog
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
            return TextUtils.equals(trafficInfo.packageName, packageName) &&
                TextUtils.equals(trafficInfo.imsi, imsi) &&
                trafficInfo.networkType == networkType &&
                trafficInfo.rcv == rcv &&
                kotlin.math.abs(trafficInfo.messageTs - messageTs) <= TRAFFIC_INTERVAL
        }
    }

    @JvmStatic
    fun distributionTraffic(context: Context?, packageName: String?, bytes: Long, rcv: Boolean, encrypted: Boolean, timestamp: Long) {
        saveTraffic(context, packageName, getTraffic(getNetworkType(context), bytes, rcv, timestamp, encrypted), rcv, timestamp)
    }

    private fun getActiveNetworkType(context: Context?): Int {
        return try {
            Network.getActiveNetworkType(context)
        } catch (e: Exception) {
            -1
        }
    }

    private fun getIMSI(context: Context): String {
        synchronized(TrafficUtils::class.java) {
            return if (TextUtils.isEmpty(imsi)) "" else imsi
        }
    }

    @JvmStatic
    fun getNetworkType(context: Context?): Int {
        if (networkType == -1) {
            networkType = getActiveNetworkType(context)
        }
        return networkType
    }

    private fun getTraffic(networkType: Int, bytes: Long, rcv: Boolean, timestamp: Long, encrypted: Boolean): Long {
        if (rcv && encrypted) {
            val lastTimestamp = lastRxTs
            lastRxTs = timestamp
            if (timestamp - lastTimestamp > IDLE_INTERVAL && bytes > 1024) {
                return 2 * bytes
            }
        }
        return (if (networkType == 0) 13L else 11L) * bytes / 10
    }

    private fun getTrafficDatabaseHelper(context: Context): TrafficDatabaseHelper {
        val helper = dbHelper
        if (helper != null) {
            return helper
        }
        return TrafficDatabaseHelper(context).also { dbHelper = it }
    }

    @JvmStatic
    fun getTrafficFlow(str: String): Int = str.toByteArray(Charsets.UTF_8).size

    @JvmStatic
    fun insertTraffic(context: Context, list: List<TrafficInfo>) {
        try {
            synchronized(TrafficDatabaseHelper.DataBaseLock) {
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
            MyLog.e(e)
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
            context == null ||
            TextUtils.isEmpty(packageName) ||
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
                    packageName.orEmpty(),
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
        synchronized(TrafficUtils::class.java) {
            if (!MIUIUtils.isGlobalRegion() && !TextUtils.isEmpty(str)) {
                imsi = str.orEmpty()
            }
        }
    }
}
