package com.xiaomi.push.service;

import android.content.Context;
import com.xiaomi.channel.commonutils.android.AppInfoUtils;
import com.xiaomi.channel.commonutils.android.SystemUtils;
import com.xiaomi.mipush.sdk.Constants;
import com.xiaomi.push.service.PushClientsManager;
import java.util.Locale;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/MIPushAccount.class */
public class MIPushAccount {
    public static final String PREF_KEY_APP_ID = "app_id";
    public static final String PREF_KEY_APP_TOKEN = "app_token";
    public static final String PREF_KEY_DEVICE_ID = "device_id";
    public static final String PREF_KEY_PACKAGENAME = "package_name";
    public static final String PREF_KEY_SECURITY = "security";
    public static final String PREF_KEY_TOKEN = "token";
    public static final String PREF_KEY_UUID = "uuid";
    public final String account;
    public final String appId;
    public final String appToken;
    public final int envType;
    public final String packageName;
    public final String security;
    public final String token;

    public MIPushAccount(String str, String str2, String str3, String str4, String str5, String str6, int i) {
        this.account = str;
        this.token = str2;
        this.security = str3;
        this.appId = str4;
        this.appToken = str5;
        this.packageName = str6;
        this.envType = i;
    }

    public static boolean isAbTestSupported(Context context) {
        return PushConstants.PUSH_SERVICE_PACKAGE_NAME.equals(context.getPackageName()) && isMIUIAlphaVersion();
    }

    public static boolean isMIUIAlphaVersion() {
        try {
            return SystemUtils.loadClass(null, "miui.os.Build").getField("IS_ALPHA_BUILD").getBoolean(null);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isMIUIPush(Context context) {
        return context.getPackageName().equals(PushConstants.PUSH_SERVICE_PACKAGE_NAME);
    }

    public PushClientsManager.ClientLoginInfo toClientLoginInfo(Context context) {
        return toClientLoginInfo(new PushClientsManager.ClientLoginInfo(), context, new ClientEventDispatcher(), "d");
    }

    public PushClientsManager.ClientLoginInfo toClientLoginInfo(PushClientsManager.ClientLoginInfo clientLoginInfo, Context context, ClientEventDispatcher clientEventDispatcher, String str) {
        clientLoginInfo.pkgName = context.getPackageName();
        clientLoginInfo.userId = this.account;
        clientLoginInfo.security = this.security;
        clientLoginInfo.token = this.token;
        clientLoginInfo.chid = "5";
        clientLoginInfo.authMethod = "XMPUSH-PASS";
        clientLoginInfo.kick = false;
        clientLoginInfo.clientExtra = String.format("%1$s:%2$s,%3$s:%4$s,%5$s:%6$s:%7$s:%8$s,%9$s:%10$s,%11$s:%12$s", "sdk_ver", 41, PushConstants.KEY_CHANNEL_PUSH_VERSION_NAME, PushConstants.PUSH_VERSION_NAME, PushConstants.KEY_CHANNEL_PUSH_VERSION_CODE, Integer.valueOf(PushConstants.PUSH_VERSION_CODE), PushConstants.RUNNING_APP_PACKAGE_NAMES, isMIUIPush(context) ? AppInfoUtils.getRunningAppPkgNames(context) : "", PushConstants.KEY_COUNTRY_CODE, AppRegionStorage.getInstance(context).getCountryCode(), PushConstants.KEY_REGION, AppRegionStorage.getInstance(context).getRegion());
        clientLoginInfo.cloudExtra = String.format("%1$s:%2$s,%3$s:%4$s,%5$s:%6$s,sync:1", PushServiceConstants.EXTENSION_ATTRIBUTE_OPENPLATFORM_APPID, isMIUIPush(context) ? MIPushAccountUtils.MIPUSH_MIUI_APPID : this.appId, "locale", Locale.getDefault().toString(), Constants.EXTRA_KEY_MIID, SystemUtils.getMIID(context));
        if (isAbTestSupported(context)) {
            clientLoginInfo.cloudExtra += String.format(",%1$s:%2$s", "ab", str);
        }
        clientLoginInfo.mClientEventDispatcher = clientEventDispatcher;
        return clientLoginInfo;
    }

    public PushClientsManager.ClientLoginInfo toClientLoginInfo(XMPushService xMPushService) {
        PushClientsManager.ClientLoginInfo clientLoginInfo = new PushClientsManager.ClientLoginInfo(xMPushService);
        toClientLoginInfo(clientLoginInfo, xMPushService, xMPushService.getClientEventDispatcher(), "c");
        return clientLoginInfo;
    }
}
