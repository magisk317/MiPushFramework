package com.xiaomi.slim;

import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.mipush.sdk.Constants;
import com.xiaomi.push.protobuf.ChannelMessage;
import com.xiaomi.push.service.RC4Cryption;
import com.xiaomi.smack.packet.Packet;
import com.xiaomi.smack.util.StringUtils;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/slim/Blob.class */
public class Blob {
    static final int CHCKSUM_SIZE = 4;
    static final int CIPHER_AES = 2;
    static final int CIPHER_NONE = 0;
    static final int CIPHER_RC4 = 1;
    public static final String CLIENT_PING_ID = "0";
    public static final String CMD_BIND = "BIND";
    public static final String CMD_CLOSE = "CLOSE";
    public static final String CMD_CONN = "CONN";
    public static final String CMD_KICK = "KICK";
    public static final String CMD_NOTIFY = "NOTIFY";
    public static final String CMD_PING = "PING";
    public static final String CMD_SECMSG = "SECMSG";
    public static final String CMD_SYNC = "SYNC";
    public static final String CMD_UNBIND = "UBND";
    public static final String CMD_XMLMSG = "XMLMSG";
    public static final int ERROR_ALLOCATE_SESSION_FAILED = 206;
    public static final int ERROR_BIND_TIMEOUT = 206;
    public static final int ERROR_FE_TRANSPORT_CLOSED = 303;
    public static final int ERROR_FIRST_PACKET_NOT_CONN = 103;
    public static final int ERROR_HOST_UNKNOWN = 100;
    public static final int ERROR_INVALID_CHID = 200;
    public static final int ERROR_INVALID_FROM = 201;
    public static final int ERROR_INVALID_XML = 205;
    public static final int ERROR_PARSE_CONN = 102;
    public static final int ERROR_PARSE_REQUEST = 204;
    public static final int ERROR_SEND_TO_GATEWAY_FAILED = 302;
    public static final int ERROR_SEND_TO_IMS_FAILED = 300;
    public static final int ERROR_SEND_TO_XMQ_FAILED = 301;
    public static final int ERROR_UNEXPECTED_REQUEST = 202;
    public static final int ERROR_UNSUPPORTED_VERSION = 101;
    public static final int ERROR_USR_NOT_BIND = 203;
    static final int HEADER_SIZE = 8;
    private static final byte HEAD_LEN_OFFSET = 2;
    public static final String ID_NOT_AVAILABLE = "ID_NOT_AVAILABLE";
    static final byte LENGTH_OFFSET = 4;
    public static final short MAGIC = -15618;
    static final byte MAGIC_OFFSET = 0;
    public static final int MAX_BLOB_SIZE = 32768;
    static final short PAYLOAD_BINARY = 2;
    private static final byte PAYLOAD_LEN_OFFSET = 4;
    public static final short PAYLOAD_PROTO = 2;
    public static final short PAYLOAD_THRIFT = 1;
    private static final byte PAYLOAD_TYPE_OFFSET = 0;
    public static final short PAYLOAD_XML = 3;
    public static final String SERVER_PING_ID = "1";
    public static final String SUBCMD_CONF = "CONF";
    private static final byte SUB_HEADER_OFFSET = 8;
    public static final short VERSION = 5;
    public static final short VERSION5 = 4;
    static final byte VERSION_OFFSET = 2;
    public static final String XIAOMI_SERVER = "xiaomi.com";
    private ChannelMessage.ClientHeader mHeader;
    String mPackageName;
    private byte[] mPayload;
    private short mPayloadType;
    private static String prefix = StringUtils.randomString(5) + Constants.ACCEPT_TIME_SEPARATOR_SERVER;
    private static long id = 0;
    private static final byte[] EMPTY = new byte[0];

    public Blob() {
        this.mPayloadType = (short) 2;
        this.mPayload = EMPTY;
        this.mPackageName = null;
        this.mHeader = new ChannelMessage.ClientHeader();
    }

    Blob(ChannelMessage.ClientHeader clientHeader, short s, byte[] bArr) {
        this.mPayloadType = (short) 2;
        this.mPayload = EMPTY;
        this.mPackageName = null;
        this.mHeader = clientHeader;
        this.mPayloadType = s;
        this.mPayload = bArr;
    }

    public static Blob from(Packet packet, String str) {
        int i;
        Blob blob = new Blob();
        try {
            i = Integer.parseInt(packet.getChannelId());
        } catch (Exception e) {
            MyLog.w("Blob parse chid err " + e.getMessage());
            i = 1;
        }
        blob.setChannelId(i);
        blob.setPacketID(packet.getPacketID());
        blob.setFrom(packet.getFrom());
        blob.setPackageName(packet.getPackageName());
        blob.setCmd(CMD_XMLMSG, null);
        try {
            blob.setPayload(packet.toXML().getBytes("utf8"), str);
            if (TextUtils.isEmpty(str)) {
                blob.setPayloadType((short) 3);
            } else {
                blob.setPayloadType((short) 2);
                blob.setCmd(CMD_SECMSG, null);
            }
        } catch (UnsupportedEncodingException e2) {
            MyLog.w("Blob setPayload err： " + e2.getMessage());
        }
        return blob;
    }

    static Blob from(ByteBuffer byteBuffer) throws IOException {
        try {
            ByteBuffer byteBufferSlice = byteBuffer.slice();
            short s = byteBufferSlice.getShort(0);
            short s2 = byteBufferSlice.getShort(2);
            int i = byteBufferSlice.getInt(4);
            ChannelMessage.ClientHeader clientHeader = new ChannelMessage.ClientHeader();
            clientHeader.mergeFrom(byteBufferSlice.array(), byteBufferSlice.arrayOffset() + 8, s2);
            byte[] bArr = new byte[i];
            byteBufferSlice.position(s2 + 8);
            byteBufferSlice.get(bArr, 0, i);
            return new Blob(clientHeader, s, bArr);
        } catch (Exception e) {
            MyLog.w("read Blob err :" + e.getMessage());
            throw new IOException("Malformed Input");
        }
    }

    public static String nextID() {
        String string;
        synchronized (Blob.class) {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append(prefix);
                long j = id;
                id = 1 + j;
                sb.append(Long.toString(j));
                string = sb.toString();
            } catch (Throwable th) {
                throw th;
            }
        }
        return string;
    }

    public int getChannelId() {
        return this.mHeader.getChid();
    }

    public String getCmd() {
        return this.mHeader.getCmd();
    }

    public byte[] getDecryptedPayload(String str) {
        if (this.mHeader.getCipher() == 1) {
            return RC4Cryption.encrypt(RC4Cryption.generateKeyForRC4(str, getPacketID()), this.mPayload);
        }
        if (this.mHeader.getCipher() == 0) {
            return this.mPayload;
        }
        MyLog.w("unknow cipher = " + this.mHeader.getCipher());
        return this.mPayload;
    }

    public int getErrCode() {
        return this.mHeader.getErrCode();
    }

    public String getErrStr() {
        return this.mHeader.getErrStr();
    }

    public String getFullUserName() {
        if (!this.mHeader.hasUuid()) {
            return null;
        }
        return Long.toString(this.mHeader.getUuid()) + "@" + this.mHeader.getServer() + "/" + this.mHeader.getResource();
    }

    public String getPackageName() {
        return this.mPackageName;
    }

    public String getPacketID() {
        String id2 = this.mHeader.getId();
        if ("ID_NOT_AVAILABLE".equals(id2)) {
            return null;
        }
        if (!this.mHeader.hasId()) {
            id2 = nextID();
            this.mHeader.setId(id2);
        }
        return id2;
    }

    public byte[] getPayload() {
        return this.mPayload;
    }

    public short getPayloadType() {
        return this.mPayloadType;
    }

    public int getSerializedSize() {
        return this.mHeader.getSerializedSize() + 8 + this.mPayload.length;
    }

    public String getSubcmd() {
        return this.mHeader.getSubcmd();
    }

    public boolean hasErr() {
        return this.mHeader.hasErrCode();
    }

    public void setChannelId(int i) {
        this.mHeader.setChid(i);
    }

    public void setCmd(String str, String str2) {
        if (TextUtils.isEmpty(str)) {
            throw new IllegalArgumentException("command should not be empty");
        }
        this.mHeader.setCmd(str);
        this.mHeader.clearSubcmd();
        if (TextUtils.isEmpty(str2)) {
            return;
        }
        this.mHeader.setSubcmd(str2);
    }

    public void setFrom(long j, String str, String str2) {
        if (j != 0) {
            this.mHeader.setUuid(j);
        }
        if (!TextUtils.isEmpty(str)) {
            this.mHeader.setServer(str);
        }
        if (TextUtils.isEmpty(str2)) {
            return;
        }
        this.mHeader.setResource(str2);
    }

    public void setFrom(String str) {
        if (TextUtils.isEmpty(str)) {
            return;
        }
        int iIndexOf = str.indexOf("@");
        try {
            long j = Long.parseLong(str.substring(0, iIndexOf));
            int iIndexOf2 = str.indexOf("/", iIndexOf);
            String strSubstring = str.substring(iIndexOf + 1, iIndexOf2);
            String strSubstring2 = str.substring(iIndexOf2 + 1);
            this.mHeader.setUuid(j);
            this.mHeader.setServer(strSubstring);
            this.mHeader.setResource(strSubstring2);
        } catch (Exception e) {
            MyLog.w("Blob parse user err " + e.getMessage());
        }
    }

    public void setPackageName(String str) {
        this.mPackageName = str;
    }

    public void setPacketID(String str) {
        this.mHeader.setId(str);
    }

    public void setPayload(byte[] bArr, String str) {
        if (TextUtils.isEmpty(str)) {
            this.mHeader.setCipher(0);
            this.mPayload = bArr;
        } else {
            this.mHeader.setCipher(1);
            this.mPayload = RC4Cryption.encrypt(RC4Cryption.generateKeyForRC4(str, getPacketID()), bArr);
        }
    }

    public void setPayloadType(short s) {
        this.mPayloadType = s;
    }

    ByteBuffer toByteArray(ByteBuffer byteBuffer) {
        ByteBuffer byteBufferAllocate = byteBuffer;
        if (byteBuffer == null) {
            byteBufferAllocate = ByteBuffer.allocate(getSerializedSize());
        }
        byteBufferAllocate.putShort(this.mPayloadType);
        byteBufferAllocate.putShort((short) this.mHeader.getCachedSize());
        byteBufferAllocate.putInt(this.mPayload.length);
        int iPosition = byteBufferAllocate.position();
        this.mHeader.toByteArray(byteBufferAllocate.array(), byteBufferAllocate.arrayOffset() + iPosition, this.mHeader.getCachedSize());
        byteBufferAllocate.position(this.mHeader.getCachedSize() + iPosition);
        byteBufferAllocate.put(this.mPayload);
        return byteBufferAllocate;
    }

    public String toString() {
        return "Blob [chid=" + getChannelId() + "; Id=" + getPacketID() + "; cmd=" + getCmd() + "; type=" + ((int) getPayloadType()) + "; from=" + getFullUserName() + " ]";
    }
}
