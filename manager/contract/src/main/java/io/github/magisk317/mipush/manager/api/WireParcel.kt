package io.github.magisk317.mipush.manager.api

import android.os.BadParcelableException
import android.os.Parcel
import android.os.Parcelable

internal fun Parcel.writeWireFrame(writeFields: Parcel.() -> Unit) {
    val start = dataPosition()
    writeInt(0)
    writeFields()
    val end = dataPosition()
    setDataPosition(start)
    writeInt(end - start)
    setDataPosition(end)
}

internal fun <T> Parcel.readWireFrame(readFields: WireParcelReader.() -> T): T {
    val start = dataPosition()
    val size = readInt()
    if (size < Integer.BYTES || start > Int.MAX_VALUE - size) {
        throw BadParcelableException("Invalid manager wire parcel size: $size")
    }
    if (size > ManagerProtocol.MAX_WIRE_FRAME_BYTES) {
        throw BadParcelableException("Manager wire parcel exceeds maximum size: $size")
    }
    val end = start + size
    if (end > dataSize()) {
        throw BadParcelableException("Manager wire parcel exceeds available data")
    }
    return try {
        WireParcelReader(this, end).readFields()
    } finally {
        setDataPosition(end)
    }
}

internal fun Parcel.writeWireBoolean(value: Boolean) {
    writeInt(if (value) 1 else 0)
}

internal fun Parcel.writeWireNullableLong(value: Long?) {
    writeInt(if (value == null) 0 else 1)
    value?.let { writeLong(it) }
}

internal fun Parcel.writeWireNullableInt(value: Int?) {
    writeInt(if (value == null) 0 else 1)
    value?.let { writeInt(it) }
}

internal fun Parcel.writeWireNullableParcelable(value: Parcelable?, flags: Int) {
    if (value == null) {
        writeInt(0)
    } else {
        writeInt(1)
        value.writeToParcel(this, flags)
    }
}

internal fun Parcel.writeWireByteArray(value: ByteArray?) {
    if (value == null) {
        writeInt(-1)
        return
    }
    writeInt(value.size)
    writeByteArray(value)
}


internal class WireParcelReader(
    private val source: Parcel,
    private val end: Int,
) {
    fun readInt(defaultValue: Int = 0): Int =
        if (hasRemaining(Integer.BYTES)) source.readInt() else defaultValue

    fun readLong(defaultValue: Long = 0L): Long =
        if (hasRemaining(java.lang.Long.BYTES)) source.readLong() else defaultValue

    fun readBoolean(defaultValue: Boolean = false): Boolean {
        if (!hasRemaining(Integer.BYTES)) return defaultValue
        return when (val value = source.readInt()) {
            0 -> false
            1 -> true
            else -> throw BadParcelableException("Invalid manager wire boolean: $value")
        }
    }

    fun readNullableLong(): Long? {
        if (!hasRemaining(Integer.BYTES)) return null
        return when (val present = source.readInt()) {
            0 -> null
            1 -> {
                if (!hasRemaining(java.lang.Long.BYTES)) {
                    throw BadParcelableException("Manager wire nullable long is truncated")
                }
                source.readLong()
            }

            else -> throw BadParcelableException("Invalid manager wire nullable long marker: $present")
        }
    }

    fun readNullableInt(): Int? {
        if (!hasRemaining(Integer.BYTES)) return null
        return when (val present = source.readInt()) {
            0 -> null
            1 -> {
                if (!hasRemaining(Integer.BYTES)) {
                    throw BadParcelableException("Manager wire nullable int is truncated")
                }
                source.readInt()
            }

            else -> throw BadParcelableException("Invalid manager wire nullable int marker: $present")
        }
    }

    fun readString(
        defaultValue: String? = null,
        maxLength: Int = ManagerProtocol.MAX_WIRE_STRING_LENGTH,
    ): String? {
        if (!hasRemaining(Integer.BYTES)) return defaultValue
        val value = source.readString()
        enforceFrameBoundary()
        if (value != null && value.length > maxLength) {
            throw BadParcelableException("Manager wire string exceeds limit: ${value.length}")
        }
        return value
    }

    fun readStringList(
        defaultValue: List<String> = emptyList(),
        maxItems: Int = ManagerProtocol.MAX_CAPABILITY_COUNT,
        maxItemLength: Int = ManagerProtocol.MAX_CAPABILITY_LENGTH,
    ): List<String> {
        if (!hasRemaining(Integer.BYTES)) return defaultValue
        val size = source.readInt()
        if (size < 0) throw BadParcelableException("Invalid manager wire list size: $size")
        if (size > maxItems) {
            throw BadParcelableException("Manager wire list exceeds limit: $size")
        }
        return List(size) {
            readString(maxLength = maxItemLength).orEmpty()
        }
    }

    fun <T> readParcelableList(
        creator: Parcelable.Creator<T>,
        maxItems: Int,
    ): List<T> {
        if (!hasRemaining(Integer.BYTES)) return emptyList()
        val size = source.readInt()
        if (size < 0) throw BadParcelableException("Invalid manager wire parcelable list size: $size")
        if (size > maxItems) {
            throw BadParcelableException("Manager wire parcelable list exceeds limit: $size")
        }
        return List(size) {
            val value = creator.createFromParcel(source)
            enforceFrameBoundary()
            value
        }
    }


    fun readByteArray(
        maxLength: Int = ManagerProtocol.MAX_EVENT_PAYLOAD_BYTES,
    ): ByteArray? {
        if (!hasRemaining(Integer.BYTES)) return null
        val size = source.readInt()
        if (size < 0) return null
        if (size > maxLength) {
            throw BadParcelableException("Manager wire byte array exceeds limit: $size")
        }
        if (!hasRemaining(size)) {
            throw BadParcelableException("Manager wire byte array is truncated")
        }
        val value = ByteArray(size)
        source.readByteArray(value)
        enforceFrameBoundary()
        return value
    }

    fun <T> readParcelable(creator: Parcelable.Creator<T>, defaultValue: T): T =
        if (!hasRemaining(Integer.BYTES)) defaultValue else creator.createFromParcel(source).also {
            enforceFrameBoundary()
        }

    fun <T : Parcelable> readNullableParcelable(creator: Parcelable.Creator<T>): T? {
        if (!hasRemaining(Integer.BYTES)) return null
        return when (val present = source.readInt()) {
            0 -> null
            1 -> creator.createFromParcel(source).also { enforceFrameBoundary() }
            else -> throw BadParcelableException("Invalid manager wire nullable parcelable marker: $present")
        }
    }

    private fun enforceFrameBoundary() {
        if (source.dataPosition() > end) {
            throw BadParcelableException("Manager wire field exceeds declared frame")
        }
    }

    private fun hasRemaining(byteCount: Int): Boolean = source.dataPosition() <= end - byteCount
}
