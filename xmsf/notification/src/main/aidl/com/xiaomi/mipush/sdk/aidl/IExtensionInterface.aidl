package com.xiaomi.mipush.sdk.aidl;

import com.xiaomi.mipush.sdk.aidl.IExtensionCallback;
import com.xiaomi.mipush.sdk.aidl.RemoteNotificationInfo;

oneway interface IExtensionInterface {
    void baseReceiveRemoteNotification(in RemoteNotificationInfo info, IExtensionCallback callback);
    void baseExtensionTimeWillExpire(in RemoteNotificationInfo info, IExtensionCallback callback);
}
