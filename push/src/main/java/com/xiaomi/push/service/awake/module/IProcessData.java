package com.xiaomi.push.service.awake.module;

import android.content.Context;
import java.util.HashMap;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/awake/module/IProcessData.class */
public interface IProcessData {
    void sendByTinyData(Context context, HashMap<String, String> map);

    void sendDirectly(Context context, HashMap<String, String> map);

    void shouldDoLast(Context context, HashMap<String, String> map);
}
