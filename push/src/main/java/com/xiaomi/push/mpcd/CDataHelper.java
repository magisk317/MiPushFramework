package com.xiaomi.push.mpcd;

import android.content.Context;
import android.os.Build;
import com.xiaomi.channel.commonutils.android.DataCryptUtils;
import com.xiaomi.channel.commonutils.misc.JobMutualExclusor;
import com.xiaomi.channel.commonutils.string.Base64Coder;
import com.xiaomi.push.service.OnlineConfig;
import com.xiaomi.xmpush.thrift.ConfigKey;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/mpcd/CDataHelper.class */
public class CDataHelper {
    private CDataHelper() {
    }

    public static boolean checkDataCollectionJobMutual(Context context, String str, long j) {
        boolean z = false;
        if (OnlineConfig.getInstance(context).getBooleanValue(ConfigKey.DCJobMutualSwitch.getValue(), false) && ((Build.VERSION.SDK_INT < 29 || context.getApplicationInfo().targetSdkVersion < 29) && !JobMutualExclusor.checkPeriodAndRecordWithFileLock(context, str, j))) {
            z = true;
        }
        return z;
    }

    public static byte[] decryptData(String str, byte[] bArr) {
        byte[] bArrMipushDecrypt;
        byte[] bArrDecode = Base64Coder.decode(str);
        try {
            parseKey(bArrDecode);
            bArrMipushDecrypt = DataCryptUtils.mipushDecrypt(bArrDecode, bArr);
        } catch (Exception e) {
            bArrMipushDecrypt = null;
        }
        return bArrMipushDecrypt;
    }

    public static byte[] encryptData(String str, byte[] bArr) {
        byte[] bArrMipushEncrypt;
        byte[] bArrDecode = Base64Coder.decode(str);
        try {
            parseKey(bArrDecode);
            bArrMipushEncrypt = DataCryptUtils.mipushEncrypt(bArrDecode, bArr);
        } catch (Exception e) {
            bArrMipushEncrypt = null;
        }
        return bArrMipushEncrypt;
    }

    private static void parseKey(byte[] bArr) {
        if (bArr.length >= 2) {
            bArr[0] = (byte) 99;
            bArr[1] = (byte) 100;
        }
    }
}
