package com.xiaomi.push.service;

import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.smack.XMPPException;
import com.xiaomi.xmpush.thrift.ActionType;
import com.xiaomi.xmpush.thrift.NotificationType;
import com.xiaomi.xmpush.thrift.XmPushActionAckNotification;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;
import com.xiaomi.xmpush.thrift.XmPushActionNotification;
import com.xiaomi.xmsf.runtime.PushRuntime;

final class MIPushAckDispatcher {
    private interface AckAction {
        void process() throws Exception;
    }

    private MIPushAckDispatcher() {
    }

    static void sendAckMessage(final XMPushService xMPushService, final XmPushActionContainer xmPushActionContainer) {
        enqueue(xMPushService, "send ack message for message.", new AckAction() { // from class: com.xiaomi.push.service.MIPushAckDispatcher.1
            @Override // com.xiaomi.push.service.MIPushAckDispatcher.AckAction
            public void process() throws Exception {
                sendServiceAck(xMPushService, xmPushActionContainer);
            }
        });
    }

    static void sendAppAbsentAck(final XMPushService xMPushService, final XmPushActionContainer xmPushActionContainer, final String str) {
        enqueue(xMPushService, "send app absent ack message for message.", new AckAction() { // from class: com.xiaomi.push.service.MIPushAckDispatcher.2
            @Override // com.xiaomi.push.service.MIPushAckDispatcher.AckAction
            public void process() throws Exception {
                sendServiceAppAbsentAck(xMPushService, xmPushActionContainer, str);
            }
        });
    }

    static void sendAppNotInstallNotification(final XMPushService xMPushService, final XmPushActionContainer xmPushActionContainer) {
        enqueue(xMPushService, "send app absent message.", new AckAction() { // from class: com.xiaomi.push.service.MIPushAckDispatcher.3
            @Override // com.xiaomi.push.service.MIPushAckDispatcher.AckAction
            public void process() throws Exception {
                MIPushHelper.sendPacket(xMPushService, MIPushHelper.contructAppAbsentMessage(xmPushActionContainer.getPackageName(), xmPushActionContainer.getAppid()));
            }
        });
    }

    static void sendClearPushMessageAck(final XMPushService xMPushService, final XmPushActionContainer xmPushActionContainer, final XmPushActionNotification xmPushActionNotification) {
        enqueue(xMPushService, "send ack message for clear push message.", new AckAction() { // from class: com.xiaomi.push.service.MIPushAckDispatcher.4
            @Override // com.xiaomi.push.service.MIPushAckDispatcher.AckAction
            public void process() throws Exception {
                sendServiceClearNotificationAck(xMPushService, xmPushActionContainer, xmPushActionNotification);
            }
        });
    }

    static void sendErrorAck(final XMPushService xMPushService, final XmPushActionContainer xmPushActionContainer, final String str, final String str2) {
        enqueue(xMPushService, "send wrong message ack for message.", new AckAction() { // from class: com.xiaomi.push.service.MIPushAckDispatcher.5
            @Override // com.xiaomi.push.service.MIPushAckDispatcher.AckAction
            public void process() throws Exception {
                sendServiceErrorAck(xMPushService, xmPushActionContainer, str, str2);
            }
        });
    }

    static void sendMIUINewAdsAckMessage(final XMPushService xMPushService, final XmPushActionContainer xmPushActionContainer) {
        enqueue(xMPushService, "send ack message for unrecognized new miui message.", new AckAction() { // from class: com.xiaomi.push.service.MIPushAckDispatcher.6
            @Override // com.xiaomi.push.service.MIPushAckDispatcher.AckAction
            public void process() throws Exception {
                sendServiceAckWithMarker(xMPushService, xmPushActionContainer, "miui_message_unrecognized", "1", "MIPushAckDispatcher.sendMIUINewAdsAckMessage");
            }
        });
    }

    static void sendMIUIOldAdsAckMessage(final XMPushService xMPushService, final XmPushActionContainer xmPushActionContainer) {
        enqueue(xMPushService, "send ack message for obsleted message.", new AckAction() { // from class: com.xiaomi.push.service.MIPushAckDispatcher.7
            @Override // com.xiaomi.push.service.MIPushAckDispatcher.AckAction
            public void process() throws Exception {
                sendServiceAckWithMarker(xMPushService, xmPushActionContainer, "message_obsleted", "1", "MIPushAckDispatcher.sendMIUIOldAdsAckMessage");
            }
        });
    }

    private static void enqueue(final XMPushService xMPushService, final String str, final AckAction ackAction) {
        xMPushService.executeJob(new XMPushService.Job(4) { // from class: com.xiaomi.push.service.MIPushAckDispatcher.8
            @Override // com.xiaomi.push.service.XMPushService.Job
            public String getDesc() {
                return str;
            }

            @Override // com.xiaomi.push.service.XMPushService.Job
            public void process() {
                try {
                    ackAction.process();
                } catch (Exception e) {
                    MyLog.e(e);
                    if (e instanceof XMPPException) {
                        xMPushService.disconnect(10, (XMPPException) e);
                    }
                }
            }
        });
    }

    private static void sendServiceAck(XMPushService xMPushService, XmPushActionContainer xmPushActionContainer) throws Exception {
        XmPushActionContainer constructAckMessage = MIPushEventProcessor.constructAckMessage(xMPushService, xmPushActionContainer);
        MIPushHelper.sendPacket(xMPushService, constructAckMessage);
        PushRuntime.observeChannelEvent(xmPushActionContainer.packageName, "service_ack_sent", "MIPushAckDispatcher.sendServiceAck");
    }

    private static void sendServiceAppAbsentAck(XMPushService xMPushService, XmPushActionContainer xmPushActionContainer, String str) throws Exception {
        XmPushActionContainer constructAckMessage = MIPushEventProcessor.constructAckMessage(xMPushService, xmPushActionContainer);
        if (constructAckMessage.metaInfo != null) {
            constructAckMessage.metaInfo.putToExtra("absent_target_package", str);
        }
        MIPushHelper.sendPacket(xMPushService, constructAckMessage);
        PushRuntime.observeChannelEvent(xmPushActionContainer.packageName, "service_app_absent_ack_sent", "MIPushAckDispatcher.sendServiceAppAbsentAck");
    }

    private static void sendServiceErrorAck(XMPushService xMPushService, XmPushActionContainer xmPushActionContainer, String str, String str2) throws Exception {
        XmPushActionContainer constructAckMessage = MIPushEventProcessor.constructAckMessage(xMPushService, xmPushActionContainer);
        if (constructAckMessage.metaInfo != null) {
            constructAckMessage.metaInfo.putToExtra(com.xiaomi.smack.packet.Message.MSG_TYPE_ERROR, str);
            constructAckMessage.metaInfo.putToExtra("reason", str2);
        }
        MIPushHelper.sendPacket(xMPushService, constructAckMessage);
        PushRuntime.observeChannelEvent(xmPushActionContainer.packageName, "service_error_ack_sent", "MIPushAckDispatcher.sendServiceErrorAck");
    }

    private static void sendServiceAckWithMarker(XMPushService xMPushService, XmPushActionContainer xmPushActionContainer, String str, String str2, String str3) throws Exception {
        XmPushActionContainer constructAckMessage = MIPushEventProcessor.constructAckMessage(xMPushService, xmPushActionContainer);
        if (constructAckMessage.metaInfo != null) {
            constructAckMessage.metaInfo.putToExtra(str, str2);
        }
        MIPushHelper.sendPacket(xMPushService, constructAckMessage);
        PushRuntime.observeChannelEvent(xmPushActionContainer.packageName, "service_ack_marker_sent", str3);
    }

    private static void sendServiceClearNotificationAck(XMPushService xMPushService, XmPushActionContainer xmPushActionContainer, XmPushActionNotification xmPushActionNotification) throws Exception {
        XmPushActionAckNotification xmPushActionAckNotification = new XmPushActionAckNotification();
        xmPushActionAckNotification.type = NotificationType.CancelPushMessageACK.value;
        xmPushActionAckNotification.id = xmPushActionNotification.id;
        xmPushActionAckNotification.target = xmPushActionNotification.target;
        xmPushActionAckNotification.appId = xmPushActionNotification.appId;
        xmPushActionAckNotification.packageName = xmPushActionNotification.packageName;
        xmPushActionAckNotification.errorCode = 0L;
        xmPushActionAckNotification.reason = "success clear push message.";
        MIPushHelper.sendPacket(xMPushService, MIPushHelper.constructResponseContainer(xmPushActionContainer.packageName, xmPushActionContainer.appid, xmPushActionAckNotification, ActionType.Notification));
        PushRuntime.observeNotificationEvent(xmPushActionNotification.packageName, "service_clear_notification_ack_sent", "MIPushAckDispatcher.sendServiceClearNotificationAck");
    }
}
