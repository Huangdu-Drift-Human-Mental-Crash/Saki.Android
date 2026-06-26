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
