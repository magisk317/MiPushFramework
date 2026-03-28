package com.xiaomi.push.service.awake.module;

import android.content.Context;
import android.content.Intent;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/awake/module/IAwakeModule.class */
public interface IAwakeModule {
    void doAwake(Context context, AwakeInfo awakeInfo);

    void doSendAwakeResult(Context context, Intent intent, String str);
}
