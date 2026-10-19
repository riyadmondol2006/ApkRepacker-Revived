package com.riyadm.apkrepacker.utils.qickedit

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipException

/**
 * Rewrites an APK replacing only a few entries. Every other entry is copied raw: its compressed
 * bytes, method, CRC and sizes stay exactly as in the source, so nothing is inflated or deflated
 * again. Entries are read through the central directory (what Android reads), data descriptors
 * are dropped (sizes go into the local header), and STORED entries are aligned the way apksig and
 * zipalign do it: 4 bytes, 16 KB for native libraries. Replaced entries are written DEFLATED.
 * The APK signing block of the source is not copied: the output is signed again anyway.
 */
object ZipRepacker {

    private const val LOCAL_SIG = 0x04034b50
    private const val CENTRAL_SIG = 0x02014b50
    private const val EOCD_SIG = 0x06054b50
    private const val LOCAL_HEADER_SIZE = 30
    private const val CENTRAL_HEADER_SIZE = 46
    private const val EOCD_SIZE = 22
    private const val STORED = 0
    private const val DEFLATED = 8
    /** Bits kept from the source flags: deflate options (1, 2) and UTF-8 names (11). */
    private const val KEPT_FLAGS = 0x0806
    private const val ALIGNMENT_EXTRA_ID = 0xd935
    private const val ALIGNMENT = 4
    private const val LIBRARY_ALIGNMENT = 16384

    private class Entry(
        val name: String, val nameBytes: ByteArray, val versionMadeBy: Int, val versionNeeded: Int,
        val flags: Int, val method: Int, val time: Int, val date: Int, val crc: Int,
        val compressedSize: Long, val size: Long, val internalAttrs: Int, val externalAttrs: Int,
        val localOffset: Long, val dataOffset: Long,
    )

    /**
     * Writes [src] to [dst]. [replace] gets every entry name and returns the file with that entry's
     * new content, or null to copy the entry unchanged. Throws [ZipException] before writing anything
     * when the source is not a plain (non-zip64, single disk) zip this copier can read.
     */
    @Throws(IOException::class)
    fun repack(src: File, dst: File, replace: (String) -> File?) {
        RandomAccessFile(src, "r").use { raf ->
            val input = raf.channel
            val entries = readEntries(input)
            FileOutputStream(dst).use { fos ->
                val output = fos.channel
                val central = ByteArrayOutputStream()
                var count = 0
                for (entry in entries) {
                    val offset = output.position()
                    val newContent = replace(entry.name)
                    if (newContent != null) {
                        writeDeflated(output, central, entry, newContent, offset)
                    } else {
                        writeRaw(input, output, central, entry, offset)
                    }
                    count++
                }
                val centralOffset = output.position()
                if (count > 0xffff || centralOffset + central.size() > 0xffffffffL) throw ZipException("Output needs zip64")
                writeFully(output, ByteBuffer.wrap(central.toByteArray()))
                val eocd = buffer(EOCD_SIZE)
                eocd.putInt(EOCD_SIG).putShort(0).putShort(0)
                    .putShort(count.toShort()).putShort(count.toShort())
                    .putInt(central.size()).putInt(centralOffset.toInt()).putShort(0)
                writeFully(output, flip(eocd))
            }
        }
    }

    /** The central directory's entries without repeated names (the first one wins), in file order so the source is read sequentially. */
    private fun readEntries(input: FileChannel): List<Entry> {
        val fileSize = input.size()
        val tailSize = minOf(fileSize, (EOCD_SIZE + 0xffff).toLong()).toInt()
        val tail = readAt(input, fileSize - tailSize, tailSize)
        var eocd = -1
        for (i in tailSize - EOCD_SIZE downTo 0) {
            if (tail.getInt(i) == EOCD_SIG && i + EOCD_SIZE + u16(tail, i + 20) <= tailSize) {
                eocd = i
                break
            }
        }
        if (eocd < 0) throw ZipException("No end of central directory")
        val eocdOffset = fileSize - tailSize + eocd
        if (u16(tail, eocd + 4) != 0 || u16(tail, eocd + 6) != 0) throw ZipException("Multi-disk zip")
        val total = u16(tail, eocd + 10)
        val centralSize = u32(tail, eocd + 12)
        val centralOffset = u32(tail, eocd + 16)
        if (total == 0xffff || centralSize == 0xffffffffL || centralOffset == 0xffffffffL) throw ZipException("zip64")
        if (centralOffset + centralSize > eocdOffset) throw ZipException("Central directory out of range")
        val cd = readAt(input, centralOffset, centralSize.toInt())
        val entries = ArrayList<Entry>(total)
        val names = HashSet<String>(total * 2)
        var p = 0
        while (p + CENTRAL_HEADER_SIZE <= cd.limit() && cd.getInt(p) == CENTRAL_SIG) {
            val nameLength = u16(cd, p + 28)
            val recordSize = CENTRAL_HEADER_SIZE + nameLength + u16(cd, p + 30) + u16(cd, p + 32)
            if (p + recordSize > cd.limit()) throw ZipException("Truncated central directory")
            val compressedSize = u32(cd, p + 20)
            val size = u32(cd, p + 24)
            val localOffset = u32(cd, p + 42)
            if (compressedSize == 0xffffffffL || size == 0xffffffffL || localOffset == 0xffffffffL) throw ZipException("zip64 entry")
            val nameBytes = ByteArray(nameLength)
            for (i in 0 until nameLength) nameBytes[i] = cd.get(p + CENTRAL_HEADER_SIZE + i)
            val name = String(nameBytes, Charsets.UTF_8)
            if (names.add(name)) {
                // The data starts after the local header's own name and extra field, which may differ from the central ones.
                if (localOffset + LOCAL_HEADER_SIZE > centralOffset) throw ZipException("Bad local header: $name")
                val local = readAt(input, localOffset, LOCAL_HEADER_SIZE)
                if (local.getInt(0) != LOCAL_SIG) throw ZipException("Bad local header: $name")
                val dataOffset = localOffset + LOCAL_HEADER_SIZE + u16(local, 26) + u16(local, 28)
                if (dataOffset + compressedSize > centralOffset) throw ZipException("Entry data out of range: $name")
                entries.add(Entry(
                    name, nameBytes, u16(cd, p + 4), u16(cd, p + 6), u16(cd, p + 8), u16(cd, p + 10),
                    u16(cd, p + 12), u16(cd, p + 14), cd.getInt(p + 16), compressedSize, size,
                    u16(cd, p + 36), cd.getInt(p + 38), localOffset, dataOffset,
                ))
            }
            p += recordSize
        }
        entries.sortBy { it.localOffset }
        return entries
    }

    private fun writeRaw(input: FileChannel, output: FileChannel, central: ByteArrayOutputStream, entry: Entry, offset: Long) {
        val alignment = when {
            entry.method != STORED -> 0
            entry.name.endsWith(".so") -> LIBRARY_ALIGNMENT
            else -> ALIGNMENT
        }
        val extra = alignmentExtra(offset + LOCAL_HEADER_SIZE + entry.nameBytes.size, alignment)
        val flags = entry.flags and KEPT_FLAGS
        writeLocalHeader(output, entry, entry.versionNeeded, flags, entry.method, entry.crc, entry.compressedSize, entry.size, extra)
        var position = entry.dataOffset
        val end = entry.dataOffset + entry.compressedSize
        while (position < end) {
            val copied = input.transferTo(position, end - position, output)
            if (copied <= 0) throw IOException("Short read: ${entry.name}")
            position += copied
        }
        writeCentralHeader(central, entry, entry.versionNeeded, flags, entry.method, entry.crc, entry.compressedSize, entry.size, offset)
    }

    private fun writeDeflated(output: FileChannel, central: ByteArrayOutputStream, entry: Entry, content: File, offset: Long) {
        val data = content.readBytes()
        val crc = CRC32().apply { update(data) }.value.toInt()
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        val compressed = ByteArrayOutputStream(data.size / 2 + 64)
        try {
            deflater.setInput(data)
            deflater.finish()
            val chunk = ByteArray(64 * 1024)
            while (!deflater.finished()) {
                val n = deflater.deflate(chunk)
                compressed.write(chunk, 0, n)
            }
        } finally {
            deflater.end()
        }
        val versionNeeded = maxOf(entry.versionNeeded, 20)
        val flags = entry.flags and 0x0800
        writeLocalHeader(output, entry, versionNeeded, flags, DEFLATED, crc, compressed.size().toLong(), data.size.toLong(), ByteArray(0))
        writeFully(output, ByteBuffer.wrap(compressed.toByteArray()))
        writeCentralHeader(central, entry, versionNeeded, flags, DEFLATED, crc, compressed.size().toLong(), data.size.toLong(), offset)
    }

    /** An apksig-style alignment extra field (id 0xd935: the alignment, then zero padding) putting the data at a multiple of [alignment]. */
    private fun alignmentExtra(extraOffset: Long, alignment: Int): ByteArray {
        if (alignment == 0) return ByteArray(0)
        val minimum = 6
        val padding = ((alignment - (extraOffset + minimum) % alignment) % alignment).toInt()
        return buffer(minimum + padding)
            .putShort(ALIGNMENT_EXTRA_ID.toShort())
            .putShort((2 + padding).toShort())
            .putShort(alignment.toShort())
            .array()
    }

    private fun writeLocalHeader(
        output: FileChannel, entry: Entry, versionNeeded: Int, flags: Int, method: Int,
        crc: Int, compressedSize: Long, size: Long, extra: ByteArray,
    ) {
        val header = buffer(LOCAL_HEADER_SIZE + entry.nameBytes.size + extra.size)
        header.putInt(LOCAL_SIG).putShort(versionNeeded.toShort()).putShort(flags.toShort())
            .putShort(method.toShort()).putShort(entry.time.toShort()).putShort(entry.date.toShort())
            .putInt(crc).putInt(compressedSize.toInt()).putInt(size.toInt())
            .putShort(entry.nameBytes.size.toShort()).putShort(extra.size.toShort())
            .put(entry.nameBytes).put(extra)
        writeFully(output, flip(header))
    }

    private fun writeCentralHeader(
        central: ByteArrayOutputStream, entry: Entry, versionNeeded: Int, flags: Int, method: Int,
        crc: Int, compressedSize: Long, size: Long, offset: Long,
    ) {
        if (offset > 0xffffffffL) throw ZipException("Output needs zip64")
        val header = buffer(CENTRAL_HEADER_SIZE + entry.nameBytes.size)
        header.putInt(CENTRAL_SIG).putShort(entry.versionMadeBy.toShort()).putShort(versionNeeded.toShort())
            .putShort(flags.toShort()).putShort(method.toShort())
            .putShort(entry.time.toShort()).putShort(entry.date.toShort())
            .putInt(crc).putInt(compressedSize.toInt()).putInt(size.toInt())
            .putShort(entry.nameBytes.size.toShort()).putShort(0).putShort(0).putShort(0)
            .putShort(entry.internalAttrs.toShort()).putInt(entry.externalAttrs).putInt(offset.toInt())
            .put(entry.nameBytes)
        central.write(header.array(), 0, header.position())
    }

    private fun buffer(size: Int): ByteBuffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)

    private fun readAt(input: FileChannel, position: Long, size: Int): ByteBuffer {
        val bytes = buffer(size)
        while (bytes.hasRemaining()) {
            if (input.read(bytes, position + bytes.position()) < 0) throw ZipException("Unexpected end of file")
        }
        return flip(bytes)
    }

    /** Through [Buffer]: ByteBuffer.flip() only returns a ByteBuffer from Android 9 on. */
    private fun flip(bytes: ByteBuffer): ByteBuffer {
        val buffer: Buffer = bytes
        buffer.flip()
        return bytes
    }

    private fun writeFully(output: FileChannel, bytes: ByteBuffer) {
        while (bytes.hasRemaining()) output.write(bytes)
    }

    private fun u16(b: ByteBuffer, at: Int): Int = b.getShort(at).toInt() and 0xffff

    private fun u32(b: ByteBuffer, at: Int): Long = b.getInt(at).toLong() and 0xffffffffL
}
