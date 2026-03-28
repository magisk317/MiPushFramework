package com.xiaomi.measite.smack;

import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.push.mpcd.Constants;
import com.xiaomi.slim.Blob;
import com.xiaomi.smack.Connection;
import com.xiaomi.smack.ConnectionListener;
import com.xiaomi.smack.PacketListener;
import com.xiaomi.smack.debugger.SmackDebugger;
import com.xiaomi.smack.filter.PacketFilter;
import com.xiaomi.smack.packet.Packet;
import java.text.SimpleDateFormat;
import java.util.Date;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/measite/smack/AndroidDebugger.class */
public class AndroidDebugger implements SmackDebugger {
    public static boolean printInterpreted = false;
    private Connection connection;
    private SimpleDateFormat dateFormatter = new SimpleDateFormat("hh:mm:ss aaa");
    private Listener readListener = null;
    private Listener writeListener = null;
    private ConnectionListener connListener = null;
    private final String TAG = "[Slim] ";

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/measite/smack/AndroidDebugger$Listener.class */
    class Listener implements PacketListener, PacketFilter {
        String rcvOrSent;

        Listener(boolean z) {
            this.rcvOrSent = z ? " RCV " : " Sent ";
        }

        @Override // com.xiaomi.smack.filter.PacketFilter
        public boolean accept(Packet packet) {
            return true;
        }

        @Override // com.xiaomi.smack.PacketListener
        public void process(Blob blob) {
            if (AndroidDebugger.printInterpreted) {
                MyLog.v("[Slim] " + AndroidDebugger.this.dateFormatter.format(new Date()) + this.rcvOrSent + blob.toString());
                return;
            }
            MyLog.v("[Slim] " + AndroidDebugger.this.dateFormatter.format(new Date()) + this.rcvOrSent + " Blob [" + blob.getCmd() + "," + blob.getChannelId() + "," + blob.getPacketID() + "]");
        }

        @Override // com.xiaomi.smack.PacketListener
        public void processPacket(Packet packet) {
            if (AndroidDebugger.printInterpreted) {
                MyLog.v("[Slim] " + AndroidDebugger.this.dateFormatter.format(new Date()) + this.rcvOrSent + " PKT " + packet.toXML());
                return;
            }
            MyLog.v("[Slim] " + AndroidDebugger.this.dateFormatter.format(new Date()) + this.rcvOrSent + " PKT [" + packet.getChannelId() + "," + packet.getPacketID() + "]");
        }
    }

    public AndroidDebugger(Connection connection) {
        this.connection = null;
        this.connection = connection;
        createDebug();
    }

    private void createDebug() {
        this.readListener = new Listener(true);
        this.writeListener = new Listener(false);
        Connection connection = this.connection;
        Listener listener = this.readListener;
        connection.addPacketListener(listener, listener);
        Connection connection2 = this.connection;
        Listener listener2 = this.writeListener;
        connection2.addPacketSendingListener(listener2, listener2);
        this.connListener = new ConnectionListener() { // from class: com.xiaomi.measite.smack.AndroidDebugger.1
            @Override // com.xiaomi.smack.ConnectionListener
            public void connectionClosed(Connection connection3, int i, Exception exc) {
                MyLog.v("[Slim] " + AndroidDebugger.this.dateFormatter.format(new Date()) + " Connection closed (" + AndroidDebugger.this.connection.hashCode() + Constants.SEPARATOR_RIGHT_PARENTESIS);
            }

            @Override // com.xiaomi.smack.ConnectionListener
            public void connectionStarted(Connection connection3) {
                MyLog.v("[Slim] " + AndroidDebugger.this.dateFormatter.format(new Date()) + " Connection started (" + AndroidDebugger.this.connection.hashCode() + Constants.SEPARATOR_RIGHT_PARENTESIS);
            }

            @Override // com.xiaomi.smack.ConnectionListener
            public void reconnectionFailed(Connection connection3, Exception exc) {
                MyLog.v("[Slim] " + AndroidDebugger.this.dateFormatter.format(new Date()) + " Reconnection failed due to an exception (" + AndroidDebugger.this.connection.hashCode() + Constants.SEPARATOR_RIGHT_PARENTESIS);
                exc.printStackTrace();
            }

            @Override // com.xiaomi.smack.ConnectionListener
            public void reconnectionSuccessful(Connection connection3) {
                MyLog.v("[Slim] " + AndroidDebugger.this.dateFormatter.format(new Date()) + " Connection reconnected (" + AndroidDebugger.this.connection.hashCode() + Constants.SEPARATOR_RIGHT_PARENTESIS);
            }
        };
    }

    @Override // com.xiaomi.smack.debugger.SmackDebugger
    public PacketListener getReaderListener() {
        return this.readListener;
    }

    @Override // com.xiaomi.smack.debugger.SmackDebugger
    public PacketListener getWriterListener() {
        return this.writeListener;
    }
}
