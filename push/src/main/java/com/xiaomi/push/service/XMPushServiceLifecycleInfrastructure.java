package com.xiaomi.push.service;

import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Messenger;
import android.os.Process;
import android.provider.Settings;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.push.protobuf.ChannelMessage;
import com.xiaomi.smack.ConnectionConfiguration;

final class XMPushServiceLifecycleInfrastructure {
    private static final String EXTREME_POWER_MODE = "EXTREME_POWER_MODE_ENABLE";
    private static final String SUPER_POWER_MODE = "power_supersave_mode_open";

    private final XMPushService service;

    XMPushServiceLifecycleInfrastructure(XMPushService xMPushService) {
        this.service = xMPushService;
    }

    ConnectionConfiguration createConnectionConfiguration() {
        return new ConnectionConfiguration(null, PushServiceConstants.XMPP_SERVER_PORT, "xiaomi.com", null) { // from class: com.xiaomi.push.service.XMPushServiceLifecycleInfrastructure.1
            @Override
            public byte[] getConnectionBlob() {
                try {
                    ChannelMessage.PushServiceConfigMsg pushServiceConfigMsg = new ChannelMessage.PushServiceConfigMsg();
                    pushServiceConfigMsg.setClientVersion(ServiceConfig.getInstance().getConfigVersion());
                    return pushServiceConfigMsg.toByteArray();
                } catch (Exception e) {
                    MyLog.w("getOBBString err: " + e.toString());
                    return null;
                }
            }
        };
    }

    void installMessenger() {
        this.service.setServiceMessenger(new Messenger(new Handler() { // from class: com.xiaomi.push.service.XMPushServiceLifecycleInfrastructure.2
            @Override
            public void handleMessage(android.os.Message message) {
                super.handleMessage(message);
                if (message != null) {
                    try {
                        switch (message.what) {
                            case 17:
                                if (message.obj != null) {
                                    XMPushServiceLifecycleInfrastructure.this.service.onStart((Intent) message.obj, 1);
                                }
                                break;
                            case 18:
                                android.os.Message messageObtain = android.os.Message.obtain((Handler) null, 0);
                                messageObtain.what = 18;
                                Bundle bundle = new Bundle();
                                bundle.putString(PushConstants.MESSAGE_KEY_XMSF_REGION, XMPushServiceLifecycleInfrastructure.this.service.getRegionName());
                                messageObtain.setData(bundle);
                                message.replyTo.send(messageObtain);
                                break;
                        }
                    } catch (Throwable unused) {
                    }
                }
            }
        }));
    }

    void installPowerModeObservers() {
        Uri uriFor = Settings.Secure.getUriFor(EXTREME_POWER_MODE);
        if (uriFor != null) {
            ContentObserver contentObserver = new ContentObserver(new Handler(Looper.getMainLooper())) { // from class: com.xiaomi.push.service.XMPushServiceLifecycleInfrastructure.3
                @Override
                public void onChange(boolean z) {
                    super.onChange(z);
                    boolean isExtremePowerSaveMode = XMPushServiceLifecycleInfrastructure.this.service.isExtremePowerSaveMode();
                    MyLog.w("ExtremePowerMode:" + isExtremePowerSaveMode);
                    if (!isExtremePowerSaveMode) {
                        XMPushServiceLifecycleInfrastructure.this.service.scheduleConnect(true);
                    } else {
                        XMPushServiceLifecycleInfrastructure.this.service.executeJob(new DisconnectJob(XMPushServiceLifecycleInfrastructure.this.service, 23, null));
                    }
                }
            };
            this.service.setExtremePowerModeObserver(contentObserver);
            try {
                this.service.getContentResolver().registerContentObserver(uriFor, false, contentObserver);
            } catch (Throwable th) {
                MyLog.w("register observer err:" + th.getMessage());
            }
        }
        Uri uriFor2 = Settings.System.getUriFor(SUPER_POWER_MODE);
        if (uriFor2 != null) {
            ContentObserver contentObserver2 = new ContentObserver(new Handler(Looper.getMainLooper())) { // from class: com.xiaomi.push.service.XMPushServiceLifecycleInfrastructure.4
                @Override
                public void onChange(boolean z) {
                    super.onChange(z);
                    boolean isSuperPowerModeEnable = XMPushServiceLifecycleInfrastructure.this.service.isSuperPowerModeEnable();
                    MyLog.w("SuperPowerMode:" + isSuperPowerModeEnable);
                    XMPushServiceLifecycleInfrastructure.this.service.updateAlarmTimer();
                    if (!isSuperPowerModeEnable) {
                        XMPushServiceLifecycleInfrastructure.this.service.scheduleConnect(true);
                    } else {
                        XMPushServiceLifecycleInfrastructure.this.service.executeJob(new DisconnectJob(XMPushServiceLifecycleInfrastructure.this.service, 24, null));
                    }
                }
            };
            this.service.setSuperPowerModeObserver(contentObserver2);
            try {
                this.service.getContentResolver().registerContentObserver(uriFor2, false, contentObserver2);
            } catch (Throwable th2) {
                MyLog.e("register super-power-mode observer err:" + th2.getMessage());
            }
        }
    }

    void installFalldownReceiver() {
        int[] falldownTimeRange = this.service.getFalldownTimeRange();
        if (falldownTimeRange == null) {
            return;
        }
        ScreenStateReceiver screenStateReceiver = new ScreenStateReceiver(this.service);
        this.service.setScreenStateReceiver(screenStateReceiver);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.SCREEN_ON");
        intentFilter.addAction("android.intent.action.SCREEN_OFF");
        this.service.registerReceiver(screenStateReceiver, intentFilter);
        this.service.setFalldownWindow(falldownTimeRange[0], falldownTimeRange[1]);
        MyLog.w("falldown initialized: " + falldownTimeRange[0] + "," + falldownTimeRange[1]);
    }

    void unregisterPowerModeObservers() {
        if (PushConstants.PUSH_SERVICE_PACKAGE_NAME.equals(this.service.getPackageName()) && this.service.getExtremePowerModeObserver() != null) {
            try {
                this.service.getContentResolver().unregisterContentObserver(this.service.getExtremePowerModeObserver());
            } catch (Throwable th) {
                MyLog.w("unregister observer err:" + th.getMessage());
            }
            this.service.setExtremePowerModeObserver(null);
        }
        if (PushConstants.PUSH_SERVICE_PACKAGE_NAME.equals(this.service.getPackageName()) && this.service.getSuperPowerModeObserver() != null) {
            try {
                this.service.getContentResolver().unregisterContentObserver(this.service.getSuperPowerModeObserver());
            } catch (Throwable th2) {
                MyLog.e("unregister super-power-mode err:" + th2.getMessage());
            }
            this.service.setSuperPowerModeObserver(null);
        }
    }

    void persistCreationLog(MIPushAccount mIPushAccount) {
        String str = "";
        if (mIPushAccount != null) {
            try {
                if (!TextUtils.isEmpty(mIPushAccount.account)) {
                    String[] strArrSplit = mIPushAccount.account.split("@");
                    if (strArrSplit != null && strArrSplit.length > 0) {
                        str = strArrSplit[0];
                    }
                }
            } catch (Exception unused) {
                str = "";
            }
        }
        MyLog.persist("XMPushService created. pid=" + Process.myPid() + ", uid=" + Process.myUid() + ", uuid=" + str);
    }
}
