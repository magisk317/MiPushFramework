package com.xiaomi.smack.debugger

import com.xiaomi.smack.PacketListener

interface SmackDebugger {
    fun getReaderListener(): PacketListener

    fun getWriterListener(): PacketListener
}
