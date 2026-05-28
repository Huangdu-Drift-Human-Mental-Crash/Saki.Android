package org.hdhmc.saki.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.size
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
    val useExpressiveSurfaceContainers: Boolean = false,
    val chromeIconButtonContainerAlpha: Float = 0f,
    val chromeIconButtonCornerRadius: Dp = 24.dp,
    val chromeIconButtonIconSize: Dp = 24.dp,
    val browseSectionChipContainerAlpha: Float = 0f,
    val browseSectionChipSelectedContainerAlpha: Float = 0f,
    val browseSectionChipOutlineAlpha: Float = 0f,
    val browseSectionChipCornerRadius: Dp = 18.dp,
    val browseRowActionIconAlpha: Float = 1f,
    val browseRowNavigationIconAlpha: Float = 1f,
    val useExpressiveLoadingIndicator: Boolean = false,
    val pullRefreshLoadingIndicatorSize: Dp = 36.dp,
    val miniPlayerContainerCornerRadius: Dp = 28.dp,
    val miniPlayerHandleWidth: Dp = 36.dp,
    val miniPlayerHandleAlpha: Float = 0.28f,
    val miniPlayerArtworkCornerRadius: Dp = 14.dp,
    val miniPlayerControlContainerAlpha: Float = 0f,
    val miniPlayerControlCornerRadius: Dp = 24.dp,
    val miniPlayerControlIconSize: Dp = 24.dp,
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
    val nowPlayingTopControlEdgeOffset: Dp = 12.dp,
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
    cardContainerAlpha = 0.98f,
    subtleCardContainerAlpha = 0.96f,
    selectedContainerAlpha = 0.86f,
    tonalContainerAlpha = 0.72f,
    useExpressiveSurfaceContainers = true,
    chromeIconButtonContainerAlpha = 1f,
    chromeIconButtonCornerRadius = 24.dp,
    chromeIconButtonIconSize = 24.dp,
    browseSectionChipContainerAlpha = 1f,
    browseSectionChipSelectedContainerAlpha = 1f,
    browseSectionChipOutlineAlpha = 0.56f,
    browseSectionChipCornerRadius = 22.dp,
    browseRowActionIconAlpha = 0.72f,
    browseRowNavigationIconAlpha = 0.64f,
    useExpressiveLoadingIndicator = true,
    pullRefreshLoadingIndicatorSize = 36.dp,
    miniPlayerContainerCornerRadius = 32.dp,
    miniPlayerHandleWidth = 44.dp,
    miniPlayerHandleAlpha = 0.38f,
    miniPlayerArtworkCornerRadius = 16.dp,
    miniPlayerControlContainerAlpha = 1f,
    miniPlayerControlCornerRadius = 24.dp,
    miniPlayerControlIconSize = 24.dp,
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
    nowPlayingTopControlEdgeOffset = 0.dp,
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
    if (SakiTheme.visuals.useExpressiveSurfaceContainers) {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = SakiTheme.visuals.cardContainerAlpha)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = SakiTheme.visuals.cardContainerAlpha)
    }

@Composable
@ReadOnlyComposable
fun sakiSubtleCardContainerColor(): Color =
    if (SakiTheme.visuals.useExpressiveSurfaceContainers) {
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = SakiTheme.visuals.subtleCardContainerAlpha)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = SakiTheme.visuals.subtleCardContainerAlpha)
    }

@Composable
@ReadOnlyComposable
fun sakiSelectedContainerColor(): Color =
    MaterialTheme.colorScheme.primaryContainer.copy(alpha = SakiTheme.visuals.selectedContainerAlpha)

@Composable
@ReadOnlyComposable
fun sakiTonalContainerColor(): Color =
    if (SakiTheme.visuals.useExpressiveSurfaceContainers) {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = SakiTheme.visuals.tonalContainerAlpha)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = SakiTheme.visuals.tonalContainerAlpha)
    }

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
            color = MaterialTheme.colorScheme.secondaryContainer.copy(
                alpha = visuals.chromeIconButtonContainerAlpha,
            ),
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
