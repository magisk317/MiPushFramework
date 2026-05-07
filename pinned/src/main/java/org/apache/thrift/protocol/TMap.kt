package org.apache.thrift.protocol

class TMap @JvmOverloads constructor(
    @JvmField val keyType: Byte = 0,
    @JvmField val valueType: Byte = 0,
    @JvmField val size: Int = 0,
)
