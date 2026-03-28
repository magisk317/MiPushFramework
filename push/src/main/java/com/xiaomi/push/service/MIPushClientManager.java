package com.xiaomi.push.service;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.util.Pair;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.smack.XMPPException;
import com.xiaomi.xmsf.runtime.PendingPacketErrorNotifier;
import com.xiaomi.xmsf.runtime.PendingPacketSender;
import com.xiaomi.xmsf.runtime.PushRuntimePendingPacketStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/MIPushClientManager.class */
public class MIPushClientManager {
    private static final int MAX_PENDING_MESSAGE = 50;
    private static final Map<String, byte[]> pendingRegisterationRequests = new HashMap();
    private static ArrayList<Pair<String, byte[]>> pendingMessages = new ArrayList<>();

    public static void addPendingMessages(String str, byte[] bArr) {
        PushRuntimePendingPacketStore.addPendingMessage(str, bArr);
    }

    public static void notifyError(Context context, String str, byte[] bArr, int i, String str2) {
        Intent intent = new Intent(PushConstants.MIPUSH_ACTION_ERROR);
        intent.setPackage(str);
        intent.putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, bArr);
        intent.putExtra(PushConstants.MIPUSH_EXTRA_ERROR_CODE, i);
        intent.putExtra(PushConstants.MIPUSH_EXTRA_ERROR_MSG, str2);
        context.sendBroadcast(intent, MIPushHelper.getReceiverPermission(str));
    }

    public static void notifyRegisterError(Context context, int i, String str) {
        PushRuntimePendingPacketStore.notifyRegisterError(i, str, new PendingPacketErrorNotifier() { // from class: com.xiaomi.push.service.MIPushClientManager.1
            @Override // com.xiaomi.xmsf.runtime.PendingPacketErrorNotifier
            public void notify(String str2, byte[] bArr, int i2, String str3) {
                MIPushClientManager.notifyError(context, str2, bArr, i2, str3);
            }
        });
    }

    public static void processPendingMessages(XMPushService xMPushService) {
        try {
            final boolean z = Thread.currentThread() == Looper.getMainLooper().getThread();
            PushRuntimePendingPacketStore.processPendingMessages("MIPushClientManager.processPendingMessages", new PendingPacketSender() { // from class: com.xiaomi.push.service.MIPushClientManager.2
                @Override // com.xiaomi.xmsf.runtime.PendingPacketSender
                public void send(String str, byte[] bArr) {
                    try {
                        MIPushHelper.sendPacket(xMPushService, str, bArr);
                        if (!z) {
                            try {
                                Thread.sleep(100L);
                            } catch (InterruptedException e) {
                            }
                        }
                    } catch (XMPPException e2) {
                        throw new RuntimeException(e2);
                    }
                }
            });
        } catch (RuntimeException e3) {
            Throwable cause = e3.getCause();
            if (cause instanceof XMPPException) {
                MyLog.e("meet error when process pending message. " + cause);
                xMPushService.disconnect(10, (XMPPException) cause);
                return;
            }
            throw e3;
        }
    }

    public static void processPendingRegistrationRequest(XMPushService xMPushService) {
        try {
            PushRuntimePendingPacketStore.processPendingRegistrationRequests("MIPushClientManager.processPendingRegistrationRequest", new PendingPacketSender() { // from class: com.xiaomi.push.service.MIPushClientManager.3
                @Override // com.xiaomi.xmsf.runtime.PendingPacketSender
                public void send(String str, byte[] bArr) {
                    try {
                        MIPushHelper.sendPacket(xMPushService, str, bArr);
                    } catch (XMPPException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        } catch (RuntimeException e2) {
            Throwable cause = e2.getCause();
            if (cause instanceof XMPPException) {
                MyLog.e("fail to deal with pending register request. " + cause);
                xMPushService.disconnect(10, (XMPPException) cause);
                return;
            }
            throw e2;
        }
    }

    public static void registerApp(String str, byte[] bArr) {
        PushRuntimePendingPacketStore.cacheRegistrationRequest(str, bArr);
    }
}
