package io.github.halilozel1903.deezer.media

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Serves Deezer artwork to Android Auto, which only loads images from `content://` urls. The first request for an
 * image downloads it into `cacheDir/deezer-artwork`; later requests read the file.
 *
 * Only https images from Deezer's own hosts (`*.dzcdn.net`, `api.deezer.com`) are fetched, so the provider can't
 * be used to download anything else. It is declared by the library's manifest with the authority
 * `<applicationId>.deezer.artwork`.
 */
public class DeezerArtworkProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val context = context ?: throw FileNotFoundException("No context")
        if (mode != "r") throw FileNotFoundException("Artwork is read-only")
        val remote = uri.getQueryParameter(PARAM_URL) ?: throw FileNotFoundException("No url in $uri")
        if (!isAllowed(remote)) throw FileNotFoundException("Not a Deezer image: $remote")
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val file = File(dir, sha256(remote) + ".jpg")
        if (!file.isFile || file.length() == 0L) {
            try {
                download(remote, dir, file)
            } catch (e: IOException) {
                throw FileNotFoundException("Could not load $remote: ${e.message}")
            }
            trim(dir)
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/jpeg"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun download(remote: String, dir: File, target: File) {
        var url = URL(remote)
        // Follow up to three redirects (api.deezer.com/.../image redirects to the CDN), staying on Deezer's hosts.
        repeat(MAX_REDIRECTS + 1) {
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.instanceFollowRedirects = false
            try {
                when (val code = connection.responseCode) {
                    in 300..399 -> {
                        val next = connection.getHeaderField("Location") ?: throw IOException("Redirect without location")
                        val resolved = URL(url, next)
                        if (!isAllowed(resolved.toString())) throw IOException("Redirect away from Deezer: $resolved")
                        url = resolved
                    }
                    in 200..299 -> {
                        val temp = File.createTempFile("artwork", ".tmp", dir)
                        try {
                            connection.inputStream.use { input -> temp.outputStream().use { input.copyTo(it) } }
                            if (!temp.renameTo(target)) throw IOException("Could not store $target")
                        } finally {
                            temp.delete()
                        }
                        return
                    }
                    else -> throw IOException("HTTP $code")
                }
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("Too many redirects")
    }

    private fun trim(dir: File) {
        val files = dir.listFiles()?.filter { it.isFile && it.name.endsWith(".jpg") }.orEmpty()
        var total = files.sumOf { it.length() }
        if (total <= MAX_CACHE_BYTES) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= MAX_CACHE_BYTES) break
            val length = file.length()
            if (file.delete()) total -= length
        }
    }

    public companion object {
        private const val PARAM_URL = "url"
        private const val CACHE_DIR = "deezer-artwork"
        private const val MAX_REDIRECTS = 3
        private const val TIMEOUT_MILLIS = 15_000
        private const val MAX_CACHE_BYTES = 30L * 1024 * 1024

        /** `<applicationId>.deezer.artwork`. */
        public fun authority(context: Context): String = "${context.packageName}.deezer.artwork"

        /** The content uri that serves the image at [url] through this provider. */
        public fun contentUri(context: Context, url: String): Uri = Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(authority(context))
            .appendPath("artwork")
            .appendQueryParameter(PARAM_URL, url)
            .build()

        /** True for https urls on Deezer's image and API hosts. */
        public fun isAllowed(url: String): Boolean {
            val uri = try {
                URL(url)
            } catch (e: java.net.MalformedURLException) {
                return false
            }
            val host = uri.host?.lowercase() ?: return false
            return uri.protocol == "https" && (host.endsWith(".dzcdn.net") || host == "api.deezer.com")
        }

        private fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
