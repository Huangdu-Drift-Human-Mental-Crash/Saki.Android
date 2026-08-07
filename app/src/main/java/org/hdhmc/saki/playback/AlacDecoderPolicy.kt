package org.hdhmc.saki.playback

import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.hdhmc.saki.domain.model.AlacDecoderMode

internal class AlacDecoderPolicy(initialMode: AlacDecoderMode) {
    private val mode = AtomicReference(initialMode)
    private val autoSystemDecoderFailed = AtomicBoolean(false)

    fun updateMode(newMode: AlacDecoderMode): Boolean {
        val previousMode = mode.getAndSet(newMode)
        if (previousMode == newMode) return false
        autoSystemDecoderFailed.set(false)
        return true
    }

    fun isBundledDecoderEnabled(): Boolean = mode.get() != AlacDecoderMode.SYSTEM

    fun shouldDisableSystemDecoder(): Boolean = when (mode.get()) {
        AlacDecoderMode.AUTO -> autoSystemDecoderFailed.get()
        AlacDecoderMode.SYSTEM -> false
        AlacDecoderMode.BUNDLED -> true
    }

    fun markAutoSystemDecoderFailed(): Boolean =
        mode.get() == AlacDecoderMode.AUTO &&
            autoSystemDecoderFailed.compareAndSet(false, true)
}

internal object AlacSystemDecoderSupport {
    val isSupported: Boolean by lazy(::querySystemDecoderSupport)

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun querySystemDecoderSupport(): Boolean =
        runCatching {
            MediaCodecSelector.DEFAULT
                .getDecoderInfos(
                    MimeTypes.AUDIO_ALAC,
                    /* requiresSecureDecoder = */ false,
                    /* requiresTunnelingDecoder = */ false,
                )
                .isNotEmpty()
        }.getOrDefault(false)
}
