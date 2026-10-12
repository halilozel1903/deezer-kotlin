package io.github.halilozel1903.deezer.cache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * A [DeezerCache] of one file per response in [directory]. Writes are atomic (a temporary file is moved into
 * place), entries older than [ttl] are ignored and deleted, and the oldest files are removed once the directory
 * grows past [maxBytes]. On Android use a folder in `context.cacheDir`. I/O runs on [Dispatchers.IO].
 */
public class DiskCache(
    public val directory: File,
    public val maxBytes: Long = DEFAULT_MAX_BYTES,
    public val ttl: Duration = 1.days,
    private val clock: () -> Long = System::currentTimeMillis,
) : DeezerCache {

    init {
        require(maxBytes > 0) { "maxBytes must be positive, was $maxBytes" }
    }

    override suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        val file = fileFor(key)
        val text = try {
            if (!file.isFile) return@withContext null
            file.readText()
        } catch (_: IOException) {
            return@withContext null
        }
        val header = text.substringBefore('\n', missingDelimiterValue = "")
        val storedAt = header.removePrefix(HEADER_PREFIX).toLongOrNull()
        if (!header.startsWith(HEADER_PREFIX) || storedAt == null || isExpired(storedAt)) {
            file.delete()
            return@withContext null
        }
        text.substring(header.length + 1)
    }

    override suspend fun put(key: String, value: String) {
        withContext(Dispatchers.IO) {
            try {
                directory.mkdirs()
                val target = fileFor(key)
                val temp = File.createTempFile("entry", ".tmp", directory)
                temp.writeText("$HEADER_PREFIX${clock()}\n$value")
                try {
                    Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: IOException) {
                    Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                trim()
            } catch (_: IOException) {
                // A cache that cannot write is only a slower cache.
            }
        }
    }

    override suspend fun remove(key: String) {
        withContext(Dispatchers.IO) { fileFor(key).delete() }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            directory.listFiles()?.forEach { if (it.isFile) it.delete() }
        }
    }

    /** The total size of the cached files in bytes. */
    public fun sizeBytes(): Long = cacheFiles().sumOf { it.length() }

    private fun cacheFiles(): List<File> =
        directory.listFiles()?.filter { it.isFile && it.name.endsWith(SUFFIX) }.orEmpty()

    private fun trim() {
        val files = cacheFiles()
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            val length = file.length()
            if (file.delete()) total -= length
        }
    }

    private fun isExpired(storedAt: Long): Boolean =
        !ttl.isInfinite() && clock() - storedAt >= ttl.inWholeMilliseconds

    private fun fileFor(key: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        val name = digest.joinToString("") { "%02x".format(it) }
        return File(directory, name + SUFFIX)
    }

    public companion object {
        /** 20 MB. */
        public const val DEFAULT_MAX_BYTES: Long = 20L * 1024 * 1024
        private const val SUFFIX = ".json"
        private const val HEADER_PREFIX = "deezer-cache-v1 "
    }
}
