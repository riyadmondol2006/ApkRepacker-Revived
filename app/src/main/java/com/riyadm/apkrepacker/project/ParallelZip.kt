package com.riyadm.apkrepacker.project

import com.riyadm.apkrepacker.utils.SafeZip
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.ArrayDeque
import java.util.Calendar
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * ZIP writing and reading spread over several threads. A project is tens of thousands of small
 * files; deflating them one after another keeps a single core busy while the others idle.
 *
 * The writer deflates the files on worker threads (a bounded window ahead of the output) and
 * writes them in order, so the archive looks like one from ZipOutputStream: same entry order,
 * UTF-8 names, Zip64 when the entry count or sizes need it. Readable by ZipInputStream and ZipFile.
 */
internal object ParallelZip {

    /** One archive entry: a folder (name ends with '/') or a file read from [file], [size] bytes long. */
    class Entry(val name: String, val file: File?, val time: Long, val size: Long = 0) {
        val isDirectory: Boolean get() = file == null
    }

    /** Files up to this size are deflated in memory by a worker; bigger ones are streamed in order. */
    private const val IN_MEMORY_LIMIT = 8L * 1024 * 1024

    private const val ZIP64_MAGIC = 0xFFFFFFFFL
    private const val ZIP64_MAGIC_COUNT = 0xFFFF
    private const val FLAG_UTF8 = 0x0800
    private const val FLAG_DATA_DESCRIPTOR = 0x0008
    private const val STORED = 0
    private const val DEFLATED = 8

    private data class Queued(val entry: Entry, val size: Long, val future: Future<Packed>?)

    private class Packed(val method: Int, val crc: Long, val size: Long, val data: ByteArray, val dataLength: Int)

    private class Written(
        val name: ByteArray, val method: Int, val flags: Int, val dosTime: Long,
        val crc: Long, val size: Long, val compressedSize: Long, val offset: Long,
    )

    /** One per worker: a fresh 64 KB array per file would be tens of thousands of large allocations. */
    private val copyBuffers = object : ThreadLocal<ByteArray>() {
        override fun initialValue() = ByteArray(64 * 1024)
    }

    /** Reused deflaters (each holds a few hundred KB of native memory); ended when a ZIP is done. */
    private class Deflaters : AutoCloseable {
        private val idle = ConcurrentLinkedQueue<Deflater>()

        fun <T> borrow(block: (Deflater) -> T): T {
            val deflater = idle.poll() ?: Deflater(Deflater.DEFAULT_COMPRESSION, true)
            deflater.reset()
            try {
                return block(deflater)
            } finally {
                idle.add(deflater)
            }
        }

        override fun close() {
            while (true) (idle.poll() ?: return).end()
        }
    }

    /**
     * Writes [entries] in their order as a ZIP to [out] (closed afterwards). [onFileWritten] is
     * called after each file entry, on the calling thread.
     */
    @Throws(IOException::class)
    fun write(out: OutputStream, entries: List<Entry>, threads: Int, onFileWritten: ((Entry) -> Unit)?) {
        val windowBytes = (Runtime.getRuntime().maxMemory() / 8).coerceIn(8L * 1024 * 1024, 64L * 1024 * 1024)
        val pool = Executors.newFixedThreadPool(threads.coerceAtLeast(1))
        val cancelled = AtomicBoolean(false)
        val deflaters = Deflaters()
        try {
            CountingOutput(BufferedOutputStream(out, 64 * 1024)).use { zip ->
                val written = ArrayList<Written>(entries.size)
                val pending = ArrayDeque<Queued>()
                var inFlight = 0L
                var next = 0
                while (next < entries.size || pending.isNotEmpty()) {
                    // Keep the workers fed: queue entries ahead until the memory window is full.
                    while (next < entries.size && (pending.isEmpty() || inFlight < windowBytes) && pending.size < threads * 8) {
                        val entry = entries[next++]
                        val file = entry.file
                        val size = entry.size
                        val future = if (file != null && size <= IN_MEMORY_LIMIT) {
                            inFlight += size
                            pool.submit<Packed> { if (cancelled.get()) throw IOException("cancelled") else pack(file, deflaters) }
                        } else {
                            null
                        }
                        pending.addLast(Queued(entry, size, future))
                    }
                    val (entry, size, future) = pending.removeFirst()
                    val file = entry.file
                    if (file == null) {
                        written += writeStored(zip, entry, ByteArray(0), 0, 0L)
                    } else if (future != null) {
                        inFlight -= size
                        val packed = try {
                            future.get()
                        } catch (e: ExecutionException) {
                            throw (e.cause as? IOException) ?: IOException(e.cause)
                        }
                        written += writePacked(zip, entry, packed)
                        onFileWritten?.invoke(entry)
                    } else {
                        written += deflaters.borrow { writeStreamed(zip, entry, file, it) }
                        onFileWritten?.invoke(entry)
                    }
                }
                writeCentralDirectory(zip, written)
            }
        } finally {
            cancelled.set(true)
            pool.shutdownNow()
            try {
                pool.awaitTermination(30, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            deflaters.close()
        }
    }

    /** Deflates a whole (small) file; keeps it stored when deflating doesn't make it smaller. */
    private fun pack(file: File, deflaters: Deflaters): Packed {
        val raw = file.readBytes()
        val crc = CRC32().apply { update(raw, 0, raw.size) }.value
        if (raw.isEmpty()) return Packed(STORED, crc, 0, raw, 0)
        return deflaters.borrow { deflate(raw, crc, it) }
    }

    private fun deflate(raw: ByteArray, crc: Long, deflater: Deflater): Packed {
        deflater.setInput(raw)
        deflater.finish()
        var buffer = ByteArray(raw.size + raw.size / 16 + 64)
        var length = 0
        while (!deflater.finished()) {
            if (length == buffer.size) buffer = buffer.copyOf(buffer.size * 2)
            length += deflater.deflate(buffer, length, buffer.size - length)
            // Already bigger than the file itself: storing it is smaller and faster to read back.
            if (length >= raw.size) return Packed(STORED, crc, raw.size.toLong(), raw, raw.size)
        }
        return Packed(DEFLATED, crc, raw.size.toLong(), buffer, length)
    }

    private fun writePacked(zip: CountingOutput, entry: Entry, packed: Packed): Written =
        if (packed.method == STORED) {
            writeStored(zip, entry, packed.data, packed.dataLength, packed.crc)
        } else {
            val name = entry.name.toByteArray(Charsets.UTF_8)
            val dosTime = dosTime(entry.time)
            val offset = zip.count
            writeLocalHeader(zip, name, DEFLATED, FLAG_UTF8, dosTime, packed.crc, packed.dataLength.toLong(), packed.size)
            zip.write(packed.data, 0, packed.dataLength)
            Written(name, DEFLATED, FLAG_UTF8, dosTime, packed.crc, packed.size, packed.dataLength.toLong(), offset)
        }

    private fun writeStored(zip: CountingOutput, entry: Entry, data: ByteArray, length: Int, crc: Long): Written {
        val name = entry.name.toByteArray(Charsets.UTF_8)
        val dosTime = dosTime(entry.time)
        val offset = zip.count
        writeLocalHeader(zip, name, STORED, FLAG_UTF8, dosTime, crc, length.toLong(), length.toLong())
        zip.write(data, 0, length)
        return Written(name, STORED, FLAG_UTF8, dosTime, crc, length.toLong(), length.toLong(), offset)
    }

    /** A big file, deflated on this thread straight into the output, sizes in a data descriptor. */
    private fun writeStreamed(zip: CountingOutput, entry: Entry, file: File, deflater: Deflater): Written {
        val name = entry.name.toByteArray(Charsets.UTF_8)
        val dosTime = dosTime(entry.time)
        val flags = FLAG_UTF8 or FLAG_DATA_DESCRIPTOR
        val offset = zip.count
        writeLocalHeader(zip, name, DEFLATED, flags, dosTime, 0, 0, 0)
        val crc = CRC32()
        val input = ByteArray(64 * 1024)
        val output = ByteArray(64 * 1024)
        var size = 0L
        val start = zip.count
        file.inputStream().use { stream ->
            while (true) {
                val read = stream.read(input)
                if (read < 0) break
                if (read == 0) continue
                crc.update(input, 0, read)
                size += read
                deflater.setInput(input, 0, read)
                while (!deflater.needsInput()) {
                    val n = deflater.deflate(output)
                    zip.write(output, 0, n)
                }
            }
        }
        deflater.finish()
        while (!deflater.finished()) {
            val n = deflater.deflate(output)
            zip.write(output, 0, n)
        }
        val compressed = zip.count - start
        writeInt(zip, 0x08074b50L)
        writeInt(zip, crc.value)
        // The same rule ZipInputStream reads by: 8-byte sizes once either one passes 4 GB.
        if (compressed >= ZIP64_MAGIC || size >= ZIP64_MAGIC) {
            writeLong(zip, compressed)
            writeLong(zip, size)
        } else {
            writeInt(zip, compressed)
            writeInt(zip, size)
        }
        return Written(name, DEFLATED, flags, dosTime, crc.value, size, compressed, offset)
    }

    private fun writeLocalHeader(
        zip: OutputStream, name: ByteArray, method: Int, flags: Int, dosTime: Long,
        crc: Long, compressedSize: Long, size: Long,
    ) {
        writeInt(zip, 0x04034b50L)
        writeShort(zip, if (method == DEFLATED) 20 else 10)
        writeShort(zip, flags)
        writeShort(zip, method)
        writeInt(zip, dosTime)
        writeInt(zip, crc)
        writeInt(zip, compressedSize)
        writeInt(zip, size)
        writeShort(zip, name.size)
        writeShort(zip, 0)
        zip.write(name)
    }

    private fun writeCentralDirectory(zip: CountingOutput, written: List<Written>) {
        val cenStart = zip.count
        for (w in written) {
            val bigSize = w.size >= ZIP64_MAGIC
            val bigCompressed = w.compressedSize >= ZIP64_MAGIC
            val bigOffset = w.offset >= ZIP64_MAGIC
            val extraLength = (if (bigSize) 8 else 0) + (if (bigCompressed) 8 else 0) + (if (bigOffset) 8 else 0)
            val zip64 = extraLength > 0
            val version = if (zip64) 45 else if (w.method == DEFLATED) 20 else 10
            writeInt(zip, 0x02014b50L)
            writeShort(zip, version)
            writeShort(zip, version)
            writeShort(zip, w.flags)
            writeShort(zip, w.method)
            writeInt(zip, w.dosTime)
            writeInt(zip, w.crc)
            writeInt(zip, if (bigCompressed) ZIP64_MAGIC else w.compressedSize)
            writeInt(zip, if (bigSize) ZIP64_MAGIC else w.size)
            writeShort(zip, w.name.size)
            writeShort(zip, if (zip64) extraLength + 4 else 0)
            writeShort(zip, 0) // comment
            writeShort(zip, 0) // disk
            writeShort(zip, 0) // internal attributes
            writeInt(zip, 0) // external attributes
            writeInt(zip, if (bigOffset) ZIP64_MAGIC else w.offset)
            zip.write(w.name)
            if (zip64) {
                // The Zip64 extra holds, in this order, only the fields that overflowed.
                writeShort(zip, 0x0001)
                writeShort(zip, extraLength)
                if (bigSize) writeLong(zip, w.size)
                if (bigCompressed) writeLong(zip, w.compressedSize)
                if (bigOffset) writeLong(zip, w.offset)
            }
        }
        val cenEnd = zip.count
        val cenSize = cenEnd - cenStart
        val count = written.size
        if (count >= ZIP64_MAGIC_COUNT || cenSize >= ZIP64_MAGIC || cenStart >= ZIP64_MAGIC) {
            writeInt(zip, 0x06064b50L) // Zip64 end of central directory record
            writeLong(zip, 44)
            writeShort(zip, 45)
            writeShort(zip, 45)
            writeInt(zip, 0)
            writeInt(zip, 0)
            writeLong(zip, count.toLong())
            writeLong(zip, count.toLong())
            writeLong(zip, cenSize)
            writeLong(zip, cenStart)
            writeInt(zip, 0x07064b50L) // Zip64 end of central directory locator
            writeInt(zip, 0)
            writeLong(zip, cenEnd)
            writeInt(zip, 1)
        }
        writeInt(zip, 0x06054b50L)
        writeShort(zip, 0)
        writeShort(zip, 0)
        writeShort(zip, minOf(count, ZIP64_MAGIC_COUNT))
        writeShort(zip, minOf(count, ZIP64_MAGIC_COUNT))
        writeInt(zip, minOf(cenSize, ZIP64_MAGIC))
        writeInt(zip, minOf(cenStart, ZIP64_MAGIC))
        writeShort(zip, 0)
    }

    /** MS-DOS date and time in local time, as ZipOutputStream stores them (1980 at the earliest). */
    private fun dosTime(time: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = time }
        val year = c.get(Calendar.YEAR)
        if (year < 1980) return (1L shl 21) or (1L shl 16)
        if (year > 2107) return 0xFF9FBF7DL // 2107-12-31 23:59:58
        return ((year - 1980).toLong() shl 25) or ((c.get(Calendar.MONTH) + 1).toLong() shl 21) or
            (c.get(Calendar.DAY_OF_MONTH).toLong() shl 16) or (c.get(Calendar.HOUR_OF_DAY).toLong() shl 11) or
            (c.get(Calendar.MINUTE).toLong() shl 5) or (c.get(Calendar.SECOND).toLong() shr 1)
    }

    private fun writeShort(out: OutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v ushr 8) and 0xFF)
    }

    private fun writeInt(out: OutputStream, v: Long) {
        out.write((v and 0xFF).toInt())
        out.write(((v ushr 8) and 0xFF).toInt())
        out.write(((v ushr 16) and 0xFF).toInt())
        out.write(((v ushr 24) and 0xFF).toInt())
    }

    private fun writeInt(out: OutputStream, v: Int) = writeInt(out, v.toLong() and 0xFFFFFFFFL)

    private fun writeLong(out: OutputStream, v: Long) {
        writeInt(out, v and 0xFFFFFFFFL)
        writeInt(out, v ushr 32)
    }

    private class CountingOutput(private val out: OutputStream) : OutputStream() {
        var count = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }

        override fun flush() = out.flush()

        override fun close() = out.close()
    }

    // region reading

    /**
     * [SafeZip.resolve] for a folder this import created and only fills with plain files and
     * folders, so it holds no symbolic links: a name without "." or ".." parts then cannot leave
     * it and needs no canonicalizing (a disk lookup per path part, seconds for a large project).
     * Every other name goes through [SafeZip.resolve].
     */
    fun resolveInFreshDir(canonicalRoot: File, entryName: String): File? {
        val name = entryName.replace('\\', '/').trimStart('/')
        if (name.isEmpty() || '\u0000' in name) return SafeZip.resolve(canonicalRoot, entryName)
        for (part in name.split('/')) {
            if (part == "." || part == "..") return SafeZip.resolve(canonicalRoot, entryName)
        }
        return File(canonicalRoot, name)
    }

    /** A file to extract: [target] is already checked to lie inside the destination. */
    class Extraction(val entry: ZipEntry, val target: File)

    /**
     * Extracts [jobs] from [zip] on [threads] threads. Parent folders must exist. [onBytes] gets
     * the running total of extracted bytes (and may throw to stop); [onFileDone] runs after each file.
     */
    @Throws(IOException::class)
    fun extract(
        zip: ZipFile, jobs: List<Extraction>, threads: Int,
        onBytes: (Long) -> Unit, onFileDone: (Extraction) -> Unit,
    ) {
        if (jobs.isEmpty()) return
        val pool = Executors.newFixedThreadPool(threads.coerceAtLeast(1))
        val failed = AtomicBoolean(false)
        val firstError = AtomicReference<Throwable?>(null)
        val extracted = AtomicLong(0)
        try {
            val futures = jobs.map { job ->
                pool.submit(Runnable {
                    if (failed.get()) return@Runnable
                    try {
                        val buffer = copyBuffers.get()!!
                        zip.getInputStream(job.entry).use { input ->
                            job.target.outputStream().use { out ->
                                while (!failed.get()) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    onBytes(extracted.addAndGet(read.toLong()))
                                    out.write(buffer, 0, read)
                                }
                            }
                        }
                        onFileDone(job)
                    } catch (t: Throwable) {
                        if (failed.compareAndSet(false, true)) firstError.set(t)
                        throw t
                    }
                })
            }
            for (future in futures) {
                try {
                    future.get()
                } catch (e: ExecutionException) {
                    failed.set(true)
                    val cause = firstError.get() ?: e.cause
                    throw (cause as? IOException) ?: IOException(cause)
                }
            }
        } finally {
            failed.set(true)
            pool.shutdownNow()
            // The caller deletes the stage afterwards: no worker may still be writing into it.
            try {
                pool.awaitTermination(30, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    // endregion
}
