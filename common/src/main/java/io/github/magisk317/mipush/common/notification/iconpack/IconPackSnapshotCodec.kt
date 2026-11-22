package io.github.magisk317.mipush.common.notification.iconpack

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/** Which configured icon-pack layer supplied an entry. */
enum class IconPackSource(val wireId: Int) {
    USER_OVERRIDE(1),
    ANIP(2),
    ;

    companion object {
        fun fromWireId(value: Int): IconPackSource? = entries.firstOrNull { it.wireId == value }
    }
}

/** One icon-pack row as published to SystemUI: the package, source layer and PNG bytes. */
class IconPackSnapshotEntry(
    val packageName: String,
    val pngBytes: ByteArray,
    val source: IconPackSource = IconPackSource.ANIP,
)

/**
 * A complete icon-pack index.
 *
 * Whole-index by design: a package missing from [entries] is a confirmed "not configured", which is
 * a different answer from "the runtime could not build an index yet". Consumers may cache the first
 * and must never cache the second.
 */
class IconPackSnapshot(
    val releaseStamp: Long,
    val generatedAtMs: Long,
    val entries: List<IconPackSnapshotEntry>,
) {
    fun indexOfPackage(): Map<String, ByteArray> = entries.associate { it.packageName to it.pngBytes }

    fun entriesByPackage(): Map<String, IconPackSnapshotEntry> = entries.associateBy { it.packageName }
}

/**
 * Binary container for [IconPackSnapshot].
 *
 * Layout: magic(int) version(short) releaseStamp(long) generatedAt(long) count(int), then per entry
 * a length-prefixed UTF-8 package name and a length-prefixed PNG. Decodable in one pass on
 * SystemUI's worker thread, and the header alone answers "is this the release I already hold".
 */
object IconPackSnapshotCodec {
    const val MAGIC = 0x4D504953 // "MPIS"
    const val VERSION: Short = 2

    // A snapshot holds one row per configured package; anything larger is a corrupt header, and
    // preallocating from it would cost memory SystemUI cannot recover.
    private const val MAX_ENTRIES = 20_000
    private const val MAX_PACKAGE_NAME_BYTES = 512
    private const val MAX_PNG_BYTES = 16 * 1024 * 1024

    fun write(snapshot: IconPackSnapshot, output: OutputStream): Int {
        val data = DataOutputStream(output)
        data.writeInt(MAGIC)
        data.writeShort(VERSION.toInt())
        data.writeLong(snapshot.releaseStamp)
        data.writeLong(snapshot.generatedAtMs)
        data.writeInt(snapshot.entries.size)
        snapshot.entries.forEach { entry ->
            val name = entry.packageName.toByteArray(Charsets.UTF_8)
            data.writeInt(name.size)
            data.write(name)
            data.writeByte(entry.source.wireId)
            data.writeInt(entry.pngBytes.size)
            data.write(entry.pngBytes)
        }
        data.flush()
        return snapshot.entries.size
    }

    /** Null means unusable: wrong magic, unknown version, or a truncated write. */
    fun read(input: InputStream): IconPackSnapshot? = runCatching {
        val data = DataInputStream(input)
        if (data.readInt() != MAGIC || data.readShort() != VERSION) return null
        val releaseStamp = data.readLong()
        val generatedAtMs = data.readLong()
        val count = data.readInt()
        if (count < 0 || count > MAX_ENTRIES) return null
        val entries = ArrayList<IconPackSnapshotEntry>(count)
        repeat(count) {
            val nameLength = data.readInt()
            if (nameLength <= 0 || nameLength > MAX_PACKAGE_NAME_BYTES) return null
            val name = ByteArray(nameLength)
            data.readFully(name)
            val source = IconPackSource.fromWireId(data.readUnsignedByte()) ?: return null
            val pngLength = data.readInt()
            if (pngLength < 0 || pngLength > MAX_PNG_BYTES) return null
            val png = ByteArray(pngLength)
            data.readFully(png)
            entries.add(IconPackSnapshotEntry(String(name, Charsets.UTF_8), png, source))
        }
        IconPackSnapshot(releaseStamp, generatedAtMs, entries)
    }.getOrNull()

    /**
     * Release stamp of an existing snapshot without decoding its entries, so the provider can answer
     * a request straight from the file it already has.
     */
    fun readReleaseStamp(input: InputStream): Long? = runCatching {
        val data = DataInputStream(input)
        if (data.readInt() != MAGIC || data.readShort() != VERSION) return null
        data.readLong()
    }.getOrNull()
}
