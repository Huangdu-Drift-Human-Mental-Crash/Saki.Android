package org.hdhmc.saki.playback

import org.hdhmc.saki.domain.model.AlacDecoderMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlacDecoderPolicyTest {
    @Test
    fun autoKeepsSystemPreferredAndBundledAvailable() {
        val policy = AlacDecoderPolicy(AlacDecoderMode.AUTO)

        assertFalse(policy.shouldDisableSystemDecoder())
        assertTrue(policy.isBundledDecoderEnabled())
    }

    @Test
    fun systemModeStrictlyDisablesBundledDecoder() {
        val policy = AlacDecoderPolicy(AlacDecoderMode.SYSTEM)

        assertFalse(policy.shouldDisableSystemDecoder())
        assertFalse(policy.isBundledDecoderEnabled())
        assertFalse(policy.markAutoSystemDecoderFailed())
    }

    @Test
    fun bundledModeDisablesOnlySystemAlacRoute() {
        val policy = AlacDecoderPolicy(AlacDecoderMode.BUNDLED)

        assertTrue(policy.shouldDisableSystemDecoder())
        assertTrue(policy.isBundledDecoderEnabled())
        assertFalse(policy.markAutoSystemDecoderFailed())
    }

    @Test
    fun autoFailureFallsBackOnceAndModeChangeResetsIt() {
        val policy = AlacDecoderPolicy(AlacDecoderMode.AUTO)

        assertTrue(policy.markAutoSystemDecoderFailed())
        assertTrue(policy.shouldDisableSystemDecoder())
        assertFalse(policy.markAutoSystemDecoderFailed())

        assertTrue(policy.updateMode(AlacDecoderMode.SYSTEM))
        assertTrue(policy.updateMode(AlacDecoderMode.AUTO))
        assertFalse(policy.shouldDisableSystemDecoder())
        assertFalse(policy.updateMode(AlacDecoderMode.AUTO))
    }
}
