package com.xiaomi.xmpush.thrift;

import org.apache.thrift.TEnum;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/ConfigKey.class */
public enum ConfigKey implements TEnum {
    UploadSwitch(1),
    UploadFrequency(2),
    ScreenSizeCollectionSwitch(3),
    MacCollectionSwitch(4),
    IMSICollectionSwitch(5),
    AndroidVnCollectionSwitch(6),
    AndroidVcCollectionSwitch(7),
    AndroidIdCollectionSwitch(8),
    DeviceInfoCollectionFrequency(9),
    AppInstallListCollectionSwitch(10),
    AppInstallListCollectionFrequency(11),
    AppActiveListCollectionSwitch(12),
    AppActiveListCollectionFrequency(13),
    BluetoothCollectionSwitch(14),
    BluetoothCollectionFrequency(15),
    LocationCollectionSwitch(16),
    LocationCollectionFrequency(17),
    AccountCollectionSwitch(18),
    AccountCollectionFrequency(19),
    WifiCollectionSwitch(20),
    WifiCollectionFrequency(21),
    CellularCollectionSwitch(22),
    CellularCollectionFrequency(23),
    TopAppCollectionSwitch(24),
    TopAppCollectionFrequency(25),
    DataCollectionSwitch(26),
    OcVersionCheckFrequency(27),
    SyncInfoFrequency(28),
    UploadNotificationInfoFrequency(29),
    UploadNotificationInfoMaxNum(30),
    CollectionNotificationInfoBaseSwitch(31),
    CollectionNotificationInfoAppSwitch(32),
    CollectionNotificationInfoRemovedSwitch(33),
    ForegroundServiceSwitch(34),
    SyncMIIDFrequency(35),
    Upload4GSwitch(36),
    Upload4GFrequency(37),
    Upload3GSwitch(38),
    Upload3GFrequency(39),
    ShieldTypeConfig(40),
    UploadWIFIGeoLocFrequency(41),
    UploadNOWIFIGeoLocFrequency(42),
    BroadcastActionCollectionSwitch(43),
    BroadcastActionCollectionFrequency(44),
    UploadGeoLocSwitch(45),
    ServiceBootMode(46),
    AppPermissionCollectionSwitch(47),
    AppPermissionCollectionFrequency(48),
    WifiDevicesMacCollectionSwitch(49),
    WifiDevicesMacCollectionFrequency(50),
    WifiDevicesMacWifiUnchangedCollectionFrequency(51),
    AggregationSdkMonitorSwitch(52),
    AggregationSdkMonitorFrequency(53),
    AggregationSdkMonitorDepth(54),
    UploadGeoAppLocSwitch(55),
    ThirdPushControlSwitch(56),
    ThirdPushComponentKeyWords(57),
    ThirdPushWhiteList(58),
    XmsfScanWhitelist(59),
    IccidCollectionSwitch(60),
    LimitThridPushStrategyMode(61),
    GlobalPushChannelException(62),
    TinyDataUploadSwitch(63),
    TinyDataUploadFrequency(64),
    GlobalRegionIOSwitch(65),
    GlobalRegionIOWait(66),
    AggregatePushSwitch(67),
    ActivityTSSwitch(68),
    OperatorSwitch(69),
    DeviceIdSwitch(70),
    DeviceBaseInfoCollectionFrequency(71),
    UsageStatsCollectionFrequency(72),
    UsageStatsCollectionWhiteList(73),
    ForceHandleCrashSwitch(74),
    Crash4GUploadSwitch(75),
    Crash4GUploadFrequency(76),
    CrashWIFIUploadFrequency(77),
    EventUploadSwitch(78),
    PerfUploadSwitch(79),
    EventUploadFrequency(80),
    PerfUploadFrequency(81),
    BatteryCollectionSwitch(82),
    BatteryCollectionFrequency(83),
    AwakeInfoUploadWaySwitch(84),
    AwakeAppPingSwitch(85),
    AwakeAppPingFrequency(86),
    StorageCollectionSwitch(87),
    StorageCollectionFrequency(88),
    PopupDialogWhiteList(94),
    PopupDialogContent(95),
    PopupDialogSwitch(96),
    FallDownTimeRange(97),
    AppIsInstalledCollectionSwitch(98),
    AppIsInstalledCollectionFrequency(99),
    AppIsInstalledList(100),
    TopNotificationUpdateFrequency(101),
    TopNotificationUpdatePeriod(102),
    TopNotificationUpdateSwitch(103),
    EventUploadNewSwitch(104),
    ScreenOnOrChargingTinyDataUploadSwitch(105),
    NotificationAutoGroupSwitch(106),
    LatestNotificationNotIntoGroupSwitch(107),
    DCJobMutualSwitch(108),
    NotificationBelongToAppSwitch(109),
    DCJobUploadRepeatedInterval(110),
    LauncherAppListCollectionSwitch(111),
    LauncherAppListCollectionFrequency(112),
    StatDataUploadFrequency(120),
    StatDataUploadNum(121),
    StatDataProcessFrequency(122),
    StatDataSwitch(123),
    StatDataUploadWay(124),
    StatDataDeleteFrequency(125),
    CollectionDataPluginVersion(1001),
    CollectionPluginDownloadUrl(1002),
    CollectionPluginMd5(1003),
    CollectionPluginForceStop(1004);

    private final int value;

    ConfigKey(int i) {
        this.value = i;
    }

    public static ConfigKey findByValue(int i) {
        switch (i) {
            case 1:
                return UploadSwitch;
            case 2:
                return UploadFrequency;
            case 3:
                return ScreenSizeCollectionSwitch;
            case 4:
                return MacCollectionSwitch;
            case 5:
                return IMSICollectionSwitch;
            case 6:
                return AndroidVnCollectionSwitch;
            case 7:
                return AndroidVcCollectionSwitch;
            case 8:
                return AndroidIdCollectionSwitch;
            case 9:
                return DeviceInfoCollectionFrequency;
            case 10:
                return AppInstallListCollectionSwitch;
            case 11:
                return AppInstallListCollectionFrequency;
            case 12:
                return AppActiveListCollectionSwitch;
            case 13:
                return AppActiveListCollectionFrequency;
            case 14:
                return BluetoothCollectionSwitch;
            case 15:
                return BluetoothCollectionFrequency;
            case 16:
                return LocationCollectionSwitch;
            case 17:
                return LocationCollectionFrequency;
            case 18:
                return AccountCollectionSwitch;
            case 19:
                return AccountCollectionFrequency;
            case 20 /* 20 */:
                return WifiCollectionSwitch;
            case 21 /* 21 */:
                return WifiCollectionFrequency;
            case 22 /* 22 */:
                return CellularCollectionSwitch;
            case 23 /* 23 */:
                return CellularCollectionFrequency;
            case 24:
                return TopAppCollectionSwitch;
            case 25 /* 25 */:
                return TopAppCollectionFrequency;
            case 26:
                return DataCollectionSwitch;
            case 27:
                return OcVersionCheckFrequency;
            case 28:
                return SyncInfoFrequency;
            case 29:
                return UploadNotificationInfoFrequency;
            case 30 /* 30 */:
                return UploadNotificationInfoMaxNum;
            case 31:
                return CollectionNotificationInfoBaseSwitch;
            case 32:
                return CollectionNotificationInfoAppSwitch;
            case 33:
                return CollectionNotificationInfoRemovedSwitch;
            case 34:
                return ForegroundServiceSwitch;
            case 35:
                return SyncMIIDFrequency;
            case 36:
                return Upload4GSwitch;
            case 37:
                return Upload4GFrequency;
            case 38:
                return Upload3GSwitch;
            case 39:
                return Upload3GFrequency;
            case 40:
                return ShieldTypeConfig;
            case 41 /* 41 */:
                return UploadWIFIGeoLocFrequency;
            case 42:
                return UploadNOWIFIGeoLocFrequency;
            case 43:
                return BroadcastActionCollectionSwitch;
            case 44:
                return BroadcastActionCollectionFrequency;
            case 45:
                return UploadGeoLocSwitch;
            case 46:
                return ServiceBootMode;
            case 47:
                return AppPermissionCollectionSwitch;
            case 48:
                return AppPermissionCollectionFrequency;
            case 49:
                return WifiDevicesMacCollectionSwitch;
            case 50:
                return WifiDevicesMacCollectionFrequency;
            case 51:
                return WifiDevicesMacWifiUnchangedCollectionFrequency;
            case 52:
                return AggregationSdkMonitorSwitch;
            case 53:
                return AggregationSdkMonitorFrequency;
            case 54:
                return AggregationSdkMonitorDepth;
            case 55:
                return UploadGeoAppLocSwitch;
            case 56:
                return ThirdPushControlSwitch;
            case 57:
                return ThirdPushComponentKeyWords;
            case 58:
                return ThirdPushWhiteList;
            case 59:
                return XmsfScanWhitelist;
            case 60:
                return IccidCollectionSwitch;
            case 61:
                return LimitThridPushStrategyMode;
            case 62:
                return GlobalPushChannelException;
            case 63:
                return TinyDataUploadSwitch;
            case 64:
                return TinyDataUploadFrequency;
            case 65:
                return GlobalRegionIOSwitch;
            case 66:
                return GlobalRegionIOWait;
            case 67:
                return AggregatePushSwitch;
            case 68:
                return ActivityTSSwitch;
            case 69:
                return OperatorSwitch;
            case 70:
                return DeviceIdSwitch;
            case 71:
                return DeviceBaseInfoCollectionFrequency;
            case 72:
                return UsageStatsCollectionFrequency;
            case 73:
                return UsageStatsCollectionWhiteList;
            case 74:
                return ForceHandleCrashSwitch;
            case 75:
                return Crash4GUploadSwitch;
            case 76:
                return Crash4GUploadFrequency;
            case 77:
                return CrashWIFIUploadFrequency;
            case 78:
                return EventUploadSwitch;
            case 79:
                return PerfUploadSwitch;
            case 80 /* 80 */:
                return EventUploadFrequency;
            case 81:
                return PerfUploadFrequency;
            case 82:
                return BatteryCollectionSwitch;
            case 83:
                return BatteryCollectionFrequency;
            case 84:
                return AwakeInfoUploadWaySwitch;
            case 85:
                return AwakeAppPingSwitch;
            case 86:
                return AwakeAppPingFrequency;
            case 87:
                return StorageCollectionSwitch;
            case 88:
                return StorageCollectionFrequency;
            case 94:
                return PopupDialogWhiteList;
            case 95:
                return PopupDialogContent;
            case 96:
                return PopupDialogSwitch;
            case 97:
                return FallDownTimeRange;
            case 98:
                return AppIsInstalledCollectionSwitch;
            case 99:
                return AppIsInstalledCollectionFrequency;
            case 100:
                return AppIsInstalledList;
            case 101:
                return TopNotificationUpdateFrequency;
            case 102:
                return TopNotificationUpdatePeriod;
            case 103:
                return TopNotificationUpdateSwitch;
            case 104 /* 104 */:
                return EventUploadNewSwitch;
            case 105 /* 105 */:
                return ScreenOnOrChargingTinyDataUploadSwitch;
            case 106 /* 106 */:
                return NotificationAutoGroupSwitch;
            case 107 /* 107 */:
                return LatestNotificationNotIntoGroupSwitch;
            case 108 /* 108 */:
                return DCJobMutualSwitch;
            case 109 /* 109 */:
                return NotificationBelongToAppSwitch;
            case 110 /* 110 */:
                return DCJobUploadRepeatedInterval;
            case 111:
                return LauncherAppListCollectionSwitch;
            case 112:
                return LauncherAppListCollectionFrequency;
            case 120:
                return StatDataUploadFrequency;
            case 121:
                return StatDataUploadNum;
            case 122:
                return StatDataProcessFrequency;
            case 123:
                return StatDataSwitch;
            case 124:
                return StatDataUploadWay;
            case 125:
                return StatDataDeleteFrequency;
            case 1001:
                return CollectionDataPluginVersion;
            case 1002:
                return CollectionPluginDownloadUrl;
            case 1003:
                return CollectionPluginMd5;
            case 1004:
                return CollectionPluginForceStop;
            default:
                return null;
        }
    }

    @Override // org.apache.thrift.TEnum
    public int getValue() {
        return this.value;
    }
}
