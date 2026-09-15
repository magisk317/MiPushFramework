package com.xiaomi.xmpush.thrift;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/NotificationType.class */
public enum NotificationType {
    Invalid("INVALID"),
    BarClick("bar:click"),
    BarCancel("bar:cancel"),
    AppOpen("app:open"),
    PackageUninstall("package uninstalled"),
    AppUninstall("app_uninstalled"),
    ClientInfoUpdate("client_info_update"),
    ClientInfoUpdateOk("client_info_update_ok"),
    ClientMIIDUpdate("client_miid_update"),
    PullOfflineMessage("pull"),
    IosSleep("ios_sleep"),
    IosWakeUp("ios_wakeup"),
    AwakeApp("awake_app"),
    NormalClientConfigUpdate("normal_client_config_update"),
    CustomClientConfigUpdate("custom_client_config_update"),
    DailyCheckClientConfig("daily_check_client_config"),
    DataCollection("data_collection"),
    RegIdExpired("registration id expired"),
    ConnectionDisabled("!!!MILINK CONNECTION DISABLED!!!"),
    PackageUnregistered("package_unregistered"),
    DecryptMessageFail("decrypt_msg_fail"),
    SyncInfo("sync_info"),
    SyncInfoResult("sync_info_result"),
    ForceSync("force_sync"),
    UploadClientLog("upload_client_log"),
    NotificationBarInfo("notification_bar_info"),
    SyncMIID("sync_miid"),
    UploadTinyData("upload"),
    CancelPushMessage("clear_push_message"),
    CancelPushMessageACK("clear_push_message_ack"),
    DisablePushMessage("disable_push"),
    EnablePushMessage("enable_push"),
    ClientABTest("client_ab_test"),
    AwakeSystemApp("awake_system_app"),
    AwakeAppResponse("awake_app_response"),
    HybridRegister("hb_register"),
    HybridRegisterResult("hb_register_res"),
    HybridUnregister("hb_unregister"),
    HybridUnregisterResult("hb_unregister_res"),
    ThirdPartyRegUpdate("3rd_party_reg_update"),
    VRUpload("vr_upload"),
    PushLogUpload("log_upload"),
    APP_WAKEUP("app_wakeup"),
    APP_SLEEP("app_sleep"),
    NOTIFICATION_SWITCH("notification_switch"),
    SubscribeChannelSync("subscribe_channel_sync"),
    SubscribeChannelSyncAck("subscribe_channel_sync_ack"),
    SubscribeChannelSyncResult("subscribe_channel_sync_result"),
    SyncAppSceneMiChannel("sync_app_scene_michannel"),
    SyncAppSceneMiChannelResult("sync_app_scene_michannel_result"),
    // Stock 7.5.29 ae.n:96,97,102,104. Only the inbound control/ack wire types are added;
    // the PushDataRecover/RecoverLBSSubscription uplink requests are not ported.
    SettingAppNotificationPermission("setting_app_notification_permission"),
    SettingAppNotificationPermissionACK("setting_app_notification_permission_ack"),
    PushDataForRecoverACK("push_data_recover_ack"),
    RecoverLBSSubscriptionACK("recover_lbs_subscription_ack");

    public final String value;

    NotificationType(String str) {
        this.value = str;
    }

    @Override // java.lang.Enum
    public String toString() {
        return this.value;
    }
}
