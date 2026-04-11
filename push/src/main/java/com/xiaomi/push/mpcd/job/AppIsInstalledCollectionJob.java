package com.xiaomi.push.mpcd.job;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.android.AppInfoUtils;
import com.xiaomi.channel.commonutils.string.Base64Coder;
import com.xiaomi.push.mpcd.Constants;
import com.xiaomi.xmpush.thrift.ClientCollectionType;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/mpcd/job/AppIsInstalledCollectionJob.class */
public class AppIsInstalledCollectionJob extends CollectionJob {
    private String mApps;

    public AppIsInstalledCollectionJob(Context context, int i, String str) {
        super(context, i);
        this.mApps = str;
    }

    private String[] revertAppList() {
        if (TextUtils.isEmpty(this.mApps)) {
            return null;
        }
        String strDecodeString = Base64Coder.decodeString(this.mApps);
        if (TextUtils.isEmpty(strDecodeString)) {
            return null;
        }
        return strDecodeString.contains(",") ? strDecodeString.split(",") : new String[]{strDecodeString};
    }

    private static String getInstallerPackageNameCompat(PackageManager packageManager, String str) {
        try {
            Object installSourceInfo = PackageManager.class.getMethod("getInstallSourceInfo", String.class).invoke(packageManager, str);
            if (installSourceInfo == null) {
                return null;
            }
            Object invoke = installSourceInfo.getClass().getMethod("getInstallingPackageName").invoke(installSourceInfo);
            return invoke instanceof String ? (String) invoke : null;
        } catch (ReflectiveOperationException unused) {
            try {
                Object invoke = PackageManager.class.getMethod("getInstallerPackageName", String.class).invoke(packageManager, str);
                return invoke instanceof String ? (String) invoke : null;
            } catch (ReflectiveOperationException e) {
                return null;
            }
        }
    }

    @Override // com.xiaomi.push.mpcd.job.CollectionJob
    public String collectInfo() {
        String[] strArrRevertAppList = revertAppList();
        if (strArrRevertAppList == null || strArrRevertAppList.length <= 0) {
            return null;
        }
        PackageManager packageManager = this.context.getPackageManager();
        StringBuilder sb = new StringBuilder();
        for (String str : strArrRevertAppList) {
            try {
                PackageInfo packageInfo = packageManager.getPackageInfo(str, 16384);
                if (packageInfo != null) {
                    if (sb.length() > 0) {
                        sb.append(Constants.ITEM_SEPARATOR);
                    }
                    String installerPackageName = getInstallerPackageNameCompat(packageManager, str);
                    if (TextUtils.isEmpty(installerPackageName)) {
                        installerPackageName = "null";
                    }
                    sb.append(packageInfo.applicationInfo.loadLabel(packageManager).toString());
                    sb.append(",");
                    sb.append(packageInfo.packageName);
                    sb.append(",");
                    sb.append(packageInfo.versionName);
                    sb.append(",");
                    sb.append(AppInfoUtils.getVersionCode(this.context, packageInfo.packageName));
                    sb.append(",");
                    sb.append(packageInfo.firstInstallTime);
                    sb.append(",");
                    sb.append(packageInfo.lastUpdateTime);
                    sb.append(",");
                    sb.append(installerPackageName);
                }
            } catch (Exception e2) {
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    @Override // com.xiaomi.push.mpcd.job.CollectionJob
    public ClientCollectionType getCollectionType() {
        return ClientCollectionType.AppIsInstalled;
    }

    @Override // com.xiaomi.channel.commonutils.misc.ScheduledJobManager.Job
    public String getJobId() {
        return "24";
    }
}
