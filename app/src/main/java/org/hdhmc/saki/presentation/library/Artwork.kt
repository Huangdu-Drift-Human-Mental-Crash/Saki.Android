package org.hdhmc.saki.presentation.library

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import androidx.palette.graphics.Palette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.hdhmc.saki.domain.model.CachedSong
import org.hdhmc.saki.domain.model.ServerConfig
import org.hdhmc.saki.domain.model.ServerEndpoint
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

const val THUMBNAIL_COVER_ART_SIZE_PX = 256
const val PALETTE_COVER_ART_SIZE_PX = 300
const val FULL_COVER_ART_SIZE_PX = 768

@Composable
fun ArtworkCard(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    cornerRadiusDp: Int = 24,
    contentScale: ContentScale = ContentScale.Crop,
    requestSizePx: Int? = null,
) {
    val fallbackBrush = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f),
        ),
    )
    val context = LocalContext.current
    val imageModel = remember(model, requestSizePx, context) {
        if (model != null && requestSizePx != null) {
            ImageRequest.Builder(context)
                .data(model)
                .size(requestSizePx.coerceAtLeast(1))
                .build()
        } else {
            model
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadiusDp.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
    ) {
        if (imageModel != null) {
            AsyncImage(
                model = imageModel,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(fallbackBrush)
                    .clip(RoundedCornerShape(cornerRadiusDp.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.LibraryMusic,
                    contentDescription = null,
                    modifier = Modifier.size(42.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

/**
 * Hero artwork that adapts to the source aspect ratio: portrait images (height > width) are
 * centered in a square frame with a blurred fill on the sides (mirrors the Now Playing backdrop),
 * while landscape/square images keep their natural ratio with no fill. Blur uses RenderEffect on
 * API 31+; below that the sides fall back to the surface container color.
 */
@Composable
fun AdaptiveBlurArtwork(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    cornerRadiusDp: Int = 24,
) {
    val fallbackBrush = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f),
        ),
    )
    val context = LocalContext.current
    val request = remember(model, context) {
        ImageRequest.Builder(context)
            .data(model)
            .size(FULL_COVER_ART_SIZE_PX)
            .build()
    }
    val painter = rememberAsyncImagePainter(model = request)
    val state by painter.state.collectAsState()
    val intrinsic = (state as? AsyncImagePainter.State.Success)?.painter?.intrinsicSize
    val isPortrait = intrinsic != null && intrinsic.width > 0f && intrinsic.height > intrinsic.width
    val frameAspect = if (intrinsic != null && intrinsic.height > 0f && intrinsic.width >= intrinsic.height) {
        intrinsic.width / intrinsic.height
    } else {
        1f
    }
    val blurEffect = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            RenderEffect.createBlurEffect(60f, 60f, Shader.TileMode.CLAMP).asComposeRenderEffect()
        } else {
            null
        }
    }

    Surface(
        modifier = modifier.aspectRatio(frameAspect),
        shape = RoundedCornerShape(cornerRadiusDp.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
    ) {
        if (model == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(fallbackBrush),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.LibraryMusic,
                    contentDescription = null,
                    modifier = Modifier.size(42.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isPortrait && blurEffect != null) {
                    Image(
                        painter = painter,
                        contentDescription = null,
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                scaleX = 1.15f
                                scaleY = 1.15f
                                renderEffect = blurEffect
                            },
                        contentScale = ContentScale.Crop,
                    )
                }
                Image(
                    painter = painter,
                    contentDescription = contentDescription,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

/**
 * Extracts a vibrant accent color from [model]'s artwork for content-driven theming, trying the
 * vibrant / dark-vibrant / light-vibrant swatches in order. Desaturated or muted artwork (no
 * vibrant swatch) intentionally returns [fallback] rather than the dominant swatch, which tends to
 * be a washed-out gray. Mirrors the Now Playing palette extraction; the bitmap is decoded from
 * Coil at palette resolution.
 */
@Composable
fun rememberArtworkAccentColor(model: Any?, fallback: Color): Color {
    val context = LocalContext.current
    var accent by remember(model) { mutableStateOf(fallback) }
    LaunchedEffect(model) {
        if (model == null) return@LaunchedEffect
        val extracted = withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(model)
                    .size(PALETTE_COVER_ART_SIZE_PX)
                    .allowHardware(false)
                    .build()
                val image = context.imageLoader.execute(request).image ?: return@withContext null
                val palette = Palette.from(image.toBitmap()).clearFilters().generate()
                (palette.getVibrantColor(0).takeIf { it != 0 }
                    ?: palette.getDarkVibrantColor(0).takeIf { it != 0 }
                    ?: palette.getLightVibrantColor(0).takeIf { it != 0 })?.let(::Color)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            }
        }
        if (extracted != null) accent = extracted
    }
    return accent
}

fun resolveArtworkModel(
    server: ServerConfig?,
    coverArtId: String?,
    cachedSong: CachedSong?,
): Any? {
    val localCoverPath = cachedSong?.coverArtPath
    if (!localCoverPath.isNullOrBlank() && File(localCoverPath).exists()) {
        return File(localCoverPath)
    }
    return server?.buildCoverArtUrl(coverArtId)
}

/**
 * Builds a deterministic cover art URL using the first endpoint by order and a salt derived
 * from the cover art ID. Stable for a fixed server configuration and coverArtId, enabling
 * Coil's disk cache to reuse entries across thumbnail and full-size UI requests. At request
 * time, [CoverArtEndpointInterceptor] rewrites the base URL to the current best endpoint.
 */
private fun ServerConfig.buildCoverArtUrl(coverArtId: String?): String? {
    if (coverArtId.isNullOrBlank()) return null
    val endpoint = endpoints.sortedBy(ServerEndpoint::order).firstOrNull() ?: return null
    val baseUrl = endpoint.baseUrl.toHttpUrlOrNull() ?: return null
    val salt = md5(coverArtId).take(8)
    val hash = md5("$password$salt")

    return baseUrl.newBuilder()
        .addPathSegments("rest/getCoverArt.view")
        .addQueryParameter("id", coverArtId)
        .addQueryParameter("size", FULL_COVER_ART_SIZE_PX.toString())
        .addQueryParameter("u", username)
        .addQueryParameter("t", hash)
        .addQueryParameter("s", salt)
        .addQueryParameter("v", apiVersion)
        .addQueryParameter("c", clientName)
        .build()
        .toString()
}

private fun md5(input: String): String {
    val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray())
    return digest.joinToString(separator = "") { byte ->
        "%02x".format(Locale.US, byte)
    }
}
