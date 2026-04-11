package com.xiaomi.mipush.sdk;

import android.content.Context;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.push.service.OnlineConfig;
import com.xiaomi.xmpush.thrift.ConfigKey;
import java.util.HashMap;
import java.util.Map;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/AssemblePushCollectionsManager.class */
public class AssemblePushCollectionsManager implements AbstractPushManager {
    private static volatile AssemblePushCollectionsManager sInstance;
    private PushConfiguration mConfiguration;
    private Context mContext;
    private boolean oldOCValue = false;
    private Map<AssemblePush, AbstractPushManager> mManagers = new HashMap<>();

    /* JADX INFO: renamed from: com.xiaomi.mipush.sdk.AssemblePushCollectionsManager$2, reason: invalid class name */
    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/AssemblePushCollectionsManager$2.class */
    static /* synthetic */ class AnonymousClass2 {
        static final /* synthetic */ int[] $SwitchMap$com$xiaomi$mipush$sdk$AssemblePush;

        static {
            int[] iArr = new int[AssemblePush.values().length];
            $SwitchMap$com$xiaomi$mipush$sdk$AssemblePush = iArr;
            try {
                iArr[AssemblePush.ASSEMBLE_PUSH_HUAWEI.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$xiaomi$mipush$sdk$AssemblePush[AssemblePush.ASSEMBLE_PUSH_FCM.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$xiaomi$mipush$sdk$AssemblePush[AssemblePush.ASSEMBLE_PUSH_COS.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$xiaomi$mipush$sdk$AssemblePush[AssemblePush.ASSEMBLE_PUSH_FTOS.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
        }
    }

    private AssemblePushCollectionsManager(Context context) {
        this.mContext = context.getApplicationContext();
    }

    public static AssemblePushCollectionsManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (AssemblePushCollectionsManager.class) {
                try {
                    if (sInstance == null) {
                        sInstance = new AssemblePushCollectionsManager(context);
                    }
                } catch (Throwable th) {
                    throw th;
                }
            }
        }
        return sInstance;
    }

    private void initAssemblePushManager() {
        AbstractPushManager manager;
        AbstractPushManager manager2;
        AbstractPushManager manager3;
        AbstractPushManager manager4;
        PushConfiguration pushConfiguration = this.mConfiguration;
        if (pushConfiguration != null) {
            if (pushConfiguration.getOpenHmsPush()) {
                StringBuilder sb = new StringBuilder();
                sb.append("ASSEMBLE_PUSH : ");
                sb.append(" HW user switch : " + this.mConfiguration.getOpenHmsPush() + " HW online switch : " + AssemblePushHelper.isOpenAssemblePushOnlineSwitch(this.mContext, AssemblePush.ASSEMBLE_PUSH_HUAWEI) + " HW isSupport : " + PhoneBrand.HUAWEI.equals(AssemblePushUtils.getPhoneBrand(this.mContext)));
                MyLog.w(sb.toString());
            }
            if (this.mConfiguration.getOpenHmsPush() && AssemblePushHelper.isOpenAssemblePushOnlineSwitch(this.mContext, AssemblePush.ASSEMBLE_PUSH_HUAWEI) && PhoneBrand.HUAWEI.equals(AssemblePushUtils.getPhoneBrand(this.mContext))) {
                if (!contain(AssemblePush.ASSEMBLE_PUSH_HUAWEI)) {
                    addManager(AssemblePush.ASSEMBLE_PUSH_HUAWEI, PushManagerFactory.getManager(this.mContext, AssemblePush.ASSEMBLE_PUSH_HUAWEI));
                }
                MyLog.v("hw manager add to list");
            } else if (contain(AssemblePush.ASSEMBLE_PUSH_HUAWEI) && (manager = getManager(AssemblePush.ASSEMBLE_PUSH_HUAWEI)) != null) {
                removeManager(AssemblePush.ASSEMBLE_PUSH_HUAWEI);
                manager.unregister();
            }
            if (this.mConfiguration.getOpenFCMPush()) {
                StringBuilder sb2 = new StringBuilder();
                sb2.append("ASSEMBLE_PUSH : ");
                sb2.append(" FCM user switch : " + this.mConfiguration.getOpenFCMPush() + " FCM online switch : " + AssemblePushHelper.isOpenAssemblePushOnlineSwitch(this.mContext, AssemblePush.ASSEMBLE_PUSH_FCM) + " FCM isSupport : " + AssemblePushUtils.isGoogleServiceSatisfied(this.mContext));
                MyLog.w(sb2.toString());
            }
            if (this.mConfiguration.getOpenFCMPush() && AssemblePushHelper.isOpenAssemblePushOnlineSwitch(this.mContext, AssemblePush.ASSEMBLE_PUSH_FCM) && AssemblePushUtils.isGoogleServiceSatisfied(this.mContext)) {
                if (!contain(AssemblePush.ASSEMBLE_PUSH_FCM)) {
                    addManager(AssemblePush.ASSEMBLE_PUSH_FCM, PushManagerFactory.getManager(this.mContext, AssemblePush.ASSEMBLE_PUSH_FCM));
                }
                MyLog.v("fcm manager add to list");
            } else if (contain(AssemblePush.ASSEMBLE_PUSH_FCM) && (manager2 = getManager(AssemblePush.ASSEMBLE_PUSH_FCM)) != null) {
                removeManager(AssemblePush.ASSEMBLE_PUSH_FCM);
                manager2.unregister();
            }
            if (this.mConfiguration.getOpenCOSPush()) {
                StringBuilder sb3 = new StringBuilder();
                sb3.append("ASSEMBLE_PUSH : ");
                sb3.append(" COS user switch : " + this.mConfiguration.getOpenCOSPush() + " COS online switch : " + AssemblePushHelper.isOpenAssemblePushOnlineSwitch(this.mContext, AssemblePush.ASSEMBLE_PUSH_COS) + " COS isSupport : " + AssemblePushUtils.isColorOSPushSupport(this.mContext));
                MyLog.w(sb3.toString());
            }
            if (this.mConfiguration.getOpenCOSPush() && AssemblePushHelper.isOpenAssemblePushOnlineSwitch(this.mContext, AssemblePush.ASSEMBLE_PUSH_COS) && AssemblePushUtils.isColorOSPushSupport(this.mContext)) {
                addManager(AssemblePush.ASSEMBLE_PUSH_COS, PushManagerFactory.getManager(this.mContext, AssemblePush.ASSEMBLE_PUSH_COS));
            } else if (contain(AssemblePush.ASSEMBLE_PUSH_COS) && (manager3 = getManager(AssemblePush.ASSEMBLE_PUSH_COS)) != null) {
                removeManager(AssemblePush.ASSEMBLE_PUSH_COS);
                manager3.unregister();
            }
            if (this.mConfiguration.getOpenFTOSPush() && AssemblePushHelper.isOpenAssemblePushOnlineSwitch(this.mContext, AssemblePush.ASSEMBLE_PUSH_FTOS) && AssemblePushUtils.isFunTouchOSPushSupport(this.mContext)) {
                addManager(AssemblePush.ASSEMBLE_PUSH_FTOS, PushManagerFactory.getManager(this.mContext, AssemblePush.ASSEMBLE_PUSH_FTOS));
            } else {
                if (!contain(AssemblePush.ASSEMBLE_PUSH_FTOS) || (manager4 = getManager(AssemblePush.ASSEMBLE_PUSH_FTOS)) == null) {
                    return;
                }
                removeManager(AssemblePush.ASSEMBLE_PUSH_FTOS);
                manager4.unregister();
            }
        }
    }

    public void addManager(AssemblePush assemblePush, AbstractPushManager abstractPushManager) {
        if (abstractPushManager != null) {
            if (this.mManagers.containsKey(assemblePush)) {
                this.mManagers.remove(assemblePush);
            }
            this.mManagers.put(assemblePush, abstractPushManager);
        }
    }

    public boolean contain(AssemblePush assemblePush) {
        return this.mManagers.containsKey(assemblePush);
    }

    public AbstractPushManager getManager(AssemblePush assemblePush) {
        return this.mManagers.get(assemblePush);
    }

    public boolean getUserSwitch(AssemblePush assemblePush) {
        boolean openHmsPush = false;
        switch (AnonymousClass2.$SwitchMap$com$xiaomi$mipush$sdk$AssemblePush[assemblePush.ordinal()]) {
            case 1:
                PushConfiguration pushConfiguration = this.mConfiguration;
                openHmsPush = false;
                if (pushConfiguration != null) {
                    openHmsPush = pushConfiguration.getOpenHmsPush();
                }
                break;
            case 2:
                PushConfiguration pushConfiguration2 = this.mConfiguration;
                openHmsPush = false;
                if (pushConfiguration2 != null) {
                    openHmsPush = pushConfiguration2.getOpenFCMPush();
                }
                break;
            case 3:
                PushConfiguration pushConfiguration3 = this.mConfiguration;
                openHmsPush = false;
                if (pushConfiguration3 != null) {
                    openHmsPush = pushConfiguration3.getOpenCOSPush();
                    break;
                }
            case 4:
                PushConfiguration pushConfiguration4 = this.mConfiguration;
                if (pushConfiguration4 != null) {
                    openHmsPush = pushConfiguration4.getOpenFTOSPush();
                }
                break;
            default:
                openHmsPush = false;
                break;
        }
        return openHmsPush;
    }

    @Override // com.xiaomi.mipush.sdk.AbstractPushManager
    public void register() {
        MyLog.w("ASSEMBLE_PUSH : assemble push register");
        if (this.mManagers.size() <= 0) {
            initAssemblePushManager();
        }
        if (this.mManagers.size() > 0) {
            for (AbstractPushManager abstractPushManager : this.mManagers.values()) {
                if (abstractPushManager != null) {
                    abstractPushManager.register();
                }
            }
            AssemblePushHelper.checkAssemblePushStatus(this.mContext);
        }
    }

    public void removeManager(AssemblePush assemblePush) {
        this.mManagers.remove(assemblePush);
    }

    public void setConfiguration(PushConfiguration pushConfiguration) {
        this.mConfiguration = pushConfiguration;
        this.oldOCValue = OnlineConfig.getInstance(this.mContext).getBooleanValue(ConfigKey.AggregatePushSwitch.getValue(), true);
        if (this.mConfiguration.getOpenHmsPush() || this.mConfiguration.getOpenFCMPush() || this.mConfiguration.getOpenCOSPush()) {
            OnlineConfig.getInstance(this.mContext).addOCUpdateCallbacks(new OnlineConfig.OCUpdateCallback(101, "assemblePush") { // from class: com.xiaomi.mipush.sdk.AssemblePushCollectionsManager.1
                @Override // com.xiaomi.push.service.OnlineConfig.OCUpdateCallback
                protected void onCallback() {
                    boolean booleanValue = OnlineConfig.getInstance(AssemblePushCollectionsManager.this.mContext).getBooleanValue(ConfigKey.AggregatePushSwitch.getValue(), true);
                    if (AssemblePushCollectionsManager.this.oldOCValue != booleanValue) {
                        AssemblePushCollectionsManager.this.oldOCValue = booleanValue;
                        AssemblePushHelper.registerAssemblePush(AssemblePushCollectionsManager.this.mContext);
                    }
                }
            });
        }
    }

    @Override // com.xiaomi.mipush.sdk.AbstractPushManager
    public void unregister() {
        MyLog.w("ASSEMBLE_PUSH : assemble push unregister");
        for (AbstractPushManager abstractPushManager : this.mManagers.values()) {
            if (abstractPushManager != null) {
                abstractPushManager.unregister();
            }
        }
        this.mManagers.clear();
    }
}
