package com.puretv.twitch.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.puretv.twitch.core.model.PlaybackBackend
import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.core.model.UpscalingMode
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType

/**
 * The in-player "Playback" menu: one panel reused by the live stream and VOD
 * players (highlights are VODs). It consolidates the per-playback controls:
 * Resolution (source quality, live), Scaling (mpv shaders or VLC RTX VSR), and
 * Engine (restart-gated). Rendered in the player Column (NOT floating over the
 * video, the heavyweight AWT Canvas paints above Compose), styled to the
 * Cinémathèque system. Stateless: the caller owns current values + callbacks.
 */
@Composable
fun PlayerSettingsMenu(
    currentQuality: StreamQuality,
    onQualitySelected: (StreamQuality) -> Unit,
    upscalingMode: UpscalingMode,
    onUpscalingSelected: (UpscalingMode) -> Unit,
    scalingEnabled: Boolean,
    activeBackend: PlaybackBackend,
    selectedBackend: PlaybackBackend,
    onBackendSelected: (PlaybackBackend) -> Unit,
    alwaysOnTop: Boolean,
    onAlwaysOnTopSelected: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PureTvTheme.colors
    val scalingOptions = when (activeBackend) {
        PlaybackBackend.VLC -> listOf(UpscalingMode.OFF, UpscalingMode.NVIDIA_RTX_VSR)
        PlaybackBackend.MPV -> listOf(UpscalingMode.OFF, UpscalingMode.STANDARD, UpscalingMode.ANIME)
    }
    // A persisted mode may belong to the other backend after an engine switch.
    // Render that state as Off until the user chooses a mode for the selected engine.
    val displayedUpscalingMode = upscalingMode.takeIf { it in scalingOptions } ?: UpscalingMode.OFF

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(c.surface)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        PlayerMenuSection("Resolution") {
            SegmentedControl(StreamQuality.entries.toList(), currentQuality, { it.label }, onQualitySelected)
        }
        PlayerMenuSection("Scaling") {
            if (scalingEnabled) {
                SegmentedControl(scalingOptions, displayedUpscalingMode, { it.label }, onUpscalingSelected)
                Text(
                    when (activeBackend) {
                        PlaybackBackend.VLC ->
                            "RTX VSR uses VLC Direct3D 11 Super Resolution on compatible NVIDIA RTX GPUs. Scaling changes apply after restart."
                        PlaybackBackend.MPV ->
                            "Sharp = general; Anime = animation. Hold X to compare against Off, F3 for live stats."
                    },
                    style = PureTvType.dataSmall,
                    color = c.outline,
                )
            } else {
                Text(
                    "GPU upscaling is unavailable for the selected engine.",
                    style = PureTvType.data,
                    color = c.outline,
                )
            }
        }
        PlayerMenuSection("Engine") {
            SegmentedControl(PlaybackBackend.entries.toList(), selectedBackend, { it.label }, onBackendSelected)
            Text(
                if (selectedBackend == activeBackend) "Engine changes apply after restart."
                else "Restart to switch from ${activeBackend.label} to ${selectedBackend.label}.",
                style = PureTvType.dataSmall,
                color = c.outline,
            )
        }
        PlayerMenuSection("Window") {
            SegmentedControl(
                options = listOf(false, true),
                selected = alwaysOnTop,
                label = { if (it) "Always on top" else "Normal" },
                onSelect = onAlwaysOnTopSelected,
            )
            Text(
                "Always on top keeps PureTV above other windows while you play or work.",
                style = PureTvType.dataSmall,
                color = c.outline,
            )
        }
    }
}

@Composable
private fun PlayerMenuSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Kicker(title)
        content()
    }
}
