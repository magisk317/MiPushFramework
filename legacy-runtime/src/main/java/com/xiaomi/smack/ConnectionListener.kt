package com.xiaomi.smack

interface ConnectionListener {
    fun connectionClosed(connection: Connection, reason: Int, error: Exception?)

    fun connectionStarted(connection: Connection)

    fun reconnectionFailed(connection: Connection, error: Exception)

    fun reconnectionSuccessful(connection: Connection)
}
