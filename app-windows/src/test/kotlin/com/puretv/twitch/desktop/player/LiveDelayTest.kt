package com.puretv.twitch.desktop.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LiveDelayTest {
    @Test fun derivesDelayFromSeekableLiveWindow() {
        assertEquals(
            2_350L,
            liveDelayMs(PlayerStatus(isSeekable = true, positionMs = 57_650, durationMs = 60_000)),
        )
        assertEquals("+2.4s", formatLiveDelay(2_350))
    }

    @Test fun hidesImplausibleOrUnavailableTimeline() {
        assertNull(liveDelayMs(PlayerStatus(positionMs = 10_000, durationMs = 20_000)))
        assertNull(liveDelayMs(PlayerStatus(isSeekable = true, positionMs = 0, durationMs = 500_000)))
        assertNull(liveDelayMs(PlayerStatus(isSeekable = true, positionMs = 30_000, durationMs = 20_000)))
    }
}
