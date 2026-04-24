package com.xiaomi.clientreport.processor
import io.github.magisk317.mipush.protocol.model.*

import com.xiaomi.clientreport.data.BaseClientReport
import java.util.HashMap

interface IPerfProcessor : IDataSend, IWrite {
    fun setPerfMap(map: HashMap<String, HashMap<String, BaseClientReport>>)
}
