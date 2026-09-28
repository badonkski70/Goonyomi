package tachiyomi.source.local.io

import com.hippo.unifile.UniFile
import tachiyomi.core.common.storage.extension

object ArchiveAnime {

    private val SUPPORTED_ARCHIVE_TYPES = listOf("avi", "flv", "mkv", "mov", "mp4", "webm", "wmv", "torrent")

    private val SUPPORTED_IMAGE_TYPES = setOf(
        "jpg",
        "jpeg",
        "jfif",
        "png",
        "webp",
        "gif",
        "bmp",
        "avif",
        "heic",
        "heif",
        "jxl",
    )

    /**
     * Art the app generates into the folder. Never gallery content, and a folder of videos would
     * otherwise end up with a `cover` episode as soon as the cover was extracted.
     */
    private val RESERVED_IMAGE_NAMES = setOf("cover", "background", "thumbnail")

    fun isImage(file: UniFile): Boolean = isImageName(file.name.orEmpty())

    fun isSupported(file: UniFile): Boolean = with(file) {
        file.extension in SUPPORTED_ARCHIVE_TYPES || isImage(file)
    }

    /**
     * Whether a local episode url, `"<anime dir>/<file name>"`, points at a photo. For the call
     * sites that only hold the url, such as the player entry points.
     */
    fun isImageUrl(url: String): Boolean = isImageName(url.substringAfterLast('/'))

    private fun isImageName(name: String): Boolean {
        return name.substringAfterLast('.', "").lowercase() in SUPPORTED_IMAGE_TYPES &&
            name.substringBeforeLast('.').lowercase() !in RESERVED_IMAGE_NAMES
    }
}
