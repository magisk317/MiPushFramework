package com.xiaomi.smack

interface ConnectionListener {
    fun connectionClosed(connection: Connection, i: Int, exc: Exception?)

    fun connectionStarted(connection: Connection)

    fun reconnectionFailed(connection: Connection, exc: Exception)

    fun reconnectionSuccessful(connection: Connection)
}
