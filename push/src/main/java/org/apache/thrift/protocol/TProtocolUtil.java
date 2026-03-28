package org.apache.thrift.protocol;

import org.apache.thrift.TException;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:org/apache/thrift/protocol/TProtocolUtil.class */
public class TProtocolUtil {
    private static int maxSkipDepth = Integer.MAX_VALUE;

    public static void setMaxSkipDepth(int i) {
        maxSkipDepth = i;
    }

    public static void skip(TProtocol tProtocol, byte b) throws TException {
        skip(tProtocol, b, maxSkipDepth);
    }

    public static void skip(TProtocol tProtocol, byte b, int i) throws TException {
        if (i <= 0) {
            throw new TException("Maximum skip depth exceeded");
        }
        switch (b) {
            case 2:
                tProtocol.readBool();
                return;
            case 3:
                tProtocol.readByte();
                return;
            case 4:
                tProtocol.readDouble();
                return;
            case 5:
            case 7:
            case 9:
            default:
                return;
            case 6:
                tProtocol.readI16();
                return;
            case 8:
                tProtocol.readI32();
                return;
            case 10:
                tProtocol.readI64();
                return;
            case 11:
                tProtocol.readBinary();
                return;
            case 12:
                tProtocol.readStructBegin();
                while (true) {
                    TField fieldBegin = tProtocol.readFieldBegin();
                    if (fieldBegin.type == 0) {
                        tProtocol.readStructEnd();
                        return;
                    } else {
                        skip(tProtocol, fieldBegin.type, i - 1);
                        tProtocol.readFieldEnd();
                    }
                }
            case 13:
                TMap mapBegin = tProtocol.readMapBegin();
                for (int i2 = 0; i2 < mapBegin.size; i2++) {
                    skip(tProtocol, mapBegin.keyType, i - 1);
                    skip(tProtocol, mapBegin.valueType, i - 1);
                }
                tProtocol.readMapEnd();
                return;
            case 14:
                TSet setBegin = tProtocol.readSetBegin();
                for (int i3 = 0; i3 < setBegin.size; i3++) {
                    skip(tProtocol, setBegin.elemType, i - 1);
                }
                tProtocol.readSetEnd();
                return;
            case 15:
                TList listBegin = tProtocol.readListBegin();
                for (int i4 = 0; i4 < listBegin.size; i4++) {
                    skip(tProtocol, listBegin.elemType, i - 1);
                }
                tProtocol.readListEnd();
                return;
        }
    }
}
