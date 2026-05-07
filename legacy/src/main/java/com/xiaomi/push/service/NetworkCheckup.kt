package com.xiaomi.push.service

import android.text.TextUtils
import com.xiaomi.channel.commonutils.file.IOUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.network.Host
import com.xiaomi.stats.StatsHelper
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.Socket
import java.net.UnknownHostException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

object NetworkCheckup {
    private const val CONNECTIVITY_CON_PORT = 5222
    private const val CONNECTIVITY_CON_TIMEOUT = 5000
    private const val GET_GATEWAY = "ip route"
    private const val PING_TEMPLATE = "ping -W 500 -i 0.2 -c 3 %s"

    private var lastCheckTime = 0L
    private val executor = ThreadPoolExecutor(
        1,
        1,
        20,
        TimeUnit.SECONDS,
        LinkedBlockingQueue(),
    )

    @JvmStatic
    fun connectivityTest(observer: IPushRuntimeObserver) {
        val now = System.currentTimeMillis()
        val config = ServiceConfig.getInstance().getConfig()
        if (config != null && observer.shouldRunConnectivityTest(
                executor.activeCount,
                lastCheckTime,
                config.testHostsCount,
            )
        ) {
            lastCheckTime = now
            connectivityTest(config.testHostsList, true)
        }
    }

    @JvmStatic
    fun connectivityTest(hosts: List<String>, checkAllHosts: Boolean) {
        executor.execute {
            var connected = doConnectTest("www.baidu.com:80")
            for (host in hosts) {
                connected = connected || doConnectTest(host)
                if (connected && !checkAllHosts) {
                    break
                }
            }
            StatsHelper.count(if (connected) 1 else 2)
        }
    }

    @JvmStatic
    fun doCheckup() {
        if (executor.activeCount > 0) {
            return
        }
        executor.execute {
            val gateway = getGateway()
            if (TextUtils.isEmpty(gateway)) {
                MyLog.w("Network Checkup: cannot get gateway")
            } else {
                MyLog.w("Network Checkup: get gateway:$gateway")
                doPing(gateway!!)
            }
            try {
                val address = InetAddress.getByName("www.baidu.com")
                MyLog.w("Network Checkup: get address for www.baidu.com:${address.address}")
                address.hostAddress?.let(::doPing)
            } catch (_: UnknownHostException) {
                MyLog.w("Network Checkup: cannot resolve the host www.baidu.com")
            } catch (t: Throwable) {
                MyLog.w("the checkup failure.$t")
            }
        }
    }

    @JvmStatic
    fun dumpNativeNetInfo() {
        readFile("/proc/self/net/tcp")?.takeIf { it.isNotEmpty() }?.let {
            MyLog.w("dump tcp for uid = ${android.os.Process.myUid()}")
            MyLog.w(it)
        }
        readFile("/proc/self/net/tcp6")?.takeIf { it.isNotEmpty() }?.let {
            MyLog.w("dump tcp6 for uid = ${android.os.Process.myUid()}")
            MyLog.w(it)
        }
    }

    private fun doConnectTest(address: String): Boolean {
        val start = System.currentTimeMillis()
        return try {
            MyLog.w("ConnectivityTest: begin to connect to $address")
            Socket().use { socket ->
                socket.connect(Host.from(address, CONNECTIVITY_CON_PORT), CONNECTIVITY_CON_TIMEOUT)
                socket.tcpNoDelay = true
            }
            MyLog.w("ConnectivityTest: connect to $address in ${System.currentTimeMillis() - start}")
            true
        } catch (t: Throwable) {
            MyLog.e("ConnectivityTest: could not connect to:$address exception: ${t.javaClass.simpleName} description: ${t.message}")
            false
        }
    }

    private fun doPing(host: String) {
        MyLog.w("Network Checkup: Begin to ping $host")
        var process: Process? = null
        var reader: BufferedReader? = null
        try {
            process = Runtime.getRuntime().exec(String.format(PING_TEMPLATE, host))
            reader = BufferedReader(InputStreamReader(process.inputStream))
            var line = reader.readLine()
            while (line != null) {
                MyLog.w("Network Checkup:$line")
                line = reader.readLine()
            }
            process.waitFor()
        } catch (e: IOException) {
            MyLog.e(e)
        } catch (e: Exception) {
            MyLog.e(e)
        } finally {
            IOUtils.closeQuietly(reader)
            process?.destroy()
        }
    }

    private fun getGateway(): String? {
        var process: Process? = null
        var reader: BufferedReader? = null
        return try {
            process = Runtime.getRuntime().exec(GET_GATEWAY)
            reader = BufferedReader(InputStreamReader(process.inputStream))
            val gateway = extractGateway(reader.readLine())
            process.waitFor()
            gateway
        } catch (e: IOException) {
            MyLog.e(e)
            null
        } catch (e: Exception) {
            MyLog.e(e)
            null
        } finally {
            IOUtils.closeQuietly(reader)
            process?.destroy()
        }
    }

    private fun extractGateway(line: String?): String? {
        if (line.isNullOrBlank()) return null
        val parts = line.split(" ")
        val index = parts.indexOf("default")
        if (index != -1 && index + 2 < parts.size && parts[index + 1] == "via") {
            return parts[index + 2]
        }
        return null
    }

    private fun readFile(path: String): String? {
        return try {
            BufferedReader(FileReader(File(path))).use { reader ->
                buildString {
                    var line = reader.readLine()
                    while (line != null) {
                        append("\n")
                        append(line)
                        line = reader.readLine()
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
