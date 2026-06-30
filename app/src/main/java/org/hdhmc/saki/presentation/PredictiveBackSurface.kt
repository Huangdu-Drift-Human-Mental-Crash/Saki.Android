package org.hdhmc.saki.presentation

import android.view.animation.PathInterpolator
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException

private val PredictiveBackInterpolator = PathInterpolator(0.1f, 0.1f, 0f, 1f)

/**
 * Predictive-back model by surface role (issue #317).
 *
 * Every animated back gesture must preview the same target the completed back will reach. Pick the
 * treatment by what the surface *is*, not ad-hoc per screen:
 *
 * - **Page / route** (Now Playing, Settings, Search overlay, Browse detail pages): use
 *   [predictiveBackMotion] — scale + rounded corners + slight translate, revealing the previous
 *   surface. Gate `enabled` so it is OFF whenever a higher-priority surface is consuming back
 *   (a sheet, dialog, menu, or an internal expanded state). See `NowPlayingOverlay`.
 * - **Anchored sheet** (queue sheet): do NOT use page motion. Map predictive progress to the sheet
 *   anchors (Expanded -> PartiallyExpanded -> Hidden) with a single offset source of truth and a
 *   critically-damped (non-bouncy) commit so the settle never rebounds. Reference implementation:
 *   `PlayerQueueSheet` in PlayerChrome.kt.
 * - **Dialog / menu** (AlertDialog, DropdownMenu, single-anchor ModalBottomSheet): rely on the
 *   Material/system default dismiss. These render in their own window / install their own back
 *   handling, so they already pre-empt the page motion underneath — just make sure the page's
 *   `predictiveBackMotion` is not *also* enabled for an inline (non-window) overlay.
 * - **Internal state** (lyrics panel, other in-page expanded/collapsed states): a plain
 *   `BackHandler` that undoes the state first, and keep the page motion disabled while it is shown.
 */
@Composable
fun Modifier.predictiveBackMotion(
    enabled: Boolean,
    onBack: () -> Unit,
    maxScaleReduction: Float = 0.1f,
    maxHorizontalShiftFraction: Float = 1f / 20f,
    horizontalShiftInset: Dp = 8.dp,
    maxVerticalShiftFraction: Float = 1f / 20f,
    verticalShiftInset: Dp = 8.dp,
    maxCornerRadius: Dp = 28.dp,
    targetAlpha: Float = 1f,
): Modifier {
    val progress = remember { Animatable(0f) }
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var touchY by remember { mutableFloatStateOf(0f) }
    val latestOnBack by rememberUpdatedState(onBack)
    LaunchedEffect(enabled) {
        if (enabled) {
            progress.snapTo(0f)
        }
    }
    PredictiveBackHandler(enabled = enabled) { backEvents ->
        var completed = false
        try {
            backEvents.collect { event ->
                progress.snapTo(event.progress)
                swipeEdge = event.swipeEdge
                touchY = event.touchY
            }
            completed = true
            progress.animateTo(1f, animationSpec = spring(stiffness = 400f))
            latestOnBack()
        } catch (_: CancellationException) {
            if (!completed) {
                // User cancelled — animate back from the exact gesture position.
                progress.animateTo(0f, animationSpec = spring(stiffness = 400f))
            }
        }
    }

    val displayProgress = progress.value
    val interpolated = if (displayProgress <= 0f) 0f
    else PredictiveBackInterpolator.getInterpolation(displayProgress.coerceIn(0f, 1f))
    val density = LocalDensity.current
    val horizontalShiftInsetPx = with(density) { horizontalShiftInset.toPx() }
    val verticalShiftInsetPx = with(density) { verticalShiftInset.toPx() }
    val currentSwipeEdge = swipeEdge
    val currentTouchY = touchY

    return if (interpolated <= 0f) this
    else this.graphicsLayer {
        val scale = 1f - (interpolated * maxScaleReduction)
        scaleX = scale
        scaleY = scale
        alpha = 1f - (interpolated * (1f - targetAlpha))
        val maxShiftX = (size.width * maxHorizontalShiftFraction - horizontalShiftInsetPx).coerceAtLeast(0f)
        translationX = interpolated * maxShiftX * if (currentSwipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
        val centerY = size.height / 2f
        val maxShiftY = (size.height * maxVerticalShiftFraction - verticalShiftInsetPx).coerceAtLeast(0f)
        val yOffset = if (centerY > 0f) (currentTouchY - centerY) / centerY else 0f
        translationY = interpolated * maxShiftY * yOffset
        shape = RoundedCornerShape((interpolated * maxCornerRadius.value).dp)
        clip = true
    }
}

/**
 * Enter motion for a hierarchical child **page/route** (e.g. a Browse detail pushed onto the
 * stack). Open is a discrete action, not a gesture, so this is NOT the inverse of the back
 * preview — it is a crisp, confident forward "cover": the incoming page rises a short distance
 * and fades in over its parent, then settles. Pairs coherently with the shrink-away back without
 * trying to rewind it.
 *
 * The animation runs once per composition; wrap the page in a `key(route)` (as Browse does) so a
 * freshly pushed route re-triggers it. Independent of the back gesture's progress.
 */
@Composable
fun Modifier.pageEnterMotion(
    initialOffsetFraction: Float = 0.06f,
): Modifier {
    // 1f = just entered (shifted down + transparent), 0f = settled at rest.
    val progress = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 0f,
            animationSpec = spring(dampingRatio = 1f, stiffness = 500f),
        )
    }
    val displayProgress = progress.value
    val interpolated = if (displayProgress <= 0f) 0f
    else PredictiveBackInterpolator.getInterpolation(displayProgress.coerceIn(0f, 1f))

    return if (interpolated <= 0f) this
    else this.graphicsLayer {
        alpha = 1f - interpolated
        translationY = interpolated * size.height * initialOffsetFraction
    }
}
