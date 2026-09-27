package com.puretv.twitch.desktop.player

import com.puretv.twitch.core.model.PlaylistVariant
import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.core.stream.HlsMasterParser

/**
 * Chooses one deterministic HLS variant for a requested quality.
 *
 * Exact enum matches win. Source always resolves to the highest-fidelity stream.
 * If Twitch omits a named rung (common with unusual 936p/900p ladders), fixed
 * resolution requests choose the best variant at or below the target height
 * instead of returning the whole adaptive master and silently changing quality.
 */
internal fun selectPlaybackVariant(
    variants: List<PlaylistVariant>,
    quality: StreamQuality,
): PlaylistVariant? {
    variants.firstOrNull { it.quality == quality }?.let { return it }

    if (quality == StreamQuality.SOURCE) {
        return HlsMasterParser.highestQualityVariant(variants)
    }
    if (quality == StreamQuality.AUTO || quality == StreamQuality.AUDIO_ONLY) return null

    val targetHeight = when (quality) {
        StreamQuality.P1080P60 -> 1080
        StreamQuality.P720P60 -> 720
        StreamQuality.P480P -> 480
        StreamQuality.P360P -> 360
        else -> return null
    }

    val video = variants.mapNotNull { v ->
        val h = v.resolution.substringAfter('x', "").toIntOrNull() ?: return@mapNotNull null
        v to h
    }
    if (video.isEmpty()) return null

    val comparator = compareBy<Pair<PlaylistVariant, Int>> { it.second }
        .thenBy { it.first.frameRate }
        .thenBy { it.first.bandwidth }

    return video.filter { it.second <= targetHeight }
        .maxWithOrNull(comparator)
        ?.first
        ?: video.minWithOrNull(comparator)?.first
}
