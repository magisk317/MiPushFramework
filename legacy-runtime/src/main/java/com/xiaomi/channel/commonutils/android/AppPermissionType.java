package com.xiaomi.channel.commonutils.android;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/android/AppPermissionType.class */
public enum AppPermissionType {
    INTERNET(0),
    ACCESS_NETWORK_STATE(1),
    CHANGE_NETWORK_STATE(2),
    ACCESS_WIFI_STATE(3),
    CHANGE_WIFI_STATE(4),
    BLUETOOTH(5),
    BLUETOOTH_ADMIN(6),
    WRITE_OWNER_DATA(7),
    WRITE_EXTERNAL_STORAGE(8),
    READ_PHONE_STATE(9),
    CALL_PHONE(10),
    READ_PHONE_SMS(11),
    SEND_SMS(12),
    RECEIVE_SMS(13),
    WRITE_SMS(14),
    BROADCAST_SMS(15),
    BATTERY_STATS(16),
    CAMERA(17),
    FLASHLIGHT(18),
    VIBRATE(19),
    READ_CONTACTS(20),
    WRITE_CONTACTS(21),
    RECORD_AUDIO(22),
    SET_ORIENTATION(23),
    SET_TIME(24),
    SET_TIME_ZONE(25),
    READ_OWNER_DATA(26),
    DELETE_CACHE_FILES(27),
    DELETE_PACKAGES(28),
    MODIFY_AUDIO_SETTINGS(29),
    PROCESS_OUTGOING_CALLS(30),
    READ_HISTORY_BOOKMARKS(31),
    REBOOT(32),
    SET_WALLPAPER(33),
    RECEIVE_MMS(34),
    WRITE_HISTORY_BOOKMARKS(35),
    GET_TASKS(36),
    ACCESS_CHECKIN_PROPERTIES(37),
    ACCESS_COARSE_LOCATION(38),
    ACCESS_FINE_LOCATION(39),
    ACCESS_LOCATION_EXTRA_COMMANDS(40),
    ACCESS_NOTIFICATION_POLICY(41),
    ACCOUNT_MANAGER(42),
    ADD_VOICEMAIL(43),
    BIND_ACCESSIBILITY_SERVICE(44),
    BIND_APPWIDGET(45),
    BIND_CARRIER_MESSAGING_SERVICE(46),
    BIND_CARRIER_SERVICES(47),
    BIND_CHOOSER_TARGET_SERVICE(48),
    BIND_CONDITION_PROVIDER_SERVICE(49),
    BIND_DEVICE_ADMIN(50),
    BIND_DREAM_SERVICE(51),
    BIND_INCALL_SERVICE(52),
    BIND_INPUT_METHOD(53),
    BIND_MIDI_DEVICE_SERVICE(54),
    BIND_NFC_SERVICE(55),
    BIND_NOTIFICATION_LISTENER_SERVICE(56),
    BIND_PRINT_SERVICE(57),
    BIND_QUICK_SETTINGS_TILE(58),
    BIND_REMOTEVIEWS(59),
    BIND_SCREENING_SERVICE(60),
    BIND_TELECOM_CONNECTION_SERVICE(61),
    BIND_TEXT_SERVICE(62),
    BIND_TV_INPUT(63),
    BIND_VOICE_INTERACTION(64),
    BIND_VPN_SERVICE(65),
    BIND_VR_LISTENER_SERVICE(66),
    BIND_WALLPAPER(67),
    BLUETOOTH_PRIVILEGED(68),
    BODY_SENSORS(69),
    BROADCAST_PACKAGE_REMOVED(70),
    BROADCAST_STICKY(71),
    BROADCAST_WAP_PUSH(72),
    CALL_PRIVILEGED(73),
    CAPTURE_AUDIO_OUTPUT(74),
    CAPTURE_SECURE_VIDEO_OUTPUT(75),
    CAPTURE_VIDEO_OUTPUT(76),
    CHANGE_COMPONENT_ENABLED_STATE(77),
    CHANGE_CONFIGURATION(78),
    CHANGE_WIFI_MULTICAST_STATE(79),
    CLEAR_APP_CACHE(80),
    CONTROL_LOCATION_UPDATES(81),
    DIAGNOSTIC(82),
    DISABLE_KEYGUARD(83),
    DUMP(84),
    EXPAND_STATUS_BAR(85),
    FACTORY_TEST(86),
    GET_ACCOUNTS(87),
    GET_ACCOUNTS_PRIVILEGED(88),
    GET_PACKAGE_SIZE(89),
    GLOBAL_SEARCH(90),
    INSTALL_LOCATION_PROVIDER(91),
    INSTALL_PACKAGES(92),
    INSTALL_SHORTCUT(93),
    KILL_BACKGROUND_PROCESSES(94),
    LOCATION_HARDWARE(95),
    MANAGE_DOCUMENTS(96),
    MASTER_CLEAR(97),
    MEDIA_CONTENT_CONTROL(98),
    MODIFY_PHONE_STATE(99),
    MOUNT_FORMAT_FILESYSTEMS(100),
    MOUNT_UNMOUNT_FILESYSTEMS(101),
    NFC(102),
    PACKAGE_USAGE_STATS(103),
    PERSISTENT_ACTIVITY(104),
    READ_CALENDAR(105),
    READ_CALL_LOG(106),
    READ_EXTERNAL_STORAGE(107),
    READ_FRAME_BUFFER(108),
    READ_INPUT_STATE(109),
    READ_LOGS(110),
    READ_SYNC_SETTINGS(111),
    READ_SYNC_STATS(112),
    READ_VOICEMAIL(113),
    RECEIVE_BOOT_COMPLETED(114),
    RECEIVE_WAP_PUSH(115),
    REORDER_TASKS(116),
    REQUEST_IGNORE_BATTERY_OPTIMIZATIONS(117),
    REQUEST_INSTALL_PACKAGES(118),
    RESTART_PACKAGES(119),
    SEND_RESPOND_VIA_MESSAGE(120),
    SET_ALARM(121),
    SET_ALWAYS_FINISH(122),
    SET_ANIMATION_SCALE(123),
    SET_DEBUG_APP(124),
    SET_PREFERRED_APPLICATIONS(125),
    SET_PROCESS_LIMIT(126),
    SET_WALLPAPER_HINTS(127),
    SIGNAL_PERSISTENT_PROCESSES(128),
    STATUS_BAR(129),
    SYSTEM_ALERT_WINDOW(130),
    TRANSMIT_IR(131),
    UNINSTALL_SHORTCUT(132),
    UPDATE_DEVICE_STATS(133),
    USE_FINGERPRINT(134),
    USE_SIP(135),
    WRITE_GSERVICES(136),
    WAKE_LOCK(137),
    WRITE_APN_SETTINGS(138),
    WRITE_CALENDAR(139),
    WRITE_CALL_LOG(140),
    WRITE_SECURE_SETTINGS(141),
    WRITE_SETTINGS(142),
    WRITE_SYNC_SETTINGS(143),
    WRITE_VOICEMAIL(144);

    private final int value;

    AppPermissionType(int i) {
        this.value = i;
    }

    public static AppPermissionType findByValue(int i) {
        switch (i) {
            case 0:
                return INTERNET;
            case 1:
                return ACCESS_NETWORK_STATE;
            case 2:
                return CHANGE_NETWORK_STATE;
            case 3:
                return ACCESS_WIFI_STATE;
            case 4:
                return CHANGE_WIFI_STATE;
            case 5:
                return BLUETOOTH;
            case 6:
                return BLUETOOTH_ADMIN;
            case 7:
                return WRITE_OWNER_DATA;
            case 8:
                return WRITE_EXTERNAL_STORAGE;
            case 9:
                return READ_PHONE_STATE;
            case 10:
                return CALL_PHONE;
            case 11:
                return READ_PHONE_SMS;
            case 12:
                return SEND_SMS;
            case 13:
                return RECEIVE_SMS;
            case 14:
                return WRITE_SMS;
            case 15:
                return BROADCAST_SMS;
            case 16:
                return BATTERY_STATS;
            case 17:
                return CAMERA;
            case 18:
                return FLASHLIGHT;
            case 19:
                return VIBRATE;
            case 20:
                return READ_CONTACTS;
            case 21:
                return WRITE_CONTACTS;
            case 22:
                return RECORD_AUDIO;
            case 23:
                return SET_ORIENTATION;
            case 24:
                return SET_TIME;
            case 25:
                return SET_TIME_ZONE;
            case 26:
                return READ_OWNER_DATA;
            case 27:
                return DELETE_CACHE_FILES;
            case 28:
                return DELETE_PACKAGES;
            case 29:
                return MODIFY_AUDIO_SETTINGS;
            case 30:
                return PROCESS_OUTGOING_CALLS;
            case 31:
                return READ_HISTORY_BOOKMARKS;
            case 32:
                return REBOOT;
            case 33:
                return SET_WALLPAPER;
            case 34:
                return RECEIVE_MMS;
            case 35:
                return WRITE_HISTORY_BOOKMARKS;
            case 36:
                return GET_TASKS;
            case 37:
                return ACCESS_CHECKIN_PROPERTIES;
            case 38:
                return ACCESS_COARSE_LOCATION;
            case 39:
                return ACCESS_FINE_LOCATION;
            case 40:
                return ACCESS_LOCATION_EXTRA_COMMANDS;
            case 41:
                return ACCESS_NOTIFICATION_POLICY;
            case 42:
                return ACCOUNT_MANAGER;
            case 43:
                return ADD_VOICEMAIL;
            case 44:
                return BIND_ACCESSIBILITY_SERVICE;
            case 45:
                return BIND_APPWIDGET;
            case 46:
                return BIND_CARRIER_MESSAGING_SERVICE;
            case 47:
                return ACCESS_CHECKIN_PROPERTIES;
            case 48:
                return BIND_CHOOSER_TARGET_SERVICE;
            case 49:
                return BIND_CONDITION_PROVIDER_SERVICE;
            case 50:
                return BIND_DEVICE_ADMIN;
            case 51:
                return BIND_DREAM_SERVICE;
            case 52:
                return BIND_INCALL_SERVICE;
            case 53:
                return BIND_INPUT_METHOD;
            case 54:
                return BIND_MIDI_DEVICE_SERVICE;
            case 55:
                return BIND_NFC_SERVICE;
            case 56:
                return BIND_NOTIFICATION_LISTENER_SERVICE;
            case 57:
                return BIND_PRINT_SERVICE;
            case 58:
                return BIND_QUICK_SETTINGS_TILE;
            case 59:
                return BIND_REMOTEVIEWS;
            case 60:
                return BIND_SCREENING_SERVICE;
            case 61:
                return BIND_TELECOM_CONNECTION_SERVICE;
            case 62:
                return ACCOUNT_MANAGER;
            case 63:
                return BIND_TV_INPUT;
            case 64:
                return BIND_VOICE_INTERACTION;
            case 65:
                return BIND_VPN_SERVICE;
            case 66:
                return BIND_VR_LISTENER_SERVICE;
            case 67:
                return BIND_WALLPAPER;
            case 68:
                return BLUETOOTH_PRIVILEGED;
            case 69:
                return BODY_SENSORS;
            case 70:
                return BROADCAST_PACKAGE_REMOVED;
            case 71:
                return BROADCAST_STICKY;
            case 72:
                return BROADCAST_WAP_PUSH;
            case 73:
                return CALL_PRIVILEGED;
            case 74:
                return CAPTURE_AUDIO_OUTPUT;
            case 75:
                return CAPTURE_SECURE_VIDEO_OUTPUT;
            case 76:
                return CAPTURE_VIDEO_OUTPUT;
            case 77:
                return CHANGE_COMPONENT_ENABLED_STATE;
            case 78:
                return CHANGE_CONFIGURATION;
            case 79:
                return CHANGE_WIFI_MULTICAST_STATE;
            case 80:
                return CLEAR_APP_CACHE;
            case 81:
                return CONTROL_LOCATION_UPDATES;
            case 82:
                return DIAGNOSTIC;
            case 83:
                return DISABLE_KEYGUARD;
            case 84:
                return DUMP;
            case 85:
                return EXPAND_STATUS_BAR;
            case 86:
                return FACTORY_TEST;
            case 87:
                return GET_ACCOUNTS;
            case 88:
                return GET_ACCOUNTS_PRIVILEGED;
            case 89:
                return GET_PACKAGE_SIZE;
            case 90:
                return GLOBAL_SEARCH;
            case 91:
                return INSTALL_LOCATION_PROVIDER;
            case 92:
                return INSTALL_PACKAGES;
            case 93:
                return INSTALL_SHORTCUT;
            case 94:
                return KILL_BACKGROUND_PROCESSES;
            case 95:
                return LOCATION_HARDWARE;
            case 96:
                return MANAGE_DOCUMENTS;
            case 97:
                return MASTER_CLEAR;
            case 98:
                return MEDIA_CONTENT_CONTROL;
            case 99:
                return MODIFY_PHONE_STATE;
            case 100:
                return MOUNT_FORMAT_FILESYSTEMS;
            case 101:
                return MOUNT_UNMOUNT_FILESYSTEMS;
            case 102:
                return NFC;
            case 103:
                return PACKAGE_USAGE_STATS;
            case 104:
                return PERSISTENT_ACTIVITY;
            case 105:
                return READ_CALENDAR;
            case 106:
                return READ_CALL_LOG;
            case 107:
                return READ_EXTERNAL_STORAGE;
            case 108:
                return READ_FRAME_BUFFER;
            case 109:
                return READ_INPUT_STATE;
            case 110:
                return READ_LOGS;
            case 111:
                return READ_SYNC_SETTINGS;
            case 112:
                return READ_SYNC_STATS;
            case 113:
                return READ_VOICEMAIL;
            case 114:
                return RECEIVE_BOOT_COMPLETED;
            case 115:
                return RECEIVE_WAP_PUSH;
            case 116:
                return REORDER_TASKS;
            case 117:
                return REQUEST_IGNORE_BATTERY_OPTIMIZATIONS;
            case 118:
                return REQUEST_INSTALL_PACKAGES;
            case 119:
                return RESTART_PACKAGES;
            case 120:
                return SEND_RESPOND_VIA_MESSAGE;
            case 121:
                return SET_ALARM;
            case 122:
                return SET_ALWAYS_FINISH;
            case 123:
                return SET_ANIMATION_SCALE;
            case 124:
                return SET_DEBUG_APP;
            case 125:
                return SET_PREFERRED_APPLICATIONS;
            case 126:
                return SET_PROCESS_LIMIT;
            case 127:
                return SET_WALLPAPER_HINTS;
            case 128:
                return SIGNAL_PERSISTENT_PROCESSES;
            case 129:
                return STATUS_BAR;
            case 130:
                return SYSTEM_ALERT_WINDOW;
            case 131:
                return TRANSMIT_IR;
            case 132:
                return UNINSTALL_SHORTCUT;
            case 133:
                return UPDATE_DEVICE_STATS;
            case 134:
                return USE_FINGERPRINT;
            case 135:
                return USE_SIP;
            case 136:
                return WRITE_GSERVICES;
            case 137:
                return WAKE_LOCK;
            case 138:
                return WRITE_APN_SETTINGS;
            case 139:
                return WRITE_CALENDAR;
            case 140:
                return WRITE_CALL_LOG;
            case 141:
                return WRITE_SECURE_SETTINGS;
            case 142:
                return WRITE_SETTINGS;
            case 143:
                return WRITE_SYNC_SETTINGS;
            case 144:
                return WRITE_VOICEMAIL;
            default:
                return null;
        }
    }

    public int getValue() {
        return this.value;
    }
}
