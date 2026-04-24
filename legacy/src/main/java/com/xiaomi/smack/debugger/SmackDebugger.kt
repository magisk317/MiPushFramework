package com.xiaomi.smack.debugger
import io.github.magisk317.mipush.protocol.model.*

import com.xiaomi.smack.PacketListener

interface SmackDebugger {
    fun getReaderListener(): PacketListener

    fun getWriterListener(): PacketListener
}
