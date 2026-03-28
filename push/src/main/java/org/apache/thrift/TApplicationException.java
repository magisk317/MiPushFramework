package org.apache.thrift;

import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolUtil;
import org.apache.thrift.protocol.TStruct;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:org/apache/thrift/TApplicationException.class */
public class TApplicationException extends TException {
    public static final int BAD_SEQUENCE_ID = 4;
    public static final int INTERNAL_ERROR = 6;
    public static final int INVALID_MESSAGE_TYPE = 2;
    public static final int MISSING_RESULT = 5;
    public static final int PROTOCOL_ERROR = 7;
    public static final int UNKNOWN = 0;
    public static final int UNKNOWN_METHOD = 1;
    public static final int WRONG_METHOD_NAME = 3;
    private static final long serialVersionUID = 1;
    protected int type_;
    private static final TStruct TAPPLICATION_EXCEPTION_STRUCT = new TStruct("TApplicationException");
    private static final TField MESSAGE_FIELD = new TField("message", (byte) 11, 1);
    private static final TField TYPE_FIELD = new TField("type", (byte) 8, 2);

    public TApplicationException() {
        this.type_ = 0;
    }

    public TApplicationException(int i) {
        this.type_ = 0;
        this.type_ = i;
    }

    public TApplicationException(int i, String str) {
        super(str);
        this.type_ = 0;
        this.type_ = i;
    }

    public TApplicationException(String str) {
        super(str);
        this.type_ = 0;
    }

    public static TApplicationException read(TProtocol tProtocol) throws TException {
        tProtocol.readStructBegin();
        String string = null;
        int i32 = 0;
        while (true) {
            TField fieldBegin = tProtocol.readFieldBegin();
            if (fieldBegin.type == 0) {
                tProtocol.readStructEnd();
                return new TApplicationException(i32, string);
            }
            switch (fieldBegin.id) {
                case 1:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        string = tProtocol.readString();
                    }
                    break;
                case 2:
                    if (fieldBegin.type != 8) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        i32 = tProtocol.readI32();
                    }
                    break;
                default:
                    TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    break;
            }
            tProtocol.readFieldEnd();
        }
    }

    public int getType() {
        return this.type_;
    }

    public void write(TProtocol tProtocol) throws TException {
        tProtocol.writeStructBegin(TAPPLICATION_EXCEPTION_STRUCT);
        if (getMessage() != null) {
            tProtocol.writeFieldBegin(MESSAGE_FIELD);
            tProtocol.writeString(getMessage());
            tProtocol.writeFieldEnd();
        }
        tProtocol.writeFieldBegin(TYPE_FIELD);
        tProtocol.writeI32(this.type_);
        tProtocol.writeFieldEnd();
        tProtocol.writeFieldStop();
        tProtocol.writeStructEnd();
    }
}
