package com.puretv.twitch.desktop.player

import com.puretv.twitch.core.model.StreamQuality
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackRecoveryControllerTest {
    @Test
    fun sourceReloadsOnceThenFallsBackOnRepeatedFault() {
        val c = PlaybackRecoveryController(
            repeatedFaultWindowMs = 30_000,
            recoveryCooldownMs = 8_000,
            fallbackStableMs = 45_000,
        )
        val broken = PlayerStatus(error = "network")

        assertEquals(
            PlaybackRecoveryAction.RELOAD_CURRENT,
            c.sample(broken, StreamQuality.SOURCE, StreamQuality.SOURCE, 0),
        )
        assertEquals(
            PlaybackRecoveryAction.NONE,
            c.sample(broken, StreamQuality.SOURCE, StreamQuality.SOURCE, 2_000),
        )
        assertEquals(
            PlaybackRecoveryAction.FALLBACK_720P,
            c.sample(broken, StreamQuality.SOURCE, StreamQuality.SOURCE, 8_000),
        )
    }

    @Test
    fun cleanFallbackReturnsToSourceAfterStableWindow() {
        val c = PlaybackRecoveryController(
            repeatedFaultWindowMs = 30_000,
            recoveryCooldownMs = 1_000,
            fallbackStableMs = 10_000,
        )
        val broken = PlayerStatus(error = "network")
        c.sample(broken, StreamQuality.SOURCE, StreamQuality.SOURCE, 0)
        c.sample(broken, StreamQuality.SOURCE, StreamQuality.SOURCE, 1_000)

        val healthy = PlayerStatus(isPlaying = true, positionMs = 1_000)
        assertEquals(
            PlaybackRecoveryAction.NONE,
            c.sample(healthy, StreamQuality.SOURCE, StreamQuality.P720P60, 2_000),
        )
        assertEquals(
            PlaybackRecoveryAction.RESTORE_SOURCE,
            c.sample(healthy.copy(positionMs = 11_000), StreamQuality.SOURCE, StreamQuality.P720P60, 12_000),
        )
    }

    @Test
    fun manuallySelectedQualityNeverAutoSwitchesToSourceOrAnotherRung() {
        val c = PlaybackRecoveryController(recoveryCooldownMs = 1_000)
        val broken = PlayerStatus(error = "network")

        assertEquals(
            PlaybackRecoveryAction.RELOAD_CURRENT,
            c.sample(broken, StreamQuality.P720P60, StreamQuality.P720P60, 0),
        )
        assertEquals(
            PlaybackRecoveryAction.RELOAD_CURRENT,
            c.sample(broken, StreamQuality.P720P60, StreamQuality.P720P60, 1_000),
        )
    }
}
