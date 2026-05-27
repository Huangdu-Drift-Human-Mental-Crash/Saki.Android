package org.hdhmc.saki.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class SakiVisualTokens(
    val backgroundPrimaryOverlayAlpha: Float = 0.16f,
    val backgroundTertiaryOverlayAlpha: Float = 0.10f,
    val cardContainerAlpha: Float = 0.90f,
    val subtleCardContainerAlpha: Float = 0.88f,
    val selectedContainerAlpha: Float = 0.90f,
    val tonalContainerAlpha: Float = 0.42f,
)

internal val DefaultSakiVisualTokens = SakiVisualTokens()

internal val MaterialExpressiveSakiVisualTokens = SakiVisualTokens(
    backgroundPrimaryOverlayAlpha = 0.12f,
    backgroundTertiaryOverlayAlpha = 0.16f,
    cardContainerAlpha = 0.94f,
    subtleCardContainerAlpha = 0.92f,
    selectedContainerAlpha = 0.86f,
    tonalContainerAlpha = 0.50f,
)

internal val LocalSakiVisualTokens = staticCompositionLocalOf { DefaultSakiVisualTokens }

object SakiTheme {
    val visuals: SakiVisualTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalSakiVisualTokens.current
}

@Composable
@ReadOnlyComposable
fun sakiCardContainerColor(): Color =
    MaterialTheme.colorScheme.surface.copy(alpha = SakiTheme.visuals.cardContainerAlpha)

@Composable
@ReadOnlyComposable
fun sakiSubtleCardContainerColor(): Color =
    MaterialTheme.colorScheme.surface.copy(alpha = SakiTheme.visuals.subtleCardContainerAlpha)

@Composable
@ReadOnlyComposable
fun sakiSelectedContainerColor(): Color =
    MaterialTheme.colorScheme.primaryContainer.copy(alpha = SakiTheme.visuals.selectedContainerAlpha)

@Composable
@ReadOnlyComposable
fun sakiTonalContainerColor(): Color =
    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = SakiTheme.visuals.tonalContainerAlpha)
