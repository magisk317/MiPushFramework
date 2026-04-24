package com.xiaomi.push.service.awake.module
import io.github.magisk317.mipush.protocol.model.*

import android.content.Context
import java.util.HashMap

interface IProcessData {
    fun sendByTinyData(context: Context, map: HashMap<String, String>)

    fun sendDirectly(context: Context, map: HashMap<String, String>)

    fun shouldDoLast(context: Context, map: HashMap<String, String>)
}
