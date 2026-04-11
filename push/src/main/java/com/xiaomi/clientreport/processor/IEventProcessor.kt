package com.xiaomi.clientreport.processor

import com.xiaomi.clientreport.data.BaseClientReport
import java.util.ArrayList
import java.util.HashMap

interface IEventProcessor : IDataSend, IWrite {
    fun bytesToString(bArr: ByteArray): String

    fun setEventMap(map: HashMap<String, ArrayList<BaseClientReport>>)

    fun stringToBytes(str: String): ByteArray
}
