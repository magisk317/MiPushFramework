package com.xiaomi.push.service;

import android.util.Pair;
import com.xiaomi.channel.commonutils.misc.CollectionUtils;
import com.xiaomi.xmpush.thrift.ConfigListType;
import com.xiaomi.xmpush.thrift.ConfigType;
import com.xiaomi.xmpush.thrift.NormalConfig;
import com.xiaomi.xmpush.thrift.OnlineConfigItem;
import com.xiaomi.xmpush.thrift.XmPushActionCustomConfig;
import com.xiaomi.xmpush.thrift.XmPushActionNormalConfig;
import java.util.ArrayList;
import java.util.List;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/OnlineConfigHelper.class */
public class OnlineConfigHelper {
    private static final String OC_VERSION_PREFIX = "oc_version_";

    /* JADX INFO: renamed from: com.xiaomi.push.service.OnlineConfigHelper$1, reason: invalid class name */
    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/OnlineConfigHelper$1.class */
    static /* synthetic */ class AnonymousClass1 {
        static final /* synthetic */ int[] $SwitchMap$com$xiaomi$xmpush$thrift$ConfigListType;
        static final /* synthetic */ int[] $SwitchMap$com$xiaomi$xmpush$thrift$ConfigType;

        static {
            int[] iArr = new int[ConfigType.values().length];
            $SwitchMap$com$xiaomi$xmpush$thrift$ConfigType = iArr;
            try {
                iArr[ConfigType.INT.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$xiaomi$xmpush$thrift$ConfigType[ConfigType.LONG.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$xiaomi$xmpush$thrift$ConfigType[ConfigType.STRING.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$xiaomi$xmpush$thrift$ConfigType[ConfigType.BOOLEAN.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            int[] iArr2 = new int[ConfigListType.values().length];
            $SwitchMap$com$xiaomi$xmpush$thrift$ConfigListType = iArr2;
            try {
                iArr2[ConfigListType.MISC_CONFIG.ordinal()] = 1;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$xiaomi$xmpush$thrift$ConfigListType[ConfigListType.PLUGIN_CONFIG.ordinal()] = 2;
            } catch (NoSuchFieldError e6) {
            }
        }
    }

    private OnlineConfigHelper() {
    }

    private static List<Pair<Integer, Object>> convertMessage(List<OnlineConfigItem> list, boolean z) {
        if (CollectionUtils.isEmpty(list)) {
            return null;
        }
        ArrayList arrayList = new ArrayList();
        for (OnlineConfigItem onlineConfigItem : list) {
            Pair pair = null;
            int key = onlineConfigItem.getKey();
            ConfigType configTypeFindByValue = ConfigType.findByValue(onlineConfigItem.getType());
            if (configTypeFindByValue != null) {
                if (z && onlineConfigItem.clear) {
                    arrayList.add(new Pair(Integer.valueOf(key), null));
                } else {
                    switch (AnonymousClass1.$SwitchMap$com$xiaomi$xmpush$thrift$ConfigType[configTypeFindByValue.ordinal()]) {
                        case 1:
                            pair = new Pair(Integer.valueOf(key), Integer.valueOf(onlineConfigItem.getIntValue()));
                            break;
                        case 2:
                            pair = new Pair(Integer.valueOf(key), Long.valueOf(onlineConfigItem.getLongValue()));
                            break;
                        case 3:
                            pair = new Pair(Integer.valueOf(key), onlineConfigItem.getStringValue());
                            break;
                        case 4:
                            pair = new Pair(Integer.valueOf(key), Boolean.valueOf(onlineConfigItem.isBoolValue()));
                            break;
                    }
                    arrayList.add(pair);
                }
            }
        }
        return arrayList;
    }

    public static int getVersion(OnlineConfig onlineConfig, ConfigListType configListType) {
        String versionKey = getVersionKey(configListType);
        int i = 0;
        switch (AnonymousClass1.$SwitchMap$com$xiaomi$xmpush$thrift$ConfigListType[configListType.ordinal()]) {
            case 1:
                i = 1;
                break;
            case 2:
                i = 0;
                break;
        }
        return onlineConfig.preferences.getInt(versionKey, i);
    }

    private static String getVersionKey(ConfigListType configListType) {
        return OC_VERSION_PREFIX + configListType.getValue();
    }

    public static void setVersion(OnlineConfig onlineConfig, ConfigListType configListType, int i) {
        onlineConfig.preferences.edit().putInt(getVersionKey(configListType), i).commit();
    }

    public static void updateCustomConfigs(OnlineConfig onlineConfig, XmPushActionCustomConfig xmPushActionCustomConfig) {
        onlineConfig.updateCustomConfigs(convertMessage(xmPushActionCustomConfig.getCustomConfigs(), true));
        onlineConfig.runCallback();
    }

    public static void updateNormalConfigs(OnlineConfig onlineConfig, XmPushActionNormalConfig xmPushActionNormalConfig) {
        for (NormalConfig normalConfig : xmPushActionNormalConfig.getNormalConfigs()) {
            if (normalConfig.getVersion() > getVersion(onlineConfig, normalConfig.getType())) {
                setVersion(onlineConfig, normalConfig.getType(), normalConfig.getVersion());
                onlineConfig.updateNormalConfigs(convertMessage(normalConfig.configItems, false));
            }
        }
        onlineConfig.runCallback();
    }
}
