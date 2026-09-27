package com.puretv.twitch.core.emotes

import com.puretv.twitch.core.model.ChannelEmote
import com.puretv.twitch.core.model.EmoteProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * SECTION 05.3 — fetches & caches third-party emotes from BTTV, FFZ, and 7TV.
 *
 * Cache strategy (per the spec):
 *   - persist emote metadata in Room (Android/TV) / SQLite (Desktop) — see
 *     [EmoteCache] for the storage-agnostic interface this repo writes through
 *   - emote *images* are cached by Coil's disk cache, not here
 *   - channel emotes refresh on every channel join; globals refresh once/session
 */
class EmoteRepository(
    private val httpClient: HttpClient,
    private val cache: EmoteCache,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private var globalsLoadedThisSession = false

    suspend fun loadGlobalEmotes(): List<ChannelEmote> {
        if (globalsLoadedThisSession) cache.globalEmotes()?.let { return it }
        return coroutineScope {
            val bttvResult = async { runCatching { fetchBttvGlobal() } }
            val seventvResult = async { runCatching { fetchSevenTvGlobal() } }
            val bttv = bttvResult.await()
            val seventv = seventvResult.await()
            val all = bttv.getOrDefault(emptyList()) + seventv.getOrDefault(emptyList())
            if (bttv.isSuccess && seventv.isSuccess) {
                cache.putGlobalEmotes(all)
                globalsLoadedThisSession = true
            }
            all
        }
    }

    suspend fun loadChannelEmotes(channelId: String, channelLogin: String): List<ChannelEmote> = coroutineScope {
        val bttv = async { runCatching { fetchBttvChannel(channelId) } }
        val ffz = async { runCatching { fetchFfzChannel(channelLogin) } }
        val seventv = async { runCatching { fetchSevenTvChannel(channelId) } }
        val bttvResult = bttv.await()
        val ffzResult = ffz.await()
        val seventvResult = seventv.await()
        val all = bttvResult.getOrDefault(emptyList()) +
            ffzResult.getOrDefault(emptyList()) +
            seventvResult.getOrDefault(emptyList())
        // Only persist when every provider fetch succeeded (audit M3). Mirrors
        // loadGlobalEmotes: caching a partial/all-failed combined set would
        // clobber a good cached set with a degraded or empty one on a transient
        // outage (offline, provider 5xx). The live (possibly partial) `all` is
        // still returned to the caller for this session.
        if (bttvResult.isSuccess && ffzResult.isSuccess && seventvResult.isSuccess) {
            cache.putChannelEmotes(channelId, all)
        }
        all
    }

    // ---- BTTV ----

    private suspend fun fetchBttvGlobal(): List<ChannelEmote> {
        val raw: JsonArray = httpClient.get("https://api.betterttv.net/3/cached/emotes/global").body()
        // Skip individual malformed entries instead of failing the whole set (audit L6).
        return raw.mapNotNull { runCatching { it.jsonObject.toBttvEmote(channelEmote = false) }.getOrNull() }
    }

    private suspend fun fetchBttvChannel(channelId: String): List<ChannelEmote> {
        val raw: JsonObject = httpClient.get("https://api.betterttv.net/3/cached/users/twitch/$channelId").body()
        val channelEmotes = raw["channelEmotes"]?.jsonArray.orEmpty()
        val sharedEmotes = raw["sharedEmotes"]?.jsonArray.orEmpty()
        return (channelEmotes + sharedEmotes).mapNotNull { runCatching { it.jsonObject.toBttvEmote(channelEmote = true) }.getOrNull() }
    }

    private fun JsonObject.toBttvEmote(channelEmote: Boolean): ChannelEmote {
        val id = this["id"]!!.jsonPrimitive.content
        val code = this["code"]!!.jsonPrimitive.content
        val imageType = this["imageType"]?.jsonPrimitive?.contentOrNull ?: "png"
        return ChannelEmote(
            id = id,
            name = code,
            url = "https://cdn.betterttv.net/emote/$id/3x",
            provider = EmoteProvider.BTTV,
            animated = imageType == "gif",
        )
    }

    // ---- FFZ ----

    private suspend fun fetchFfzChannel(channelLogin: String): List<ChannelEmote> {
        val raw: JsonObject = httpClient.get("https://api.frankerfacez.com/v1/room/$channelLogin").body()
        val sets = raw["sets"]?.jsonObject ?: return emptyList()
        return sets.values.flatMap { set ->
            set.jsonObject["emoticons"]?.jsonArray.orEmpty()
                .mapNotNull { runCatching { it.jsonObject.toFfzEmote() }.getOrNull() } // skip malformed entries (audit L6)
        }
    }

    private fun JsonObject.toFfzEmote(): ChannelEmote {
        val id = this["id"]!!.jsonPrimitive.content
        val name = this["name"]!!.jsonPrimitive.content
        val urls = this["urls"]?.jsonObject
        val bestUrl = urls?.get("4") ?: urls?.get("2") ?: urls?.get("1")
        return ChannelEmote(
            id = id,
            name = name,
            url = bestUrl?.jsonPrimitive?.content?.let { if (it.startsWith("http")) it else "https:$it" } ?: "",
            provider = EmoteProvider.FFZ,
            animated = false,
        )
    }

    // ---- 7TV ----

    private suspend fun fetchSevenTvGlobal(): List<ChannelEmote> {
        val raw: JsonObject = httpClient.get("https://7tv.io/v3/emote-sets/global").body()
        return raw["emotes"]?.jsonArray.orEmpty()
            .mapNotNull { runCatching { it.jsonObject.toSevenTvEmote() }.getOrNull() } // skip malformed entries (audit L6)
    }

    private suspend fun fetchSevenTvChannel(channelId: String): List<ChannelEmote> {
        val user: JsonObject = httpClient.get("https://7tv.io/v3/users/twitch/$channelId").body()
        val embeddedSet = user["emote_set"]?.jsonObject ?: return emptyList()

        // Fetch the emote set itself by id so the picker gets the complete,
        // authoritative active set. Fall back to the embedded user payload.
        val setId = embeddedSet["id"]?.jsonPrimitive?.contentOrNull
        val fullSet = if (setId.isNullOrBlank()) {
            embeddedSet
        } else {
            runCatching {
                httpClient.get("https://7tv.io/v3/emote-sets/$setId").body<JsonObject>()
            }.getOrNull() ?: embeddedSet
        }
        return parseSevenTvSet(fullSet)
    }

    private fun parseSevenTvSet(set: JsonObject): List<ChannelEmote> =
        set["emotes"]?.jsonArray.orEmpty()
            .mapNotNull { runCatching { it.jsonObject.toSevenTvEmote() }.getOrNull() }
}

internal fun JsonObject.toSevenTvEmote(): ChannelEmote {
    val id = this["id"]!!.jsonPrimitive.content
    val name = this["name"]!!.jsonPrimitive.content
    val data = this["data"]?.jsonObject
    val animated = data?.get("animated")?.jsonPrimitive?.boolean ?: false

    // Use the exact CDN asset list advertised by 7TV. Static emotes are not
    // guaranteed to expose a PNG at every scale, so the old hard-coded 4x.png
    // path could leave valid emotes blank in the picker.
    val host = data?.get("host")?.jsonObject
    val rawHostUrl = host?.get("url")?.jsonPrimitive?.contentOrNull
    val hostUrl = when {
        rawHostUrl.isNullOrBlank() -> "https://cdn.7tv.app/emote/$id"
        rawHostUrl.startsWith("//") -> "https:$rawHostUrl"
        rawHostUrl.startsWith("http://") || rawHostUrl.startsWith("https://") -> rawHostUrl
        rawHostUrl.startsWith("/") -> "https://cdn.7tv.app$rawHostUrl"
        else -> "https://$rawHostUrl"
    }.trimEnd('/')

    val advertisedFiles = host?.get("files")?.jsonArray.orEmpty().mapNotNull { file ->
        runCatching { file.jsonObject["name"]?.jsonPrimitive?.contentOrNull }.getOrNull()
    }

    // Chat renders emotes at roughly 28dp. Pulling 4x assets for every first-use
    // emote was visually indistinguishable at that size but noticeably slower,
    // especially for animated WebP where download + decode cost scales with pixel
    // count. Prefer 2x for animated emotes and 3x for static emotes, falling back
    // to the nearest advertised WebP scale. This keeps them crisp on HiDPI while
    // cutting first-load latency and decode work substantially.
    val bestFile = selectSevenTvAsset(advertisedFiles, animated)
        ?: if (animated) "2x.webp" else "3x.webp"

    // 7TV marks overlays two ways across its API surface.
    val activeFlags = this["flags"]?.jsonPrimitive?.intOrNull ?: 0
    val dataFlags = data?.get("flags")?.jsonPrimitive?.intOrNull ?: 0
    val zeroWidth = (activeFlags and 0x1) != 0 || (dataFlags and 0x100) != 0
    return ChannelEmote(
        id = id,
        name = name,
        url = "$hostUrl/$bestFile",
        provider = EmoteProvider.SEVENTV,
        animated = animated,
        zeroWidth = zeroWidth,
    )
}

internal fun selectSevenTvAsset(files: List<String>, animated: Boolean): String? {
    val targetScale = if (animated) 2 else 3
    val candidates = files.mapNotNull { file ->
        val scale = Regex("""^(\d+)x\.""").find(file)?.groupValues?.get(1)?.toIntOrNull()
            ?: return@mapNotNull null
        val formatRank = when (file.substringAfterLast('.', "").lowercase()) {
            "webp" -> 0
            "gif" -> 1
            "png" -> 2
            "avif" -> 3
            else -> 4
        }
        Triple(file, scale, formatRank)
    }
    if (candidates.isEmpty()) return null

    return candidates.minWithOrNull(
        compareBy<Triple<String, Int, Int>> { kotlin.math.abs(it.second - targetScale) }
            // At equal distance, keep the sharper scale rather than dropping lower.
            .thenByDescending { it.second }
            // Prefer WebP because our desktop path handles it reliably for static
            // and animated emotes and it is typically smaller than GIF/PNG.
            .thenBy { it.third },
    )?.first
}

/**
 * Storage-agnostic cache contract. Implemented by `RoomEmoteCache` on
 * Android/TV and `SqliteEmoteCache` on Desktop (see androidMain/desktopMain).
 */
interface EmoteCache {
    suspend fun globalEmotes(): List<ChannelEmote>?
    suspend fun putGlobalEmotes(emotes: List<ChannelEmote>)
    suspend fun channelEmotes(channelId: String): List<ChannelEmote>?
    suspend fun putChannelEmotes(channelId: String, emotes: List<ChannelEmote>)
}

/**
 * In-memory cache used in tests and as the Desktop default until SQLite-backed
 * cache lands. The single [EmoteRepository] is shared across screens, so rapid
 * channel switches (or PiP + a second stream) can call these overlapping; a
 * [Mutex] keeps the backing map from corrupting (mirrors ChannelRepository).
 */
class InMemoryEmoteCache : EmoteCache {
    private val lock = Mutex()
    private var globals: List<ChannelEmote>? = null
    private val channels = mutableMapOf<String, List<ChannelEmote>>()

    override suspend fun globalEmotes(): List<ChannelEmote>? = lock.withLock { globals }
    override suspend fun putGlobalEmotes(emotes: List<ChannelEmote>) = lock.withLock { globals = emotes }
    override suspend fun channelEmotes(channelId: String): List<ChannelEmote>? = lock.withLock { channels[channelId] }
    override suspend fun putChannelEmotes(channelId: String, emotes: List<ChannelEmote>) =
        lock.withLock { channels[channelId] = emotes }
}
