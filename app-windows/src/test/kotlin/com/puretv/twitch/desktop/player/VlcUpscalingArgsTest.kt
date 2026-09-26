package com.puretv.twitch.desktop.player

import com.puretv.twitch.core.model.UpscalingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VlcUpscalingArgsTest {
    @Test fun rtxVsrForcesD3d11AndSuperResolution() {
        val args = vlcUpscalingArgs(UpscalingMode.NVIDIA_RTX_VSR)
        assertTrue("--vout=direct3d11" in args)
        assertTrue("--d3d11-upscale-mode=super" in args)
    }

    @Test fun offUsesLinearWithoutForcingD3d11() {
        val args = vlcUpscalingArgs(UpscalingMode.OFF)
        assertEquals(listOf("--d3d11-upscale-mode=linear"), args)
        assertFalse("--vout=direct3d11" in args)
    }

    @Test fun mpvSpecificModesDoNotAccidentallyEnableVsrInVlc() {
        for (mode in listOf(UpscalingMode.STANDARD, UpscalingMode.ANIME)) {
            val args = vlcUpscalingArgs(mode)
            assertEquals(listOf("--d3d11-upscale-mode=linear"), args)
        }
    }
}
