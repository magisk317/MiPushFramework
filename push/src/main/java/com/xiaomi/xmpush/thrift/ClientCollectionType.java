package com.xiaomi.xmpush.thrift;

import com.xiaomi.push.service.PushConstants;
import org.apache.thrift.TEnum;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/ClientCollectionType.class */
public enum ClientCollectionType implements TEnum {
    DeviceInfo(1),
    AppInstallList(2),
    AppActiveList(3),
    Bluetooth(4),
    Location(5),
    Account(6),
    WIFI(7),
    Cellular(8),
    TopApp(9),
    BroadcastAction(10),
    BroadcastActionAdded(11),
    BroadcastActionRemoved(12),
    BroadcastActionReplaced(13),
    BroadcastActionDataCleared(14),
    BroadcastActionRestarted(15),
    BroadcastActionChanged(16),
    AppPermission(17),
    WifiDevicesMac(18),
    ActivityActiveTimeStamp(19),
    DeviceBaseInfo(20),
    DeviceInfoV2(21),
    Battery(22),
    Storage(23),
    AppIsInstalled(24);

    private final int value;

    ClientCollectionType(int i) {
        this.value = i;
    }

    public static ClientCollectionType findByValue(int i) {
        switch (i) {
            case 1:
                return DeviceInfo;
            case 2:
                return AppInstallList;
            case 3:
                return AppActiveList;
            case 4:
                return Bluetooth;
            case 5:
                return Location;
            case 6:
                return Account;
            case 7:
                return WIFI;
            case 8:
                return Cellular;
            case 9:
                return TopApp;
            case 10:
                return BroadcastAction;
            case 11:
                return BroadcastActionAdded;
            case 12:
                return BroadcastActionRemoved;
            case 13:
                return BroadcastActionReplaced;
            case 14:
                return BroadcastActionDataCleared;
            case 15:
                return BroadcastActionRestarted;
            case 16:
                return BroadcastActionChanged;
            case 17:
                return AppPermission;
            case 18:
                return WifiDevicesMac;
            case 19:
                return ActivityActiveTimeStamp;
            case PushConstants.ERROR_REDIRECT /* 20 */:
                return DeviceBaseInfo;
            case PushConstants.ERROR_BIND_TIMEOUT /* 21 */:
                return DeviceInfoV2;
            case PushConstants.ERROR_PING_TIMEOUT /* 22 */:
                return Battery;
            case PushConstants.ERROR_IN_EXTREME_POWER_MODE /* 23 */:
                return Storage;
            case 24:
                return AppIsInstalled;
            default:
                return null;
        }
    }

    @Override // org.apache.thrift.TEnum
    public int getValue() {
        return this.value;
    }
}
