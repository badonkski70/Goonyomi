package tachiyomi.source.local.io.anime

import com.hippo.unifile.UniFile
import tachiyomi.domain.storage.service.StorageManager

actual class LocalAnimeSourceFileSystem(
    private val storageManager: StorageManager,
) {

    actual fun getBaseDirectories(): List<UniFile> {
        return storageManager.getLocalAnimeSourceDirectories()
    }

    // ponytail: roots are searched in order and the first hit wins, so a series folder name that
    // exists in two roots resolves to the earlier one. Key the url by root path if that matters.
    actual fun getFilesInBaseDirectory(): List<UniFile> {
        return getBaseDirectories().flatMap { it.listFiles().orEmpty().asList() }
    }

    actual fun getAnimeDirectory(name: String): UniFile? {
        return getBaseDirectories()
            .firstNotNullOfOrNull { it.findFile(name)?.takeIf { dir -> dir.isDirectory } }
    }

    actual fun getFilesInAnimeDirectory(name: String): List<UniFile> {
        return getAnimeDirectory(name)?.listFiles().orEmpty().toList()
    }
}
