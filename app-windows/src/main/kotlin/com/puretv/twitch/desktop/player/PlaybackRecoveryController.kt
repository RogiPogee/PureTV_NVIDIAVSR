package com.puretv.twitch.desktop.player

import com.puretv.twitch.core.model.StreamQuality

internal enum class PlaybackRecoveryAction {
    NONE,
    RELOAD_CURRENT,
    FALLBACK_720P,
    RESTORE_SOURCE,
}

/**
 * Coordinates live-stream self-healing without permanently sacrificing quality.
 *
 * A single player fault reloads the current stream. If Source faults again soon
 * after that recovery attempt, playback temporarily drops to 720p60. Once the
 * fallback has played cleanly for [fallbackStableMs], Source is tried again.
 *
 * Persistent error callbacks are rate-limited by [recoveryCooldownMs], otherwise
 * one error state sampled every two seconds would look like many independent
 * failures and immediately force a fallback.
 */
internal class PlaybackRecoveryController(
    private val stallWatchdog: PlaybackStallWatchdog = PlaybackStallWatchdog(),
    private val repeatedFaultWindowMs: Long = 30_000,
    private val recoveryCooldownMs: Long = 8_000,
    private val fallbackStableMs: Long = 45_000,
) {
    private var lastFaultAtMs: Long? = null
    private var lastRecoveryAtMs: Long? = null
    private var fallbackActive = false
    private var stableFallbackSinceMs: Long? = null

    fun sample(
        status: PlayerStatus,
        preferredQuality: StreamQuality,
        currentQuality: StreamQuality,
        nowMs: Long,
    ): PlaybackRecoveryAction {
        val fault = status.error != null || stallWatchdog.sample(status, nowMs)

        if (preferredQuality != StreamQuality.SOURCE) {
            fallbackActive = false
            stableFallbackSinceMs = null
            if (!fault || isCoolingDown(nowMs)) return PlaybackRecoveryAction.NONE
            markRecovery(nowMs)
            return PlaybackRecoveryAction.RELOAD_CURRENT
        }

        if (fallbackActive || currentQuality != StreamQuality.SOURCE) {
            if (fault) {
                stableFallbackSinceMs = null
                if (isCoolingDown(nowMs)) return PlaybackRecoveryAction.NONE
                markRecovery(nowMs)
                return PlaybackRecoveryAction.RELOAD_CURRENT
            }

            if (status.isPlaying && !status.isBuffering && status.error == null) {
                val since = stableFallbackSinceMs
                if (since == null) {
                    stableFallbackSinceMs = nowMs
                } else if (nowMs - since >= fallbackStableMs) {
                    fallbackActive = false
                    stableFallbackSinceMs = null
                    lastFaultAtMs = null
                    lastRecoveryAtMs = nowMs
                    stallWatchdog.reset()
                    return PlaybackRecoveryAction.RESTORE_SOURCE
                }
            } else {
                stableFallbackSinceMs = null
            }
            return PlaybackRecoveryAction.NONE
        }

        if (!fault || isCoolingDown(nowMs)) return PlaybackRecoveryAction.NONE

        val previousFault = lastFaultAtMs
        lastFaultAtMs = nowMs
        lastRecoveryAtMs = nowMs
        stallWatchdog.reset()

        return if (previousFault != null && nowMs - previousFault <= repeatedFaultWindowMs) {
            fallbackActive = true
            stableFallbackSinceMs = null
            PlaybackRecoveryAction.FALLBACK_720P
        } else {
            PlaybackRecoveryAction.RELOAD_CURRENT
        }
    }

    fun reset() {
        lastFaultAtMs = null
        lastRecoveryAtMs = null
        fallbackActive = false
        stableFallbackSinceMs = null
        stallWatchdog.reset()
    }

    private fun isCoolingDown(nowMs: Long): Boolean =
        lastRecoveryAtMs?.let { nowMs - it < recoveryCooldownMs } == true

    private fun markRecovery(nowMs: Long) {
        lastFaultAtMs = nowMs
        lastRecoveryAtMs = nowMs
        stallWatchdog.reset()
    }
}
