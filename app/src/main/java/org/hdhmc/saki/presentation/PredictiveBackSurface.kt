package org.hdhmc.saki.presentation

import android.view.animation.PathInterpolator
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
    val state = rememberPredictiveBackMotion(
        enabled = enabled,
        onBack = onBack,
        maxScaleReduction = maxScaleReduction,
        maxHorizontalShiftFraction = maxHorizontalShiftFraction,
        horizontalShiftInset = horizontalShiftInset,
        maxVerticalShiftFraction = maxVerticalShiftFraction,
        verticalShiftInset = verticalShiftInset,
        maxCornerRadius = maxCornerRadius,
        targetAlpha = targetAlpha,
    )
    return this.then(state.modifier)
}

/**
 * Holds the progress of a page/route's predictive-back motion and exposes both the [modifier] that
 * renders it and a programmatic [dismiss] that replays the same commit animation a completed back
 * gesture would, then invokes `onBack`. Route an in-app back/close button through [dismiss] so the
 * button animates identically to the gesture (and to the system back button, which
 * [PredictiveBackHandler] already animates).
 */
/**
 * Holds the motion state for a page/route surface and exposes both the [modifier] that renders it
 * and a programmatic [dismiss]. The predictive gesture — and the system back button, which
 * [PredictiveBackHandler] drives — animate the follow-the-finger preview + commit. An in-app
 * back/close button should instead go through [dismiss], which plays a quick reverse-of-open
 * (slide down + fade out) before invoking `onBack`.
 */
@Stable
class PredictiveBackMotionState internal constructor(
    private val scope: CoroutineScope,
    internal val progress: Animatable<Float, AnimationVector1D>,
    internal val dismissProgress: Animatable<Float, AnimationVector1D>,
    private val onBack: () -> Unit,
    private val maxScaleReduction: Float,
    private val maxHorizontalShiftFraction: Float,
    private val horizontalShiftInset: Dp,
    private val maxVerticalShiftFraction: Float,
    private val verticalShiftInset: Dp,
    private val maxCornerRadius: Dp,
    private val targetAlpha: Float,
    private val dismissOffsetFraction: Float,
) {
    internal var swipeEdge by mutableIntStateOf(BackEventCompat.EDGE_LEFT)
    internal var touchY by mutableFloatStateOf(0f)
    private var isDismissing = false

    /**
     * Programmatic close for an in-app back/close button. This is NOT the predictive follow-finger
     * commit — a tap has no gesture to track. Instead it plays a quick reverse-of-open (slide down
     * + fade out), then invokes onBack. Ignores re-entrant calls so a double-tap can't invoke
     * onBack more than once.
     */
    fun dismiss() {
        if (isDismissing) return
        isDismissing = true
        scope.launch {
            try {
                if (dismissProgress.value < 1f) {
                    dismissProgress.animateTo(1f, animationSpec = spring(dampingRatio = 1f, stiffness = 900f))
                }
                onBack()
            } finally {
                isDismissing = false
            }
        }
    }

    val modifier: Modifier = Modifier.graphicsLayer {
        val gestureProgress = progress.value
        val gestureInterpolated = if (gestureProgress <= 0f) 0f
        else PredictiveBackInterpolator.getInterpolation(gestureProgress.coerceIn(0f, 1f))
        val exit = dismissProgress.value.coerceIn(0f, 1f)
        if (gestureInterpolated <= 0f && exit <= 0f) return@graphicsLayer

        var ty = 0f
        if (gestureInterpolated > 0f) {
            // Predictive gesture: scale + rounded corners + slight follow-the-finger translate.
            val scale = 1f - (gestureInterpolated * maxScaleReduction)
            scaleX = scale
            scaleY = scale
            val maxShiftX = (size.width * maxHorizontalShiftFraction - horizontalShiftInset.toPx()).coerceAtLeast(0f)
            translationX = gestureInterpolated * maxShiftX * if (swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
            val centerY = size.height / 2f
            val maxShiftY = (size.height * maxVerticalShiftFraction - verticalShiftInset.toPx()).coerceAtLeast(0f)
            val yOffset = if (centerY > 0f) (touchY - centerY) / centerY else 0f
            ty += gestureInterpolated * maxShiftY * yOffset
            shape = RoundedCornerShape((gestureInterpolated * maxCornerRadius.value).dp)
            clip = true
        }
        if (exit > 0f) {
            // Button close: quick reverse of the open cover — slide down + fade out.
            ty += exit * size.height * dismissOffsetFraction
        }
        translationY = ty
        alpha = (1f - (gestureInterpolated * (1f - targetAlpha))) * (1f - exit)
    }
}

@Composable
fun rememberPredictiveBackMotion(
    enabled: Boolean,
    onBack: () -> Unit,
    maxScaleReduction: Float = 0.1f,
    maxHorizontalShiftFraction: Float = 1f / 20f,
    horizontalShiftInset: Dp = 8.dp,
    maxVerticalShiftFraction: Float = 1f / 20f,
    verticalShiftInset: Dp = 8.dp,
    maxCornerRadius: Dp = 28.dp,
    targetAlpha: Float = 1f,
    dismissOffsetFraction: Float = 0.06f,
): PredictiveBackMotionState {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    val dismissProgress = remember { Animatable(0f) }
    val latestOnBack = rememberUpdatedState(onBack)
    val state = remember {
        PredictiveBackMotionState(
            scope = scope,
            progress = progress,
            dismissProgress = dismissProgress,
            onBack = { latestOnBack.value() },
            maxScaleReduction = maxScaleReduction,
            maxHorizontalShiftFraction = maxHorizontalShiftFraction,
            horizontalShiftInset = horizontalShiftInset,
            maxVerticalShiftFraction = maxVerticalShiftFraction,
            verticalShiftInset = verticalShiftInset,
            maxCornerRadius = maxCornerRadius,
            targetAlpha = targetAlpha,
            dismissOffsetFraction = dismissOffsetFraction,
        )
    }
    LaunchedEffect(enabled) {
        if (enabled) {
            progress.snapTo(0f)
            dismissProgress.snapTo(0f)
        }
    }
    PredictiveBackHandler(enabled = enabled) { backEvents ->
        var completed = false
        try {
            backEvents.collect { event ->
                progress.snapTo(event.progress)
                state.swipeEdge = event.swipeEdge
                state.touchY = event.touchY
            }
            completed = true
            progress.animateTo(1f, animationSpec = spring(stiffness = 400f))
            latestOnBack.value()
        } catch (_: CancellationException) {
            if (!completed) {
                // User cancelled — animate back from the exact gesture position.
                progress.animateTo(0f, animationSpec = spring(stiffness = 400f))
            }
        }
    }
    return state
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
