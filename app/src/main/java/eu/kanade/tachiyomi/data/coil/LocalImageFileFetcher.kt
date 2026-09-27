package eu.kanade.tachiyomi.data.coil

import android.content.Context
import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.util.storage.DiskUtil
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.Source
import okio.buffer
import okio.sink
import okio.source
import java.io.File

/**
 * Request data for [LocalImageFileFetcher]. A dedicated type because Coil's own
 * `ContentUriFetcher` is registered ahead of any fetcher added by the app, so an image behind a
 * `content://` uri can only be intercepted by not looking like a uri at all.
 */
data class LocalImage(val uri: String)

/**
 * Copies an image behind a `content://` uri into [cacheDir] and serves it from there. Coil only
 * disk caches file backed sources, and reading straight from the document provider is slow.
 *
 * The file's mtime is part of the cache key, so replacing the image on disk is picked up.
 *
 * [cacheDir] should be the app's cache dir, so the system can reclaim it under storage pressure.
 * Entries are a few KB and keyed by uri + mtime, so a replaced image never leaves a stale hit.
 */
class LocalImageFileFetcher(
    private val data: LocalImage,
    private val options: Options,
    private val cacheDir: File,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        return localImageFetchResult(options.context, data.uri, cacheDir)
    }

    class Factory(private val cacheDir: File) : Fetcher.Factory<LocalImage> {
        override fun create(data: LocalImage, options: Options, imageLoader: ImageLoader): Fetcher {
            return LocalImageFileFetcher(data, options, cacheDir)
        }
    }
}

/**
 * Returns a file backed fetch result for a `content://` [urlString], copying it into [cacheDir]
 * first, or the raw stream when there is no [cacheDir] or the copy fails.
 */
internal fun localImageFetchResult(context: Context, urlString: String, cacheDir: File?): FetchResult {
    val uniFile = UniFile.fromUri(context, urlString.toUri())!!

    if (cacheDir != null) {
        val cacheFile = File(cacheDir, DiskUtil.hashKeyForDisk("$urlString-${uniFile.lastModified()}"))
        if (!cacheFile.exists()) {
            runCatching { uniFile.openInputStream().source().use { writeToImageCache(it, cacheFile) } }
        }
        if (cacheFile.exists()) {
            return SourceFetchResult(
                source = ImageSource(
                    file = cacheFile.toOkioPath(),
                    fileSystem = FileSystem.SYSTEM,
                    diskCacheKey = cacheFile.path,
                ),
                mimeType = "image/*",
                dataSource = DataSource.DISK,
            )
        }
    }

    return SourceFetchResult(
        source = ImageSource(uniFile.openInputStream().source().buffer(), FileSystem.SYSTEM),
        mimeType = "image/*",
        dataSource = DataSource.DISK,
    )
}

internal fun writeToImageCache(input: Source, cacheFile: File) {
    cacheFile.parentFile?.mkdirs()
    cacheFile.delete()
    try {
        cacheFile.sink().buffer().use { output ->
            output.writeAll(input)
        }
    } catch (e: Exception) {
        cacheFile.delete()
        throw e
    }
}
