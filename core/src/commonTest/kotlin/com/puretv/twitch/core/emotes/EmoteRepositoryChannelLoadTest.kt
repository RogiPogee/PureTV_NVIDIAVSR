package com.puretv.twitch.core.emotes

import com.puretv.twitch.core.CallCounter
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class EmoteRepositoryChannelLoadTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test fun channelLoadUsesCompleteSevenTvSetEndpoint() = runTest {
        val fullSetCalls = CallCounter()
        val engine = MockEngine { request ->
            val url = request.url.toString()
            when {
                "betterttv.net/3/cached/users/twitch/123" in url ->
                    respond("""{"channelEmotes":[],"sharedEmotes":[]}""", HttpStatusCode.OK, jsonHeaders)
                "frankerfacez.com/v1/room/tester" in url ->
                    respond("""{"sets":{}}""", HttpStatusCode.OK, jsonHeaders)
                "7tv.io/v3/users/twitch/123" in url ->
                    respond(
                        """{"emote_set":{"id":"set1","emotes":[{"id":"a","name":"One","data":{"animated":false}}]}}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                "7tv.io/v3/emote-sets/set1" in url -> {
                    fullSetCalls.incrementAndGet()
                    respond(
                        """{"id":"set1","emotes":[
                            {"id":"a","name":"One","data":{"animated":false}},
                            {"id":"b","name":"Two","data":{"animated":false}}
                        ]}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val repo = EmoteRepository(client, InMemoryEmoteCache())

        val result = repo.loadChannelEmotes("123", "tester")

        assertEquals(1, fullSetCalls.value)
        assertEquals(setOf("One", "Two"), result.map { it.name }.toSet())
    }
}
