package com.puretv.twitch.desktop.player

import com.puretv.twitch.core.model.PlaylistVariant
import com.puretv.twitch.core.model.StreamQuality
import kotlin.test.Test
import kotlin.test.assertEquals

class QualityFallbackTest {
    private fun v(q: StreamQuality, res: String, fps: Double, bw: Long, url: String) =
        PlaylistVariant(q, res, fps, bw, url)

    @Test fun sourceUsesHighestAvailableVariant() {
        val variants = listOf(
            v(StreamQuality.P720P60, "1280x720", 60.0, 4_000_000, "720"),
            v(StreamQuality.AUTO, "1664x936", 60.0, 6_000_000, "936"),
        )
        assertEquals("936", selectPlaybackVariant(variants, StreamQuality.SOURCE)?.url)
    }

    @Test fun missing720ChoosesBestRungBelowTarget() {
        val variants = listOf(
            v(StreamQuality.AUTO, "1664x936", 60.0, 6_000_000, "936"),
            v(StreamQuality.AUTO, "852x480", 60.0, 2_000_000, "480"),
            v(StreamQuality.AUTO, "640x360", 30.0, 900_000, "360"),
        )
        assertEquals("480", selectPlaybackVariant(variants, StreamQuality.P720P60)?.url)
    }

    @Test fun exactRequestedRungAlwaysWins() {
        val variants = listOf(
            v(StreamQuality.P720P60, "1280x720", 60.0, 4_000_000, "exact"),
            v(StreamQuality.AUTO, "1280x720", 30.0, 5_000_000, "other"),
        )
        assertEquals("exact", selectPlaybackVariant(variants, StreamQuality.P720P60)?.url)
    }
}
