package io.github.magisk317.mipush.common.notification.iconpack

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/** One icon-pack row as published to SystemUI: the package and its PNG bytes. */
class IconPackSnapshotEntry(val packageName: String, val pngBytes: ByteArray)

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
    const val VERSION: Short = 1

    // A snapshot holds one row per configured package; anything larger is a corrupt header, and
    // preallocating from it would cost memory SystemUI cannot recover.
    private const val MAX_ENTRIES = 20_000

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
            val name = ByteArray(data.readInt())
            data.readFully(name)
            val png = ByteArray(data.readInt())
            data.readFully(png)
            entries.add(IconPackSnapshotEntry(String(name, Charsets.UTF_8), png))
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
