package eu.kanade.presentation.more.settings.screen.appearance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.NavStyle
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.Tab
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.presentation.core.util.plus
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TabOrderScreen : Screen() {

    @Composable
    override fun Content() {
        val uiPreferences = Injekt.get<UiPreferences>()
        val navigator = LocalNavigator.currentOrThrow
        val orderRaw by uiPreferences.tabOrder().collectAsState()
        val showAnime by uiPreferences.showAnimeTab().collectAsState()
        val showManga by uiPreferences.showMangaLibraryTab().collectAsState()
        val showUpdates by uiPreferences.showUpdatesTab().collectAsState()
        val showHistory by uiPreferences.showHistoryTab().collectAsState()
        val showBrowse by uiPreferences.showBrowseTab().collectAsState()
        val tabs = remember(orderRaw) { NavStyle.orderToTabs(NavStyle.parseOrder(orderRaw)) }
        val tabsState = remember { tabs.toMutableStateList() }
        val lazyListState = rememberLazyListState()

        val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
            val tab = tabsState.removeAt(from.index)
            tabsState.add(to.index, tab)
            uiPreferences.tabOrder().set(NavStyle.orderString(tabsState))
        }

        LaunchedEffect(tabs) {
            if (!reorderableState.isAnyItemDragging) {
                tabsState.clear()
                tabsState.addAll(tabs)
            }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(AYMR.strings.pref_bottom_nav_tab_order_header),
                    navigateUp = { navigator.pop() },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { paddingValues ->
val config = NavStyle.tabsConfig(
            order = NavStyle.parseOrder(orderRaw),
            showAnime = showAnime,
            showManga = showManga,
            showUpdates = showUpdates,
            showHistory = showHistory,
            showBrowse = showBrowse,
        )
        TabOrderList(
            tabs = tabsState,
            isTabHidden = { it in config.moreTabs },
            reorderableState = reorderableState,
            lazyListState = lazyListState,
            contentPadding = paddingValues + topSmallPaddingValues,
        )
        }
    }
}

@Composable
private fun TabOrderList(
    tabs: List<Tab>,
    isTabHidden: (Tab) -> Boolean,
    reorderableState: ReorderableLazyListState,
    lazyListState: LazyListState,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = lazyListState,
        contentPadding = contentPadding + PaddingValues(horizontal = MaterialTheme.padding.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        items(
            items = tabs,
            key = { NavStyle.tabKey.getValue(it) },
        ) { tab ->
            ReorderableItem(reorderableState, NavStyle.tabKey.getValue(tab)) {
                TabOrderItem(
                    tab = tab,
                    isHidden = isTabHidden(tab),
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.TabOrderItem(
    tab: Tab,
    isHidden: Boolean,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(vertical = MaterialTheme.padding.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.DragHandle,
                contentDescription = null,
                modifier = Modifier
                    .padding(MaterialTheme.padding.medium)
                    .draggableHandle(),
            )
            Icon(
                imageVector = NavStyle.moreTabIcon(tab),
                contentDescription = null,
                modifier = Modifier.padding(end = MaterialTheme.padding.small),
            )
            Text(
                text = NavStyle.tabTitle(tab),
                modifier = Modifier.weight(1f),
            )
            if (isHidden) {
                Icon(
                    imageVector = Icons.Outlined.VisibilityOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(end = MaterialTheme.padding.small),
                )
                Text(
                    text = stringResource(AYMR.strings.pref_bottom_nav_hidden),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(end = MaterialTheme.padding.medium),
                )
            }
        }
    }
}
