package org.hdhmc.saki.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class SakiVisualTokens(
    val backgroundPrimaryOverlayAlpha: Float = 0.16f,
    val backgroundTertiaryOverlayAlpha: Float = 0.10f,
    val cardContainerAlpha: Float = 0.90f,
    val subtleCardContainerAlpha: Float = 0.88f,
    val selectedContainerAlpha: Float = 0.90f,
    val tonalContainerAlpha: Float = 0.42f,
    val nowPlayingBackgroundDominantOverlayAlpha: Float = 0.50f,
    val nowPlayingBackgroundAccentOverlayAlpha: Float = 0.35f,
    val nowPlayingBackgroundTailOverlayAlpha: Float = 0.12f,
    val nowPlayingCapsuleContainerAlpha: Float = 0.96f,
    val nowPlayingStatusContainerAlpha: Float = 0.88f,
    val nowPlayingQueueSelectedContainerAlpha: Float = 0.84f,
    val nowPlayingQueueContainerAlpha: Float = 0.42f,
    val nowPlayingDisabledEndpointContainerAlpha: Float = 0.50f,
    val nowPlayingArtworkBackdropContainerAlpha: Float = 0.34f,
    val nowPlayingArtworkBackdropOverlayAlpha: Float = 0.25f,
    val nowPlayingLyricsOverlayAlpha: Float = 0.65f,
    val nowPlayingPrimaryControlCornerRadius: Dp = 22.dp,
    val nowPlayingPrimaryControlHorizontalPadding: Dp = 22.dp,
    val nowPlayingPrimaryControlIconSize: Dp = 24.dp,
    val nowPlayingPrimaryControlLabelSpacing: Dp = 10.dp,
    val nowPlayingSecondaryControlContainerAlpha: Float = 0f,
    val nowPlayingSecondaryControlCornerRadius: Dp = 28.dp,
    val nowPlayingSecondaryControlIconSize: Dp = 24.dp,
    val nowPlayingToggleContainerAlpha: Float = 0f,
    val nowPlayingToggleSelectedContainerAlpha: Float = 0f,
    val nowPlayingToggleIconAlpha: Float = 0.38f,
    val nowPlayingToggleSelectedIconAlpha: Float = 1f,
    val nowPlayingMoreControlContainerAlpha: Float = 0f,
)

internal val DefaultSakiVisualTokens = SakiVisualTokens()

internal val MaterialExpressiveSakiVisualTokens = SakiVisualTokens(
    backgroundPrimaryOverlayAlpha = 0.12f,
    backgroundTertiaryOverlayAlpha = 0.16f,
    cardContainerAlpha = 0.94f,
    subtleCardContainerAlpha = 0.92f,
    selectedContainerAlpha = 0.86f,
    tonalContainerAlpha = 0.50f,
    nowPlayingBackgroundDominantOverlayAlpha = 0.58f,
    nowPlayingBackgroundAccentOverlayAlpha = 0.42f,
    nowPlayingBackgroundTailOverlayAlpha = 0.16f,
    nowPlayingCapsuleContainerAlpha = 0.98f,
    nowPlayingStatusContainerAlpha = 0.94f,
    nowPlayingQueueSelectedContainerAlpha = 0.78f,
    nowPlayingQueueContainerAlpha = 0.52f,
    nowPlayingDisabledEndpointContainerAlpha = 0.58f,
    nowPlayingArtworkBackdropContainerAlpha = 0.44f,
    nowPlayingArtworkBackdropOverlayAlpha = 0.18f,
    nowPlayingLyricsOverlayAlpha = 0.72f,
    nowPlayingPrimaryControlCornerRadius = 30.dp,
    nowPlayingPrimaryControlHorizontalPadding = 24.dp,
    nowPlayingPrimaryControlIconSize = 28.dp,
    nowPlayingPrimaryControlLabelSpacing = 12.dp,
    nowPlayingSecondaryControlContainerAlpha = 0.56f,
    nowPlayingSecondaryControlCornerRadius = 24.dp,
    nowPlayingSecondaryControlIconSize = 26.dp,
    nowPlayingToggleContainerAlpha = 0.48f,
    nowPlayingToggleSelectedContainerAlpha = 0.82f,
    nowPlayingToggleIconAlpha = 0.80f,
    nowPlayingToggleSelectedIconAlpha = 1f,
    nowPlayingMoreControlContainerAlpha = 0.60f,
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
