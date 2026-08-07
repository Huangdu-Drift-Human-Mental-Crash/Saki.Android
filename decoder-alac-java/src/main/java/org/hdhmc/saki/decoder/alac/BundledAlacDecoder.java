package org.hdhmc.saki.decoder.alac;

import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.decoder.SimpleDecoder;
import androidx.media3.decoder.SimpleDecoderOutputBuffer;
import java.nio.ByteBuffer;

@UnstableApi
final class BundledAlacDecoder extends SimpleDecoder<
        DecoderInputBuffer, SimpleDecoderOutputBuffer, BundledAlacDecoderException> {
    private static final int BUFFER_COUNT = 8;
    private static final int DEFAULT_INPUT_BUFFER_SIZE = 64 * 1024;
    private static final int MAX_INITIAL_INPUT_BUFFER_SIZE = 1024 * 1024;

    private final AlacStreamInfo streamInfo;
    private final AlacDecoderCore decoderCore;

    BundledAlacDecoder(Format format) throws BundledAlacDecoderException {
        super(
                new DecoderInputBuffer[BUFFER_COUNT],
                new SimpleDecoderOutputBuffer[BUFFER_COUNT]);
        try {
            streamInfo = AlacStreamInfo.fromInitializationData(format.initializationData);
            decoderCore = new AlacDecoderCore(streamInfo);
        } catch (IllegalArgumentException error) {
            throw new BundledAlacDecoderException("Invalid ALAC configuration", error);
        }
        int initialInputSize = format.maxInputSize != Format.NO_VALUE
                ? format.maxInputSize
                : streamInfo.getMaxFrameBytes();
        if (initialInputSize <= 0) {
            initialInputSize = DEFAULT_INPUT_BUFFER_SIZE;
        }
        setInitialInputBufferSize(Math.min(initialInputSize, MAX_INITIAL_INPUT_BUFFER_SIZE));
    }

    AlacStreamInfo getStreamInfo() {
        return streamInfo;
    }

    @Override
    public String getName() {
        return "SakiBundledAlacDecoder";
    }

    @Override
    protected DecoderInputBuffer createInputBuffer() {
        return new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);
    }

    @Override
    protected SimpleDecoderOutputBuffer createOutputBuffer() {
        return new SimpleDecoderOutputBuffer(this::releaseOutputBuffer);
    }

    @Override
    protected BundledAlacDecoderException createUnexpectedDecodeException(Throwable error) {
        return new BundledAlacDecoderException("Unexpected ALAC decode error", error);
    }

    @Override
    protected BundledAlacDecoderException decode(
            DecoderInputBuffer inputBuffer,
            SimpleDecoderOutputBuffer outputBuffer,
            boolean reset) {
        ByteBuffer inputData = Util.castNonNull(inputBuffer.data);
        ByteBuffer outputData = outputBuffer.init(
                inputBuffer.timeUs, streamInfo.getMaxDecodedFrameBytes());
        try {
            decoderCore.decode(inputData, outputData);
            outputData.flip();
            return null;
        } catch (BundledAlacDecoderException error) {
            return error;
        }
    }
}
