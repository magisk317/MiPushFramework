package org.apache.thrift.protocol

class TField @JvmOverloads constructor(
    @JvmField val name: String = "",
    @JvmField val type: Byte = 0,
    @JvmField val id: Short = 0,
) {
    constructor(name: String, type: Byte, id: Int) : this(name, type, id.toShort())

    fun equals(tField: TField): Boolean {
        return type == tField.type && id == tField.id
    }

    override fun toString(): String {
        return "<TField name:'$name' type:${type.toInt()} field-id:${id.toInt()}>"
    }
}
