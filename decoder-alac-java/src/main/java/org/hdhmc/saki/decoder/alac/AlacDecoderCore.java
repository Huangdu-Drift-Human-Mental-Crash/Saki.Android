package org.hdhmc.saki.decoder.alac;

import androidx.media3.common.util.UnstableApi;
import com.beatofthedrum.alacdecoder.AlacDecodeUtils;
import com.beatofthedrum.alacdecoder.AlacFile;
import java.nio.ByteBuffer;

@UnstableApi
final class AlacDecoderCore {
    private static final int BIT_READER_PADDING_BYTES = 3;
    private static final int LEGACY_CONFIG_PREFIX_SIZE = 24;
    static final int MAX_INPUT_FRAME_BYTES = 1024 * 1024;

    private final AlacStreamInfo streamInfo;
    private final AlacFile alacFile;
    private final int[] decodedSamples;
    private final int maxInputFrameBytes;
    private byte[] inputBytes = new byte[0];

    AlacDecoderCore(AlacStreamInfo streamInfo) {
        this.streamInfo = streamInfo;
        alacFile = AlacDecodeUtils.create_alac(
                streamInfo.getSampleSize(), streamInfo.getChannelCount());
        int[] legacyConfig = new int[LEGACY_CONFIG_PREFIX_SIZE + 24];
        byte[] codecConfig = streamInfo.getCodecConfig();
        for (int i = 0; i < 24; i++) {
            legacyConfig[LEGACY_CONFIG_PREFIX_SIZE + i] = codecConfig[i] & 0xff;
        }
        AlacDecodeUtils.alac_set_info(alacFile, legacyConfig);
        decodedSamples = new int[streamInfo.getMaxDecodedFrameBytes()];
        int declaredMaxFrameBytes = streamInfo.getMaxFrameBytes();
        maxInputFrameBytes = declaredMaxFrameBytes > 0
                ? Math.min(declaredMaxFrameBytes, MAX_INPUT_FRAME_BYTES)
                : MAX_INPUT_FRAME_BYTES;
    }

    int decode(ByteBuffer input, ByteBuffer output) throws BundledAlacDecoderException {
        int inputSize = input.remaining();
        if (inputSize > maxInputFrameBytes) {
            throw new BundledAlacDecoderException(
                    "ALAC frame exceeds bundled decoder input limit: "
                            + inputSize + " > " + maxInputFrameBytes);
        }
        ensureInputCapacity(inputSize + BIT_READER_PADDING_BYTES);
        input.get(inputBytes, 0, inputSize);
        for (int i = inputSize; i < inputSize + BIT_READER_PADDING_BYTES; i++) {
            inputBytes[i] = 0;
        }

        final int decodedByteCount;
        try {
            decodedByteCount = AlacDecodeUtils.decode_frame(
                    alacFile,
                    inputBytes,
                    inputSize,
                    decodedSamples,
                    streamInfo.getMaxDecodedFrameBytes());
        } catch (RuntimeException error) {
            throw new BundledAlacDecoderException("Malformed ALAC frame", error);
        }
        int bytesPerFrame = streamInfo.getBytesPerSample() * streamInfo.getChannelCount();
        if (decodedByteCount <= 0
                || decodedByteCount > streamInfo.getMaxDecodedFrameBytes()
                || decodedByteCount % bytesPerFrame != 0) {
            throw new BundledAlacDecoderException(
                    "Invalid decoded ALAC frame size: " + decodedByteCount);
        }

        if (streamInfo.getSampleSize() == 16) {
            int sampleCount = decodedByteCount / 2;
            for (int i = 0; i < sampleCount; i++) {
                int sample = decodedSamples[i];
                output.put((byte) sample);
                output.put((byte) (sample >>> 8));
            }
        } else {
            for (int i = 0; i < decodedByteCount; i++) {
                output.put((byte) decodedSamples[i]);
            }
        }
        return decodedByteCount;
    }

    private void ensureInputCapacity(int requiredSize) {
        if (inputBytes.length < requiredSize) {
            inputBytes = new byte[requiredSize];
        }
    }
}
