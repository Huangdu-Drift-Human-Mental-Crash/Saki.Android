package org.hdhmc.saki.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import org.hdhmc.saki.domain.model.SakiPaletteStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class SakiVisualTokens(
    val backgroundPrimaryOverlayAlpha: Float = 0.05f,
    val backgroundTertiaryOverlayAlpha: Float = 0.07f,
    val cardContainerAlpha: Float = 0.98f,
    val subtleCardContainerAlpha: Float = 0.96f,
    val selectedContainerAlpha: Float = 0.45f,
    val tonalContainerAlpha: Float = 0.72f,
    val chromeIconButtonContainerAlpha: Float = 0f,
    val chromeIconButtonCornerRadius: Dp = 24.dp,
    val chromeIconButtonIconSize: Dp = 24.dp,
    val browseSectionChipContainerAlpha: Float = 1f,
    val browseSectionChipSelectedContainerAlpha: Float = 1f,
    val browseSectionChipOutlineAlpha: Float = 0.56f,
    val browseSectionChipCornerRadius: Dp = 22.dp,
    val browseRowActionIconAlpha: Float = 0.72f,
    val browseRowNavigationIconAlpha: Float = 0.64f,
    val pullRefreshLoadingIndicatorSize: Dp = 36.dp,
    val miniPlayerContainerCornerRadius: Dp = 32.dp,
    val miniPlayerHandleWidth: Dp = 44.dp,
    val miniPlayerHandleAlpha: Float = 0.38f,
    val miniPlayerRestingAlpha: Float = 0.95f,
    val miniPlayerScrollingAlpha: Float = 0.30f,
    val miniPlayerFastScrollingAlpha: Float = 0.12f,
    val miniPlayerArtworkCornerRadius: Dp = 16.dp,
    val miniPlayerControlContainerAlpha: Float = 0.56f,
    val miniPlayerControlCornerRadius: Dp = 22.dp,
    val miniPlayerControlVisibleSize: Dp = 42.dp,
    val miniPlayerControlIconSize: Dp = 22.dp,
    val miniPlayerPrimaryControlContainerAlpha: Float = 1f,
    val miniPlayerPrimaryControlSize: Dp = 58.dp,
    val miniPlayerPrimaryControlIconSize: Dp = 28.dp,
    val nowPlayingBackgroundDominantOverlayAlpha: Float = 0.58f,
    val nowPlayingBackgroundAccentOverlayAlpha: Float = 0.42f,
    val nowPlayingBackgroundTailOverlayAlpha: Float = 0.16f,
    val nowPlayingCapsuleContainerAlpha: Float = 0.98f,
    val nowPlayingStatusContainerAlpha: Float = 0.94f,
    val nowPlayingQueueSelectedContainerAlpha: Float = 0.78f,
    val nowPlayingQueueContainerAlpha: Float = 0.52f,
    val nowPlayingDisabledEndpointContainerAlpha: Float = 0.58f,
    val nowPlayingArtworkBackdropContainerAlpha: Float = 0.44f,
    val nowPlayingArtworkBackdropOverlayAlpha: Float = 0.18f,
    val nowPlayingLyricsOverlayAlpha: Float = 0.72f,
    val nowPlayingPrimaryControlCornerRadius: Dp = 30.dp,
    val nowPlayingPrimaryControlHorizontalPadding: Dp = 24.dp,
    val nowPlayingPrimaryControlIconSize: Dp = 28.dp,
    val nowPlayingPrimaryControlLabelSpacing: Dp = 12.dp,
    val nowPlayingSecondaryControlContainerAlpha: Float = 0.56f,
    val nowPlayingTopControlEdgeOffset: Dp = 0.dp,
    val nowPlayingSecondaryControlCornerRadius: Dp = 24.dp,
    val nowPlayingSecondaryControlIconSize: Dp = 26.dp,
    val nowPlayingToggleContainerAlpha: Float = 0.48f,
    val nowPlayingToggleSelectedContainerAlpha: Float = 0.82f,
    val nowPlayingToggleIconAlpha: Float = 0.80f,
    val nowPlayingToggleSelectedIconAlpha: Float = 1f,
    val nowPlayingMoreControlContainerAlpha: Float = 0.60f,
)

internal val DefaultSakiVisualTokens = SakiVisualTokens()

internal val LocalSakiVisualTokens = staticCompositionLocalOf { DefaultSakiVisualTokens }

internal val LocalSakiPaletteStyle = staticCompositionLocalOf { SakiPaletteStyle.TONAL_SPOT }

object SakiTheme {
    val visuals: SakiVisualTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalSakiVisualTokens.current
}

@Composable
@ReadOnlyComposable
fun sakiCardContainerColor(): Color = MaterialTheme.colorScheme.surfaceContainerHigh

@Composable
@ReadOnlyComposable
fun sakiSubtleCardContainerColor(): Color = MaterialTheme.colorScheme.surfaceContainer

@Composable
@ReadOnlyComposable
fun sakiSelectedContainerColor(): Color =
    MaterialTheme.colorScheme.primaryContainer.copy(alpha = SakiTheme.visuals.selectedContainerAlpha)

@Composable
@ReadOnlyComposable
fun sakiTonalContainerColor(): Color = MaterialTheme.colorScheme.surfaceContainerHighest

@Composable
fun SakiChromeIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val visuals = SakiTheme.visuals
    if (visuals.chromeIconButtonContainerAlpha <= 0f) {
        IconButton(onClick = onClick, modifier = modifier) {
            Icon(icon, contentDescription = contentDescription)
        }
    } else {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(visuals.chromeIconButtonCornerRadius),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            IconButton(onClick = onClick) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(visuals.chromeIconButtonIconSize),
                )
            }
        }
    }
}
