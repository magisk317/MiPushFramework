package com.xiaomi.clientreport.processor

import com.xiaomi.clientreport.data.BaseClientReport
import java.util.HashMap

interface IPerfProcessor : IDataSend, IWrite {
    fun setPerfMap(map: HashMap<String, HashMap<String, BaseClientReport>>)
}
