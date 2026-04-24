package org.apache.thrift.protocol

class TSet @JvmOverloads constructor(
    @JvmField val elemType: Byte = 0,
    @JvmField val size: Int = 0,
) {
    constructor(tList: TList) : this(tList.elemType, tList.size)
}
