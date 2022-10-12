package com.xiaomi.push.service;

import static com.xiaomi.push.service.MIPushEventProcessor.buildContainer;
import static com.xiaomi.push.service.MIPushEventProcessor.buildIntent;
import static com.xiaomi.push.service.MiPushMsgAck.geoMessageIsValidated;
import static com.xiaomi.push.service.MiPushMsgAck.processGeoMessage;
import static com.xiaomi.push.service.MiPushMsgAck.sendAckMessage;
import static com.xiaomi.push.service.MiPushMsgAck.sendAppAbsentAck;
import static com.xiaomi.push.service.MiPushMsgAck.sendAppNotInstallNotification;
import static com.xiaomi.push.service.MiPushMsgAck.sendErrorAck;
import static com.xiaomi.push.service.MiPushMsgAck.shouldSendBroadcast;
import static com.xiaomi.push.service.MiPushMsgAck.verifyGeoMessage;
import static com.xiaomi.push.service.PushServiceConstants.PREF_KEY_REGISTERED_PKGS;

import android.accounts.Account;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.elvishew.xlog.Logger;
import com.elvishew.xlog.XLog;
import com.xiaomi.channel.commonutils.android.AppInfoUtils;
import com.xiaomi.channel.commonutils.android.MIIDUtils;
import com.xiaomi.xmpush.thrift.ActionType;
import com.xiaomi.xmpush.thrift.PushMetaInfo;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;
import com.xiaomi.xmsf.BuildConfig;
import com.xiaomi.xmsf.R;

import java.util.Map;

import top.trumeet.common.cache.ApplicationNameCache;
import top.trumeet.common.db.RegisteredApplicationDb;
import top.trumeet.common.register.RegisteredApplication;


/**
 *
 * @author zts1993
 * @date 2018/2/8
 */

public class MyMIPushMessageProcessor {
    private static Logger logger = XLog.tag("MyMIPushMessageProcessor").build();

    public static void process(XMPushService pushService, byte[] decryptedContent, long packetBytesLen) {
        try {
            XmPushActionContainer container = buildContainer(decryptedContent);
            if (container == null) {
                return;
            }

            Long receiveTime = Long.valueOf(System.currentTimeMillis());
            Intent intent = buildIntent(decryptedContent, receiveTime.longValue());
            String realTargetPackage = MIPushNotificationHelper.getTargetPackage(container);
            PushMetaInfo metaInfo = container.getMetaInfo();
            if (metaInfo != null) {
                metaInfo.putToExtra(PushConstants.MESSAGE_RECEIVE_TIME, Long.toString(receiveTime.longValue()));
            }

            boolean isSendMessage = ActionType.SendMessage == container.getAction();
            boolean isBusinessMessage = MIPushNotificationHelper.isBusinessMessage(container);

            if (isSendMessage && MIPushAppInfo.getInstance(pushService).isUnRegistered(container.packageName) && !isBusinessMessage) {
                String msgId = "";
                if (metaInfo != null) {
                    msgId = metaInfo.getId();
                }
                logger.w("Drop a message for unregistered, msgid=" + msgId);
                sendAppAbsentAck(pushService, container, container.packageName);
            } else if (isSendMessage && MIPushAppInfo.getInstance(pushService).isPushDisabled4User(container.packageName) && !isBusinessMessage) {
                String msgId2 = "";
                if (metaInfo != null) {
                    msgId2 = metaInfo.getId();
                }
                logger.w("Drop a message for push closed, msgid=" + msgId2);
                sendAppAbsentAck(pushService, container, container.packageName);
            } else if (isSendMessage && !TextUtils.equals(pushService.getPackageName(), PushConstants.PUSH_SERVICE_PACKAGE_NAME) && !TextUtils.equals(pushService.getPackageName(), container.packageName)) {
                logger.w("Receive a message with wrong package name, expect " + pushService.getPackageName() + ", received " + container.packageName);
                sendErrorAck(pushService, container, "unmatched_package", "package should be " + pushService.getPackageName() + ", but got " + container.packageName);
            } else {
                if (metaInfo != null && metaInfo.getId() != null) {
                    logger.i(String.format("receive a message, appid=%s, msgid= %s", container.getAppid(), metaInfo.getId()));
                }

                if (metaInfo != null) {
                    Map<String, String> var17 = metaInfo.getExtra();
                    if (var17 != null && var17.containsKey("hide") && "true".equalsIgnoreCase(var17.get("hide"))) {
                        logger.i(String.format("hide a message, appid=%s, msgid= %s", container.getAppid(), metaInfo.getId()));
                        sendAckMessage(pushService, container);
                        return;
                    }
                }

                if ((metaInfo != null) && (metaInfo.getExtra() != null) && (metaInfo.getExtra().containsKey("__miid"))) {
                    String str2 = metaInfo.getExtra().get("__miid");
                    Account localAccount = MIIDUtils.getXiaomiAccount(pushService);
                    String oldAccount = "";
                    if (localAccount == null) {
                        // xiaomi account login ?
                        oldAccount = "nothing";
                    } else {
                        if (TextUtils.equals(str2, localAccount.name)) {

                        } else {
                            oldAccount = localAccount.name;
                            logger.w(str2 + " should be login, but got " + localAccount);
                        }
                    }

                    if (!oldAccount.isEmpty()) {
                        logger.w("miid already logout or anther already login :" + oldAccount);
                        sendErrorAck(pushService, container, "miid already logout or anther already login", oldAccount);
                    }
                }

                boolean relatedToGeo = metaInfo != null && verifyGeoMessage(metaInfo.getExtra());
                if (relatedToGeo) {
                    if (!geoMessageIsValidated(pushService, container)) {
                        return;
                    }

                    boolean var10 = processGeoMessage(pushService, metaInfo, decryptedContent);
                    MIPushEventProcessor.sendGeoAck(pushService, container, true, false, false);
                    if (!var10) {
                        return;
                    }
                }

                postProcessMIPushMessage(pushService, realTargetPackage, decryptedContent, intent, relatedToGeo);

            }


        } catch (RuntimeException e2) {
            logger.e("fallbackProcessMIPushMessage failed at" + System.currentTimeMillis(), e2);
        }
    }


    /**
     * @see MIPushEventProcessor#postProcessMIPushMessage
     */
    private static void postProcessMIPushMessage(XMPushService pushService, String realTargetPackage, byte[] decryptedContent, Intent intent, boolean relateToGeo) {
        XmPushActionContainer container = buildContainer(decryptedContent);
        PushMetaInfo metaInfo = container.getMetaInfo();

        boolean pkgInstalled = AppInfoUtils.isPkgInstalled(pushService, container.packageName);
        if (!pkgInstalled) {
            sendAppNotInstallNotification(pushService, container);
            return;
        }

        String targetPackage = MIPushNotificationHelper.getTargetPackage(container);

        boolean isBusinessMessage = MIPushNotificationHelper.isBusinessMessage(container);
        if (isBusinessMessage) {
            if (ActionType.Registration == container.getAction()) {
                String str2 = container.getPackageName();
                SharedPreferences.Editor localEditor = pushService.getSharedPreferences(PREF_KEY_REGISTERED_PKGS, 0).edit();
                localEditor.putString(str2, container.appid);
                localEditor.apply();
                com.xiaomi.tinyData.TinyDataManager.getInstance(pushService).processPendingData("Register Success, package name is " + str2);
            }
        }

        if (metaInfo == null) {
            return;
        }

        //abtest
        if (BuildConfig.APPLICATION_ID.contains(targetPackage) && !container.isEncryptAction() &&
                metaInfo.getExtra() != null && metaInfo.getExtra().containsKey("ab")) {
            sendAckMessage(pushService, container);
            logger.i("receive abtest message. ack it." + metaInfo.getId());
            return;
        }

        String title = metaInfo.getTitle();
        String description = metaInfo.getDescription();

        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(description)) {
        } else {

            if (TextUtils.isEmpty(title)) {
                CharSequence appName = ApplicationNameCache.getInstance().getAppName(pushService, targetPackage);
                metaInfo.setTitle(appName.toString());
            }

            if (TextUtils.isEmpty(description)) {
                metaInfo.setDescription(pushService.getString(R.string.see_pass_though_msg));
            }

        }


        RegisteredApplication application = RegisteredApplicationDb.registerApplication(
                targetPackage, false, pushService, null);
        if (!TextUtils.isEmpty(metaInfo.getTitle()) && !TextUtils.isEmpty(metaInfo.getDescription()) &&
                (metaInfo.passThrough != 1 || application.isShowPassThrough())) {

            String idKey = null;
            if (metaInfo.extra != null) {
                idKey = metaInfo.extra.get("jobkey");
            }
            if (TextUtils.isEmpty(idKey)) {
                idKey = metaInfo.getId();
            }
            boolean isDuplicateMessage = MiPushMessageDuplicate.isDuplicateMessage(pushService, targetPackage, idKey);
            if (isDuplicateMessage) {
                logger.w("drop a duplicate message, key=" + idKey);
            } else {
                MyMIPushNotificationHelper.notifyPushMessage(pushService, container, decryptedContent);

                //send broadcast
                if (!isBusinessMessage) {
                    Intent localIntent = new Intent(PushConstants.MIPUSH_ACTION_MESSAGE_ARRIVED);
                    localIntent.putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, decryptedContent);
                    localIntent.putExtra(MIPushNotificationHelper.FROM_NOTIFICATION, true);
                    localIntent.setPackage(targetPackage);

                    pushService.sendBroadcast(localIntent, ClientEventDispatcher.getReceiverPermission(targetPackage));
                }

            }

            if (relateToGeo) {
                MIPushEventProcessor.sendGeoAck(pushService, container, false, true, false);
            } else {
                sendAckMessage(pushService, container);
            }
        } else if (shouldSendBroadcast(pushService, targetPackage, container, metaInfo)) {
            pushService.sendBroadcast(intent, ClientEventDispatcher.getReceiverPermission(targetPackage));
        }

    }


}
