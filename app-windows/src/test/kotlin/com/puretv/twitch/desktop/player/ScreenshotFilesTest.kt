package com.puretv.twitch.desktop.player

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScreenshotFilesTest {
    @Test fun sanitizesChannelForWindowsFilename() {
        assertEquals("some_channel_name", sanitizeScreenshotStem(" some/channel:name "))
    }

    @Test fun usesNativePngScreenshotFilename() {
        val f = nextScreenshotFile("tester", LocalDateTime.of(2026, 9, 27, 12, 34, 56, 789_000_000))
        assertTrue(f.name.startsWith("PureTV_tester_2026-09-27_12-34-56-789"))
        assertTrue(f.name.endsWith(".png"))
    }
}
