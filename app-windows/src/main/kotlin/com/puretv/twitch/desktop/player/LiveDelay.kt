package com.puretv.twitch.desktop.player

/**
 * Estimates how far playback is behind the newest point in a seekable live HLS
 * window using the backend's exposed timeline. Returns null when the backend
 * does not expose a plausible live window.
 *
 * This is intentionally a player-buffer delay, not an end-to-end broadcaster
 * latency claim. It is the quantity the "Jump to Live" control can actually
 * eliminate.
 */
internal fun liveDelayMs(status: PlayerStatus): Long? {
    if (!status.isSeekable || status.durationMs <= 0L) return null
    val behind = status.durationMs - status.positionMs
    if (behind < 0L || behind > 120_000L) return null
    return behind
}

internal fun formatLiveDelay(delayMs: Long?): String? {
    val ms = delayMs ?: return null
    val tenths = (ms + 50L) / 100L
    return "+${tenths / 10}.${tenths % 10}s"
}
