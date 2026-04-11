package com.xiaomi.clientreport.processor

interface IDataSend {
    fun readAndSend()

    fun send(list: List<String>)
}
