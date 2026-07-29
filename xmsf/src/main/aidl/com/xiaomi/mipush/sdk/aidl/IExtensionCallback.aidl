package com.xiaomi.mipush.sdk.aidl;

import com.xiaomi.mipush.sdk.aidl.RemoteNotificationContent;

interface IExtensionCallback {
    void onFinish(in RemoteNotificationContent content);
}
