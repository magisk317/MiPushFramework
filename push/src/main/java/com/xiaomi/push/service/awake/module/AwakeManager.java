package com.xiaomi.push.service.awake.module;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager;
import com.xiaomi.push.service.MIPushNotificationHelper;
import com.xiaomi.push.service.awake.AwakeUploadHelper;
import java.util.HashMap;
import org.json.JSONException;
import org.json.JSONObject;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/awake/module/AwakeManager.class */
public final class AwakeManager {
    private static volatile AwakeManager sInstance;
    private String mAppId;
    private Context mContext;
    private HashMap<HelpType, IAwakeModule> mModuleMap;
    private int mOnLineCmd;
    private String mPackageName;
    private IProcessData mSendDataIml;

    private AwakeManager(Context context) {
        HashMap<HelpType, IAwakeModule> map = new HashMap<>();
        this.mModuleMap = map;
        this.mContext = context;
        map.put(HelpType.SERVICE_ACTION, new ServiceActionAwakeModule());
        this.mModuleMap.put(HelpType.SERVICE_COMPONENT, new ServiceComponentAwakeModule());
        this.mModuleMap.put(HelpType.ACTIVITY, new ActivityActionAwakeModule());
        this.mModuleMap.put(HelpType.PROVIDER, new ProviderAwakeModule());
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void doAwake(HelpType helpType, Context context, AwakeInfo awakeInfo) {
        this.mModuleMap.get(helpType).doAwake(context, awakeInfo);
    }

    public static AwakeManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (AwakeManager.class) {
                try {
                    if (sInstance == null) {
                        sInstance = new AwakeManager(context);
                    }
                } catch (Throwable th) {
                    throw th;
                }
            }
        }
        return sInstance;
    }

    public static boolean isMeForeground(Context context) {
        return MIPushNotificationHelper.isApplicationForeground(context, context.getPackageName());
    }

    public String getAppId() {
        return this.mAppId;
    }

    public int getOnLineCmd() {
        return this.mOnLineCmd;
    }

    public String getPackageName() {
        return this.mPackageName;
    }

    public IProcessData getSendDataIml() {
        return this.mSendDataIml;
    }

    public void sendResult(HelpType helpType, Context context, Intent intent, String str) {
        if (helpType != null) {
            this.mModuleMap.get(helpType).doSendAwakeResult(context, intent, str);
        } else {
            AwakeUploadHelper.uploadData(context, "null", 1008, "A receive a incorrect message with empty type");
        }
    }

    public void setAppId(String str) {
        this.mAppId = str;
    }

    public void setOnLineCmd(int i) {
        this.mOnLineCmd = i;
    }

    public void setPackageInfo(String str, String str2, int i) {
        setAppId(str);
        setPackageName(str2);
        setOnLineCmd(i);
    }

    public void setPackageInfo(String str, String str2, int i, IProcessData iProcessData) {
        setAppId(str);
        setPackageName(str2);
        setOnLineCmd(i);
        setSendDataIml(iProcessData);
    }

    public void setPackageName(String str) {
        this.mPackageName = str;
    }

    public void setSendDataIml(IProcessData iProcessData) {
        this.mSendDataIml = iProcessData;
    }

    public void wakeup(final Context context, final String str, int i, final String str2, final String str3) {
        if (context != null && !TextUtils.isEmpty(str) && !TextUtils.isEmpty(str2) && !TextUtils.isEmpty(str3)) {
            setOnLineCmd(i);
            ScheduledJobManager.getInstance(this.mContext).addOneShootJob(new Runnable() { // from class: com.xiaomi.push.service.awake.module.AwakeManager.1
                @Override // java.lang.Runnable
                public void run() {
                    if (TextUtils.isEmpty(str)) {
                        AwakeUploadHelper.uploadData(context, "null", 1008, "A receive a incorrect message with empty info");
                        return;
                    }
                    try {
                        AwakeUploadHelper.uploadData(context, str, 1001, "get message");
                        JSONObject jSONObject = new JSONObject(str);
                        String strOptString = jSONObject.optString("action");
                        String strOptString2 = jSONObject.optString("awakened_app_packagename");
                        String strOptString3 = jSONObject.optString("awake_app_packagename");
                        String strOptString4 = jSONObject.optString("awake_app");
                        String strOptString5 = jSONObject.optString("awake_type");
                        int iOptInt = jSONObject.optInt("awake_foreground", 0);
                        if (str2.equals(strOptString3) && str3.equals(strOptString4)) {
                            if (TextUtils.isEmpty(strOptString5) || TextUtils.isEmpty(strOptString3) || TextUtils.isEmpty(strOptString4) || TextUtils.isEmpty(strOptString2)) {
                                AwakeUploadHelper.uploadData(context, str, 1008, "A receive a incorrect message with empty type");
                                return;
                            }
                            AwakeManager.this.setPackageName(strOptString3);
                            AwakeManager.this.setAppId(strOptString4);
                            AwakeInfo awakeInfo = new AwakeInfo();
                            awakeInfo.setAction(strOptString);
                            awakeInfo.setTargetPackageName(strOptString2);
                            awakeInfo.setAwakeForeground(iOptInt);
                            awakeInfo.setAwakeInfo(str);
                            if ("service".equals(strOptString5)) {
                                if (TextUtils.isEmpty(strOptString)) {
                                    awakeInfo.setClassName("com.xiaomi.mipush.sdk.PushMessageHandler");
                                    AwakeManager.this.doAwake(HelpType.SERVICE_COMPONENT, context, awakeInfo);
                                } else {
                                    AwakeManager.this.doAwake(HelpType.SERVICE_ACTION, context, awakeInfo);
                                }
                            } else if (HelpType.ACTIVITY.typeValue.equals(strOptString5)) {
                                AwakeManager.this.doAwake(HelpType.ACTIVITY, context, awakeInfo);
                            } else if (HelpType.PROVIDER.typeValue.equals(strOptString5)) {
                                AwakeManager.this.doAwake(HelpType.PROVIDER, context, awakeInfo);
                            } else {
                                AwakeUploadHelper.uploadData(context, str, 1008, "A receive a incorrect message with unknown type " + strOptString5);
                            }
                            return;
                        }
                        AwakeUploadHelper.uploadData(context, str, 1008, "A receive a incorrect message with incorrect package info" + strOptString3);
                    } catch (JSONException e) {
                        MyLog.e(e);
                        AwakeUploadHelper.uploadData(context, str, 1008, "A meet a exception when receive the message");
                    }
                }
            });
        } else {
            AwakeUploadHelper.uploadData(context, "" + str, 1008, "A receive a incorrect message");
        }
    }
}
