package org.hdhmc.saki.decoder.alac;

import androidx.media3.decoder.DecoderException;
import androidx.media3.common.util.UnstableApi;

/** Thrown when the bundled Java ALAC decoder cannot initialize or decode a frame. */
@UnstableApi
public final class BundledAlacDecoderException extends DecoderException {
    public BundledAlacDecoderException(String message) {
        super(message);
    }

    public BundledAlacDecoderException(String message, Throwable cause) {
        super(message, cause);
    }
}
