package org.hdhmc.saki.playback

import android.content.Context
import android.os.Handler
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import java.util.ArrayList
import org.hdhmc.saki.decoder.alac.BundledAlacAudioRenderer

@UnstableApi
internal class SakiRenderersFactory(
    context: Context,
    private val alacDecoderPolicy: AlacDecoderPolicy,
) : DefaultRenderersFactory(context) {
    private lateinit var bundledAlacRenderer: BundledAlacAudioRenderer

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>,
    ) {
        val policyAwareMediaCodecSelector = MediaCodecSelector {
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder,
            ->
            if (
                MimeTypes.AUDIO_ALAC.equals(mimeType, ignoreCase = true) &&
                alacDecoderPolicy.shouldDisableSystemDecoder()
            ) {
                emptyList()
            } else {
                mediaCodecSelector.getDecoderInfos(
                    mimeType,
                    requiresSecureDecoder,
                    requiresTunnelingDecoder,
                )
            }
        }
        super.buildAudioRenderers(
            context,
            extensionRendererMode,
            policyAwareMediaCodecSelector,
            enableDecoderFallback,
            audioSink,
            eventHandler,
            eventListener,
            out,
        )
        bundledAlacRenderer = BundledAlacAudioRenderer(
            eventHandler,
            eventListener,
            audioSink,
            alacDecoderPolicy::isBundledDecoderEnabled,
        )
        out += bundledAlacRenderer
    }

    fun refreshAlacDecoderPolicy(player: ExoPlayer) {
        if (!::bundledAlacRenderer.isInitialized) return
        player.createMessage(bundledAlacRenderer)
            .setType(BundledAlacAudioRenderer.MSG_REFRESH_DECODER_POLICY)
            .send()
    }
}
