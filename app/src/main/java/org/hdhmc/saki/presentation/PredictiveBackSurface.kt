package org.hdhmc.saki.presentation

import android.view.animation.PathInterpolator
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

private val PredictiveBackInterpolator = PathInterpolator(0.1f, 0.1f, 0f, 1f)

@Composable
fun Modifier.predictiveBackMotion(
    enabled: Boolean,
    onBack: () -> Unit,
): Modifier {
    var progress by remember { mutableFloatStateOf(0f) }
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var touchY by remember { mutableFloatStateOf(0f) }
    // Only animate on cancel (progress -> 0), snap during gesture
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(stiffness = 400f),
    )
    val latestOnBack by rememberUpdatedState(onBack)
    PredictiveBackHandler(enabled = enabled) { backEvents ->
        try {
            backEvents.collect { event ->
                progress = event.progress
                swipeEdge = event.swipeEdge
                touchY = event.touchY
            }
            latestOnBack()
        } catch (_: kotlinx.coroutines.CancellationException) {
            // User cancelled — animatedProgress springs back to 0
        } finally {
            progress = 0f
        }
    }

    // Use raw progress during gesture, animated on cancel
    val displayProgress = if (progress > 0f) progress else animatedProgress
    val interpolated = if (displayProgress <= 0f) 0f
    else PredictiveBackInterpolator.getInterpolation(displayProgress.coerceIn(0f, 1f))
    val margin8dp = with(LocalDensity.current) { 8.dp.toPx() }
    val currentSwipeEdge = swipeEdge
    val currentTouchY = touchY

    return if (interpolated <= 0f) this
    else this.graphicsLayer {
        val scale = 1f - (interpolated * 0.1f)
        scaleX = scale
        scaleY = scale
        val maxShiftX = (size.width / 20f - margin8dp).coerceAtLeast(0f)
        translationX = interpolated * maxShiftX * if (currentSwipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
        val centerY = size.height / 2f
        val maxShiftY = (size.height / 20f - margin8dp).coerceAtLeast(0f)
        val yOffset = if (centerY > 0f) (currentTouchY - centerY) / centerY else 0f
        translationY = interpolated * maxShiftY * yOffset
        shape = RoundedCornerShape((interpolated * 28f).dp)
        clip = true
    }
}
