package org.hdhmc.saki.decoder.alac;

import android.os.Handler;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.CryptoConfig;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DecoderAudioRenderer;
import java.util.function.BooleanSupplier;

/** Media3 audio renderer backed entirely by the bundled Java ALAC decoder. */
@UnstableApi
public final class BundledAlacAudioRenderer extends DecoderAudioRenderer<BundledAlacDecoder> {
    public static final String RENDERER_NAME = "SakiBundledAlacAudioRenderer";
    public static final int MSG_REFRESH_DECODER_POLICY = Renderer.MSG_CUSTOM_BASE + 1;

    private final BooleanSupplier decoderEnabled;

    public BundledAlacAudioRenderer(
            Handler eventHandler,
            AudioRendererEventListener eventListener,
            AudioSink audioSink,
            BooleanSupplier decoderEnabled) {
        super(eventHandler, eventListener, audioSink);
        this.decoderEnabled = decoderEnabled;
    }

    @Override
    public String getName() {
        return RENDERER_NAME;
    }

    @Override
    protected @C.FormatSupport int supportsFormatInternal(Format format) {
        if (!MimeTypes.AUDIO_ALAC.equalsIgnoreCase(format.sampleMimeType)) {
            return C.FORMAT_UNSUPPORTED_TYPE;
        }
        if (!decoderEnabled.getAsBoolean()) {
            return C.FORMAT_UNSUPPORTED_SUBTYPE;
        }
        if (format.cryptoType != C.CRYPTO_TYPE_NONE) {
            return C.FORMAT_UNSUPPORTED_DRM;
        }
        final AlacStreamInfo streamInfo;
        try {
            streamInfo = AlacStreamInfo.fromInitializationData(format.initializationData);
        } catch (IllegalArgumentException error) {
            return C.FORMAT_UNSUPPORTED_SUBTYPE;
        }
        Format outputFormat = Util.getPcmFormat(
                Util.getPcmEncoding(streamInfo.getSampleSize()),
                streamInfo.getChannelCount(),
                streamInfo.getSampleRate());
        return sinkSupportsFormat(outputFormat)
                ? C.FORMAT_HANDLED
                : C.FORMAT_UNSUPPORTED_SUBTYPE;
    }

    @Override
    protected BundledAlacDecoder createDecoder(
            Format format, CryptoConfig cryptoConfig)
            throws BundledAlacDecoderException {
        return new BundledAlacDecoder(format);
    }

    @Override
    protected Format getOutputFormat(BundledAlacDecoder decoder) {
        AlacStreamInfo streamInfo = decoder.getStreamInfo();
        return Util.getPcmFormat(
                Util.getPcmEncoding(streamInfo.getSampleSize()),
                streamInfo.getChannelCount(),
                streamInfo.getSampleRate());
    }

    @Override
    public void handleMessage(int messageType, Object message)
            throws ExoPlaybackException {
        if (messageType == MSG_REFRESH_DECODER_POLICY) {
            onRendererCapabilitiesChanged();
            return;
        }
        super.handleMessage(messageType, message);
    }
}
