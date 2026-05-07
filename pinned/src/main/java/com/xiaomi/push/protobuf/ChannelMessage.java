package com.xiaomi.push.protobuf;

import com.google.protobuf.micro.ByteStringMicro;
import com.google.protobuf.micro.CodedInputStreamMicro;
import com.google.protobuf.micro.CodedOutputStreamMicro;
import com.google.protobuf.micro.InvalidProtocolBufferMicroException;
import com.google.protobuf.micro.MessageMicro;
import java.io.IOException;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/protobuf/ChannelMessage.class */
public final class ChannelMessage {
    private static final int HTTP_DEFAULT_PORT = 80;

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/protobuf/ChannelMessage$ClientHeader.class */
    public static final class ClientHeader extends MessageMicro {
        public static final int CHID_FIELD_NUMBER = 1;
        public static final int CIPHER_FIELD_NUMBER = 9;
        public static final int CMD_FIELD_NUMBER = 5;
        public static final int CS_ONEWAY = 1;
        public static final int CS_REQ = 2;
        public static final int CS_RESP = 3;
        public static final int DIR_FLAG_FIELD_NUMBER = 8;
        public static final int ERR_CODE_FIELD_NUMBER = 10;
        public static final int ERR_STR_FIELD_NUMBER = 11;
        public static final int ID_FIELD_NUMBER = 7;
        public static final int RESOURCE_FIELD_NUMBER = 4;
        public static final int SC_ONEWAY = 4;
        public static final int SC_REQ = 5;
        public static final int SC_RESP = 6;
        public static final int SERVER_FIELD_NUMBER = 3;
        public static final int SUBCMD_FIELD_NUMBER = 6;
        public static final int UUID_FIELD_NUMBER = 2;
        private boolean hasChid;
        private boolean hasCipher;
        private boolean hasCmd;
        private boolean hasDirFlag;
        private boolean hasErrCode;
        private boolean hasErrStr;
        private boolean hasId;
        private boolean hasResource;
        private boolean hasServer;
        private boolean hasSubcmd;
        private boolean hasUuid;
        private int chid_ = 0;
        private long uuid_ = 0;
        private String server_ = "";
        private String resource_ = "";
        private String cmd_ = "";
        private String subcmd_ = "";
        private String id_ = "";
        private int dirFlag_ = 1;
        private int cipher_ = 0;
        private int errCode_ = 0;
        private String errStr_ = "";
        private int cachedSize = -1;

        public static ClientHeader parseFrom(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
            return new ClientHeader().mergeFrom(codedInputStreamMicro);
        }

        public static ClientHeader parseFrom(byte[] bArr) throws InvalidProtocolBufferMicroException {
            return (ClientHeader) new ClientHeader().mergeFrom(bArr);
        }

        public final ClientHeader clear() {
            clearChid();
            clearUuid();
            clearServer();
            clearResource();
            clearCmd();
            clearSubcmd();
            clearId();
            clearDirFlag();
            clearCipher();
            clearErrCode();
            clearErrStr();
            this.cachedSize = -1;
            return this;
        }

        public ClientHeader clearChid() {
            this.hasChid = false;
            this.chid_ = 0;
            return this;
        }

        public ClientHeader clearCipher() {
            this.hasCipher = false;
            this.cipher_ = 0;
            return this;
        }

        public ClientHeader clearCmd() {
            this.hasCmd = false;
            this.cmd_ = "";
            return this;
        }

        public ClientHeader clearDirFlag() {
            this.hasDirFlag = false;
            this.dirFlag_ = 1;
            return this;
        }

        public ClientHeader clearErrCode() {
            this.hasErrCode = false;
            this.errCode_ = 0;
            return this;
        }

        public ClientHeader clearErrStr() {
            this.hasErrStr = false;
            this.errStr_ = "";
            return this;
        }

        public ClientHeader clearId() {
            this.hasId = false;
            this.id_ = "";
            return this;
        }

        public ClientHeader clearResource() {
            this.hasResource = false;
            this.resource_ = "";
            return this;
        }

        public ClientHeader clearServer() {
            this.hasServer = false;
            this.server_ = "";
            return this;
        }

        public ClientHeader clearSubcmd() {
            this.hasSubcmd = false;
            this.subcmd_ = "";
            return this;
        }

        public ClientHeader clearUuid() {
            this.hasUuid = false;
            this.uuid_ = 0L;
            return this;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public int getCachedSize() {
            if (this.cachedSize < 0) {
                getSerializedSize();
            }
            return this.cachedSize;
        }

        public int getChid() {
            return this.chid_;
        }

        public int getCipher() {
            return this.cipher_;
        }

        public String getCmd() {
            return this.cmd_;
        }

        public int getDirFlag() {
            return this.dirFlag_;
        }

        public int getErrCode() {
            return this.errCode_;
        }

        public String getErrStr() {
            return this.errStr_;
        }

        public String getId() {
            return this.id_;
        }

        public String getResource() {
            return this.resource_;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public int getSerializedSize() {
            int iComputeInt32Size = 0;
            if (hasChid()) {
                iComputeInt32Size = 0 + CodedOutputStreamMicro.computeInt32Size(1, getChid());
            }
            int iComputeInt64Size = iComputeInt32Size;
            if (hasUuid()) {
                iComputeInt64Size = iComputeInt32Size + CodedOutputStreamMicro.computeInt64Size(2, getUuid());
            }
            int iComputeStringSize = iComputeInt64Size;
            if (hasServer()) {
                iComputeStringSize = iComputeInt64Size + CodedOutputStreamMicro.computeStringSize(3, getServer());
            }
            int iComputeStringSize2 = iComputeStringSize;
            if (hasResource()) {
                iComputeStringSize2 = iComputeStringSize + CodedOutputStreamMicro.computeStringSize(4, getResource());
            }
            int iComputeStringSize3 = iComputeStringSize2;
            if (hasCmd()) {
                iComputeStringSize3 = iComputeStringSize2 + CodedOutputStreamMicro.computeStringSize(5, getCmd());
            }
            int iComputeStringSize4 = iComputeStringSize3;
            if (hasSubcmd()) {
                iComputeStringSize4 = iComputeStringSize3 + CodedOutputStreamMicro.computeStringSize(6, getSubcmd());
            }
            int iComputeStringSize5 = iComputeStringSize4;
            if (hasId()) {
                iComputeStringSize5 = iComputeStringSize4 + CodedOutputStreamMicro.computeStringSize(7, getId());
            }
            int iComputeInt32Size2 = iComputeStringSize5;
            if (hasDirFlag()) {
                iComputeInt32Size2 = iComputeStringSize5 + CodedOutputStreamMicro.computeInt32Size(8, getDirFlag());
            }
            int iComputeInt32Size3 = iComputeInt32Size2;
            if (hasCipher()) {
                iComputeInt32Size3 = iComputeInt32Size2 + CodedOutputStreamMicro.computeInt32Size(9, getCipher());
            }
            int iComputeInt32Size4 = iComputeInt32Size3;
            if (hasErrCode()) {
                iComputeInt32Size4 = iComputeInt32Size3 + CodedOutputStreamMicro.computeInt32Size(10, getErrCode());
            }
            int iComputeStringSize6 = iComputeInt32Size4;
            if (hasErrStr()) {
                iComputeStringSize6 = iComputeInt32Size4 + CodedOutputStreamMicro.computeStringSize(11, getErrStr());
            }
            this.cachedSize = iComputeStringSize6;
            return iComputeStringSize6;
        }

        public String getServer() {
            return this.server_;
        }

        public String getSubcmd() {
            return this.subcmd_;
        }

        public long getUuid() {
            return this.uuid_;
        }

        public boolean hasChid() {
            return this.hasChid;
        }

        public boolean hasCipher() {
            return this.hasCipher;
        }

        public boolean hasCmd() {
            return this.hasCmd;
        }

        public boolean hasDirFlag() {
            return this.hasDirFlag;
        }

        public boolean hasErrCode() {
            return this.hasErrCode;
        }

        public boolean hasErrStr() {
            return this.hasErrStr;
        }

        public boolean hasId() {
            return this.hasId;
        }

        public boolean hasResource() {
            return this.hasResource;
        }

        public boolean hasServer() {
            return this.hasServer;
        }

        public boolean hasSubcmd() {
            return this.hasSubcmd;
        }

        public boolean hasUuid() {
            return this.hasUuid;
        }

        public final boolean isInitialized() {
            return true;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public ClientHeader mergeFrom(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
            while (true) {
                int tag = codedInputStreamMicro.readTag();
                switch (tag) {
                    case 0:
                        return this;
                    case 8:
                        setChid(codedInputStreamMicro.readInt32());
                        break;
                    case 16:
                        setUuid(codedInputStreamMicro.readInt64());
                        break;
                    case 26:
                        setServer(codedInputStreamMicro.readString());
                        break;
                    case 34:
                        setResource(codedInputStreamMicro.readString());
                        break;
                    case 42:
                        setCmd(codedInputStreamMicro.readString());
                        break;
                    case 50:
                        setSubcmd(codedInputStreamMicro.readString());
                        break;
                    case 58:
                        setId(codedInputStreamMicro.readString());
                        break;
                    case 64:
                        setDirFlag(codedInputStreamMicro.readInt32());
                        break;
                    case 72:
                        setCipher(codedInputStreamMicro.readInt32());
                        break;
                    case HTTP_DEFAULT_PORT:
                        setErrCode(codedInputStreamMicro.readInt32());
                        break;
                    case 90:
                        setErrStr(codedInputStreamMicro.readString());
                        break;
                    default:
                        if (!parseUnknownField(codedInputStreamMicro, tag)) {
                            return this;
                        }
                        break;
                }
            }
        }

        public ClientHeader setChid(int i) {
            this.hasChid = true;
            this.chid_ = i;
            return this;
        }

        public ClientHeader setCipher(int i) {
            this.hasCipher = true;
            this.cipher_ = i;
            return this;
        }

        public ClientHeader setCmd(String str) {
            this.hasCmd = true;
            this.cmd_ = str;
            return this;
        }

        public ClientHeader setDirFlag(int i) {
            this.hasDirFlag = true;
            this.dirFlag_ = i;
            return this;
        }

        public ClientHeader setErrCode(int i) {
            this.hasErrCode = true;
            this.errCode_ = i;
            return this;
        }

        public ClientHeader setErrStr(String str) {
            this.hasErrStr = true;
            this.errStr_ = str;
            return this;
        }

        public ClientHeader setId(String str) {
            this.hasId = true;
            this.id_ = str;
            return this;
        }

        public ClientHeader setResource(String str) {
            this.hasResource = true;
            this.resource_ = str;
            return this;
        }

        public ClientHeader setServer(String str) {
            this.hasServer = true;
            this.server_ = str;
            return this;
        }

        public ClientHeader setSubcmd(String str) {
            this.hasSubcmd = true;
            this.subcmd_ = str;
            return this;
        }

        public ClientHeader setUuid(long j) {
            this.hasUuid = true;
            this.uuid_ = j;
            return this;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public void writeTo(CodedOutputStreamMicro codedOutputStreamMicro) throws IOException {
            if (hasChid()) {
                codedOutputStreamMicro.writeInt32(1, getChid());
            }
            if (hasUuid()) {
                codedOutputStreamMicro.writeInt64(2, getUuid());
            }
            if (hasServer()) {
                codedOutputStreamMicro.writeString(3, getServer());
            }
            if (hasResource()) {
                codedOutputStreamMicro.writeString(4, getResource());
            }
            if (hasCmd()) {
                codedOutputStreamMicro.writeString(5, getCmd());
            }
            if (hasSubcmd()) {
                codedOutputStreamMicro.writeString(6, getSubcmd());
            }
            if (hasId()) {
                codedOutputStreamMicro.writeString(7, getId());
            }
            if (hasDirFlag()) {
                codedOutputStreamMicro.writeInt32(8, getDirFlag());
            }
            if (hasCipher()) {
                codedOutputStreamMicro.writeInt32(9, getCipher());
            }
            if (hasErrCode()) {
                codedOutputStreamMicro.writeInt32(10, getErrCode());
            }
            if (hasErrStr()) {
                codedOutputStreamMicro.writeString(11, getErrStr());
            }
        }
    }

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/protobuf/ChannelMessage$PushServiceConfigMsg.class */
    public static final class PushServiceConfigMsg extends MessageMicro {
        public static final int CLIENTVERSION_FIELD_NUMBER = 3;
        public static final int CLOUDVERSION_FIELD_NUMBER = 4;
        public static final int DOTS_FIELD_NUMBER = 5;
        public static final int FETCHBUCKET_FIELD_NUMBER = 1;
        private boolean hasClientVersion;
        private boolean hasCloudVersion;
        private boolean hasDots;
        private boolean hasFetchBucket;
        private boolean fetchBucket_ = false;
        private int clientVersion_ = 0;
        private int cloudVersion_ = 0;
        private int dots_ = 0;
        private int cachedSize = -1;

        public static PushServiceConfigMsg parseFrom(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
            return new PushServiceConfigMsg().mergeFrom(codedInputStreamMicro);
        }

        public static PushServiceConfigMsg parseFrom(byte[] bArr) throws InvalidProtocolBufferMicroException {
            return (PushServiceConfigMsg) new PushServiceConfigMsg().mergeFrom(bArr);
        }

        public final PushServiceConfigMsg clear() {
            clearFetchBucket();
            clearClientVersion();
            clearCloudVersion();
            clearDots();
            this.cachedSize = -1;
            return this;
        }

        public PushServiceConfigMsg clearClientVersion() {
            this.hasClientVersion = false;
            this.clientVersion_ = 0;
            return this;
        }

        public PushServiceConfigMsg clearCloudVersion() {
            this.hasCloudVersion = false;
            this.cloudVersion_ = 0;
            return this;
        }

        public PushServiceConfigMsg clearDots() {
            this.hasDots = false;
            this.dots_ = 0;
            return this;
        }

        public PushServiceConfigMsg clearFetchBucket() {
            this.hasFetchBucket = false;
            this.fetchBucket_ = false;
            return this;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public int getCachedSize() {
            if (this.cachedSize < 0) {
                getSerializedSize();
            }
            return this.cachedSize;
        }

        public int getClientVersion() {
            return this.clientVersion_;
        }

        public int getCloudVersion() {
            return this.cloudVersion_;
        }

        public int getDots() {
            return this.dots_;
        }

        public boolean getFetchBucket() {
            return this.fetchBucket_;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public int getSerializedSize() {
            int iComputeBoolSize = 0;
            if (hasFetchBucket()) {
                iComputeBoolSize = 0 + CodedOutputStreamMicro.computeBoolSize(1, getFetchBucket());
            }
            int iComputeInt32Size = iComputeBoolSize;
            if (hasClientVersion()) {
                iComputeInt32Size = iComputeBoolSize + CodedOutputStreamMicro.computeInt32Size(3, getClientVersion());
            }
            int iComputeInt32Size2 = iComputeInt32Size;
            if (hasCloudVersion()) {
                iComputeInt32Size2 = iComputeInt32Size + CodedOutputStreamMicro.computeInt32Size(4, getCloudVersion());
            }
            int iComputeInt32Size3 = iComputeInt32Size2;
            if (hasDots()) {
                iComputeInt32Size3 = iComputeInt32Size2 + CodedOutputStreamMicro.computeInt32Size(5, getDots());
            }
            this.cachedSize = iComputeInt32Size3;
            return iComputeInt32Size3;
        }

        public boolean hasClientVersion() {
            return this.hasClientVersion;
        }

        public boolean hasCloudVersion() {
            return this.hasCloudVersion;
        }

        public boolean hasDots() {
            return this.hasDots;
        }

        public boolean hasFetchBucket() {
            return this.hasFetchBucket;
        }

        public final boolean isInitialized() {
            return true;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public PushServiceConfigMsg mergeFrom(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
            while (true) {
                int tag = codedInputStreamMicro.readTag();
                switch (tag) {
                    case 0:
                        return this;
                    case 8:
                        setFetchBucket(codedInputStreamMicro.readBool());
                        break;
                    case 24:
                        setClientVersion(codedInputStreamMicro.readInt32());
                        break;
                    case 32:
                        setCloudVersion(codedInputStreamMicro.readInt32());
                        break;
                    case 40:
                        setDots(codedInputStreamMicro.readInt32());
                        break;
                    default:
                        if (!parseUnknownField(codedInputStreamMicro, tag)) {
                            return this;
                        }
                        break;
                }
            }
        }

        public PushServiceConfigMsg setClientVersion(int i) {
            this.hasClientVersion = true;
            this.clientVersion_ = i;
            return this;
        }

        public PushServiceConfigMsg setCloudVersion(int i) {
            this.hasCloudVersion = true;
            this.cloudVersion_ = i;
            return this;
        }

        public PushServiceConfigMsg setDots(int i) {
            this.hasDots = true;
            this.dots_ = i;
            return this;
        }

        public PushServiceConfigMsg setFetchBucket(boolean z) {
            this.hasFetchBucket = true;
            this.fetchBucket_ = z;
            return this;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public void writeTo(CodedOutputStreamMicro codedOutputStreamMicro) throws IOException {
            if (hasFetchBucket()) {
                codedOutputStreamMicro.writeBool(1, getFetchBucket());
            }
            if (hasClientVersion()) {
                codedOutputStreamMicro.writeInt32(3, getClientVersion());
            }
            if (hasCloudVersion()) {
                codedOutputStreamMicro.writeInt32(4, getCloudVersion());
            }
            if (hasDots()) {
                codedOutputStreamMicro.writeInt32(5, getDots());
            }
        }
    }

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/protobuf/ChannelMessage$XMMsgBind.class */
    public static final class XMMsgBind extends MessageMicro {
        public static final int CLIENT_ATTRS_FIELD_NUMBER = 4;
        public static final int CLOUD_ATTRS_FIELD_NUMBER = 5;
        public static final int KICK_FIELD_NUMBER = 2;
        public static final int METHOD_FIELD_NUMBER = 3;
        public static final int SIG_FIELD_NUMBER = 6;
        public static final int TOKEN_FIELD_NUMBER = 1;
        private boolean hasClientAttrs;
        private boolean hasCloudAttrs;
        private boolean hasKick;
        private boolean hasMethod;
        private boolean hasSig;
        private boolean hasToken;
        private String token_ = "";
        private String kick_ = "";
        private String method_ = "";
        private String clientAttrs_ = "";
        private String cloudAttrs_ = "";
        private String sig_ = "";
        private int cachedSize = -1;

        public static XMMsgBind parseFrom(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
            return new XMMsgBind().mergeFrom(codedInputStreamMicro);
        }

        public static XMMsgBind parseFrom(byte[] bArr) throws InvalidProtocolBufferMicroException {
            return (XMMsgBind) new XMMsgBind().mergeFrom(bArr);
        }

        public final XMMsgBind clear() {
            clearToken();
            clearKick();
            clearMethod();
            clearClientAttrs();
            clearCloudAttrs();
            clearSig();
            this.cachedSize = -1;
            return this;
        }

        public XMMsgBind clearClientAttrs() {
            this.hasClientAttrs = false;
            this.clientAttrs_ = "";
            return this;
        }

        public XMMsgBind clearCloudAttrs() {
            this.hasCloudAttrs = false;
            this.cloudAttrs_ = "";
            return this;
        }

        public XMMsgBind clearKick() {
            this.hasKick = false;
            this.kick_ = "";
            return this;
        }

        public XMMsgBind clearMethod() {
            this.hasMethod = false;
            this.method_ = "";
            return this;
        }

        public XMMsgBind clearSig() {
            this.hasSig = false;
            this.sig_ = "";
            return this;
        }

        public XMMsgBind clearToken() {
            this.hasToken = false;
            this.token_ = "";
            return this;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public int getCachedSize() {
            if (this.cachedSize < 0) {
                getSerializedSize();
            }
            return this.cachedSize;
        }

        public String getClientAttrs() {
            return this.clientAttrs_;
        }

        public String getCloudAttrs() {
            return this.cloudAttrs_;
        }

        public String getKick() {
            return this.kick_;
        }

        public String getMethod() {
            return this.method_;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public int getSerializedSize() {
            int iComputeStringSize = 0;
            if (hasToken()) {
                iComputeStringSize = 0 + CodedOutputStreamMicro.computeStringSize(1, getToken());
            }
            int iComputeStringSize2 = iComputeStringSize;
            if (hasKick()) {
                iComputeStringSize2 = iComputeStringSize + CodedOutputStreamMicro.computeStringSize(2, getKick());
            }
            int iComputeStringSize3 = iComputeStringSize2;
            if (hasMethod()) {
                iComputeStringSize3 = iComputeStringSize2 + CodedOutputStreamMicro.computeStringSize(3, getMethod());
            }
            int iComputeStringSize4 = iComputeStringSize3;
            if (hasClientAttrs()) {
                iComputeStringSize4 = iComputeStringSize3 + CodedOutputStreamMicro.computeStringSize(4, getClientAttrs());
            }
            int iComputeStringSize5 = iComputeStringSize4;
            if (hasCloudAttrs()) {
                iComputeStringSize5 = iComputeStringSize4 + CodedOutputStreamMicro.computeStringSize(5, getCloudAttrs());
            }
            int iComputeStringSize6 = iComputeStringSize5;
            if (hasSig()) {
                iComputeStringSize6 = iComputeStringSize5 + CodedOutputStreamMicro.computeStringSize(6, getSig());
            }
            this.cachedSize = iComputeStringSize6;
            return iComputeStringSize6;
        }

        public String getSig() {
            return this.sig_;
        }

        public String getToken() {
            return this.token_;
        }

        public boolean hasClientAttrs() {
            return this.hasClientAttrs;
        }

        public boolean hasCloudAttrs() {
            return this.hasCloudAttrs;
        }

        public boolean hasKick() {
            return this.hasKick;
        }

        public boolean hasMethod() {
            return this.hasMethod;
        }

        public boolean hasSig() {
            return this.hasSig;
        }

        public boolean hasToken() {
            return this.hasToken;
        }

        public final boolean isInitialized() {
            return true;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public XMMsgBind mergeFrom(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
            while (true) {
                int tag = codedInputStreamMicro.readTag();
                switch (tag) {
                    case 0:
                        return this;
                    case 10:
                        setToken(codedInputStreamMicro.readString());
                        break;
                    case 18:
                        setKick(codedInputStreamMicro.readString());
                        break;
                    case 26:
                        setMethod(codedInputStreamMicro.readString());
                        break;
                    case 34:
                        setClientAttrs(codedInputStreamMicro.readString());
                        break;
                    case 42:
                        setCloudAttrs(codedInputStreamMicro.readString());
                        break;
                    case 50:
                        setSig(codedInputStreamMicro.readString());
                        break;
                    default:
                        if (!parseUnknownField(codedInputStreamMicro, tag)) {
                            return this;
                        }
                        break;
                }
            }
        }

        public XMMsgBind setClientAttrs(String str) {
            this.hasClientAttrs = true;
            this.clientAttrs_ = str;
            return this;
        }

        public XMMsgBind setCloudAttrs(String str) {
            this.hasCloudAttrs = true;
            this.cloudAttrs_ = str;
            return this;
        }

        public XMMsgBind setKick(String str) {
            this.hasKick = true;
            this.kick_ = str;
            return this;
        }

        public XMMsgBind setMethod(String str) {
            this.hasMethod = true;
            this.method_ = str;
            return this;
        }

        public XMMsgBind setSig(String str) {
            this.hasSig = true;
            this.sig_ = str;
            return this;
        }

        public XMMsgBind setToken(String str) {
            this.hasToken = true;
            this.token_ = str;
            return this;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public void writeTo(CodedOutputStreamMicro codedOutputStreamMicro) throws IOException {
            if (hasToken()) {
                codedOutputStreamMicro.writeString(1, getToken());
            }
            if (hasKick()) {
                codedOutputStreamMicro.writeString(2, getKick());
            }
            if (hasMethod()) {
                codedOutputStreamMicro.writeString(3, getMethod());
            }
            if (hasClientAttrs()) {
                codedOutputStreamMicro.writeString(4, getClientAttrs());
            }
            if (hasCloudAttrs()) {
                codedOutputStreamMicro.writeString(5, getCloudAttrs());
            }
            if (hasSig()) {
                codedOutputStreamMicro.writeString(6, getSig());
            }
        }
    }

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/protobuf/ChannelMessage$XMMsgBindResp.class */
    public static final class XMMsgBindResp extends MessageMicro {
        public static final int ERROR_DESC_FIELD_NUMBER = 4;
        public static final int ERROR_REASON_FIELD_NUMBER = 3;
        public static final int ERROR_TYPE_FIELD_NUMBER = 2;
        public static final int RESULT_FIELD_NUMBER = 1;
        private boolean hasErrorDesc;
        private boolean hasErrorReason;
        private boolean hasErrorType;
        private boolean hasResult;
        private boolean result_ = false;
        private String errorType_ = "";
        private String errorReason_ = "";
        private String errorDesc_ = "";
        private int cachedSize = -1;

        public static XMMsgBindResp parseFrom(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
            return new XMMsgBindResp().mergeFrom(codedInputStreamMicro);
        }

        public static XMMsgBindResp parseFrom(byte[] bArr) throws InvalidProtocolBufferMicroException {
            return (XMMsgBindResp) new XMMsgBindResp().mergeFrom(bArr);
        }

        public final XMMsgBindResp clear() {
            clearResult();
            clearErrorType();
            clearErrorReason();
            clearErrorDesc();
            this.cachedSize = -1;
            return this;
        }

        public XMMsgBindResp clearErrorDesc() {
            this.hasErrorDesc = false;
            this.errorDesc_ = "";
            return this;
        }

        public XMMsgBindResp clearErrorReason() {
            this.hasErrorReason = false;
            this.errorReason_ = "";
            return this;
        }

        public XMMsgBindResp clearErrorType() {
            this.hasErrorType = false;
            this.errorType_ = "";
            return this;
        }

        public XMMsgBindResp clearResult() {
            this.hasResult = false;
            this.result_ = false;
            return this;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public int getCachedSize() {
            if (this.cachedSize < 0) {
                getSerializedSize();
            }
            return this.cachedSize;
        }

        public String getErrorDesc() {
            return this.errorDesc_;
        }

        public String getErrorReason() {
            return this.errorReason_;
        }

        public String getErrorType() {
            return this.errorType_;
        }

        public boolean getResult() {
            return this.result_;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public int getSerializedSize() {
            int iComputeBoolSize = 0;
            if (hasResult()) {
                iComputeBoolSize = 0 + CodedOutputStreamMicro.computeBoolSize(1, getResult());
            }
            int iComputeStringSize = iComputeBoolSize;
            if (hasErrorType()) {
                iComputeStringSize = iComputeBoolSize + CodedOutputStreamMicro.computeStringSize(2, getErrorType());
            }
            int iComputeStringSize2 = iComputeStringSize;
            if (hasErrorReason()) {
                iComputeStringSize2 = iComputeStringSize + CodedOutputStreamMicro.computeStringSize(3, getErrorReason());
            }
            int iComputeStringSize3 = iComputeStringSize2;
            if (hasErrorDesc()) {
                iComputeStringSize3 = iComputeStringSize2 + CodedOutputStreamMicro.computeStringSize(4, getErrorDesc());
            }
            this.cachedSize = iComputeStringSize3;
            return iComputeStringSize3;
        }

        public boolean hasErrorDesc() {
            return this.hasErrorDesc;
        }

        public boolean hasErrorReason() {
            return this.hasErrorReason;
        }

        public boolean hasErrorType() {
            return this.hasErrorType;
        }

        public boolean hasResult() {
            return this.hasResult;
        }

        public final boolean isInitialized() {
            return true;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public XMMsgBindResp mergeFrom(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
            while (true) {
                int tag = codedInputStreamMicro.readTag();
                switch (tag) {
                    case 0:
                        return this;
                    case 8:
                        setResult(codedInputStreamMicro.readBool());
                        break;
                    case 18:
                        setErrorType(codedInputStreamMicro.readString());
                        break;
                    case 26:
                        setErrorReason(codedInputStreamMicro.readString());
                        break;
                    case 34:
                        setErrorDesc(codedInputStreamMicro.readString());
                        break;
                    default:
                        if (!parseUnknownField(codedInputStreamMicro, tag)) {
                            return this;
                        }
                        break;
                }
            }
        }

        public XMMsgBindResp setErrorDesc(String str) {
            this.hasErrorDesc = true;
            this.errorDesc_ = str;
            return this;
        }

        public XMMsgBindResp setErrorReason(String str) {
            this.hasErrorReason = true;
            this.errorReason_ = str;
            return this;
        }

        public XMMsgBindResp setErrorType(String str) {
            this.hasErrorType = true;
            this.errorType_ = str;
            return this;
        }

        public XMMsgBindResp setResult(boolean z) {
            this.hasResult = true;
            this.result_ = z;
            return this;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public void writeTo(CodedOutputStreamMicro codedOutputStreamMicro) throws IOException {
            if (hasResult()) {
                codedOutputStreamMicro.writeBool(1, getResult());
            }
            if (hasErrorType()) {
                codedOutputStreamMicro.writeString(2, getErrorType());
            }
            if (hasErrorReason()) {
                codedOutputStreamMicro.writeString(3, getErrorReason());
            }
            if (hasErrorDesc()) {
                codedOutputStreamMicro.writeString(4, getErrorDesc());
            }
        }
    }

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/protobuf/ChannelMessage$XMMsgConn.class */
    public static final class XMMsgConn extends MessageMicro {
        public static final int ANDVER_FIELD_NUMBER = 10;
        public static final int CONNPT_FIELD_NUMBER = 6;
        public static final int HOST_FIELD_NUMBER = 7;
        public static final int LOCALE_FIELD_NUMBER = 8;
        public static final int MODEL_FIELD_NUMBER = 2;
        public static final int OS_FIELD_NUMBER = 3;
        public static final int PSC_FIELD_NUMBER = 9;
        public static final int SDK_FIELD_NUMBER = 5;
        public static final int UDID_FIELD_NUMBER = 4;
        public static final int VERSION_FIELD_NUMBER = 1;
        private boolean hasAndver;
        private boolean hasConnpt;
        private boolean hasHost;
        private boolean hasLocale;
        private boolean hasModel;
        private boolean hasOs;
        private boolean hasPsc;
        private boolean hasSdk;
        private boolean hasUdid;
        private boolean hasVersion;
        private int version_ = 0;
        private String model_ = "";
        private String os_ = "";
        private String udid_ = "";
        private int sdk_ = 0;
        private String connpt_ = "";
        private String host_ = "";
        private String locale_ = "";
        private PushServiceConfigMsg psc_ = null;
        private int andver_ = 0;
        private int cachedSize = -1;

        public static XMMsgConn parseFrom(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
            return new XMMsgConn().mergeFrom(codedInputStreamMicro);
        }

        public static XMMsgConn parseFrom(byte[] bArr) throws InvalidProtocolBufferMicroException {
            return (XMMsgConn) new XMMsgConn().mergeFrom(bArr);
        }

        public final XMMsgConn clear() {
            clearVersion();
            clearModel();
            clearOs();
            clearUdid();
            clearSdk();
            clearConnpt();
            clearHost();
            clearLocale();
            clearPsc();
            clearAndver();
            this.cachedSize = -1;
            return this;
        }

        public XMMsgConn clearAndver() {
            this.hasAndver = false;
            this.andver_ = 0;
            return this;
        }

        public XMMsgConn clearConnpt() {
            this.hasConnpt = false;
            this.connpt_ = "";
            return this;
        }

        public XMMsgConn clearHost() {
            this.hasHost = false;
            this.host_ = "";
            return this;
        }

        public XMMsgConn clearLocale() {
            this.hasLocale = false;
            this.locale_ = "";
            return this;
        }

        public XMMsgConn clearModel() {
            this.hasModel = false;
            this.model_ = "";
            return this;
        }

        public XMMsgConn clearOs() {
            this.hasOs = false;
            this.os_ = "";
            return this;
        }

        public XMMsgConn clearPsc() {
            this.hasPsc = false;
            this.psc_ = null;
            return this;
        }

        public XMMsgConn clearSdk() {
            this.hasSdk = false;
            this.sdk_ = 0;
            return this;
        }

        public XMMsgConn clearUdid() {
            this.hasUdid = false;
            this.udid_ = "";
            return this;
        }

        public XMMsgConn clearVersion() {
            this.hasVersion = false;
            this.version_ = 0;
            return this;
        }

        public int getAndver() {
            return this.andver_;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public int getCachedSize() {
            if (this.cachedSize < 0) {
                getSerializedSize();
            }
            return this.cachedSize;
        }

        public String getConnpt() {
            return this.connpt_;
        }

        public String getHost() {
            return this.host_;
        }

        public String getLocale() {
            return this.locale_;
        }

        public String getModel() {
            return this.model_;
        }

        public String getOs() {
            return this.os_;
        }

        public PushServiceConfigMsg getPsc() {
            return this.psc_;
        }

        public int getSdk() {
            return this.sdk_;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public int getSerializedSize() {
            int iComputeUInt32Size = 0;
            if (hasVersion()) {
                iComputeUInt32Size = 0 + CodedOutputStreamMicro.computeUInt32Size(1, getVersion());
            }
            int iComputeStringSize = iComputeUInt32Size;
            if (hasModel()) {
                iComputeStringSize = iComputeUInt32Size + CodedOutputStreamMicro.computeStringSize(2, getModel());
            }
            int iComputeStringSize2 = iComputeStringSize;
            if (hasOs()) {
                iComputeStringSize2 = iComputeStringSize + CodedOutputStreamMicro.computeStringSize(3, getOs());
            }
            int iComputeStringSize3 = iComputeStringSize2;
            if (hasUdid()) {
                iComputeStringSize3 = iComputeStringSize2 + CodedOutputStreamMicro.computeStringSize(4, getUdid());
            }
            int iComputeInt32Size = iComputeStringSize3;
            if (hasSdk()) {
                iComputeInt32Size = iComputeStringSize3 + CodedOutputStreamMicro.computeInt32Size(5, getSdk());
            }
            int iComputeStringSize4 = iComputeInt32Size;
            if (hasConnpt()) {
                iComputeStringSize4 = iComputeInt32Size + CodedOutputStreamMicro.computeStringSize(6, getConnpt());
            }
            int iComputeStringSize5 = iComputeStringSize4;
            if (hasHost()) {
                iComputeStringSize5 = iComputeStringSize4 + CodedOutputStreamMicro.computeStringSize(7, getHost());
            }
            int iComputeStringSize6 = iComputeStringSize5;
            if (hasLocale()) {
                iComputeStringSize6 = iComputeStringSize5 + CodedOutputStreamMicro.computeStringSize(8, getLocale());
            }
            int iComputeMessageSize = iComputeStringSize6;
            if (hasPsc()) {
                iComputeMessageSize = iComputeStringSize6 + CodedOutputStreamMicro.computeMessageSize(9, getPsc());
            }
            int iComputeInt32Size2 = iComputeMessageSize;
            if (hasAndver()) {
                iComputeInt32Size2 = iComputeMessageSize + CodedOutputStreamMicro.computeInt32Size(10, getAndver());
            }
            this.cachedSize = iComputeInt32Size2;
            return iComputeInt32Size2;
        }

        public String getUdid() {
            return this.udid_;
        }

        public int getVersion() {
            return this.version_;
        }

        public boolean hasAndver() {
            return this.hasAndver;
        }

        public boolean hasConnpt() {
            return this.hasConnpt;
        }

        public boolean hasHost() {
            return this.hasHost;
        }

        public boolean hasLocale() {
            return this.hasLocale;
        }

        public boolean hasModel() {
            return this.hasModel;
        }

        public boolean hasOs() {
            return this.hasOs;
        }

        public boolean hasPsc() {
            return this.hasPsc;
        }

        public boolean hasSdk() {
            return this.hasSdk;
        }

        public boolean hasUdid() {
            return this.hasUdid;
        }

        public boolean hasVersion() {
            return this.hasVersion;
        }

        public final boolean isInitialized() {
            return true;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public XMMsgConn mergeFrom(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
            while (true) {
                int tag = codedInputStreamMicro.readTag();
                switch (tag) {
                    case 0:
                        return this;
                    case 8:
                        setVersion(codedInputStreamMicro.readUInt32());
                        break;
                    case 18:
                        setModel(codedInputStreamMicro.readString());
                        break;
                    case 26:
                        setOs(codedInputStreamMicro.readString());
                        break;
                    case 34:
                        setUdid(codedInputStreamMicro.readString());
                        break;
                    case 40:
                        setSdk(codedInputStreamMicro.readInt32());
                        break;
                    case 50:
                        setConnpt(codedInputStreamMicro.readString());
                        break;
                    case 58:
                        setHost(codedInputStreamMicro.readString());
                        break;
                    case 66:
                        setLocale(codedInputStreamMicro.readString());
                        break;
                    case 74:
                        PushServiceConfigMsg pushServiceConfigMsg = new PushServiceConfigMsg();
                        codedInputStreamMicro.readMessage(pushServiceConfigMsg);
                        setPsc(pushServiceConfigMsg);
                        break;
                    case HTTP_DEFAULT_PORT:
                        setAndver(codedInputStreamMicro.readInt32());
                        break;
                    default:
                        if (!parseUnknownField(codedInputStreamMicro, tag)) {
                            return this;
                        }
                        break;
                }
            }
        }

        public XMMsgConn setAndver(int i) {
            this.hasAndver = true;
            this.andver_ = i;
            return this;
        }

        public XMMsgConn setConnpt(String str) {
            this.hasConnpt = true;
            this.connpt_ = str;
            return this;
        }

        public XMMsgConn setHost(String str) {
            this.hasHost = true;
            this.host_ = str;
            return this;
        }

        public XMMsgConn setLocale(String str) {
            this.hasLocale = true;
            this.locale_ = str;
            return this;
        }

        public XMMsgConn setModel(String str) {
            this.hasModel = true;
            this.model_ = str;
            return this;
        }

        public XMMsgConn setOs(String str) {
            this.hasOs = true;
            this.os_ = str;
            return this;
        }

        public XMMsgConn setPsc(PushServiceConfigMsg pushServiceConfigMsg) {
            if (pushServiceConfigMsg == null) {
                throw new NullPointerException();
            }
            this.hasPsc = true;
            this.psc_ = pushServiceConfigMsg;
            return this;
        }

        public XMMsgConn setSdk(int i) {
            this.hasSdk = true;
            this.sdk_ = i;
            return this;
        }

        public XMMsgConn setUdid(String str) {
            this.hasUdid = true;
            this.udid_ = str;
            return this;
        }

        public XMMsgConn setVersion(int i) {
            this.hasVersion = true;
            this.version_ = i;
            return this;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public void writeTo(CodedOutputStreamMicro codedOutputStreamMicro) throws IOException {
            if (hasVersion()) {
                codedOutputStreamMicro.writeUInt32(1, getVersion());
            }
            if (hasModel()) {
                codedOutputStreamMicro.writeString(2, getModel());
            }
            if (hasOs()) {
                codedOutputStreamMicro.writeString(3, getOs());
            }
            if (hasUdid()) {
                codedOutputStreamMicro.writeString(4, getUdid());
            }
            if (hasSdk()) {
                codedOutputStreamMicro.writeInt32(5, getSdk());
            }
            if (hasConnpt()) {
                codedOutputStreamMicro.writeString(6, getConnpt());
            }
            if (hasHost()) {
                codedOutputStreamMicro.writeString(7, getHost());
            }
            if (hasLocale()) {
                codedOutputStreamMicro.writeString(8, getLocale());
            }
            if (hasPsc()) {
                codedOutputStreamMicro.writeMessage(9, getPsc());
            }
            if (hasAndver()) {
                codedOutputStreamMicro.writeInt32(10, getAndver());
            }
        }
    }

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/protobuf/ChannelMessage$XMMsgConnResp.class */
    public static final class XMMsgConnResp extends MessageMicro {
        public static final int CHALLENGE_FIELD_NUMBER = 1;
        public static final int HOST_FIELD_NUMBER = 2;
        public static final int PSC_FIELD_NUMBER = 3;
        private boolean hasChallenge;
        private boolean hasHost;
        private boolean hasPsc;
        private String challenge_ = "";
        private String host_ = "";
        private PushServiceConfigMsg psc_ = null;
        private int cachedSize = -1;

        public static XMMsgConnResp parseFrom(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
            return new XMMsgConnResp().mergeFrom(codedInputStreamMicro);
        }

        public static XMMsgConnResp parseFrom(byte[] bArr) throws InvalidProtocolBufferMicroException {
            return (XMMsgConnResp) new XMMsgConnResp().mergeFrom(bArr);
        }

        public final XMMsgConnResp clear() {
            clearChallenge();
            clearHost();
            clearPsc();
            this.cachedSize = -1;
            return this;
        }

        public XMMsgConnResp clearChallenge() {
            this.hasChallenge = false;
            this.challenge_ = "";
            return this;
        }

        public XMMsgConnResp clearHost() {
            this.hasHost = false;
            this.host_ = "";
            return this;
        }

        public XMMsgConnResp clearPsc() {
            this.hasPsc = false;
            this.psc_ = null;
            return this;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public int getCachedSize() {
            if (this.cachedSize < 0) {
                getSerializedSize();
            }
            return this.cachedSize;
        }

        public String getChallenge() {
            return this.challenge_;
        }

        public String getHost() {
            return this.host_;
        }

        public PushServiceConfigMsg getPsc() {
            return this.psc_;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public int getSerializedSize() {
            int iComputeStringSize = 0;
            if (hasChallenge()) {
                iComputeStringSize = 0 + CodedOutputStreamMicro.computeStringSize(1, getChallenge());
            }
            int iComputeStringSize2 = iComputeStringSize;
            if (hasHost()) {
                iComputeStringSize2 = iComputeStringSize + CodedOutputStreamMicro.computeStringSize(2, getHost());
            }
            int iComputeMessageSize = iComputeStringSize2;
            if (hasPsc()) {
                iComputeMessageSize = iComputeStringSize2 + CodedOutputStreamMicro.computeMessageSize(3, getPsc());
            }
            this.cachedSize = iComputeMessageSize;
            return iComputeMessageSize;
        }

        public boolean hasChallenge() {
            return this.hasChallenge;
        }

        public boolean hasHost() {
            return this.hasHost;
        }

        public boolean hasPsc() {
            return this.hasPsc;
        }

        public final boolean isInitialized() {
            return true;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public XMMsgConnResp mergeFrom(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
            while (true) {
                int tag = codedInputStreamMicro.readTag();
                switch (tag) {
                    case 0:
                        return this;
                    case 10:
                        setChallenge(codedInputStreamMicro.readString());
                        break;
                    case 18:
                        setHost(codedInputStreamMicro.readString());
                        break;
                    case 26:
                        PushServiceConfigMsg pushServiceConfigMsg = new PushServiceConfigMsg();
                        codedInputStreamMicro.readMessage(pushServiceConfigMsg);
                        setPsc(pushServiceConfigMsg);
                        break;
                    default:
                        if (!parseUnknownField(codedInputStreamMicro, tag)) {
                            return this;
                        }
                        break;
                }
            }
        }

        public XMMsgConnResp setChallenge(String str) {
            this.hasChallenge = true;
            this.challenge_ = str;
            return this;
        }

        public XMMsgConnResp setHost(String str) {
            this.hasHost = true;
            this.host_ = str;
            return this;
        }

        public XMMsgConnResp setPsc(PushServiceConfigMsg pushServiceConfigMsg) {
            if (pushServiceConfigMsg == null) {
                throw new NullPointerException();
            }
            this.hasPsc = true;
            this.psc_ = pushServiceConfigMsg;
            return this;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public void writeTo(CodedOutputStreamMicro codedOutputStreamMicro) throws IOException {
            if (hasChallenge()) {
                codedOutputStreamMicro.writeString(1, getChallenge());
            }
            if (hasHost()) {
                codedOutputStreamMicro.writeString(2, getHost());
            }
            if (hasPsc()) {
                codedOutputStreamMicro.writeMessage(3, getPsc());
            }
        }
    }

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/protobuf/ChannelMessage$XMMsgKick.class */
    public static final class XMMsgKick extends MessageMicro {
        public static final int DESC_FIELD_NUMBER = 3;
        public static final int REASON_FIELD_NUMBER = 2;
        public static final int TYPE_FIELD_NUMBER = 1;
        private boolean hasDesc;
        private boolean hasReason;
        private boolean hasType;
        private String type_ = "";
        private String reason_ = "";
        private String desc_ = "";
        private int cachedSize = -1;

        public static XMMsgKick parseFrom(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
            return new XMMsgKick().mergeFrom(codedInputStreamMicro);
        }

        public static XMMsgKick parseFrom(byte[] bArr) throws InvalidProtocolBufferMicroException {
            return (XMMsgKick) new XMMsgKick().mergeFrom(bArr);
        }

        public final XMMsgKick clear() {
            clearType();
            clearReason();
            clearDesc();
            this.cachedSize = -1;
            return this;
        }

        public XMMsgKick clearDesc() {
            this.hasDesc = false;
            this.desc_ = "";
            return this;
        }

        public XMMsgKick clearReason() {
            this.hasReason = false;
            this.reason_ = "";
            return this;
        }

        public XMMsgKick clearType() {
            this.hasType = false;
            this.type_ = "";
            return this;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public int getCachedSize() {
            if (this.cachedSize < 0) {
                getSerializedSize();
            }
            return this.cachedSize;
        }

        public String getDesc() {
            return this.desc_;
        }

        public String getReason() {
            return this.reason_;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public int getSerializedSize() {
            int iComputeStringSize = 0;
            if (hasType()) {
                iComputeStringSize = 0 + CodedOutputStreamMicro.computeStringSize(1, getType());
            }
            int iComputeStringSize2 = iComputeStringSize;
            if (hasReason()) {
                iComputeStringSize2 = iComputeStringSize + CodedOutputStreamMicro.computeStringSize(2, getReason());
            }
            int iComputeStringSize3 = iComputeStringSize2;
            if (hasDesc()) {
                iComputeStringSize3 = iComputeStringSize2 + CodedOutputStreamMicro.computeStringSize(3, getDesc());
            }
            this.cachedSize = iComputeStringSize3;
            return iComputeStringSize3;
        }

        public String getType() {
            return this.type_;
        }

        public boolean hasDesc() {
            return this.hasDesc;
        }

        public boolean hasReason() {
            return this.hasReason;
        }

        public boolean hasType() {
            return this.hasType;
        }

        public final boolean isInitialized() {
            return true;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public XMMsgKick mergeFrom(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
            while (true) {
                int tag = codedInputStreamMicro.readTag();
                switch (tag) {
                    case 0:
                        return this;
                    case 10:
                        setType(codedInputStreamMicro.readString());
                        break;
                    case 18:
                        setReason(codedInputStreamMicro.readString());
                        break;
                    case 26:
                        setDesc(codedInputStreamMicro.readString());
                        break;
                    default:
                        if (!parseUnknownField(codedInputStreamMicro, tag)) {
                            return this;
                        }
                        break;
                }
            }
        }

        public XMMsgKick setDesc(String str) {
            this.hasDesc = true;
            this.desc_ = str;
            return this;
        }

        public XMMsgKick setReason(String str) {
            this.hasReason = true;
            this.reason_ = str;
            return this;
        }

        public XMMsgKick setType(String str) {
            this.hasType = true;
            this.type_ = str;
            return this;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public void writeTo(CodedOutputStreamMicro codedOutputStreamMicro) throws IOException {
            if (hasType()) {
                codedOutputStreamMicro.writeString(1, getType());
            }
            if (hasReason()) {
                codedOutputStreamMicro.writeString(2, getReason());
            }
            if (hasDesc()) {
                codedOutputStreamMicro.writeString(3, getDesc());
            }
        }
    }

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/protobuf/ChannelMessage$XMMsgNotify.class */
    public static final class XMMsgNotify extends MessageMicro {
        public static final int ERR_CODE_FIELD_NUMBER = 1;
        public static final int ERR_STR_FIELD_NUMBER = 2;
        private boolean hasErrCode;
        private boolean hasErrStr;
        private int errCode_ = 0;
        private String errStr_ = "";
        private int cachedSize = -1;

        public static XMMsgNotify parseFrom(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
            return new XMMsgNotify().mergeFrom(codedInputStreamMicro);
        }

        public static XMMsgNotify parseFrom(byte[] bArr) throws InvalidProtocolBufferMicroException {
            return (XMMsgNotify) new XMMsgNotify().mergeFrom(bArr);
        }

        public final XMMsgNotify clear() {
            clearErrCode();
            clearErrStr();
            this.cachedSize = -1;
            return this;
        }

        public XMMsgNotify clearErrCode() {
            this.hasErrCode = false;
            this.errCode_ = 0;
            return this;
        }

        public XMMsgNotify clearErrStr() {
            this.hasErrStr = false;
            this.errStr_ = "";
            return this;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public int getCachedSize() {
            if (this.cachedSize < 0) {
                getSerializedSize();
            }
            return this.cachedSize;
        }

        public int getErrCode() {
            return this.errCode_;
        }

        public String getErrStr() {
            return this.errStr_;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public int getSerializedSize() {
            int iComputeInt32Size = 0;
            if (hasErrCode()) {
                iComputeInt32Size = 0 + CodedOutputStreamMicro.computeInt32Size(1, getErrCode());
            }
            int iComputeStringSize = iComputeInt32Size;
            if (hasErrStr()) {
                iComputeStringSize = iComputeInt32Size + CodedOutputStreamMicro.computeStringSize(2, getErrStr());
            }
            this.cachedSize = iComputeStringSize;
            return iComputeStringSize;
        }

        public boolean hasErrCode() {
            return this.hasErrCode;
        }

        public boolean hasErrStr() {
            return this.hasErrStr;
        }

        public final boolean isInitialized() {
            return true;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public XMMsgNotify mergeFrom(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
            while (true) {
                int tag = codedInputStreamMicro.readTag();
                switch (tag) {
                    case 0:
                        return this;
                    case 8:
                        setErrCode(codedInputStreamMicro.readInt32());
                        break;
                    case 18:
                        setErrStr(codedInputStreamMicro.readString());
                        break;
                    default:
                        if (!parseUnknownField(codedInputStreamMicro, tag)) {
                            return this;
                        }
                        break;
                }
            }
        }

        public XMMsgNotify setErrCode(int i) {
            this.hasErrCode = true;
            this.errCode_ = i;
            return this;
        }

        public XMMsgNotify setErrStr(String str) {
            this.hasErrStr = true;
            this.errStr_ = str;
            return this;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public void writeTo(CodedOutputStreamMicro codedOutputStreamMicro) throws IOException {
            if (hasErrCode()) {
                codedOutputStreamMicro.writeInt32(1, getErrCode());
            }
            if (hasErrStr()) {
                codedOutputStreamMicro.writeString(2, getErrStr());
            }
        }
    }

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/protobuf/ChannelMessage$XMMsgP.class */
    public static final class XMMsgP extends MessageMicro {
        public static final int COOKIE_FIELD_NUMBER = 1;
        private boolean hasCookie;
        private ByteStringMicro cookie_ = ByteStringMicro.EMPTY;
        private int cachedSize = -1;

        public static XMMsgP parseFrom(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
            return new XMMsgP().mergeFrom(codedInputStreamMicro);
        }

        public static XMMsgP parseFrom(byte[] bArr) throws InvalidProtocolBufferMicroException {
            return (XMMsgP) new XMMsgP().mergeFrom(bArr);
        }

        public final XMMsgP clear() {
            clearCookie();
            this.cachedSize = -1;
            return this;
        }

        public XMMsgP clearCookie() {
            this.hasCookie = false;
            this.cookie_ = ByteStringMicro.EMPTY;
            return this;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public int getCachedSize() {
            if (this.cachedSize < 0) {
                getSerializedSize();
            }
            return this.cachedSize;
        }

        public ByteStringMicro getCookie() {
            return this.cookie_;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public int getSerializedSize() {
            int iComputeBytesSize = 0;
            if (hasCookie()) {
                iComputeBytesSize = 0 + CodedOutputStreamMicro.computeBytesSize(1, getCookie());
            }
            this.cachedSize = iComputeBytesSize;
            return iComputeBytesSize;
        }

        public boolean hasCookie() {
            return this.hasCookie;
        }

        public final boolean isInitialized() {
            return true;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public XMMsgP mergeFrom(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
            while (true) {
                int tag = codedInputStreamMicro.readTag();
                switch (tag) {
                    case 0:
                        return this;
                    case 10:
                        setCookie(codedInputStreamMicro.readBytes());
                        break;
                    default:
                        if (!parseUnknownField(codedInputStreamMicro, tag)) {
                            return this;
                        }
                        break;
                }
            }
        }

        public XMMsgP setCookie(ByteStringMicro byteStringMicro) {
            this.hasCookie = true;
            this.cookie_ = byteStringMicro;
            return this;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public void writeTo(CodedOutputStreamMicro codedOutputStreamMicro) throws IOException {
            if (hasCookie()) {
                codedOutputStreamMicro.writeBytes(1, getCookie());
            }
        }
    }

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/protobuf/ChannelMessage$XMMsgPing.class */
    public static final class XMMsgPing extends MessageMicro {
        public static final int PSC_FIELD_NUMBER = 2;
        public static final int STATS_FIELD_NUMBER = 1;
        private boolean hasPsc;
        private boolean hasStats;
        private ByteStringMicro stats_ = ByteStringMicro.EMPTY;
        private PushServiceConfigMsg psc_ = null;
        private int cachedSize = -1;

        public static XMMsgPing parseFrom(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
            return new XMMsgPing().mergeFrom(codedInputStreamMicro);
        }

        public static XMMsgPing parseFrom(byte[] bArr) throws InvalidProtocolBufferMicroException {
            return (XMMsgPing) new XMMsgPing().mergeFrom(bArr);
        }

        public final XMMsgPing clear() {
            clearStats();
            clearPsc();
            this.cachedSize = -1;
            return this;
        }

        public XMMsgPing clearPsc() {
            this.hasPsc = false;
            this.psc_ = null;
            return this;
        }

        public XMMsgPing clearStats() {
            this.hasStats = false;
            this.stats_ = ByteStringMicro.EMPTY;
            return this;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public int getCachedSize() {
            if (this.cachedSize < 0) {
                getSerializedSize();
            }
            return this.cachedSize;
        }

        public PushServiceConfigMsg getPsc() {
            return this.psc_;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public int getSerializedSize() {
            int iComputeBytesSize = 0;
            if (hasStats()) {
                iComputeBytesSize = 0 + CodedOutputStreamMicro.computeBytesSize(1, getStats());
            }
            int iComputeMessageSize = iComputeBytesSize;
            if (hasPsc()) {
                iComputeMessageSize = iComputeBytesSize + CodedOutputStreamMicro.computeMessageSize(2, getPsc());
            }
            this.cachedSize = iComputeMessageSize;
            return iComputeMessageSize;
        }

        public ByteStringMicro getStats() {
            return this.stats_;
        }

        public boolean hasPsc() {
            return this.hasPsc;
        }

        public boolean hasStats() {
            return this.hasStats;
        }

        public final boolean isInitialized() {
            return true;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public XMMsgPing mergeFrom(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
            while (true) {
                int tag = codedInputStreamMicro.readTag();
                switch (tag) {
                    case 0:
                        return this;
                    case 10:
                        setStats(codedInputStreamMicro.readBytes());
                        break;
                    case 18:
                        PushServiceConfigMsg pushServiceConfigMsg = new PushServiceConfigMsg();
                        codedInputStreamMicro.readMessage(pushServiceConfigMsg);
                        setPsc(pushServiceConfigMsg);
                        break;
                    default:
                        if (!parseUnknownField(codedInputStreamMicro, tag)) {
                            return this;
                        }
                        break;
                }
            }
        }

        public XMMsgPing setPsc(PushServiceConfigMsg pushServiceConfigMsg) {
            if (pushServiceConfigMsg == null) {
                throw new NullPointerException();
            }
            this.hasPsc = true;
            this.psc_ = pushServiceConfigMsg;
            return this;
        }

        public XMMsgPing setStats(ByteStringMicro byteStringMicro) {
            this.hasStats = true;
            this.stats_ = byteStringMicro;
            return this;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public void writeTo(CodedOutputStreamMicro codedOutputStreamMicro) throws IOException {
            if (hasStats()) {
                codedOutputStreamMicro.writeBytes(1, getStats());
            }
            if (hasPsc()) {
                codedOutputStreamMicro.writeMessage(2, getPsc());
            }
        }
    }

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/protobuf/ChannelMessage$XMMsgU.class */
    public static final class XMMsgU extends MessageMicro {
        public static final int END_FIELD_NUMBER = 4;
        public static final int FORCE_FIELD_NUMBER = 5;
        public static final int MAXLEN_FIELD_NUMBER = 6;
        public static final int START_FIELD_NUMBER = 3;
        public static final int TOKEN_FIELD_NUMBER = 2;
        public static final int URL_FIELD_NUMBER = 1;
        private boolean hasEnd;
        private boolean hasForce;
        private boolean hasMaxlen;
        private boolean hasStart;
        private boolean hasToken;
        private boolean hasUrl;
        private String url_ = "";
        private String token_ = "";
        private long start_ = 0;
        private long end_ = 0;
        private boolean force_ = false;
        private int maxlen_ = 0;
        private int cachedSize = -1;

        public static XMMsgU parseFrom(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
            return new XMMsgU().mergeFrom(codedInputStreamMicro);
        }

        public static XMMsgU parseFrom(byte[] bArr) throws InvalidProtocolBufferMicroException {
            return (XMMsgU) new XMMsgU().mergeFrom(bArr);
        }

        public final XMMsgU clear() {
            clearUrl();
            clearToken();
            clearStart();
            clearEnd();
            clearForce();
            clearMaxlen();
            this.cachedSize = -1;
            return this;
        }

        public XMMsgU clearEnd() {
            this.hasEnd = false;
            this.end_ = 0L;
            return this;
        }

        public XMMsgU clearForce() {
            this.hasForce = false;
            this.force_ = false;
            return this;
        }

        public XMMsgU clearMaxlen() {
            this.hasMaxlen = false;
            this.maxlen_ = 0;
            return this;
        }

        public XMMsgU clearStart() {
            this.hasStart = false;
            this.start_ = 0L;
            return this;
        }

        public XMMsgU clearToken() {
            this.hasToken = false;
            this.token_ = "";
            return this;
        }

        public XMMsgU clearUrl() {
            this.hasUrl = false;
            this.url_ = "";
            return this;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public int getCachedSize() {
            if (this.cachedSize < 0) {
                getSerializedSize();
            }
            return this.cachedSize;
        }

        public long getEnd() {
            return this.end_;
        }

        public boolean getForce() {
            return this.force_;
        }

        public int getMaxlen() {
            return this.maxlen_;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public int getSerializedSize() {
            int iComputeStringSize = 0;
            if (hasUrl()) {
                iComputeStringSize = 0 + CodedOutputStreamMicro.computeStringSize(1, getUrl());
            }
            int iComputeStringSize2 = iComputeStringSize;
            if (hasToken()) {
                iComputeStringSize2 = iComputeStringSize + CodedOutputStreamMicro.computeStringSize(2, getToken());
            }
            int iComputeUInt64Size = iComputeStringSize2;
            if (hasStart()) {
                iComputeUInt64Size = iComputeStringSize2 + CodedOutputStreamMicro.computeUInt64Size(3, getStart());
            }
            int iComputeUInt64Size2 = iComputeUInt64Size;
            if (hasEnd()) {
                iComputeUInt64Size2 = iComputeUInt64Size + CodedOutputStreamMicro.computeUInt64Size(4, getEnd());
            }
            int iComputeBoolSize = iComputeUInt64Size2;
            if (hasForce()) {
                iComputeBoolSize = iComputeUInt64Size2 + CodedOutputStreamMicro.computeBoolSize(5, getForce());
            }
            int iComputeInt32Size = iComputeBoolSize;
            if (hasMaxlen()) {
                iComputeInt32Size = iComputeBoolSize + CodedOutputStreamMicro.computeInt32Size(6, getMaxlen());
            }
            this.cachedSize = iComputeInt32Size;
            return iComputeInt32Size;
        }

        public long getStart() {
            return this.start_;
        }

        public String getToken() {
            return this.token_;
        }

        public String getUrl() {
            return this.url_;
        }

        public boolean hasEnd() {
            return this.hasEnd;
        }

        public boolean hasForce() {
            return this.hasForce;
        }

        public boolean hasMaxlen() {
            return this.hasMaxlen;
        }

        public boolean hasStart() {
            return this.hasStart;
        }

        public boolean hasToken() {
            return this.hasToken;
        }

        public boolean hasUrl() {
            return this.hasUrl;
        }

        public final boolean isInitialized() {
            return true;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public XMMsgU mergeFrom(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
            while (true) {
                int tag = codedInputStreamMicro.readTag();
                switch (tag) {
                    case 0:
                        return this;
                    case 10:
                        setUrl(codedInputStreamMicro.readString());
                        break;
                    case 18:
                        setToken(codedInputStreamMicro.readString());
                        break;
                    case 24:
                        setStart(codedInputStreamMicro.readUInt64());
                        break;
                    case 32:
                        setEnd(codedInputStreamMicro.readUInt64());
                        break;
                    case 40:
                        setForce(codedInputStreamMicro.readBool());
                        break;
                    case 48:
                        setMaxlen(codedInputStreamMicro.readInt32());
                        break;
                    default:
                        if (!parseUnknownField(codedInputStreamMicro, tag)) {
                            return this;
                        }
                        break;
                }
            }
        }

        public XMMsgU setEnd(long j) {
            this.hasEnd = true;
            this.end_ = j;
            return this;
        }

        public XMMsgU setForce(boolean z) {
            this.hasForce = true;
            this.force_ = z;
            return this;
        }

        public XMMsgU setMaxlen(int i) {
            this.hasMaxlen = true;
            this.maxlen_ = i;
            return this;
        }

        public XMMsgU setStart(long j) {
            this.hasStart = true;
            this.start_ = j;
            return this;
        }

        public XMMsgU setToken(String str) {
            this.hasToken = true;
            this.token_ = str;
            return this;
        }

        public XMMsgU setUrl(String str) {
            this.hasUrl = true;
            this.url_ = str;
            return this;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public void writeTo(CodedOutputStreamMicro codedOutputStreamMicro) throws IOException {
            if (hasUrl()) {
                codedOutputStreamMicro.writeString(1, getUrl());
            }
            if (hasToken()) {
                codedOutputStreamMicro.writeString(2, getToken());
            }
            if (hasStart()) {
                codedOutputStreamMicro.writeUInt64(3, getStart());
            }
            if (hasEnd()) {
                codedOutputStreamMicro.writeUInt64(4, getEnd());
            }
            if (hasForce()) {
                codedOutputStreamMicro.writeBool(5, getForce());
            }
            if (hasMaxlen()) {
                codedOutputStreamMicro.writeInt32(6, getMaxlen());
            }
        }
    }

    private ChannelMessage() {
    }
}
