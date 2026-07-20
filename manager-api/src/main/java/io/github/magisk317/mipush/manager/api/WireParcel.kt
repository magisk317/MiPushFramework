package io.github.magisk317.mipush.manager.api

import android.os.BadParcelableException
import android.os.Parcel

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

internal class WireParcelReader(
    private val source: Parcel,
    private val end: Int,
) {
    fun readInt(defaultValue: Int = 0): Int =
        if (hasRemaining(Integer.BYTES)) source.readInt() else defaultValue

    fun readLong(defaultValue: Long = 0L): Long =
        if (hasRemaining(java.lang.Long.BYTES)) source.readLong() else defaultValue

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
        if (size < 0) return emptyList()
        if (size > maxItems) {
            throw BadParcelableException("Manager wire list exceeds limit: $size")
        }
        return List(size) {
            readString(maxLength = maxItemLength).orEmpty()
        }
    }

    private fun enforceFrameBoundary() {
        if (source.dataPosition() > end) {
            throw BadParcelableException("Manager wire field exceeds declared frame")
        }
    }

    private fun hasRemaining(byteCount: Int): Boolean = source.dataPosition() <= end - byteCount
}
