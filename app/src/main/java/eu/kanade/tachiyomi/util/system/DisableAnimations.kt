package eu.kanade.tachiyomi.util.system

import android.content.Context
import android.view.View
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.PausableMonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.WindowRecomposerFactory
import androidx.compose.ui.platform.WindowRecomposerPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import eu.kanade.domain.ui.UiPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Replaces the default window recomposer factory with one that seeds every recomposer with a
 * [MotionDurationScale] that reports 0 when "Disable animations" is on, disabling all Compose
 * animations app-wide. Mirrors how the OS "remove animations" setting scales durations.
 *
 * This is a public-API replica of androidx's internal `View.createLifecycleAwareWindowRecomposer`,
 * which we cannot call directly. `androidx.compose.runtime.animation` reads `MotionDurationScale`
 * from the effect coroutine context, which originates here.
 */
@OptIn(InternalComposeUiApi::class)
fun installAppMotionDurationScale(
    applicationContext: Context,
    uiPreferences: UiPreferences,
) {
    WindowRecomposerPolicy.setFactory(
        WindowRecomposerFactory { rootView ->
            createDisabledAnimationsRecomposer(
                rootView = rootView,
                scale = UiMotionDurationScale(applicationContext, uiPreferences),
            )
        },
    )
}

private fun createDisabledAnimationsRecomposer(
    rootView: View,
    scale: MotionDurationScale,
): Recomposer {
    val baseContext = AndroidUiDispatcher.CurrentThread
    val pausableClock = baseContext[MonotonicFrameClock]?.let {
        PausableMonotonicFrameClock(it).apply { pause() }
    }
    val context = baseContext + (pausableClock ?: EmptyCoroutineContext) + scale
    val recomposer = Recomposer(context).also { it.pauseCompositionFrameClock() }
    val runRecomposeScope = CoroutineScope(context)
    val viewTreeLifecycle = checkNotNull(rootView.findViewTreeLifecycleOwner()?.lifecycle) {
        "ViewTreeLifecycleOwner not found from $rootView"
    }

    rootView.addOnAttachStateChangeListener(
        object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}

            override fun onViewDetachedFromWindow(v: View) {
                rootView.removeOnAttachStateChangeListener(this)
                recomposer.cancel()
            }
        },
    )
    viewTreeLifecycle.addObserver(
        object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                val self = this
                when (event) {
                    Lifecycle.Event.ON_CREATE -> {
                        // Undispatched since we've configured the scope to be on the UI thread.
                        runRecomposeScope.launch(start = CoroutineStart.UNDISPATCHED) {
                            try {
                                recomposer.runRecomposeAndApplyChanges()
                            } finally {
                                source.lifecycle.removeObserver(self)
                            }
                        }
                    }
                    Lifecycle.Event.ON_START -> {
                        pausableClock?.resume()
                        recomposer.resumeCompositionFrameClock()
                    }
                    Lifecycle.Event.ON_STOP -> {
                        recomposer.pauseCompositionFrameClock()
                    }
                    Lifecycle.Event.ON_DESTROY -> {
                        recomposer.cancel()
                    }
                    else -> Unit
                }
            }
        },
    )
    return recomposer
}

private class UiMotionDurationScale(
    private val context: Context,
    private val uiPreferences: UiPreferences,
) : MotionDurationScale {
    override val key: CoroutineContext.Key<*> get() = MotionDurationScale.Key
    override val scaleFactor: Float
        get() = if (uiPreferences.disableAnimations().get()) 0f else context.animatorDurationScale
}
