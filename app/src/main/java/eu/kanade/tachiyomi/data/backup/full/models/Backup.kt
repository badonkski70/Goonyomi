package eu.kanade.tachiyomi.data.backup.full.models

import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupAnimeSource
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

// Manga field numbers are intentionally omitted: protobuf skips unknown
// numbers, so old backups still decode. Do not reuse them.
// Removed: 1 backupManga, 2 backupCategories, 101 backupSources.

@Serializable
data class Backup(
    @ProtoNumber(3) val backupAnime: List<BackupAnime> = emptyList(),
    @ProtoNumber(4) var backupAnimeCategories: List<BackupCategory> = emptyList(),
    // @ProtoNumber(102) var backupBrokenAnimeSources, legacy source model with non-compliant proto number,
    @ProtoNumber(103) var backupAnimeSources: List<BackupAnimeSource> = emptyList(),
    @ProtoNumber(104) var backupPreferences: List<BackupPreference> = emptyList(),
)
