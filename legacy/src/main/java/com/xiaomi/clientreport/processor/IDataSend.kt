package com.xiaomi.clientreport.processor
import io.github.magisk317.mipush.protocol.model.*

interface IDataSend {
    fun readAndSend()

    fun send(list: List<String>)
}
