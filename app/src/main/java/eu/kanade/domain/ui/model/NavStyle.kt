package eu.kanade.domain.ui.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.history.HistoriesTab
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryTab
import eu.kanade.tachiyomi.ui.more.MoreTab
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

object NavStyle {
    const val TAB_ORDER_DEFAULT = "anime,updates,history,browse"

    val mainTabs: List<Tab> =
        listOf(
            AnimeLibraryTab,
            UpdatesTab,
            HistoriesTab,
            BrowseTab,
        )

    val tabKey: Map<Tab, String> =
        mapOf(
            AnimeLibraryTab to "anime",
            UpdatesTab to "updates",
            HistoriesTab to "history",
            BrowseTab to "browse",
        )

    private val keyToTab: Map<String, Tab> = tabKey.entries.associate { (tab, key) -> key to tab }

    fun parseOrder(raw: String): List<String> = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    fun orderToTabs(order: List<String>): List<Tab> {
        val known = order.mapNotNull(keyToTab::get)
        return known + mainTabs.filterNot(known::contains)
    }

    fun orderString(tabs: List<Tab>): String = tabs.mapNotNull(tabKey::get).joinToString(",")

    fun tabsConfig(
        order: List<String>,
        showAnime: Boolean,
        showUpdates: Boolean,
        showHistory: Boolean,
        showBrowse: Boolean,
    ): TabsConfig {
        val visibility =
            mapOf(
                AnimeLibraryTab to showAnime,
                UpdatesTab to showUpdates,
                HistoriesTab to showHistory,
                BrowseTab to showBrowse,
            )
        val ordered = orderToTabs(order)
        return TabsConfig(
            visibleTabs = ordered.filter(visibility::getValue) + MoreTab,
            moreTabs = ordered.filterNot(visibility::getValue),
        )
    }

    @Composable
    fun tabTitle(tab: Tab): String =
        when (tab) {
            AnimeLibraryTab -> stringResource(AYMR.strings.label_anime_library)
            UpdatesTab -> stringResource(MR.strings.label_recent_updates)
            HistoriesTab -> stringResource(MR.strings.history)
            BrowseTab -> stringResource(MR.strings.browse)
            else -> ""
        }

    @Composable
    fun moreTabIcon(tab: Tab): ImageVector =
        when (tab) {
            AnimeLibraryTab -> ImageVector.vectorResource(id = R.drawable.ic_animelibrary_outline_24dp)
            UpdatesTab -> ImageVector.vectorResource(id = R.drawable.ic_updates_outline_24dp)
            HistoriesTab -> Icons.Outlined.History
            BrowseTab -> Icons.Outlined.Explore
            else -> Icons.Outlined.CollectionsBookmark
        }

    data class TabsConfig(
        val visibleTabs: List<Tab>,
        val moreTabs: List<Tab>,
    )
}
