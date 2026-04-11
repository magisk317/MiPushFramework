package com.xiaomi.channel.commonutils.misc;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/misc/BuildSettings.class */
public class BuildSettings {
    public static final String BETA = "BETA";
    public static final String DEBUG = "DEBUG";
    public static final boolean IsBetaBuild;
    public static final boolean IsDebugBuild;
    public static final boolean IsDefaultChannel;
    public static final boolean IsForYYBuild;
    public static final boolean IsLogableBuild;
    public static final boolean IsRCBuild;
    public static boolean IsTestBuild = false;
    public static final String LOGABLE = "LOGABLE";
    public static final int Official = 1;
    public static final int OneBox = 3;
    public static final String ReleaseChannel;
    public static final int SandBox = 2;
    public static final String TEST = "TEST";
    public static final String YY = "YY";
    private static int envType;

    static {
        String str = DebugSwitch.sDebugServerHost ? "ONEBOX" : "@SHIP.TO.2A2FE0D7@";
        ReleaseChannel = str;
        boolean zContains = str.contains("2A2FE0D7");
        IsDefaultChannel = zContains;
        IsDebugBuild = zContains || DEBUG.equalsIgnoreCase(str);
        IsLogableBuild = LOGABLE.equalsIgnoreCase(str);
        IsForYYBuild = str.contains(YY);
        IsTestBuild = str.equalsIgnoreCase(TEST);
        IsBetaBuild = BETA.equalsIgnoreCase(str);
        boolean z = false;
        if (str.startsWith("RC")) {
            z = true;
        }
        IsRCBuild = z;
        envType = 1;
        if (str.equalsIgnoreCase("SANDBOX")) {
            envType = 2;
        } else if (str.equalsIgnoreCase("ONEBOX")) {
            envType = 3;
        } else {
            envType = 1;
        }
    }

    public static boolean IsOneBoxBuild() {
        return envType == 3;
    }

    public static boolean IsSandBoxBuild() {
        return envType == 2;
    }

    public static int getEnvType() {
        return envType;
    }

    public static void setEnvType(int i) {
        envType = i;
    }
}
