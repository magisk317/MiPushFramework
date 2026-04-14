package com.xiaomi.slim;

import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.misc.DebugUtils;
import com.xiaomi.push.protobuf.ChannelMessage;
import com.xiaomi.push.service.PushClientsManager;
import com.xiaomi.push.service.PushSlimHandshakePlan;
import com.xiaomi.push.service.PushSlimPayloadPlan;
import io.github.magisk317.mipush.service.runtime.PushSlimStreamRuntime;
import com.xiaomi.push.service.RC4Cryption;
import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.zip.Adler32;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/slim/BlobReader.class */
class BlobReader {
    private static final int MAX_BLOB_THRSHOLD = 4096;
    private static final int MIN_BLOB_SIZE = 2048;
    private SlimConnection mConnection;
    private volatile boolean mDone;
    private InputStream mInputStream;
    private byte[] mKey;
    private ByteBuffer mBuffer = ByteBuffer.allocate(MIN_BLOB_SIZE);
    private ByteBuffer mCRCBuf = ByteBuffer.allocate(4);
    private Adler32 mChecksumTool = new Adler32();
    private PacketParser mPacketParser = new PacketParser();

    BlobReader(InputStream inputStream, SlimConnection slimConnection) {
        this.mInputStream = new BufferedInputStream(inputStream);
        this.mConnection = slimConnection;
    }

    private void loop() throws IOException {
        this.mDone = false;
        boolean z = false;
        Blob blob = read();
        if (Blob.CMD_CONN.equals(blob.getCmd())) {
            ChannelMessage.XMMsgConnResp from = ChannelMessage.XMMsgConnResp.parseFrom(blob.getPayload());
            PushSlimHandshakePlan handshakePlan = PushSlimStreamRuntime.planHandshake(from.hasChallenge(), from.hasPsc());
            z = handshakePlan.getValid();
            if (handshakePlan.getValid()) {
                this.mConnection.setChallenge(from.getChallenge());
            }
            if (handshakePlan.getShouldEmitConfigBlob()) {
                ChannelMessage.PushServiceConfigMsg psc = from.getPsc();
                Blob blob2 = new Blob();
                blob2.setCmd(Blob.CMD_SYNC, Blob.SUBCMD_CONF);
                blob2.setPayload(psc.toByteArray(), null);
                this.mConnection.notifyDataArrived(blob2);
            }
            MyLog.w("[Slim] CONN: host = " + from.getHost());
        }
        if (!z) {
            MyLog.w("[Slim] Invalid CONN");
            throw new IOException("Invalid Connection");
        }
        this.mKey = this.mConnection.getKey();
        while (!this.mDone) {
            Blob blob3 = read();
            this.mConnection.setReadAlive();
            PushSlimPayloadPlan payloadPlan = PushSlimStreamRuntime.planPayloadDispatch(blob3.getPayloadType(), blob3.getCmd(), blob3.getChannelId(), blob3.getSubcmd());
            switch (payloadPlan.getAction()) {
                case DeliverBlob:
                    this.mConnection.notifyDataArrived(blob3);
                    break;
                case ParseSecurePacket:
                    try {
                        this.mConnection.notifyDataArrived(this.mPacketParser.parse(blob3.getDecryptedPayload(PushClientsManager.getInstance().getClientLoginInfoByChidAndUserId(Integer.valueOf(blob3.getChannelId()).toString(), blob3.getFullUserName()).security), this.mConnection));
                    } catch (Exception e) {
                        MyLog.w("[Slim] Parse packet from Blob chid=" + blob3.getChannelId() + "; Id=" + blob3.getPacketID() + " failure:" + e.getMessage());
                    }
                    break;
                case ParsePacket:
                    try {
                        this.mConnection.notifyDataArrived(this.mPacketParser.parse(blob3.getPayload(), this.mConnection));
                    } catch (Exception e2) {
                        MyLog.w("[Slim] Parse packet from Blob chid=" + blob3.getChannelId() + "; Id=" + blob3.getPacketID() + " failure:" + e2.getMessage());
                    }
                    break;
                default:
                    if (payloadPlan.getShouldLogUnknownType()) {
                        MyLog.w("[Slim] unknow blob type " + ((int) blob3.getPayloadType()));
                    }
                    break;
            }
        }
    }

    private void read(ByteBuffer byteBuffer, int i) throws IOException {
        int i2;
        int i3;
        int iPosition = byteBuffer.position();
        do {
            int i4 = this.mInputStream.read(byteBuffer.array(), iPosition, i);
            if (i4 == -1) {
                throw new EOFException();
            }
            i2 = i - i4;
            i3 = iPosition + i4;
            iPosition = i3;
            i = i2;
        } while (i2 > 0);
        byteBuffer.position(i3);
    }

    private ByteBuffer readOnePacket() throws IOException {
        this.mBuffer.clear();
        read(this.mBuffer, 8);
        short s = this.mBuffer.getShort(0);
        short s2 = this.mBuffer.getShort(2);
        if (s != -15618 || s2 != 5) {
            throw new IOException("Malformed Input");
        }
        int i = this.mBuffer.getInt(4);
        int iPosition = this.mBuffer.position();
        if (i > 32768) {
            throw new IOException("Blob size too large");
        }
        if (i + 4 > this.mBuffer.remaining()) {
            ByteBuffer byteBufferAllocate = ByteBuffer.allocate(i + MIN_BLOB_SIZE);
            byteBufferAllocate.put(this.mBuffer.array(), 0, this.mBuffer.arrayOffset() + this.mBuffer.position());
            this.mBuffer = byteBufferAllocate;
        } else if (this.mBuffer.capacity() > 4096 && i < MIN_BLOB_SIZE) {
            ByteBuffer byteBufferAllocate2 = ByteBuffer.allocate(MIN_BLOB_SIZE);
            byteBufferAllocate2.put(this.mBuffer.array(), 0, this.mBuffer.arrayOffset() + this.mBuffer.position());
            this.mBuffer = byteBufferAllocate2;
        }
        read(this.mBuffer, i);
        this.mCRCBuf.clear();
        read(this.mCRCBuf, 4);
        this.mCRCBuf.position(0);
        int i2 = this.mCRCBuf.getInt();
        this.mChecksumTool.reset();
        this.mChecksumTool.update(this.mBuffer.array(), 0, this.mBuffer.position());
        if (i2 == ((int) this.mChecksumTool.getValue())) {
            byte[] bArr = this.mKey;
            if (bArr != null) {
                RC4Cryption.encrypt(bArr, this.mBuffer.array(), true, iPosition, i);
            }
            return this.mBuffer;
        }
        MyLog.w("CRC = " + ((int) this.mChecksumTool.getValue()) + " and " + i2);
        throw new IOException("Corrupted Blob bad CRC");
    }

    Blob read() throws IOException {
        int i = 0;
        try {
            ByteBuffer byteBufferReadOnePacket = readOnePacket();
            i = byteBufferReadOnePacket.position();
            byteBufferReadOnePacket.flip();
            byteBufferReadOnePacket.position(8);
            Blob ping = i == 8 ? new Ping() : Blob.from(byteBufferReadOnePacket.slice());
            MyLog.v("[Slim] Read {cmd=" + ping.getCmd() + ";chid=" + ping.getChannelId() + ";len=" + i + "}");
            return ping;
        } catch (IOException e) {
            if (i == 0) {
                i = this.mBuffer.position();
            }
            int i2 = i;
            if (i > 128) {
                i2 = 128;
            }
            MyLog.w("[Slim] read Blob [" + DebugUtils.bytes2Hex(this.mBuffer.array(), 0, i2) + "] Err:" + e.getMessage());
            throw e;
        }
    }

    void shutdown() {
        this.mDone = true;
    }

    void start() throws IOException {
        try {
            loop();
        } catch (IOException e) {
            if (!this.mDone) {
                throw e;
            }
        }
    }
}
