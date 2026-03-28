package com.xiaomi.push.service.awake.module;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/awake/module/AwakeInfo.class */
public class AwakeInfo {
    public static final int AWAKE_DEFAULT = 0;
    public static final int AWAKE_JUST_FOREGROUND = 1;
    private String mAction;
    private int mAwakeForeground;
    private String mAwakeInfo;
    private String mClassName;
    private String mTargetPackageName;

    public String getAction() {
        return this.mAction;
    }

    public int getAwakeForeground() {
        return this.mAwakeForeground;
    }

    public String getAwakeInfo() {
        return this.mAwakeInfo;
    }

    public String getClassName() {
        return this.mClassName;
    }

    public String getTargetPackageName() {
        return this.mTargetPackageName;
    }

    public void setAction(String str) {
        this.mAction = str;
    }

    public void setAwakeForeground(int i) {
        this.mAwakeForeground = i;
    }

    public void setAwakeInfo(String str) {
        this.mAwakeInfo = str;
    }

    public void setClassName(String str) {
        this.mClassName = str;
    }

    public void setTargetPackageName(String str) {
        this.mTargetPackageName = str;
    }
}
