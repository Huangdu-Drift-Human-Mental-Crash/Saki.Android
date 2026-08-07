# Bundled Java ALAC decoder

This module is a pure Java Media3 decoder extension for demuxed ALAC packets.
It currently supports 16-bit and 24-bit mono or stereo ALAC in MP4/M4A
containers. The bundled core does not support 20-bit, 32-bit, or multichannel
ALAC; Media3 reports those formats as unsupported.

The four files under `com.beatofthedrum.alacdecoder` are derived from
[Java-Apple-Lossless-decoder](https://github.com/soiaf/Java-Apple-Lossless-decoder)
at commit `afd5bf30ba51a80835f1eb4797c0bfe434e112de`. They are distributed under
the BSD license in `LICENSE`. Saki's Media3 adapter is separate from the
vendored decoder core.

The test packets are synthetic 997 Hz sine waves generated with FFmpeg. Their
decoded output is checked against SHA-256 hashes of FFmpeg's reference PCM.
