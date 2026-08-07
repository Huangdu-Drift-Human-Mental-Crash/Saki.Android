package org.hdhmc.saki.decoder.alac;

import java.util.List;

/** Parsed ALACSpecificConfig data supplied by an MP4 extractor. */
public final class AlacStreamInfo {
    private static final int CONFIG_SIZE = 24;
    private static final int MAX_FRAME_SAMPLES = 16_384;

    private final byte[] codecConfig;
    private final int maxSamplesPerFrame;
    private final int sampleSize;
    private final int channelCount;
    private final int maxFrameBytes;
    private final int sampleRate;

    public static AlacStreamInfo fromInitializationData(List<byte[]> initializationData) {
        if (initializationData.size() != 1) {
            throw new IllegalArgumentException("ALAC initialization data must contain one entry");
        }
        return parse(initializationData.get(0));
    }

    public static AlacStreamInfo parse(byte[] config) {
        if (config == null || config.length < CONFIG_SIZE) {
            throw new IllegalArgumentException("ALACSpecificConfig must be at least 24 bytes");
        }
        int maxSamplesPerFrame = readPositiveInt(config, 0, "max samples per frame");
        int compatibleVersion = config[4] & 0xff;
        int sampleSize = config[5] & 0xff;
        int channelCount = config[9] & 0xff;
        int maxFrameBytes = readNonNegativeInt(config, 12);
        int sampleRate = readPositiveInt(config, 20, "sample rate");

        if (maxSamplesPerFrame > MAX_FRAME_SAMPLES) {
            throw new IllegalArgumentException(
                    "ALAC frame size exceeds bundled decoder limit: " + maxSamplesPerFrame);
        }
        if (compatibleVersion != 0) {
            throw new IllegalArgumentException(
                    "Unsupported ALAC compatible version: " + compatibleVersion);
        }
        if (sampleSize != 16 && sampleSize != 24) {
            throw new IllegalArgumentException(
                    "Bundled ALAC decoder supports 16-bit and 24-bit audio, not " + sampleSize);
        }
        if (channelCount < 1 || channelCount > 2) {
            throw new IllegalArgumentException(
                    "Bundled ALAC decoder supports mono and stereo audio, not " + channelCount
                            + " channels");
        }
        return new AlacStreamInfo(
                config.clone(),
                maxSamplesPerFrame,
                sampleSize,
                channelCount,
                maxFrameBytes,
                sampleRate);
    }

    private AlacStreamInfo(
            byte[] codecConfig,
            int maxSamplesPerFrame,
            int sampleSize,
            int channelCount,
            int maxFrameBytes,
            int sampleRate) {
        this.codecConfig = codecConfig;
        this.maxSamplesPerFrame = maxSamplesPerFrame;
        this.sampleSize = sampleSize;
        this.channelCount = channelCount;
        this.maxFrameBytes = maxFrameBytes;
        this.sampleRate = sampleRate;
    }

    public byte[] getCodecConfig() {
        return codecConfig.clone();
    }

    public int getMaxSamplesPerFrame() {
        return maxSamplesPerFrame;
    }

    public int getSampleSize() {
        return sampleSize;
    }

    public int getChannelCount() {
        return channelCount;
    }

    public int getMaxFrameBytes() {
        return maxFrameBytes;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public int getBytesPerSample() {
        return sampleSize / 8;
    }

    public int getMaxDecodedFrameBytes() {
        return maxSamplesPerFrame * channelCount * getBytesPerSample();
    }

    private static int readPositiveInt(byte[] data, int offset, String fieldName) {
        int value = readInt(data, offset);
        if (value <= 0) {
            throw new IllegalArgumentException("Invalid ALAC " + fieldName + ": " + value);
        }
        return value;
    }

    private static int readNonNegativeInt(byte[] data, int offset) {
        int value = readInt(data, offset);
        return value > 0 ? value : 0;
    }

    private static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xff) << 24)
                | ((data[offset + 1] & 0xff) << 16)
                | ((data[offset + 2] & 0xff) << 8)
                | (data[offset + 3] & 0xff);
    }
}
