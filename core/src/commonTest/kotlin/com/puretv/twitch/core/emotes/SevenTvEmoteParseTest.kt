package com.puretv.twitch.core.emotes

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class SevenTvEmoteParseTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun obj(s: String) = json.parseToJsonElement(s).jsonObject

    @Test fun detectsZeroWidthFromActiveFlag() {
        val e = obj("""{"id":"1","name":"ZW","flags":1,"data":{"animated":false}}""").toSevenTvEmote()
        assertEquals(true, e.zeroWidth)
    }

    @Test fun detectsZeroWidthFromDataFlag() {
        val e = obj("""{"id":"1","name":"ZW","flags":0,"data":{"animated":true,"flags":256}}""").toSevenTvEmote()
        assertEquals(true, e.zeroWidth)
        assertEquals(true, e.animated)
    }

    @Test fun normalAnimatedEmoteIsNotZeroWidth() {
        val e = obj("""{"id":"60a","name":"catJAM","flags":0,"data":{"animated":true,"flags":0}}""").toSevenTvEmote()
        assertEquals(false, e.zeroWidth)
        assertEquals(true, e.animated)
        assertEquals("https://cdn.7tv.app/emote/60a/4x.webp", e.url)
    }

    @Test fun usesAdvertisedWebpInsteadOfAssumingStaticPngExists() {
        val e = obj(
            """{
                "id":"abc",
                "name":"WidePeepo",
                "flags":0,
                "data":{
                    "animated":false,
                    "flags":0,
                    "host":{
                        "url":"//cdn.7tv.app/emote/abc",
                        "files":[
                            {"name":"1x.webp"},
                            {"name":"2x.webp"},
                            {"name":"4x.webp"}
                        ]
                    }
                }
            }""",
        ).toSevenTvEmote()
        assertEquals("https://cdn.7tv.app/emote/abc/4x.webp", e.url)
    }

    @Test fun choosesHighestAdvertisedScale() {
        val e = obj(
            """{
                "id":"xyz",
                "name":"OnlySmaller",
                "data":{
                    "animated":false,
                    "host":{
                        "url":"https://cdn.7tv.app/emote/xyz/",
                        "files":[{"name":"1x.webp"},{"name":"3x.webp"}]
                    }
                }
            }""",
        ).toSevenTvEmote()
        assertEquals("https://cdn.7tv.app/emote/xyz/3x.webp", e.url)
    }
}
