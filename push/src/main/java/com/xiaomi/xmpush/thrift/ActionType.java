package com.xiaomi.xmpush.thrift;

import com.xiaomi.push.service.PushConstants;
import com.xiaomi.slim.Blob;
import com.xiaomi.smack.Connection;
import org.apache.thrift.TEnum;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/ActionType.class */
public enum ActionType implements TEnum {
    Registration(1),
    UnRegistration(2),
    Subscription(3),
    UnSubscription(4),
    SendMessage(5),
    AckMessage(6),
    SetConfig(7),
    ReportFeedback(8),
    Notification(9),
    Command(10),
    MultiConnectionBroadcast(11),
    MultiConnectionResult(12),
    ConnectionKick(13),
    ApnsMessage(14),
    IOSDeviceTokenWrite(15),
    SaveInvalidRegId(16),
    ApnsCertChanged(17),
    RegisterDevice(18),
    ExpandTopicInXmq(19),
    SendMessageNew(22),
    ExpandTopicInXmqNew(23),
    DeleteInvalidMessage(24),
    BadAction(99),
    Presence(100),
    FetchOfflineMessage(101),
    SaveJob(102),
    Broadcast(103),
    BatchPresence(Connection.ERR_TCP_NOROUTETOHOST),
    BatchMessage(Connection.ERR_TCP_TIMEOUT),
    StatCounter(Connection.ERR_TCP_UKNOWNHOST),
    FetchTopicMessage(Connection.ERR_TCP_READ_TIMEOUT),
    DeleteAliasCache(Connection.ERR_TCP_CONNRESET),
    UpdateRegistration(Connection.ERR_TCP_BROKEN_PIPE),
    BatchMessageNew(112),
    PublicWelfareMessage(113),
    RevokeMessage(114),
    SimulatorJob(Blob.ERROR_INVALID_CHID);

    private final int value;

    ActionType(int i) {
        this.value = i;
    }

    public static ActionType findByValue(int i) {
        switch (i) {
            case 1:
                return Registration;
            case 2:
                return UnRegistration;
            case 3:
                return Subscription;
            case 4:
                return UnSubscription;
            case 5:
                return SendMessage;
            case 6:
                return AckMessage;
            case 7:
                return SetConfig;
            case 8:
                return ReportFeedback;
            case 9:
                return Notification;
            case 10:
                return Command;
            case 11:
                return MultiConnectionBroadcast;
            case 12:
                return MultiConnectionResult;
            case 13:
                return ConnectionKick;
            case 14:
                return ApnsMessage;
            case 15:
                return IOSDeviceTokenWrite;
            case 16:
                return SaveInvalidRegId;
            case 17:
                return ApnsCertChanged;
            case 18:
                return RegisterDevice;
            case 19:
                return ExpandTopicInXmq;
            case PushConstants.ERROR_PING_TIMEOUT /* 22 */:
                return SendMessageNew;
            case PushConstants.ERROR_IN_EXTREME_POWER_MODE /* 23 */:
                return ExpandTopicInXmqNew;
            case 24:
                return DeleteInvalidMessage;
            case 99:
                return BadAction;
            case 100:
                return Presence;
            case 101:
                return FetchOfflineMessage;
            case 102:
                return SaveJob;
            case 103:
                return Broadcast;
            case Connection.ERR_TCP_NOROUTETOHOST /* 104 */:
                return BatchPresence;
            case Connection.ERR_TCP_TIMEOUT /* 105 */:
                return BatchMessage;
            case Connection.ERR_TCP_UKNOWNHOST /* 107 */:
                return StatCounter;
            case Connection.ERR_TCP_READ_TIMEOUT /* 108 */:
                return FetchTopicMessage;
            case Connection.ERR_TCP_CONNRESET /* 109 */:
                return DeleteAliasCache;
            case Connection.ERR_TCP_BROKEN_PIPE /* 110 */:
                return UpdateRegistration;
            case 112:
                return BatchMessageNew;
            case 113:
                return PublicWelfareMessage;
            case 114:
                return RevokeMessage;
            case Blob.ERROR_INVALID_CHID /* 200 */:
                return SimulatorJob;
            default:
                return null;
        }
    }

    @Override // org.apache.thrift.TEnum
    public int getValue() {
        return this.value;
    }
}
