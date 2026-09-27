package tachiyomi.source.local.entries.anime

import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.animesource.model.FetchType

expect class LocalAnimeFetchTypeManager {
    fun find(animeUrl: String, files: List<UniFile>? = null): FetchType
}
