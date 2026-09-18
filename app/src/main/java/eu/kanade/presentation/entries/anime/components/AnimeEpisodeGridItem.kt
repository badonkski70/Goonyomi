package eu.kanade.presentation.entries.anime.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.BadgeGroup
import tachiyomi.presentation.core.components.material.DISABLED_ALPHA

private const val GRID_SELECTED_COVER_ALPHA = 0.76f

@Composable
fun AnimeEpisodeGridItem(
    title: String,
    previewUrl: String?,
    seen: Boolean,
    bookmark: Boolean,
    fillermark: Boolean,
    selected: Boolean,
    downloadIndicatorEnabled: Boolean,
    downloadStateProvider: () -> AnimeDownload.State,
    downloadProgressProvider: () -> Int,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onDownloadClick: ((EpisodeDownloadAction) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    EpisodeGridItemSelectable(
        isSelected = selected,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
    ) {
        BoxWithConstraints {
            val indicatorSize = (maxWidth * 0.36f).coerceIn(16.dp, 44.dp)
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(ItemCover.Thumb.ratio),
                ) {
                    ItemCover.Thumb(
                        data = previewUrl?.let {
                            ImageRequest.Builder(LocalContext.current)
                                .data(it)
                                .crossfade(true)
                                .build()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (selected) GRID_SELECTED_COVER_ALPHA else 1f),
                    )
                    BadgeGroup(
                        modifier = Modifier
                            .padding(4.dp)
                            .align(Alignment.TopStart),
                    ) {
                        if (bookmark) {
                            Badge(
                                imageVector = Icons.Filled.Bookmark,
                                color = MaterialTheme.colorScheme.primary,
                                iconColor = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        if (fillermark) {
                            Badge(
                                imageVector = Icons.AutoMirrored.Filled.Label,
                                color = MaterialTheme.colorScheme.tertiary,
                                iconColor = MaterialTheme.colorScheme.onTertiary,
                            )
                        }
                    }
                    EpisodeDownloadIndicator(
                        enabled = downloadIndicatorEnabled,
                        size = indicatorSize,
                        modifier = Modifier
                            .padding(4.dp)
                            .align(Alignment.TopEnd),
                        downloadStateProvider = downloadStateProvider,
                        downloadProgressProvider = downloadProgressProvider,
                        onClick = { onDownloadClick?.invoke(it) },
                    )
                }
                Text(
                    text = title,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = LocalContentColor.current.copy(alpha = if (seen) DISABLED_ALPHA else 1f),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun EpisodeGridItemSelectable(
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .selectedOutline(isSelected = isSelected, color = MaterialTheme.colorScheme.secondary)
            .padding(4.dp),
    ) {
        val contentColor = if (isSelected) {
            MaterialTheme.colorScheme.onSecondary
        } else {
            LocalContentColor.current
        }
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

private fun Modifier.selectedOutline(
    isSelected: Boolean,
    color: Color,
) = drawBehind { if (isSelected) drawRect(color = color) }
