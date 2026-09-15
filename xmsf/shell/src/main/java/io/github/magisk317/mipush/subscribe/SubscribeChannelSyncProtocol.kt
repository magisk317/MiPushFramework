package io.github.magisk317.mipush.subscribe

/**
 * Wire type strings for the subscribe-channel-sync stack (stock 7.5.29 `ae.n`).
 *
 * These mirror the five values that the coordination report proposes for the frozen
 * `com.xiaomi.xmpush.thrift.NotificationType` enum. Until that shared-file change lands, this
 * object is the single in-module source for the subscribe package so it compiles and runs
 * independently; after the enum additions, keep both in sync (stock values are wire protocol and
 * must never diverge).
 */
object SubscribeChannelSyncProtocol {
    const val TYPE_SUBSCRIBE_CHANNEL_SYNC = "subscribe_channel_sync"
    const val TYPE_SUBSCRIBE_CHANNEL_SYNC_ACK = "subscribe_channel_sync_ack"
    const val TYPE_SUBSCRIBE_CHANNEL_SYNC_RESULT = "subscribe_channel_sync_result"
    const val TYPE_SYNC_APP_SCENE_MICHANNEL = "sync_app_scene_michannel"
    const val TYPE_SYNC_APP_SCENE_MICHANNEL_RESULT = "sync_app_scene_michannel_result"

    /** Inbound extra key carrying the scene push rule blob (stock m0 SyncAppSceneMiChannelResult branch). */
    const val EXTRA_SCENE_CHANNEL_DATA = "scene_channel_data"

    /** Stock reason string written into the subscribe ack (m0.m). */
    const val ACK_REASON_SUCCESS = "success received response"

    /** Intent action + extras of the stock SUB_GROUP_RESULT_REPORT flow (XMPushService handleIntent). */
    const val ACTION_SUB_GROUP_RESULT_REPORT = "com.xiaomi.push.SUB_GROUP_RESULT_REPORT"
    const val EXTRA_SUB_GROUP_REQUEST_ID = "sub_group_request_id"
    const val EXTRA_SUB_GROUP_PKG_NAME = "sub_group_pkg_name"
}
