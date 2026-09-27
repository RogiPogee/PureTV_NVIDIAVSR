package com.puretv.twitch.desktop.player

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val SCREENSHOT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS")

internal fun sanitizeScreenshotStem(value: String): String =
    value.trim()
        .replace(Regex("""[^A-Za-z0-9._-]+"""), "_")
        .trim('_')
        .ifBlank { "stream" }
        .take(64)

internal fun nextScreenshotFile(channelLogin: String, now: LocalDateTime = LocalDateTime.now()): File {
    val home = File(System.getProperty("user.home"))
    val dir = File(home, "Pictures/PureTV")
    dir.mkdirs()
    val stem = sanitizeScreenshotStem(channelLogin)
    val base = "PureTV_${stem}_${SCREENSHOT_TIME.format(now)}"
    var file = File(dir, "$base.png")
    var suffix = 2
    while (file.exists()) {
        file = File(dir, "$base-${suffix++}.png")
    }
    return file
}
