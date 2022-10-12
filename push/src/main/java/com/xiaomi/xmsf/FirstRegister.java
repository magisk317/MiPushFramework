package com.xiaomi.xmsf;

import static com.xiaomi.xmsf.push.control.PushControllerUtils.pushRegistered;

import static top.trumeet.common.Constants.APP_ID;
import static top.trumeet.common.Constants.APP_KEY;

import android.content.Context;

import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.mipush.sdk.MiPushClient;
import com.xiaomi.xmsf.push.control.PushControllerUtils;

import java.util.Objects;

public class FirstRegister implements Runnable {

    final Context context;

    public FirstRegister(Context context) {
        this.context = context;
    }

    @Override
    public void run() {
        Objects.requireNonNull(this.context);
        MiPushClient.registerPush(this.context, APP_ID, APP_KEY);
        if (pushRegistered(this.context)) {
            MyLog.i("register successed");
        } else {
            PushControllerUtils.registerPush(this.context, 0);
        }
        try {
            Thread.sleep(100L);
        } catch (InterruptedException e) {
            MyLog.e("register push interrupted error", e);
        }
    }
}
