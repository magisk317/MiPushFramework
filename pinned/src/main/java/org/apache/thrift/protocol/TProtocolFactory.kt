package org.apache.thrift.protocol

import org.apache.thrift.transport.TTransport
import java.io.Serializable

interface TProtocolFactory : Serializable {
    fun getProtocol(tTransport: TTransport): TProtocol
}
