package com.xiaomi.xmpush.thrift;

import android.content.Context;
import com.xiaomi.channel.commonutils.android.AppInfoUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.misc.MobileStatusUtils;
import org.apache.thrift.TBase;
import org.apache.thrift.TDeserializer;
import org.apache.thrift.TException;
import org.apache.thrift.TSerializer;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.XmPushTBinaryProtocol;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/XmPushThriftSerializeUtils.class */
public class XmPushThriftSerializeUtils {
    public static final int MASK_CHARGING = 4;
    public static final int MASK_GEO_PASS = 1;
    public static final int MASK_GEO_RECEIVE = 4;
    public static final int MASK_GEO_SHOW = 2;
    public static final int MASK_SCREEN_LOCKED = 8;
    public static final int MASK_TYPE_SHIELD = 16;

    public static <T extends TBase<T, ?>> void convertByteArrayToThriftObject(T t, byte[] bArr) throws TException {
        if (bArr == null) {
            throw new TException("the message byte is empty.");
        }
        new TDeserializer(new XmPushTBinaryProtocol.Factory(true, true, bArr.length)).deserialize(t, bArr);
    }

    public static <T extends TBase<T, ?>> byte[] convertThriftObjectToBytes(T t) {
        if (t == null) {
            return null;
        }
        try {
            return new TSerializer(new TBinaryProtocol.Factory()).serialize(t);
        } catch (TException e) {
            MyLog.e("convertThriftObjectToBytes catch TException.", e);
            return null;
        }
    }

    public static short getDeviceStatus(Context context, XmPushActionContainer xmPushActionContainer) {
        int i = 0;
        int value = AppInfoUtils.getAppNotificationOp(context, xmPushActionContainer.packageName, false).getValue();
        int i2 = MobileStatusUtils.isCharging(context) ? 4 : 0;
        if (MobileStatusUtils.isScreenLocked(context)) {
            i = 8;
        }
        return (short) (0 + value + i2 + i);
    }

    public static short getGeoMsgStatus(boolean z, boolean z2, boolean z3) {
        int i = 0;
        int i2 = z ? 4 : 0;
        if (z2) {
            i = 2;
        }
        return (short) (0 + i2 + i + (z3 ? 1 : 0));
    }
}
