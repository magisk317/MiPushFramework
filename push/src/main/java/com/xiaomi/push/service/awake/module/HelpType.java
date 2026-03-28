package com.xiaomi.push.service.awake.module;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/awake/module/HelpType.class */
public enum HelpType {
    ACTIVITY("activity"),
    SERVICE_ACTION("service_action"),
    SERVICE_COMPONENT("service_component"),
    PROVIDER("provider");

    public String typeValue;

    HelpType(String str) {
        this.typeValue = str;
    }
}
