package tachiyomi.domain.storage.service

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.storage.FolderProvider

class StoragePreferences(
    private val folderProvider: FolderProvider,
    private val preferenceStore: PreferenceStore,
) {

    fun baseStorageDirectory() = preferenceStore.getString(Preference.appStateKey("storage_dir"), folderProvider.path())

    /**
     * Extra roots the local anime source scans. Each entry is a directory holding anime folders,
     * as a sibling of [baseStorageDirectory]'s `localanime` folder.
     */
    fun extraLocalAnimeDirectories() = preferenceStore.getStringSet(Preference.appStateKey("extra_local_anime_dirs"))
}
