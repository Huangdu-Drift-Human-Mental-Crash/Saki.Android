package org.hdhmc.saki.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import org.hdhmc.saki.ui.theme.SakiTheme

@Composable
fun rememberBrowseBackgroundBrush(): Brush {
    val colorScheme = MaterialTheme.colorScheme
    val visuals = SakiTheme.visuals
    return remember(colorScheme, visuals) {
        Brush.verticalGradient(
            listOf(
                colorScheme.primary.copy(alpha = visuals.backgroundPrimaryOverlayAlpha)
                    .compositeOver(colorScheme.background),
                colorScheme.tertiary.copy(alpha = visuals.backgroundTertiaryOverlayAlpha)
                    .compositeOver(colorScheme.surface),
                colorScheme.background,
            ),
        )
    }
}

fun bottomContentPadding(overlayPadding: Dp) = PaddingValues(bottom = 24.dp + overlayPadding)
