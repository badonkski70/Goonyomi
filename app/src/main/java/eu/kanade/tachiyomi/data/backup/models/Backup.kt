package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

// Manga field numbers are intentionally omitted: protobuf skips unknown
// numbers, so old backups still decode. Do not reuse them.
// Removed: 1 backupManga, 101 backupSources, 106 backupMangaExtensionRepo,
// LegacyBackup also removed 2 backupCategories, 108 backupMangaExtensionRepo.

@Serializable
data class LegacyBackup(
    @ProtoNumber(3) val backupAnime: List<BackupAnime> = emptyList(),
    @ProtoNumber(4) var backupAnimeCategories: List<BackupCategory> = emptyList(),
    // @ProtoNumber(102) var backupBrokenAnimeSources, legacy source model with non-compliant proto number,
    @ProtoNumber(103) var backupAnimeSources: List<BackupAnimeSource> = emptyList(),
    @ProtoNumber(104) var backupPreferences: List<BackupPreference> = emptyList(),
    @ProtoNumber(105) var backupSourcePreferences: List<BackupSourcePreferences> = emptyList(),
    @ProtoNumber(106) var backupExtensions: List<BackupExtension> = emptyList(),
    @ProtoNumber(107) var backupAnimeExtensionStores: List<BackupExtensionStore> = emptyList(),
    @ProtoNumber(109) var backupCustomButton: List<BackupCustomButtons> = emptyList(),
) {
    fun toBackup(): Backup {
        return Backup(
            isLegacy = false, // Only used for detection
            backupAnime = backupAnime,
            backupAnimeCategories = backupAnimeCategories,
            backupAnimeSources = backupAnimeSources,
            backupPreferences = backupPreferences,
            backupSourcePreferences = backupSourcePreferences,
            backupExtensions = backupExtensions,
            backupAnimeExtensionStores = backupAnimeExtensionStores,
            backupCustomButton = backupCustomButton,
        )
    }
}

@Serializable
data class Backup(
    @ProtoNumber(104) var backupPreferences: List<BackupPreference> = emptyList(),
    @ProtoNumber(105) var backupSourcePreferences: List<BackupSourcePreferences> = emptyList(),

    // Aniyomi specific values
    @ProtoNumber(500) val isLegacy: Boolean = true,
    @ProtoNumber(501) val backupAnime: List<BackupAnime> = emptyList(),
    @ProtoNumber(502) var backupAnimeCategories: List<BackupCategory> = emptyList(),
    @ProtoNumber(503) var backupAnimeSources: List<BackupAnimeSource> = emptyList(),
    @ProtoNumber(504) var backupExtensions: List<BackupExtension> = emptyList(),
    @ProtoNumber(505) var backupAnimeExtensionStores: List<BackupExtensionStore> = emptyList(),
    @ProtoNumber(506) var backupCustomButton: List<BackupCustomButtons> = emptyList(),
)
