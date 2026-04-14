package com.xiaomi.slim;

import android.os.Build;
import com.xiaomi.channel.commonutils.android.SystemUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.misc.DateTimeHelper;
import com.xiaomi.push.protobuf.ChannelMessage;
import io.github.magisk317.mipush.service.runtime.PushSlimStreamRuntime;
import com.xiaomi.push.service.PushSlimWritePlan;
import com.xiaomi.push.service.RC4Cryption;
import com.xiaomi.push.service.ServiceConfig;
import com.xiaomi.smack.Connection;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.TimeZone;
import java.util.zip.Adler32;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/slim/BlobWriter.class */
public class BlobWriter {
    private static final int MAX_BLOB_THRSHOLD = 4096;
    private static final int MIN_BLOB_SIZE = 2048;
    ByteBuffer mBuffer = ByteBuffer.allocate(MIN_BLOB_SIZE);
    private ByteBuffer mCRCBuf = ByteBuffer.allocate(4);
    private Adler32 mChecksumTool = new Adler32();
    private SlimConnection mConnection;
    private int mDSTSavings;
    private byte[] mKey;
    private OutputStream mOut;
    private int mTimeZone;

    BlobWriter(OutputStream outputStream, SlimConnection slimConnection) {
        this.mOut = new BufferedOutputStream(outputStream);
        this.mConnection = slimConnection;
        TimeZone timeZone = TimeZone.getDefault();
        this.mTimeZone = timeZone.getRawOffset() / DateTimeHelper.HOUR_IN_MS;
        this.mDSTSavings = timeZone.useDaylightTime() ? 1 : 0;
    }

    public void openStream() throws IOException {
        ChannelMessage.XMMsgConn xMMsgConn = new ChannelMessage.XMMsgConn();
        xMMsgConn.setVersion(Connection.ERR_TCP_INVALARG);
        xMMsgConn.setModel(Build.MODEL);
        xMMsgConn.setOs(SystemUtils.getManufacturerOSVersion());
        xMMsgConn.setUdid(ServiceConfig.getDeviceUUID());
        xMMsgConn.setSdk(41);
        xMMsgConn.setConnpt(this.mConnection.getConnectionPoint());
        xMMsgConn.setHost(this.mConnection.getHost());
        xMMsgConn.setLocale(Locale.getDefault().toString());
        xMMsgConn.setAndver(Build.VERSION.SDK_INT);
        byte[] connectionBlob = this.mConnection.getConfiguration().getConnectionBlob();
        if (connectionBlob != null) {
            xMMsgConn.setPsc(ChannelMessage.PushServiceConfigMsg.parseFrom(connectionBlob));
        }
        Blob blob = new Blob();
        blob.setChannelId(0);
        blob.setCmd(Blob.CMD_CONN, null);
        blob.setFrom(0L, "xiaomi.com", null);
        blob.setPayload(xMMsgConn.toByteArray(), null);
        write(blob);
        MyLog.w("[slim] open conn: andver=" + Build.VERSION.SDK_INT + " sdk=41 hash=" + ServiceConfig.getDeviceUUID() + " tz=" + this.mTimeZone + ":" + this.mDSTSavings + " Model=" + Build.MODEL + " os=" + Build.VERSION.INCREMENTAL);
    }

    public void shutdown() throws IOException {
        Blob blob = new Blob();
        blob.setCmd(Blob.CMD_CLOSE, null);
        write(blob);
        this.mOut.close();
    }

    public int write(Blob blob) throws IOException {
        int serializedSize = blob.getSerializedSize();
        PushSlimWritePlan writePlan = PushSlimStreamRuntime.planWrite(serializedSize, blob.getCmd(), this.mBuffer.capacity());
        if (writePlan.getShouldDrop()) {
            MyLog.w("Blob size=" + serializedSize + " should be less than " + Blob.MAX_BLOB_SIZE + " Drop blob chid=" + blob.getChannelId() + " id=" + blob.getPacketID());
            return 0;
        }
        this.mBuffer.clear();
        if (writePlan.getRequiredCapacity() != this.mBuffer.capacity()) {
            this.mBuffer = ByteBuffer.allocate(writePlan.getRequiredCapacity());
        }
        this.mBuffer.putShort((short) -15618);
        this.mBuffer.putShort((short) 5);
        this.mBuffer.putInt(serializedSize);
        int iPosition = this.mBuffer.position();
        this.mBuffer = blob.toByteArray(this.mBuffer);
        if (writePlan.getShouldEncrypt()) {
            if (this.mKey == null) {
                this.mKey = this.mConnection.getKey();
            }
            RC4Cryption.encrypt(this.mKey, this.mBuffer.array(), true, iPosition, serializedSize);
        }
        this.mChecksumTool.reset();
        this.mChecksumTool.update(this.mBuffer.array(), 0, this.mBuffer.position());
        this.mCRCBuf.putInt(0, (int) this.mChecksumTool.getValue());
        this.mOut.write(this.mBuffer.array(), 0, this.mBuffer.position());
        this.mOut.write(this.mCRCBuf.array(), 0, 4);
        this.mOut.flush();
        int iPosition2 = this.mBuffer.position() + 4;
        MyLog.v("[Slim] Wrote {cmd=" + blob.getCmd() + ";chid=" + blob.getChannelId() + ";len=" + iPosition2 + "}");
        return iPosition2;
    }
}
