package org.hdhmc.saki.decoder.alac;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public final class AlacDecoderCoreTest {
    private static final byte[] CONFIG_16_BIT = bytes(
            0x00, 0x00, 0x10, 0x00, 0x00, 0x10, 0x28, 0x0a,
            0x0e, 0x02, 0x00, 0x00, 0x00, 0x00, 0x40, 0x04,
            0x00, 0x15, 0x88, 0x80, 0x00, 0x00, 0xac, 0x44);
    private static final byte[] CONFIG_24_BIT = bytes(
            0x00, 0x00, 0x10, 0x00, 0x00, 0x18, 0x28, 0x0a,
            0x0e, 0x02, 0x00, 0x00, 0x00, 0x00, 0x60, 0x04,
            0x00, 0x20, 0x4c, 0xc0, 0x00, 0x00, 0xac, 0x44);

    @Test
    public void decodes16BitPacketsLikeFfmpeg() throws Exception {
        AlacDecoderCore core = new AlacDecoderCore(AlacStreamInfo.parse(CONFIG_16_BIT));

        assertDecoded(
                core,
                "alac/sine-stereo-16-first.packet",
                16_384,
                "4bdc0f02cb43aa847e52be7b41ffdc2d3666110f0e2e83754f042e668001115a");
        assertDecoded(
                core,
                "alac/sine-stereo-16-tail.packet",
                1_256,
                "88b45e4c52109c8bd5a7c198422b72abbcf0a3e50e65f8cbca3c566992c3e56a");
    }

    @Test
    public void decodes24BitPacketsLikeFfmpeg() throws Exception {
        AlacDecoderCore core = new AlacDecoderCore(AlacStreamInfo.parse(CONFIG_24_BIT));

        assertDecoded(
                core,
                "alac/sine-stereo-24-first.packet",
                24_576,
                "7f1bb45763ebdff526194980fe9aea20532a01834ca6d9ae2f70c083f48bc26b");
        assertDecoded(
                core,
                "alac/sine-stereo-24-tail.packet",
                1_884,
                "a513bb9701bfd1a6fe51c3eb4f79522c9a8b2b00e1441e989b8ff0c693c16d1f");
    }

    @Test
    public void rejectsTruncatedPacketAfterLargerPacket() throws Exception {
        AlacDecoderCore core = new AlacDecoderCore(AlacStreamInfo.parse(CONFIG_16_BIT));
        byte[] packet = resource("alac/sine-stereo-16-first.packet");
        core.decode(ByteBuffer.wrap(packet), ByteBuffer.allocate(16_384));

        byte[] truncated = Arrays.copyOf(packet, packet.length / 2);
        assertThrows(
                BundledAlacDecoderException.class,
                () -> core.decode(ByteBuffer.wrap(truncated), ByteBuffer.allocate(16_384)));
    }

    @Test
    public void validatesBundledDecoderLimits() {
        byte[] unsupportedVersion = CONFIG_16_BIT.clone();
        unsupportedVersion[4] = 1;
        byte[] unsupportedDepth = CONFIG_16_BIT.clone();
        unsupportedDepth[5] = 20;
        byte[] unsupportedChannels = CONFIG_16_BIT.clone();
        unsupportedChannels[9] = 3;
        byte[] zeroFrameLength = CONFIG_16_BIT.clone();
        Arrays.fill(zeroFrameLength, 0, 4, (byte) 0);
        byte[] oversizedFrame = CONFIG_16_BIT.clone();
        oversizedFrame[2] = 0x40;
        oversizedFrame[3] = 0x01;

        assertThrows(IllegalArgumentException.class, () -> AlacStreamInfo.parse(unsupportedVersion));
        assertThrows(IllegalArgumentException.class, () -> AlacStreamInfo.parse(unsupportedDepth));
        assertThrows(IllegalArgumentException.class, () -> AlacStreamInfo.parse(unsupportedChannels));
        assertThrows(IllegalArgumentException.class, () -> AlacStreamInfo.parse(zeroFrameLength));
        assertThrows(IllegalArgumentException.class, () -> AlacStreamInfo.parse(oversizedFrame));
        assertThrows(
                IllegalArgumentException.class,
                () -> AlacStreamInfo.fromInitializationData(Collections.emptyList()));
    }

    private static void assertDecoded(
            AlacDecoderCore core,
            String resourceName,
            int expectedByteCount,
            String expectedSha256) throws Exception {
        ByteBuffer output = ByteBuffer.allocate(24_576);
        int decodedByteCount = core.decode(ByteBuffer.wrap(resource(resourceName)), output);

        assertEquals(expectedByteCount, decodedByteCount);
        assertEquals(expectedByteCount, output.position());
        assertEquals(expectedSha256, sha256(Arrays.copyOf(output.array(), decodedByteCount)));
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream input = AlacDecoderCoreTest.class.getClassLoader().getResourceAsStream(name)) {
            if (input == null) {
                throw new IOException("Missing test resource: " + name);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4_096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }
}
