package com.xiaomi.clientreport.processor;

import com.xiaomi.clientreport.data.BaseClientReport;
import java.util.HashMap;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/clientreport/processor/IPerfProcessor.class */
public interface IPerfProcessor extends IDataSend, IWrite {
    void setPerfMap(HashMap<String, HashMap<String, BaseClientReport>> map);
}
