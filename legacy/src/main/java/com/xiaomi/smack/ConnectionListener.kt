package com.xiaomi.smack
import io.github.magisk317.mipush.protocol.model.*

interface ConnectionListener {
    fun connectionClosed(connection: Connection, reason: Int, error: Exception?)

    fun connectionStarted(connection: Connection)

    fun reconnectionFailed(connection: Connection, error: Exception)

    fun reconnectionSuccessful(connection: Connection)
}
