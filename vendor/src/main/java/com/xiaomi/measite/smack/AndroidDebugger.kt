package com.xiaomi.measite.smack

import co.touchlab.kermit.Logger
import com.xiaomi.push.mpcd.Constants
import com.xiaomi.slim.Blob
import com.xiaomi.smack.Connection
import com.xiaomi.smack.ConnectionListener
import com.xiaomi.smack.PacketListener
import com.xiaomi.smack.debugger.SmackDebugger
import com.xiaomi.smack.filter.PacketFilter
import com.xiaomi.smack.packet.Packet
import java.text.SimpleDateFormat
import java.util.Date

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/measite/smack/AndroidDebugger.java
 */
class AndroidDebugger(private val connection: Connection) : SmackDebugger {
    private val dateFormatter = SimpleDateFormat("hh:mm:ss aaa", java.util.Locale.US)
    private var readListener: Listener? = null
    private var writeListener: Listener? = null
    private var connListener: ConnectionListener? = null
    private val tag = "[Slim] "

    inner class Listener(rcv: Boolean) : PacketListener, PacketFilter {
        private val rcvOrSent = if (rcv) " RCV " else " Sent "

        override fun accept(packet: Packet): Boolean = true

        override fun process(blob: Blob) {
            if (printInterpreted) {
                Logger.v { tag + dateFormatter.format(Date()) + rcvOrSent + blob.toString() }
                return
            }
            Logger.v {
                tag + dateFormatter.format(Date()) + rcvOrSent +
                    " Blob [" + blob.cmd + "," + blob.channelId + "," + blob.packetID + "]"
            }
        }

        override fun processPacket(packet: Packet) {
            if (printInterpreted) {
                Logger.v { tag + dateFormatter.format(Date()) + rcvOrSent + " PKT " + packet.toXML() }
                return
            }
            Logger.v {
                tag + dateFormatter.format(Date()) + rcvOrSent +
                    " PKT [" + packet.channelId + "," + packet.packetID + "]"
            }
        }
    }

    init {
        createDebug()
    }

    private fun createDebug() {
        readListener = Listener(true)
        writeListener = Listener(false)
        val reader = readListener
        if (reader != null) {
            connection.addPacketListener(reader, reader)
        }
        val writer = writeListener
        if (writer != null) {
            connection.addPacketSendingListener(writer, writer)
        }
        connListener = object : ConnectionListener {
            override fun connectionClosed(connection: Connection, reason: Int, error: Exception?) {
                Logger.v { tag + dateFormatter.format(Date()) + " Connection closed (" + this@AndroidDebugger.connection.hashCode() + Constants.SEPARATOR_RIGHT_PARENTESIS }
            }

            override fun connectionStarted(connection: Connection) {
                Logger.v { tag + dateFormatter.format(Date()) + " Connection started (" + this@AndroidDebugger.connection.hashCode() + Constants.SEPARATOR_RIGHT_PARENTESIS }
            }

            override fun reconnectionFailed(connection: Connection, error: Exception) {
                Logger.v(error) { tag + dateFormatter.format(Date()) + " Reconnection failed due to an exception (" + this@AndroidDebugger.connection.hashCode() + Constants.SEPARATOR_RIGHT_PARENTESIS }
            }

            override fun reconnectionSuccessful(connection: Connection) {
                Logger.v { tag + dateFormatter.format(Date()) + " Connection reconnected (" + this@AndroidDebugger.connection.hashCode() + Constants.SEPARATOR_RIGHT_PARENTESIS }
            }
        }
    }

    override fun getReaderListener(): PacketListener = readListener!!

    override fun getWriterListener(): PacketListener = writeListener!!

    companion object {
        @JvmField
        var printInterpreted: Boolean = false
    }
}
