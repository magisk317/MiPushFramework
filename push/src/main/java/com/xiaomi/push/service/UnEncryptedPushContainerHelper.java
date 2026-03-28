package com.xiaomi.push.service;

import android.content.Context;
import com.xiaomi.xmpush.thrift.ActionType;
import com.xiaomi.xmpush.thrift.XmPushActionAckMessage;
import com.xiaomi.xmpush.thrift.XmPushActionAckNotification;
import com.xiaomi.xmpush.thrift.XmPushActionCommandResult;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;
import com.xiaomi.xmpush.thrift.XmPushActionNotification;
import com.xiaomi.xmpush.thrift.XmPushActionRegistrationResult;
import com.xiaomi.xmpush.thrift.XmPushActionSendFeedbackResult;
import com.xiaomi.xmpush.thrift.XmPushActionSendMessage;
import com.xiaomi.xmpush.thrift.XmPushActionSubscriptionResult;
import com.xiaomi.xmpush.thrift.XmPushActionUnRegistrationResult;
import com.xiaomi.xmpush.thrift.XmPushActionUnSubscriptionResult;
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils;
import org.apache.thrift.TBase;
import org.apache.thrift.TException;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/UnEncryptedPushContainerHelper.class */
public class UnEncryptedPushContainerHelper {

    /* JADX INFO: renamed from: com.xiaomi.push.service.UnEncryptedPushContainerHelper$1, reason: invalid class name */
    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/UnEncryptedPushContainerHelper$1.class */
    static /* synthetic */ class AnonymousClass1 {
        static final /* synthetic */ int[] $SwitchMap$com$xiaomi$xmpush$thrift$ActionType;

        static {
            int[] iArr = new int[ActionType.values().length];
            $SwitchMap$com$xiaomi$xmpush$thrift$ActionType = iArr;
            try {
                iArr[ActionType.Registration.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$xiaomi$xmpush$thrift$ActionType[ActionType.UnRegistration.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$xiaomi$xmpush$thrift$ActionType[ActionType.Subscription.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$xiaomi$xmpush$thrift$ActionType[ActionType.UnSubscription.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$xiaomi$xmpush$thrift$ActionType[ActionType.SendMessage.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$xiaomi$xmpush$thrift$ActionType[ActionType.AckMessage.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
            try {
                $SwitchMap$com$xiaomi$xmpush$thrift$ActionType[ActionType.SetConfig.ordinal()] = 7;
            } catch (NoSuchFieldError e7) {
            }
            try {
                $SwitchMap$com$xiaomi$xmpush$thrift$ActionType[ActionType.ReportFeedback.ordinal()] = 8;
            } catch (NoSuchFieldError e8) {
            }
            try {
                $SwitchMap$com$xiaomi$xmpush$thrift$ActionType[ActionType.Notification.ordinal()] = 9;
            } catch (NoSuchFieldError e9) {
            }
            try {
                $SwitchMap$com$xiaomi$xmpush$thrift$ActionType[ActionType.Command.ordinal()] = 10;
            } catch (NoSuchFieldError e10) {
            }
        }
    }

    private static TBase createRespMessageFromAction(ActionType actionType, boolean z) {
        switch (AnonymousClass1.$SwitchMap$com$xiaomi$xmpush$thrift$ActionType[actionType.ordinal()]) {
            case 1:
                return new XmPushActionRegistrationResult();
            case 2:
                return new XmPushActionUnRegistrationResult();
            case 3:
                return new XmPushActionSubscriptionResult();
            case 4:
                return new XmPushActionUnSubscriptionResult();
            case 5:
                return new XmPushActionSendMessage();
            case 6:
                return new XmPushActionAckMessage();
            case 7:
                return new XmPushActionCommandResult();
            case 8:
                return new XmPushActionSendFeedbackResult();
            case 9:
                if (z) {
                    return new XmPushActionNotification();
                }
                XmPushActionAckNotification xmPushActionAckNotification = new XmPushActionAckNotification();
                xmPushActionAckNotification.setErrorCodeIsSet(true);
                return xmPushActionAckNotification;
            case 10:
                return new XmPushActionCommandResult();
            default:
                return null;
        }
    }

    public static TBase getResponseMessageBodyFromContainer(Context context, XmPushActionContainer xmPushActionContainer) throws TException {
        if (xmPushActionContainer.isEncryptAction()) {
            return null;
        }
        byte[] pushAction = xmPushActionContainer.getPushAction();
        TBase tBaseCreateRespMessageFromAction = createRespMessageFromAction(xmPushActionContainer.getAction(), xmPushActionContainer.isRequest);
        if (tBaseCreateRespMessageFromAction != null) {
            XmPushThriftSerializeUtils.convertByteArrayToThriftObject(tBaseCreateRespMessageFromAction, pushAction);
        }
        return tBaseCreateRespMessageFromAction;
    }
}
