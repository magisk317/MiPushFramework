package org.apache.thrift.protocol

object TMessageType {
    const val CALL: Byte = 1
    const val REPLY: Byte = 2
    const val EXCEPTION: Byte = 3
    const val ONEWAY: Byte = 4
}
