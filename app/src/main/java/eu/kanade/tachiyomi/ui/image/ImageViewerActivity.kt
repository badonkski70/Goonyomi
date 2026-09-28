package eu.kanade.tachiyomi.ui.image

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Animatable
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Size
import com.github.chrisbanes.photoview.PhotoView
import eu.kanade.tachiyomi.data.coil.LocalImage
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import eu.kanade.tachiyomi.util.view.setComposeContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.episode.repository.EpisodeRepository
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.source.local.io.ArchiveAnime
import tachiyomi.source.local.io.anime.LocalAnimeSourceFileSystem
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object LocalImageViewer {

    /**
     * Opens [episode] in the gallery when it is a photo, and returns false when it is a video or
     * anything the gallery cannot show, so the caller plays it instead.
     */
    suspend fun launchIfImage(context: Context, episode: Episode): Boolean {
        if (!ArchiveAnime.isImageUrl(episode.url)) return false

        val animeDir = episode.url.substringBeforeLast('/')
        val fileName = episode.url.substringAfterLast('/')

        val photos = withContext(Dispatchers.IO) {
            Injekt.get<LocalAnimeSourceFileSystem>().getFilesInAnimeDirectory(animeDir)
                .filter { ArchiveAnime.isImage(it) }
                // Newest first, the same order the episode list is shown in, so swiping left walks
                // down the list the photo was tapped from.
                .sortedWith { a, b -> b.name.orEmpty().compareToCaseInsensitiveNaturalOrder(a.name.orEmpty()) }
        }

        if (photos.isEmpty()) return false

        val startIndex = photos.indexOfFirst { it.name == fileName }.coerceAtLeast(0)
        val uris = photos.map { it.uri.toString() }

        withUIContext {
            context.startActivity(ImageViewerActivity.newIntent(context, uris, startIndex))
        }
        return true
    }

    /**
     * Same, for the call sites that only hold an episode id.
     */
    suspend fun launchIfImage(context: Context, episodeId: Long): Boolean {
        val episode = Injekt.get<EpisodeRepository>().getEpisodeById(episodeId) ?: return false
        return launchIfImage(context, episode)
    }
}

class ImageViewerActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val uris = intent.getStringArrayListExtra(EXTRA_URIS).orEmpty()
        if (uris.isEmpty()) {
            finish()
            return
        }
        val startIndex = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, uris.lastIndex)

        setComposeContent {
            val pagerState = rememberPagerState(initialPage = startIndex) { uris.size }

            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                HorizontalPager(state = pagerState) { page ->
                    AndroidView(
                        // PhotoView does the pinch zoom, pan and double tap zoom.
                        factory = { context -> PhotoView(context) },
                        update = { view ->
                            val context = view.context
                            val request = ImageRequest.Builder(context)
                                // Full resolution, the whole point of zooming in.
                                .data(LocalImage(uris[page]))
                                .size(Size.ORIGINAL)
                                .memoryCachePolicy(CachePolicy.DISABLED)
                                .target { image ->
                                    val drawable = image.asDrawable(context.resources)
                                    view.setImageDrawable(drawable)
                                    // A gif decodes to an AnimatedImageDrawable, which ImageView only
                                    // starts when it is already attached and shown. An AndroidView
                                    // update can run before that, so start it here or it sits on its
                                    // first frame forever.
                                    (drawable as? Animatable)?.start()
                                }
                                .build()
                            context.imageLoader.enqueue(request)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(4.dp),
                ) {
                    IconButton(onClick = { finish() }) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(MR.strings.action_close),
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_URIS = "uris"
        private const val EXTRA_INDEX = "index"

        fun newIntent(context: Context, uris: List<String>, index: Int): Intent {
            return Intent(context, ImageViewerActivity::class.java).apply {
                putStringArrayListExtra(EXTRA_URIS, ArrayList(uris))
                putExtra(EXTRA_INDEX, index)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
    }
}
