package com.xiaomi.xms.subscribe_remind;

import android.os.Bundle;
import com.xiaomi.xms.subscribe_remind.IResultCallback;

interface ISubscribeRemind {
    void subscribeRemind(inout Bundle bundle, IResultCallback callback);
    void cancelSubscribeRemind(inout Bundle bundle, IResultCallback callback);
    void deviceInfoReport(inout Bundle bundle, IResultCallback callback);
    void send(inout Bundle bundle, IResultCallback callback);
}
