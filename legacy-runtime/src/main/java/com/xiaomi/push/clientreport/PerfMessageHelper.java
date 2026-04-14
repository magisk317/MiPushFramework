package com.xiaomi.push.clientreport;

import android.content.Context;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.LoggerInterface;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.push.service.clientReport.PushClientReportHelper;
import com.xiaomi.push.service.clientReport.PushClientReportManager;
import com.xiaomi.push.service.xmpush.Command;
import com.xiaomi.smack.util.TrafficUtils;
import com.xiaomi.xmpush.thrift.ActionType;
import com.xiaomi.xmpush.thrift.NotificationType;
import com.xiaomi.xmpush.thrift.XmPushActionAckNotification;
import com.xiaomi.xmpush.thrift.XmPushActionCommand;
import com.xiaomi.xmpush.thrift.XmPushActionCommandResult;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;
import com.xiaomi.xmpush.thrift.XmPushActionNotification;
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils;
import org.apache.thrift.TBase;
import org.apache.thrift.TException;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/clientreport/PerfMessageHelper.class */
public class PerfMessageHelper {

    /* JADX INFO: renamed from: com.xiaomi.push.clientreport.PerfMessageHelper$1, reason: invalid class name */
    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/clientreport/PerfMessageHelper$1.class */
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
                $SwitchMap$com$xiaomi$xmpush$thrift$ActionType[ActionType.MultiConnectionBroadcast.ordinal()] = 9;
            } catch (NoSuchFieldError e9) {
            }
            try {
                $SwitchMap$com$xiaomi$xmpush$thrift$ActionType[ActionType.MultiConnectionResult.ordinal()] = 10;
            } catch (NoSuchFieldError e10) {
            }
            try {
                $SwitchMap$com$xiaomi$xmpush$thrift$ActionType[ActionType.Notification.ordinal()] = 11;
            } catch (NoSuchFieldError e11) {
            }
            try {
                $SwitchMap$com$xiaomi$xmpush$thrift$ActionType[ActionType.Command.ordinal()] = 12;
            } catch (NoSuchFieldError e12) {
            }
        }
    }

    public static void collectPerfData(String str, Context context, int i, int i2) {
        if (i <= 0 || i2 <= 0) {
            return;
        }
        int traffic = getTraffic(context, i2);
        if (i != PushClientReportHelper.changeOrdinalToCode(NotificationType.UploadTinyData)) {
            PushClientReportManager.getInstance(context.getApplicationContext()).reportPerf(str, i, 1L, traffic);
        }
    }

    public static void collectPerfData(String str, Context context, TBase tBase, ActionType actionType, int i) {
        collectPerfData(str, context, getMessageType(tBase, actionType), i);
    }

    public static void collectUpStream(String str, Context context, XmPushActionContainer xmPushActionContainer, int i) {
        ActionType action;
        if (context == null || xmPushActionContainer == null || (action = xmPushActionContainer.getAction()) == null) {
            return;
        }
        int iTypeToCode = typeToCode(action);
        if (i <= 0) {
            byte[] bArrConvertThriftObjectToBytes = XmPushThriftSerializeUtils.convertThriftObjectToBytes(xmPushActionContainer);
            i = 0;
            if (bArrConvertThriftObjectToBytes != null) {
                i = bArrConvertThriftObjectToBytes.length;
            }
        }
        collectPerfData(str, context, iTypeToCode, i);
    }

    public static void collectUpStream(String str, Context context, byte[] bArr) {
        if (context == null || bArr == null || bArr.length <= 0) {
            return;
        }
        XmPushActionContainer xmPushActionContainer = new XmPushActionContainer();
        try {
            XmPushThriftSerializeUtils.convertByteArrayToThriftObject(xmPushActionContainer, bArr);
            collectUpStream(str, context, xmPushActionContainer, bArr.length);
        } catch (TException e) {
            MyLog.w("fail to convert bytes to container");
        }
    }

    public static int getMessageType(TBase tBase, ActionType actionType) {
        int iChangeValueToCode = -1;
        switch (AnonymousClass1.$SwitchMap$com$xiaomi$xmpush$thrift$ActionType[actionType.ordinal()]) {
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
            case 10:
                iChangeValueToCode = PushClientReportHelper.changeValueToCode(actionType.getValue());
                break;
            case 11:
                int iChangeValueToCode2 = PushClientReportHelper.changeValueToCode(actionType.getValue());
                iChangeValueToCode = iChangeValueToCode2;
                if (tBase != null) {
                    try {
                        if (!(tBase instanceof XmPushActionAckNotification)) {
                            iChangeValueToCode = iChangeValueToCode2;
                            if (tBase instanceof XmPushActionNotification) {
                                String str = ((XmPushActionNotification) tBase).type;
                                iChangeValueToCode = iChangeValueToCode2;
                                if (!TextUtils.isEmpty(str)) {
                                    iChangeValueToCode = iChangeValueToCode2;
                                    if (PushClientReportHelper.changeOrdinalToCode(PushClientReportHelper.changeValueToNotificationType(str)) != -1) {
                                        iChangeValueToCode = PushClientReportHelper.changeOrdinalToCode(PushClientReportHelper.changeValueToNotificationType(str));
                                    }
                                    int i = iChangeValueToCode;
                                    if (NotificationType.UploadTinyData.equals(PushClientReportHelper.changeValueToNotificationType(str))) {
                                        iChangeValueToCode = -1;
                                    }
                                }
                            }
                        } else {
                            String str2 = ((XmPushActionAckNotification) tBase).type;
                            iChangeValueToCode = iChangeValueToCode2;
                            if (!TextUtils.isEmpty(str2)) {
                                iChangeValueToCode = iChangeValueToCode2;
                                if (PushClientReportHelper.changeOrdinalToCode(PushClientReportHelper.changeValueToNotificationType(str2)) != -1) {
                                    iChangeValueToCode = PushClientReportHelper.changeOrdinalToCode(PushClientReportHelper.changeValueToNotificationType(str2));
                                }
                            }
                        }
                    } catch (Exception e) {
                        MyLog.e("PERF_ERROR : parse Notification type error");
                        iChangeValueToCode = iChangeValueToCode2;
                    }
                }
                break;
            case 12:
                int iChangeValueToCode3 = PushClientReportHelper.changeValueToCode(actionType.getValue());
                iChangeValueToCode = iChangeValueToCode3;
                if (tBase != null) {
                    try {
                        if (!(tBase instanceof XmPushActionCommandResult)) {
                            iChangeValueToCode = iChangeValueToCode3;
                            if (tBase instanceof XmPushActionCommand) {
                                String cmdName = ((XmPushActionCommand) tBase).getCmdName();
                                iChangeValueToCode = iChangeValueToCode3;
                                if (!TextUtils.isEmpty(cmdName)) {
                                    iChangeValueToCode = iChangeValueToCode3;
                                    if (Command.getCode(cmdName) != -1) {
                                        iChangeValueToCode = Command.getCode(cmdName);
                                    }
                                }
                            }
                        } else {
                            String cmdName2 = ((XmPushActionCommandResult) tBase).getCmdName();
                            iChangeValueToCode = iChangeValueToCode3;
                            if (!TextUtils.isEmpty(cmdName2)) {
                                iChangeValueToCode = iChangeValueToCode3;
                                if (Command.getCode(cmdName2) != -1) {
                                    iChangeValueToCode = Command.getCode(cmdName2);
                                }
                            }
                        }
                    } catch (Exception e2) {
                        MyLog.e("PERF_ERROR : parse Command type error");
                        iChangeValueToCode = iChangeValueToCode3;
                    }
                }
                break;
        }
        return iChangeValueToCode;
    }

    public static int getTraffic(Context context, int i) {
        int networkType = TrafficUtils.getNetworkType(context);
        if (-1 == networkType) {
            return -1;
        }
        return ((networkType == 0 ? 13 : 11) * i) / 10;
    }

    public static int typeToCode(ActionType actionType) {
        return PushClientReportHelper.changeValueToCode(actionType.getValue());
    }
}
