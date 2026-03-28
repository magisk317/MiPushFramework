package com.xiaomi.push.service;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.android.AppInfoUtils;
import com.xiaomi.channel.commonutils.android.SystemUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.push.clientreport.PerfMessageHelper;
import com.xiaomi.push.service.clientReport.PushClientReportManager;
import com.xiaomi.push.service.clientReport.ReportConstants;
import com.xiaomi.smack.util.TrafficUtils;
import com.xiaomi.xmpush.thrift.ActionType;
import com.xiaomi.xmpush.thrift.NotificationType;
import com.xiaomi.xmpush.thrift.PushMetaInfo;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;
import com.xiaomi.xmpush.thrift.XmPushActionNotification;
import java.util.List;
import java.util.Map;
import org.apache.thrift.TBase;
import org.apache.thrift.TException;

final class MIPushEventProcessorSupport {
    private MIPushEventProcessorSupport() {
    }

    static void postProcessMIPushMessage(XMPushService xMPushService, String str, byte[] bArr, Intent intent) throws Throwable {
        String str2;
        boolean z;
        boolean zIsDuplicateMessage;
        String id;
        XmPushActionContainer xmPushActionContainerBuildContainer = MIPushEventProcessor.buildContainer(bArr);
        PushMetaInfo metaInfo = xmPushActionContainerBuildContainer.getMetaInfo();
        if (bArr != null) {
            PerfMessageHelper.collectPerfData(xmPushActionContainerBuildContainer.getPackageName(), xMPushService.getApplicationContext(), null, xmPushActionContainerBuildContainer.getAction(), bArr.length);
        }
        if (MIPushMessageRoutingSupport.isMIUIOldAdsSDKMessage(xmPushActionContainerBuildContainer) && MIPushMessageRoutingSupport.isMIUIPushSupported(xMPushService, str)) {
            if (MIPushNotificationHelper.isNPBMessage(xmPushActionContainerBuildContainer)) {
                PushClientReportManager.getInstance(xMPushService.getApplicationContext()).reportEvent4NeedDrop(xmPushActionContainerBuildContainer.getPackageName(), MIPushNotificationHelper.getInterfaceId(xmPushActionContainerBuildContainer), metaInfo.getId(), "5");
            }
            MIPushAckDispatcher.sendMIUIOldAdsAckMessage(xMPushService, xmPushActionContainerBuildContainer);
            return;
        }
        if (MIPushMessageRoutingSupport.isMIUIPushMessage(xmPushActionContainerBuildContainer) && !MIPushMessageRoutingSupport.isMIUIPushSupported(xMPushService, str) && !MIPushMessageRoutingSupport.predefinedNotification(xmPushActionContainerBuildContainer)) {
            if (MIPushNotificationHelper.isNPBMessage(xmPushActionContainerBuildContainer)) {
                PushClientReportManager.getInstance(xMPushService.getApplicationContext()).reportEvent4NeedDrop(xmPushActionContainerBuildContainer.getPackageName(), MIPushNotificationHelper.getInterfaceId(xmPushActionContainerBuildContainer), metaInfo.getId(), "6");
            }
            MIPushAckDispatcher.sendMIUINewAdsAckMessage(xMPushService, xmPushActionContainerBuildContainer);
            return;
        }
        if ((!MIPushNotificationHelper.isBusinessMessage(xmPushActionContainerBuildContainer) || !AppInfoUtils.isPkgInstalled(xMPushService, xmPushActionContainerBuildContainer.packageName)) && !MIPushMessageRoutingSupport.isIntentAvailable(xMPushService, intent)) {
            if (!AppInfoUtils.isPkgInstalled(xMPushService, xmPushActionContainerBuildContainer.packageName)) {
                if (MIPushNotificationHelper.isNPBMessage(xmPushActionContainerBuildContainer)) {
                    PushClientReportManager.getInstance(xMPushService.getApplicationContext()).reportEvent4ERROR(xmPushActionContainerBuildContainer.getPackageName(), MIPushNotificationHelper.getInterfaceId(xmPushActionContainerBuildContainer), metaInfo.getId(), "2");
                }
                MIPushAckDispatcher.sendAppNotInstallNotification(xMPushService, xmPushActionContainerBuildContainer);
                return;
            }
            MyLog.w("receive a mipush message, we can see the app, but we can't see the receiver.");
            if (MIPushNotificationHelper.isNPBMessage(xmPushActionContainerBuildContainer)) {
                PushClientReportManager.getInstance(xMPushService.getApplicationContext()).reportEvent4ERROR(xmPushActionContainerBuildContainer.getPackageName(), MIPushNotificationHelper.getInterfaceId(xmPushActionContainerBuildContainer), metaInfo.getId(), "3");
            }
            return;
        }
        if (ActionType.Registration == xmPushActionContainerBuildContainer.getAction()) {
            String packageName = xmPushActionContainerBuildContainer.getPackageName();
            SharedPreferences.Editor editorEdit = xMPushService.getSharedPreferences(PushServiceConstants.PREF_KEY_REGISTERED_PKGS, 0).edit();
            editorEdit.putString(packageName, xmPushActionContainerBuildContainer.appid);
            editorEdit.commit();
            MIPushAppInfo.getInstance(xMPushService).removeDisablePushPkg(packageName);
            MIPushAppInfo.getInstance(xMPushService).removeDisablePushPkgCache(packageName);
            PushClientReportManager.getInstance(xMPushService.getApplicationContext()).reportEvent(packageName, ReportConstants.REGISTER_EVENT_CHAIN_INTERFACE_ID, metaInfo.getId(), ReportConstants.REGISTER_TYPE_RECEIVE, null);
            if (!TextUtils.isEmpty(metaInfo.getId())) {
                intent.putExtra("messageId", metaInfo.getId());
                intent.putExtra(ReportConstants.EVENT_MESSAGE_TYPE, ReportConstants.REGISTER_TYPE);
            }
        }
        if (MIPushNotificationHelper.isNormalNotificationMessage(xmPushActionContainerBuildContainer)) {
            PushClientReportManager.getInstance(xMPushService.getApplicationContext()).reportEvent(xmPushActionContainerBuildContainer.getPackageName(), MIPushNotificationHelper.getInterfaceId(xmPushActionContainerBuildContainer), metaInfo.getId(), 1001, System.currentTimeMillis(), null);
            if (!TextUtils.isEmpty(metaInfo.getId())) {
                intent.putExtra("messageId", metaInfo.getId());
                intent.putExtra(ReportConstants.EVENT_MESSAGE_TYPE, 1000);
            }
        }
        if (MIPushNotificationHelper.isPassThoughMessage(xmPushActionContainerBuildContainer)) {
            PushClientReportManager.getInstance(xMPushService.getApplicationContext()).reportEvent(xmPushActionContainerBuildContainer.getPackageName(), MIPushNotificationHelper.getInterfaceId(xmPushActionContainerBuildContainer), metaInfo.getId(), 2001, System.currentTimeMillis(), null);
            if (!TextUtils.isEmpty(metaInfo.getId())) {
                intent.putExtra("messageId", metaInfo.getId());
                intent.putExtra(ReportConstants.EVENT_MESSAGE_TYPE, 2000);
            }
        }
        if (MIPushNotificationHelper.isBusinessMessage(xmPushActionContainerBuildContainer)) {
            PushClientReportManager.getInstance(xMPushService.getApplicationContext()).reportEvent(xmPushActionContainerBuildContainer.getPackageName(), MIPushNotificationHelper.getInterfaceId(xmPushActionContainerBuildContainer), metaInfo.getId(), 3001, System.currentTimeMillis(), null);
            if (!TextUtils.isEmpty(metaInfo.getId())) {
                intent.putExtra("messageId", metaInfo.getId());
                intent.putExtra(ReportConstants.EVENT_MESSAGE_TYPE, ReportConstants.AWAKE_TYPE);
            }
        }
        if (metaInfo != null && !TextUtils.isEmpty(metaInfo.getTitle()) && !TextUtils.isEmpty(metaInfo.getDescription()) && metaInfo.passThrough != 1 && (MIPushNotificationHelper.isNotifyForeground(metaInfo.getExtra()) || !MIPushNotificationHelper.isApplicationForeground(xMPushService, xmPushActionContainerBuildContainer.packageName))) {
            String str3 = null;
            if (metaInfo != null) {
                if (metaInfo.extra != null) {
                    str3 = metaInfo.extra.get(PushConstants.EXTRA_JOB_KEY);
                }
                id = str3;
                if (TextUtils.isEmpty(str3)) {
                    id = metaInfo.getId();
                }
                zIsDuplicateMessage = MiPushMessageDuplicate.isDuplicateMessage(xMPushService, xmPushActionContainerBuildContainer.packageName, id);
            } else {
                zIsDuplicateMessage = false;
                id = null;
            }
            if (zIsDuplicateMessage) {
                PushClientReportManager.getInstance(xMPushService.getApplicationContext()).reportEvent4DUPMD(xmPushActionContainerBuildContainer.getPackageName(), MIPushNotificationHelper.getInterfaceId(xmPushActionContainerBuildContainer), metaInfo.getId(), "1:" + id);
                MyLog.w("drop a duplicate message, key=" + id);
            } else {
                MIPushNotificationHelper.NotifyPushMessageInfo notifyPushMessageInfoNotifyPushMessage = MIPushNotificationHelper.notifyPushMessage(xMPushService, xmPushActionContainerBuildContainer, bArr);
                if (notifyPushMessageInfoNotifyPushMessage.traffic > 0 && !TextUtils.isEmpty(notifyPushMessageInfoNotifyPushMessage.targetPkgName)) {
                    TrafficUtils.distributionTraffic(xMPushService, notifyPushMessageInfoNotifyPushMessage.targetPkgName, notifyPushMessageInfoNotifyPushMessage.traffic, true, false, System.currentTimeMillis());
                }
                if (!MIPushNotificationHelper.isBusinessMessage(xmPushActionContainerBuildContainer) && AppInfoUtils.isAppRunning(xMPushService.getApplicationContext(), str)) {
                    Intent intent2 = new Intent(PushConstants.MIPUSH_ACTION_MESSAGE_ARRIVED);
                    intent2.putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, bArr);
                    intent2.setPackage(xmPushActionContainerBuildContainer.packageName);
                    try {
                        List<ResolveInfo> listQueryBroadcastReceivers = xMPushService.getPackageManager().queryBroadcastReceivers(intent2, 0);
                        if (listQueryBroadcastReceivers != null && !listQueryBroadcastReceivers.isEmpty()) {
                            MyLog.w("broadcast message arrived.");
                            xMPushService.sendBroadcast(intent2, MIPushHelper.getReceiverPermission(xmPushActionContainerBuildContainer.packageName));
                        }
                    } catch (Exception e) {
                        PushClientReportManager.getInstance(xMPushService.getApplicationContext()).reportEvent4ERROR(xmPushActionContainerBuildContainer.getPackageName(), MIPushNotificationHelper.getInterfaceId(xmPushActionContainerBuildContainer), metaInfo.getId(), "1");
                    }
                }
            }
            MIPushAckDispatcher.sendAckMessage(xMPushService, xmPushActionContainerBuildContainer);
            str2 = PushConstants.PUSH_SERVICE_PACKAGE_NAME;
        } else if (!PushConstants.PUSH_SERVICE_PACKAGE_NAME.contains(xmPushActionContainerBuildContainer.packageName) || xmPushActionContainerBuildContainer.isEncryptAction() || metaInfo == null || metaInfo.getExtra() == null || !metaInfo.getExtra().containsKey("ab")) {
            if (MIPushMessageRoutingSupport.shouldSendBroadcast(xMPushService, str, xmPushActionContainerBuildContainer, metaInfo)) {
                if (metaInfo != null && !TextUtils.isEmpty(metaInfo.getId())) {
                    if (MIPushNotificationHelper.isPassThoughMessage(xmPushActionContainerBuildContainer)) {
                        PushClientReportManager.getInstance(xMPushService.getApplicationContext()).reportEvent(xmPushActionContainerBuildContainer.getPackageName(), MIPushNotificationHelper.getInterfaceId(xmPushActionContainerBuildContainer), metaInfo.getId(), ReportConstants.THROUGH_TYPE_SEND_RECEIVE_BROADCAST, null);
                    } else if (MIPushNotificationHelper.isBusinessMessage(xmPushActionContainerBuildContainer)) {
                        PushClientReportManager.getInstance(xMPushService.getApplicationContext()).reportEvent4NeedDrop(xmPushActionContainerBuildContainer.getPackageName(), MIPushNotificationHelper.getInterfaceId(xmPushActionContainerBuildContainer), metaInfo.getId(), "7");
                    } else if (MIPushNotificationHelper.isNormalNotificationMessage(xmPushActionContainerBuildContainer)) {
                        PushClientReportManager.getInstance(xMPushService.getApplicationContext()).reportEvent4NeedDrop(xmPushActionContainerBuildContainer.getPackageName(), MIPushNotificationHelper.getInterfaceId(xmPushActionContainerBuildContainer), metaInfo.getId(), "8");
                    } else if (MIPushNotificationHelper.isRegisterMessage(xmPushActionContainerBuildContainer)) {
                        PushClientReportManager.getInstance(xMPushService.getApplicationContext()).reportEvent(xmPushActionContainerBuildContainer.getPackageName(), ReportConstants.REGISTER_EVENT_CHAIN_INTERFACE_ID, metaInfo.getId(), ReportConstants.REGISTER_TYPE_SEND_BROADCAST, null);
                    }
                }
                if (ActionType.Notification == xmPushActionContainerBuildContainer.action) {
                    TBase tBase = null;
                    boolean z2 = false;
                    try {
                        TBase responseMessageBodyFromContainer = UnEncryptedPushContainerHelper.getResponseMessageBodyFromContainer(xMPushService, xmPushActionContainerBuildContainer);
                        if (responseMessageBodyFromContainer == null) {
                            StringBuilder sb = new StringBuilder();
                            sb.append("receiving an un-recognized notification message. ");
                            sb.append(xmPushActionContainerBuildContainer.action);
                            tBase = responseMessageBodyFromContainer;
                            MyLog.e(sb.toString());
                        } else {
                            z2 = true;
                        }
                        tBase = responseMessageBodyFromContainer;
                    } catch (TException e2) {
                        MyLog.e("receive a message which action string is not valid. " + e2);
                        z2 = false;
                    }
                    if (z2 && (tBase instanceof XmPushActionNotification)) {
                        XmPushActionNotification xmPushActionNotification = (XmPushActionNotification) tBase;
                        if (!NotificationType.CancelPushMessage.value.equals(xmPushActionNotification.type) || xmPushActionNotification.getExtra() == null) {
                            z = true;
                        } else {
                            int i = -2;
                            String str4 = xmPushActionNotification.getExtra().get(PushConstants.PUSH_NOTIFY_ID);
                            if (!TextUtils.isEmpty(str4)) {
                                try {
                                    i = Integer.parseInt(str4);
                                } catch (NumberFormatException e3) {
                                    MyLog.w("parse notifyId from STRING to INT failed: " + e3);
                                    i = -2;
                                }
                            }
                            if (i >= -1) {
                                MyLog.w("try to retract a message by notifyId=" + i);
                                MIPushNotificationHelper.clearNotification(xMPushService, xmPushActionContainerBuildContainer.packageName, i);
                            } else {
                                String str5 = xmPushActionNotification.getExtra().get(PushConstants.PUSH_TITLE);
                                String str6 = xmPushActionNotification.getExtra().get(PushConstants.PUSH_DESCRIPTION);
                                MyLog.w("try to retract a message by title&description.");
                                MIPushNotificationHelper.clearNotification(xMPushService, xmPushActionContainerBuildContainer.packageName, str5, str6);
                            }
                            z = false;
                            MIPushAckDispatcher.sendClearPushMessageAck(xMPushService, xmPushActionContainerBuildContainer, xmPushActionNotification);
                        }
                    } else {
                        z = true;
                    }
                } else {
                    z = true;
                }
                if (z) {
                    MyLog.w("broadcast passthrough message.");
                    xMPushService.sendBroadcast(intent, MIPushHelper.getReceiverPermission(xmPushActionContainerBuildContainer.packageName));
                }
            } else {
                PushClientReportManager.getInstance(xMPushService.getApplicationContext()).reportEvent4NeedDrop(xmPushActionContainerBuildContainer.getPackageName(), MIPushNotificationHelper.getInterfaceId(xmPushActionContainerBuildContainer), metaInfo.getId(), "9");
            }
            str2 = PushConstants.PUSH_SERVICE_PACKAGE_NAME;
        } else {
            MIPushAckDispatcher.sendAckMessage(xMPushService, xmPushActionContainerBuildContainer);
            MyLog.v("receive abtest message. ack it." + metaInfo.getId());
            str2 = PushConstants.PUSH_SERVICE_PACKAGE_NAME;
        }
        if (xmPushActionContainerBuildContainer.getAction() != ActionType.UnRegistration || str2.equals(xMPushService.getPackageName())) {
            return;
        }
        xMPushService.stopSelf();
    }

    static void processMIPushMessage(XMPushService xMPushService, byte[] bArr, long j) {
        Map<String, String> extra;
        XmPushActionContainer xmPushActionContainerBuildContainer = MIPushEventProcessor.buildContainer(bArr);
        if (xmPushActionContainerBuildContainer == null) {
            return;
        }
        if (TextUtils.isEmpty(xmPushActionContainerBuildContainer.packageName)) {
            MyLog.w("receive a mipush message without package name");
            return;
        }
        Long lValueOf = Long.valueOf(System.currentTimeMillis());
        Intent intentBuildIntent = MIPushEventProcessor.buildIntent(bArr, lValueOf.longValue());
        String targetPackage = MIPushNotificationHelper.getTargetPackage(xmPushActionContainerBuildContainer);
        try {
            TrafficUtils.distributionTraffic(xMPushService, targetPackage, j, true, true, System.currentTimeMillis());
        } catch (Throwable th) {
            MyLog.e(th);
        }
        PushMetaInfo metaInfo = xmPushActionContainerBuildContainer.getMetaInfo();
        if (metaInfo != null && metaInfo.getId() != null) {
            MyLog.persist(String.format("receive a message. appid=%1$s, msgid= %2$s, action=%3$s", xmPushActionContainerBuildContainer.getAppid(), metaInfo.getId(), xmPushActionContainerBuildContainer.getAction()));
        }
        if (metaInfo != null) {
            metaInfo.putToExtra(PushConstants.MESSAGE_RECEIVE_TIME, Long.toString(lValueOf.longValue()));
        }
        if (ActionType.SendMessage == xmPushActionContainerBuildContainer.getAction() && MIPushAppInfo.getInstance(xMPushService).isUnRegistered(xmPushActionContainerBuildContainer.packageName) && !MIPushNotificationHelper.isBusinessMessage(xmPushActionContainerBuildContainer)) {
            String str = "";
            if (metaInfo != null) {
                String id = metaInfo.getId();
                str = id;
                if (MIPushNotificationHelper.isNPBMessage(xmPushActionContainerBuildContainer)) {
                    PushClientReportManager.getInstance(xMPushService.getApplicationContext()).reportEvent4NeedDrop(xmPushActionContainerBuildContainer.getPackageName(), MIPushNotificationHelper.getInterfaceId(xmPushActionContainerBuildContainer), id, "1");
                    str = id;
                }
            }
            MyLog.w("Drop a message for unregistered, msgid=" + str);
            MIPushAckDispatcher.sendAppAbsentAck(xMPushService, xmPushActionContainerBuildContainer, xmPushActionContainerBuildContainer.packageName);
            return;
        }
        if (ActionType.SendMessage == xmPushActionContainerBuildContainer.getAction() && MIPushAppInfo.getInstance(xMPushService).isPushDisabled4User(xmPushActionContainerBuildContainer.packageName) && !MIPushNotificationHelper.isBusinessMessage(xmPushActionContainerBuildContainer)) {
            String str2 = "";
            if (metaInfo != null) {
                String id2 = metaInfo.getId();
                str2 = id2;
                if (MIPushNotificationHelper.isNPBMessage(xmPushActionContainerBuildContainer)) {
                    PushClientReportManager.getInstance(xMPushService.getApplicationContext()).reportEvent4NeedDrop(xmPushActionContainerBuildContainer.getPackageName(), MIPushNotificationHelper.getInterfaceId(xmPushActionContainerBuildContainer), id2, "2");
                    str2 = id2;
                }
            }
            MyLog.w("Drop a message for push closed, msgid=" + str2);
            MIPushAckDispatcher.sendAppAbsentAck(xMPushService, xmPushActionContainerBuildContainer, xmPushActionContainerBuildContainer.packageName);
            return;
        }
        if (ActionType.SendMessage == xmPushActionContainerBuildContainer.getAction() && !TextUtils.equals(xMPushService.getPackageName(), PushConstants.PUSH_SERVICE_PACKAGE_NAME) && !TextUtils.equals(xMPushService.getPackageName(), xmPushActionContainerBuildContainer.packageName)) {
            MyLog.w("Receive a message with wrong package name, expect " + xMPushService.getPackageName() + ", received " + xmPushActionContainerBuildContainer.packageName);
            MIPushAckDispatcher.sendErrorAck(xMPushService, xmPushActionContainerBuildContainer, "unmatched_package", "package should be " + xMPushService.getPackageName() + ", but got " + xmPushActionContainerBuildContainer.packageName);
            if (metaInfo == null || !MIPushNotificationHelper.isNPBMessage(xmPushActionContainerBuildContainer)) {
                return;
            }
            PushClientReportManager.getInstance(xMPushService.getApplicationContext()).reportEvent4NeedDrop(xmPushActionContainerBuildContainer.getPackageName(), MIPushNotificationHelper.getInterfaceId(xmPushActionContainerBuildContainer), metaInfo.getId(), "3");
            return;
        }
        if (metaInfo != null && (extra = metaInfo.getExtra()) != null && extra.containsKey("hide") && "true".equalsIgnoreCase(extra.get("hide"))) {
            MIPushAckDispatcher.sendAckMessage(xMPushService, xmPushActionContainerBuildContainer);
            return;
        }
        if (metaInfo != null && metaInfo.getExtra() != null && metaInfo.getExtra().containsKey(PushConstants.EXTRA_PARAM_MIID)) {
            String str3 = metaInfo.getExtra().get(PushConstants.EXTRA_PARAM_MIID);
            String miid = SystemUtils.getMIID(xMPushService.getApplicationContext());
            if (TextUtils.isEmpty(miid) || !TextUtils.equals(str3, miid)) {
                if (MIPushNotificationHelper.isNPBMessage(xmPushActionContainerBuildContainer)) {
                    PushClientReportManager.getInstance(xMPushService.getApplicationContext()).reportEvent4NeedDrop(xmPushActionContainerBuildContainer.getPackageName(), MIPushNotificationHelper.getInterfaceId(xmPushActionContainerBuildContainer), metaInfo.getId(), "4");
                }
                MyLog.w(str3 + " should be login, but got " + miid);
                MIPushAckDispatcher.sendErrorAck(xMPushService, xmPushActionContainerBuildContainer, "miid already logout or anther already login", str3 + " should be login, but got " + miid);
                return;
            }
        }
        try {
            postProcessMIPushMessage(xMPushService, targetPackage, bArr, intentBuildIntent);
        } catch (Throwable th2) {
            MyLog.e(th2);
        }
    }
}
