package com.xiaomi.push.protobuf;

import com.google.protobuf.micro.CodedInputStreamMicro;
import com.google.protobuf.micro.CodedOutputStreamMicro;
import com.google.protobuf.micro.InvalidProtocolBufferMicroException;
import com.google.protobuf.micro.MessageMicro;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/protobuf/ChannelConfig.class */
public final class ChannelConfig {

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/protobuf/ChannelConfig$PushServiceConfig.class */
    public static final class PushServiceConfig extends MessageMicro {
        public static final int CONFIG_VERSION_FIELD_NUMBER = 1;
        public static final int CONNECT_TIMEOUT_MS_FIELD_NUMBER = 3;
        public static final int ENABLE_DYNAMIC_PING_FIELD_NUMBER = 4;
        public static final int TEST_HOSTS_FIELD_NUMBER = 5;
        public static final int USEBUCKETV2_FIELD_NUMBER = 2;
        private boolean hasConfigVersion;
        private boolean hasConnectTimeoutMs;
        private boolean hasEnableDynamicPing;
        private boolean hasUseBucketV2;
        private int configVersion_ = 0;
        private boolean useBucketV2_ = false;
        private int connectTimeoutMs_ = 0;
        private boolean enableDynamicPing_ = false;
        private List<String> testHosts_ = Collections.emptyList();
        private int cachedSize = -1;

        public static PushServiceConfig parseFrom(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
            return new PushServiceConfig().mergeFrom(codedInputStreamMicro);
        }

        public static PushServiceConfig parseFrom(byte[] bArr) throws InvalidProtocolBufferMicroException {
            return (PushServiceConfig) new PushServiceConfig().mergeFrom(bArr);
        }

        public PushServiceConfig addTestHosts(String str) {
            if (str == null) {
                throw new NullPointerException();
            }
            if (this.testHosts_.isEmpty()) {
                this.testHosts_ = new ArrayList();
            }
            this.testHosts_.add(str);
            return this;
        }

        public final PushServiceConfig clear() {
            clearConfigVersion();
            clearUseBucketV2();
            clearConnectTimeoutMs();
            clearEnableDynamicPing();
            clearTestHosts();
            this.cachedSize = -1;
            return this;
        }

        public PushServiceConfig clearConfigVersion() {
            this.hasConfigVersion = false;
            this.configVersion_ = 0;
            return this;
        }

        public PushServiceConfig clearConnectTimeoutMs() {
            this.hasConnectTimeoutMs = false;
            this.connectTimeoutMs_ = 0;
            return this;
        }

        public PushServiceConfig clearEnableDynamicPing() {
            this.hasEnableDynamicPing = false;
            this.enableDynamicPing_ = false;
            return this;
        }

        public PushServiceConfig clearTestHosts() {
            this.testHosts_ = Collections.emptyList();
            return this;
        }

        public PushServiceConfig clearUseBucketV2() {
            this.hasUseBucketV2 = false;
            this.useBucketV2_ = false;
            return this;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public int getCachedSize() {
            if (this.cachedSize < 0) {
                getSerializedSize();
            }
            return this.cachedSize;
        }

        public int getConfigVersion() {
            return this.configVersion_;
        }

        public int getConnectTimeoutMs() {
            return this.connectTimeoutMs_;
        }

        public boolean getEnableDynamicPing() {
            return this.enableDynamicPing_;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public int getSerializedSize() {
            int iComputeUInt32Size = 0;
            if (hasConfigVersion()) {
                iComputeUInt32Size = 0 + CodedOutputStreamMicro.computeUInt32Size(1, getConfigVersion());
            }
            int iComputeBoolSize = iComputeUInt32Size;
            if (hasUseBucketV2()) {
                iComputeBoolSize = iComputeUInt32Size + CodedOutputStreamMicro.computeBoolSize(2, getUseBucketV2());
            }
            int iComputeInt32Size = iComputeBoolSize;
            if (hasConnectTimeoutMs()) {
                iComputeInt32Size = iComputeBoolSize + CodedOutputStreamMicro.computeInt32Size(3, getConnectTimeoutMs());
            }
            int iComputeBoolSize2 = iComputeInt32Size;
            if (hasEnableDynamicPing()) {
                iComputeBoolSize2 = iComputeInt32Size + CodedOutputStreamMicro.computeBoolSize(4, getEnableDynamicPing());
            }
            int iComputeStringSizeNoTag = 0;
            Iterator<String> it = getTestHostsList().iterator();
            while (it.hasNext()) {
                iComputeStringSizeNoTag += CodedOutputStreamMicro.computeStringSizeNoTag(it.next());
            }
            int size = iComputeBoolSize2 + iComputeStringSizeNoTag + (getTestHostsList().size() * 1);
            this.cachedSize = size;
            return size;
        }

        public String getTestHosts(int i) {
            return this.testHosts_.get(i);
        }

        public int getTestHostsCount() {
            return this.testHosts_.size();
        }

        public List<String> getTestHostsList() {
            return this.testHosts_;
        }

        public boolean getUseBucketV2() {
            return this.useBucketV2_;
        }

        public boolean hasConfigVersion() {
            return this.hasConfigVersion;
        }

        public boolean hasConnectTimeoutMs() {
            return this.hasConnectTimeoutMs;
        }

        public boolean hasEnableDynamicPing() {
            return this.hasEnableDynamicPing;
        }

        public boolean hasUseBucketV2() {
            return this.hasUseBucketV2;
        }

        public final boolean isInitialized() {
            return true;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public PushServiceConfig mergeFrom(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
            while (true) {
                int tag = codedInputStreamMicro.readTag();
                switch (tag) {
                    case 0:
                        return this;
                    case 8:
                        setConfigVersion(codedInputStreamMicro.readUInt32());
                        break;
                    case 16:
                        setUseBucketV2(codedInputStreamMicro.readBool());
                        break;
                    case 24:
                        setConnectTimeoutMs(codedInputStreamMicro.readInt32());
                        break;
                    case 32:
                        setEnableDynamicPing(codedInputStreamMicro.readBool());
                        break;
                    case 42:
                        addTestHosts(codedInputStreamMicro.readString());
                        break;
                    default:
                        if (!parseUnknownField(codedInputStreamMicro, tag)) {
                            return this;
                        }
                        break;
                }
            }
        }

        public PushServiceConfig setConfigVersion(int i) {
            this.hasConfigVersion = true;
            this.configVersion_ = i;
            return this;
        }

        public PushServiceConfig setConnectTimeoutMs(int i) {
            this.hasConnectTimeoutMs = true;
            this.connectTimeoutMs_ = i;
            return this;
        }

        public PushServiceConfig setEnableDynamicPing(boolean z) {
            this.hasEnableDynamicPing = true;
            this.enableDynamicPing_ = z;
            return this;
        }

        public PushServiceConfig setTestHosts(int i, String str) {
            if (str == null) {
                throw new NullPointerException();
            }
            this.testHosts_.set(i, str);
            return this;
        }

        public PushServiceConfig setUseBucketV2(boolean z) {
            this.hasUseBucketV2 = true;
            this.useBucketV2_ = z;
            return this;
        }

        @Override // com.google.protobuf.micro.MessageMicro
        public void writeTo(CodedOutputStreamMicro codedOutputStreamMicro) throws IOException {
            if (hasConfigVersion()) {
                codedOutputStreamMicro.writeUInt32(1, getConfigVersion());
            }
            if (hasUseBucketV2()) {
                codedOutputStreamMicro.writeBool(2, getUseBucketV2());
            }
            if (hasConnectTimeoutMs()) {
                codedOutputStreamMicro.writeInt32(3, getConnectTimeoutMs());
            }
            if (hasEnableDynamicPing()) {
                codedOutputStreamMicro.writeBool(4, getEnableDynamicPing());
            }
            Iterator<String> it = getTestHostsList().iterator();
            while (it.hasNext()) {
                codedOutputStreamMicro.writeString(5, it.next());
            }
        }
    }

    private ChannelConfig() {
    }
}
